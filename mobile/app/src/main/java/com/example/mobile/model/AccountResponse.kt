package com.example.mobile.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal

/**
 * Envelope returned by the backend. Successful calls fill [data]; failures fill [detail],
 * [errors] (field -> messages) and/or the legacy [error] value.
 */
@Serializable
data class ApiResponse<T>(
    val data: T? = null,
    val error: JsonElement? = null,
    val detail: String? = null,
    val errors: JsonObject? = null
) {
    val errorMessage: String?
        get() {
            errors?.takeIf { it.isNotEmpty() }?.let { fields ->
                return fields.entries.joinToString("; ") { (field, value) ->
                    val messages = (value as? JsonArray)?.joinToString(", ") { it.asText() } ?: value.asText()
                    "$field: $messages"
                }
            }
            detail?.takeIf { it.isNotBlank() }?.let { return it }
            return when (error) {
                null, is JsonNull -> null
                is JsonPrimitive -> error.content
                is JsonObject -> (error["message"] as? JsonPrimitive)?.content ?: error.toString()
                else -> error.toString()
            }
        }

    private fun JsonElement.asText(): String = (this as? JsonPrimitive)?.content ?: toString()
}

@Serializable
data class AccountResponse(
    val id: Long,
    @SerialName("agency_number") val agencyNumber: String = "0001",
    @SerialName("account_number") val accountNumber: String,
    /** Decimal string such as "1500.50"; never parsed through floating point. */
    val balance: String = "0.00",
    val status: String = "active",
    @SerialName("user_id") val userId: Long? = null
) {
    val balanceAmount: BigDecimal get() = balance.toBigDecimalOrNull() ?: BigDecimal.ZERO
    val isActive: Boolean get() = status == "active"
}

@Serializable
data class AccountCreateRequest(
    @SerialName("agency_number") val agencyNumber: String = "0001"
)

@Serializable
data class AccountCreateBody(val account: AccountCreateRequest)

