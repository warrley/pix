package com.example.mobile.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

object FlexibleDoubleSerializer : KSerializer<Double> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexibleDouble", PrimitiveKind.DOUBLE)

    override fun deserialize(decoder: Decoder): Double {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeDouble()
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> element.doubleOrNull ?: element.content.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }

    override fun serialize(encoder: Encoder, value: Double) {
        encoder.encodeDouble(value)
    }
}

@Serializable
data class AccountResponse(
    val id: Long,
    val agency_number: String = "0001",
    val account_number: String,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val balance: Double = 0.0,
    val status: String = "active",
    val user_id: Long? = null
)

@Serializable
data class ApiResponse<T>(
    val data: T? = null,
    val error: String? = null
)
