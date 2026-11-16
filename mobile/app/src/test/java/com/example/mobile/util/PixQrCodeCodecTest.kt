package com.example.mobile.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PixQrCodeCodecTest {

    @Test
    fun encodeAndDecode_withFixedAmountAndAccentedName_roundTrips() {
        val code = PixQrCodeCodec.encode(
            PixQrPayload(
                pixKey = "joao@rubank.com",
                recipientName = "João da Silva",
                amount = "49.90",
                description = "Almoço de sexta"
            )
        )

        assertTrue(code.startsWith("000201"))
        assertTrue(code.contains("br.gov.bcb.pix"))
        assertTrue(code.contains("540549.90"))

        val decoded = PixQrCodeCodec.decode(code)
        assertNotNull(decoded)
        assertEquals("joao@rubank.com", decoded?.pixKey)
        assertEquals("Joao da Silva", decoded?.recipientName)
        assertEquals("49.90", decoded?.amount)
        assertEquals("Almoco de sexta", decoded?.description)
    }

    @Test
    fun encodeAndDecode_withZeroAmount_omitsAmountTagAndDecodesNullAmount() {
        val code = PixQrCodeCodec.encode(
            PixQrPayload(
                pixKey = "+5511999998888",
                recipientName = "Maria",
                amount = "0"
            )
        )

        assertFalse(code.contains("54040.00"))
        val decoded = PixQrCodeCodec.decode(code)
        assertNotNull(decoded)
        assertEquals("+5511999998888", decoded?.pixKey)
        assertNull(decoded?.amount)
    }

    @Test
    fun decode_rejectsTamperedCrcAndNonPixStrings() {
        val code = PixQrCodeCodec.encode(
            PixQrPayload(pixKey = "alice@rubank.com", recipientName = "Alice", amount = "10.00")
        )
        val tampered = code.dropLast(1) + if (code.last() == '0') '1' else '0'

        assertNull(PixQrCodeCodec.decode(tampered))
        assertNull(PixQrCodeCodec.decode("https://example.com"))
        assertNull(PixQrCodeCodec.decode(""))
    }
}
