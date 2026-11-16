package com.example.mobile.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserResponse(
    val id: Long,
    val name: String,
    val email: String,
    val phone: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class SignUpRequest(
    val name: String,
    val email: String,
    @SerialName("doc_id") val docId: String,
    val phone: String,
    val password: String
)

@Serializable
data class SignUpBody(val user: SignUpRequest)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_at") val expiresAt: String? = null,
    val user: UserResponse
)

@Serializable
data class PixKeyResponse(
    val id: Long,
    @SerialName("key_type") val keyType: String,
    @SerialName("key_value") val keyValue: String,
    val status: String = "active"
)

@Serializable
data class PixKeyCreateRequest(
    @SerialName("key_type") val keyType: String,
    @SerialName("key_value") val keyValue: String? = null
)

@Serializable
data class PixKeyCreateBody(@SerialName("pix_key") val pixKey: PixKeyCreateRequest)

@Serializable
data class PixKeyLookupResponse(
    @SerialName("key_type") val keyType: String,
    @SerialName("key_value") val keyValue: String,
    @SerialName("account_id") val accountId: Long,
    @SerialName("account_number") val accountNumber: String,
    @SerialName("agency_number") val agencyNumber: String = "0001",
    @SerialName("recipient_name") val recipientName: String
)

@Serializable
data class TransferRequest(
    @SerialName("source_account_id") val sourceAccountId: Long,
    @SerialName("pix_key") val pixKey: String,
    /** Decimal string with at most two fraction digits, e.g. "10.50". */
    val amount: String,
    val description: String? = null
)

@Serializable
data class TransferBody(val transfer: TransferRequest)

@Serializable
data class TransferParty(
    @SerialName("account_number") val accountNumber: String = "",
    @SerialName("user_name") val userName: String = ""
)

@Serializable
data class TransferReceipt(
    val id: Long,
    @SerialName("end_to_end_id") val endToEndId: String? = null,
    val amount: String,
    val status: String,
    val description: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val sender: TransferParty = TransferParty(),
    val receiver: TransferParty = TransferParty()
)

@Serializable
data class StatementEntry(
    val id: Long,
    val amount: String,
    @SerialName("operation_type") val operationType: String,
    val status: String,
    @SerialName("created_at") val createdAt: String? = null,
    val counterparty: Counterparty = Counterparty()
) {
    val isDebit: Boolean get() = operationType == "debit"
}

@Serializable
data class Counterparty(val name: String? = null)

@Serializable
data class StatementResponse(val transactions: List<StatementEntry> = emptyList())

