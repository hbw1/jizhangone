package com.bowe.localledger.data.remote

import android.os.Build
import com.bowe.localledger.data.remote.dto.BookSummaryDto
import com.bowe.localledger.data.remote.dto.BootstrapDto
import com.bowe.localledger.data.remote.dto.LoginRequestDto
import com.bowe.localledger.data.remote.dto.RegisterRequestDto
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException

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
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun hasSavedSession(): Boolean {
        val accessToken = tokenStore.accessToken.first()
        return !accessToken.isNullOrBlank()
    }

    suspend fun restoreSession(): Result<CloudSession?> = runCatching {
        val accessToken = tokenStore.accessToken.first()
        val refreshToken = tokenStore.refreshToken.first()
        if (accessToken.isNullOrBlank()) return@runCatching null

        try {
            remoteDataSource.bootstrap(accessToken).toSession()
        } catch (error: Exception) {
            if (refreshToken.isNullOrBlank()) {
                tokenStore.clear()
                throw IllegalStateException("云端登录已失效，请重新登录")
            }

            try {
                val refreshed = remoteDataSource.refresh(refreshToken)
                tokenStore.saveSession(
                    accessToken = refreshed.accessToken,
                    refreshToken = refreshed.refreshToken,
                )
                remoteDataSource.bootstrap(refreshed.accessToken).toSession()
            } catch (refreshError: Exception) {
                if (error.isAuthenticationFailure() || refreshError.isAuthenticationFailure()) {
                    tokenStore.clear()
                    throw IllegalStateException("云端登录已失效，请重新登录")
                }
                throw IllegalStateException("暂时无法连接云端，请稍后再试", refreshError)
            }
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
    }.mapApiFailure()

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
    }.mapApiFailure()

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
        .take(CLOUD_DEVICE_NAME_MAX_LENGTH)

    private fun Throwable.isAuthenticationFailure(): Boolean =
        this is HttpException && code() in listOf(401, 403)

    private fun <T> Result<T>.mapApiFailure(): Result<T> {
        val error = exceptionOrNull() ?: return this
        val readable = when (error) {
            is HttpException -> parseHttpException(error)
            else -> error.message ?: "请求失败，请稍后再试"
        }
        return Result.failure(IllegalStateException(readable, error))
    }

    private fun parseHttpException(error: HttpException): String {
        val body = runCatching { error.response()?.errorBody()?.string() }.getOrNull().orEmpty()
        if (body.isBlank()) {
            return when (error.code()) {
                401 -> "账号或密码错误"
                409 -> "这个账号已经注册过了"
                422 -> "注册信息不符合要求"
                else -> "请求失败 (${error.code()})"
            }
        }

        return runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            val errorObject = root["error"]?.jsonObject
            val details = errorObject?.get("details")
            val firstDetail = (details as? JsonArray)?.firstOrNull()?.jsonObject
            when {
                error.code() == 422 && firstDetail != null -> {
                    val field = firstDetail["field"]?.jsonPrimitive?.content.orEmpty()
                    val message = firstDetail["message"]?.jsonPrimitive?.content.orEmpty()
                    when {
                        field.endsWith("username") -> "账号格式不对：$message"
                        field.endsWith("password") -> "密码格式不对：$message"
                        field.endsWith("display_name") -> "昵称格式不对：$message"
                        field.endsWith("device_name") -> "设备信息过长，已自动修正后请重试"
                        else -> errorObject["message"]?.jsonPrimitive?.content ?: "请求参数校验失败"
                    }
                }

                else -> errorObject?.get("message")?.jsonPrimitive?.content ?: "请求失败 (${error.code()})"
            }
        }.getOrElse {
            when (error.code()) {
                401 -> "账号或密码错误"
                409 -> "这个账号已经注册过了"
                422 -> "注册信息不符合要求"
                else -> "请求失败 (${error.code()})"
            }
        }
    }
}
