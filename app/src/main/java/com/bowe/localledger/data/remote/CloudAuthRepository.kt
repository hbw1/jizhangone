package com.bowe.localledger.data.remote

import android.os.Build
import com.bowe.localledger.data.remote.dto.BookSummaryDto
import com.bowe.localledger.data.remote.dto.BootstrapDto
import com.bowe.localledger.data.remote.dto.LoginRequestDto
import com.bowe.localledger.data.remote.dto.RegisterRequestDto
import kotlinx.coroutines.flow.first

data class CloudUser(
    val id: String,
    val username: String,
    val displayName: String,
)

data class CloudBook(
    val id: String,
    val name: String,
    val role: String,
)

data class CloudSession(
    val user: CloudUser,
    val books: List<CloudBook>,
)

class CloudAuthRepository(
    private val tokenStore: TokenStore,
    private val remoteDataSource: RemoteLedgerDataSource,
) {
    suspend fun restoreSession(): Result<CloudSession?> = runCatching {
        val accessToken = tokenStore.accessToken.first()
        val refreshToken = tokenStore.refreshToken.first()
        if (accessToken.isNullOrBlank()) return@runCatching null

        try {
            remoteDataSource.bootstrap(accessToken).toSession()
        } catch (_: Exception) {
            if (refreshToken.isNullOrBlank()) {
                tokenStore.clear()
                throw IllegalStateException("云端登录已失效，请重新登录")
            }

            val refreshed = remoteDataSource.refresh(refreshToken)
            tokenStore.saveSession(
                accessToken = refreshed.accessToken,
                refreshToken = refreshed.refreshToken,
            )
            remoteDataSource.bootstrap(refreshed.accessToken).toSession()
        }
    }

    suspend fun login(username: String, password: String): Result<CloudSession> = runCatching {
        val session = remoteDataSource.login(
            LoginRequestDto(
                username = username.trim(),
                password = password,
                deviceName = currentDeviceName(),
            ),
        )
        tokenStore.saveSession(session.accessToken, session.refreshToken)
        remoteDataSource.bootstrap(session.accessToken).toSession()
    }

    suspend fun register(
        username: String,
        password: String,
        displayName: String,
    ): Result<CloudSession> = runCatching {
        val session = remoteDataSource.register(
            RegisterRequestDto(
                username = username.trim(),
                password = password,
                displayName = displayName.trim(),
                deviceName = currentDeviceName(),
            ),
        )
        tokenStore.saveSession(session.accessToken, session.refreshToken)
        remoteDataSource.bootstrap(session.accessToken).toSession()
    }

    suspend fun logout() {
        tokenStore.clear()
    }

    private fun BootstrapDto.toSession(): CloudSession =
        CloudSession(
            user = CloudUser(
                id = user.id,
                username = user.username,
                displayName = user.displayName,
            ),
            books = books.map { it.toCloudBook() },
        )

    private fun BookSummaryDto.toCloudBook(): CloudBook =
        CloudBook(
            id = id,
            name = name,
            role = role,
        )

    private fun currentDeviceName(): String = listOfNotNull(Build.MANUFACTURER, Build.MODEL)
        .joinToString(" ")
        .ifBlank { "Android Device" }
}
