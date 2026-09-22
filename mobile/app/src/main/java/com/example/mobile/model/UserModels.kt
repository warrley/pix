package com.example.mobile.model

import kotlinx.serialization.Serializable

@Serializable
data class UserResponse(
    val id: Long,
    val name: String,
    val email: String,
    val doc_id: String,
    val phone: String,
    val created_at: String? = null,
    val updated_at: String? = null
)

@Serializable
data class UserCreateRequest(
    val name: String,
    val email: String,
    val doc_id: String,
    val phone: String
)

@Serializable
data class UserUpdateRequest(
    val name: String,
    val email: String,
    val phone: String
)

@Serializable
data class UserCreateBody(
    val user: UserCreateRequest
)

@Serializable
data class UserUpdateBody(
    val user: UserUpdateRequest
)
