package com.bowe.localledger.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequestDto(
    val username: String,
    val password: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("device_name") val deviceName: String? = null,
)

@Serializable
data class LoginRequestDto(
    val username: String,
    val password: String,
    @SerialName("device_name") val deviceName: String? = null,
)

@Serializable
data class RefreshRequestDto(
    @SerialName("refresh_token") val refreshToken: String,
)

@Serializable
data class UserSummaryDto(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String,
)

@Serializable
data class BookSummaryDto(
    val id: String,
    val name: String,
    val role: String,
)

@Serializable
data class AuthSessionDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("token_type") val tokenType: String,
    val user: UserSummaryDto,
    val books: List<BookSummaryDto>,
)

@Serializable
data class BootstrapDto(
    val user: UserSummaryDto,
    val books: List<BookSummaryDto>,
    @SerialName("sync_enabled") val syncEnabled: Boolean,
)
