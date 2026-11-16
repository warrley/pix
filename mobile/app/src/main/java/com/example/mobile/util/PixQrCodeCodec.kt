package com.example.mobile.util

import java.text.Normalizer

data class PixQrPayload(
    val pixKey: String,
    val recipientName: String = "",
    val amount: String? = null,
    val description: String? = null,
    val city: String = "SAO PAULO",
    val txId: String = "***"
)

/**
 * Encodes and decodes Brazilian Pix BR Code (EMVCo MPM) strings used both in QR Codes
 * and "Pix Copia e Cola".
 */
object PixQrCodeCodec {

    private const val PIX_GUI = "br.gov.bcb.pix"

    fun encode(payload: PixQrPayload): String {
        val key = payload.pixKey.trim()
        require(key.isNotEmpty()) { "Pix key must not be blank." }

        val guiField = tlv("00", PIX_GUI)
        val keyField = tlv("01", key)
        val descMaxLen = (99 - guiField.length - keyField.length - 4).coerceAtLeast(0)
        val desc = payload.description
            ?.let { sanitizeAscii(it).take(descMaxLen.coerceAtMost(40)) }
            ?.takeIf { it.isNotEmpty() }
        val merchantAccountInfo = buildString {
            append(guiField)
            append(keyField)
            if (desc != null) append(tlv("02", desc))
        }

        val normalizedAmount = payload.amount?.let { parseAmountInput(it) }
        val merchantName = sanitizeAscii(payload.recipientName)
            .take(25)
            .ifEmpty { "RUBANK" }
        val merchantCity = sanitizeAscii(payload.city)
            .take(15)
            .ifEmpty { "SAO PAULO" }
        val txId = sanitizeAscii(payload.txId)
            .replace(" ", "")
            .take(25)
            .ifEmpty { "***" }

        val withoutCrc = buildString {
            append(tlv("00", "01"))
            append(tlv("26", merchantAccountInfo))
            append(tlv("52", "0000"))
            append(tlv("53", "986"))
            if (normalizedAmount != null) {
                append(tlv("54", normalizedAmount))
            }
            append(tlv("58", "BR"))
            append(tlv("59", merchantName))
            append(tlv("60", merchantCity))
            append(tlv("62", tlv("05", txId)))
            append("6304")
        }

        return withoutCrc + crc16Hex(withoutCrc)
    }

    fun decode(raw: String): PixQrPayload? {
        val trimmed = raw.trim()
        if (trimmed.length < 12) return null

        val topTags = parseTlv(trimmed) ?: return null
        val crcTag = topTags.lastOrNull() ?: return null
        if (crcTag.id != "63" || crcTag.value.length != 4) return null

        val expectedCrc = crc16Hex(trimmed.dropLast(4))
        if (!crcTag.value.equals(expectedCrc, ignoreCase = true)) return null

        val map = topTags.associate { it.id to it.value }
        if (map["00"] != "01") return null

        val pixAccountEntry = topTags
            .filter { it.id.toIntOrNull() in 26..51 }
            .firstNotNullOfOrNull { entry ->
                val sub = parseTlv(entry.value)?.associate { it.id to it.value } ?: return@firstNotNullOfOrNull null
                if (sub["00"].equals(PIX_GUI, ignoreCase = true)) sub else null
            } ?: return null

        val pixKey = pixAccountEntry["01"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val description = pixAccountEntry["02"]?.trim()?.takeIf { it.isNotEmpty() }
        val amount = map["54"]?.let { parseAmountInput(it) }
        val recipientName = map["59"]?.trim().orEmpty()
        val city = map["60"]?.trim().orEmpty().ifEmpty { "SAO PAULO" }
        val txId = map["62"]
            ?.let { parseTlv(it)?.firstOrNull { sub -> sub.id == "05" }?.value }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "***"

        return PixQrPayload(
            pixKey = pixKey,
            recipientName = recipientName,
            amount = amount,
            description = description,
            city = city,
            txId = txId
        )
    }

    private data class TlvTag(val id: String, val value: String)

    private fun parseTlv(input: String): List<TlvTag>? {
        val result = mutableListOf<TlvTag>()
        var index = 0
        while (index < input.length) {
            if (index + 4 > input.length) return null
            val id = input.substring(index, index + 2)
            val length = input.substring(index + 2, index + 4).toIntOrNull() ?: return null
            val start = index + 4
            val end = start + length
            if (end > input.length) return null
            result += TlvTag(id = id, value = input.substring(start, end))
            index = end
        }
        return result
    }

    private fun tlv(id: String, value: String): String {
        val len = value.length.toString().padStart(2, '0')
        return "$id$len$value"
    }

    private fun sanitizeAscii(input: String): String {
        val decomposed = Normalizer.normalize(input.trim(), Normalizer.Form.NFD)
        return decomposed
            .replace(Regex("\\p{M}+"), "")
            .filter { it.code in 32..126 }
            .trim()
    }

    /** CRC-16/CCITT-FALSE (poly 0x1021, init 0xFFFF) required by EMVCo BR Code tag 63. */
    fun crc16Hex(input: String): String {
        var crc = 0xFFFF
        for (byte in input.toByteArray(Charsets.UTF_8)) {
            crc = crc xor ((byte.toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if ((crc and 0x8000) != 0) {
                    (crc shl 1) xor 0x1021
                } else {
                    crc shl 1
                }
                crc = crc and 0xFFFF
            }
        }
        return crc.toString(16).uppercase().padStart(4, '0')
    }
}
