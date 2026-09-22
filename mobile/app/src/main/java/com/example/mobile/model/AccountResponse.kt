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
data class AccountCreateRequest(
    val user_id: Long,
    val agency_number: String = "0001"
)

@Serializable
data class AccountCreateBody(
    val account: AccountCreateRequest
)

@Serializable
data class ApiResponse<T>(
    val data: T? = null,
    val error: kotlinx.serialization.json.JsonElement? = null
) {
    val errorMessage: String?
        get() = when (error) {
            null, is kotlinx.serialization.json.JsonNull -> null
            is kotlinx.serialization.json.JsonPrimitive -> error.content
            is kotlinx.serialization.json.JsonObject -> {
                error.entries.joinToString("; ") { (key, value) ->
                    val messages = if (value is kotlinx.serialization.json.JsonArray) {
                        value.joinToString(", ") { (it as? kotlinx.serialization.json.JsonPrimitive)?.content ?: it.toString() }
                    } else {
                        value.toString()
                    }
                    "$key: $messages"
                }
            }
            else -> error.toString()
        }
}
