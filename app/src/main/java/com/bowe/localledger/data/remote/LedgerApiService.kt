package com.bowe.localledger.data.remote

import com.bowe.localledger.data.remote.dto.AuthSessionDto
import com.bowe.localledger.data.remote.dto.BootstrapDto
import com.bowe.localledger.data.remote.dto.LoginRequestDto
import com.bowe.localledger.data.remote.dto.CloudParseRequestDto
import com.bowe.localledger.data.remote.dto.CloudParseResponseDto
import com.bowe.localledger.data.remote.dto.RefreshRequestDto
import com.bowe.localledger.data.remote.dto.RegisterRequestDto
import com.bowe.localledger.data.remote.dto.SyncPullResponseDto
import com.bowe.localledger.data.remote.dto.SyncPushRequestDto
import com.bowe.localledger.data.remote.dto.SyncPushResponseDto
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface LedgerApiService {
    @POST("v1/auth/register")
    suspend fun register(
        @Body payload: RegisterRequestDto,
    ): AuthSessionDto

    @POST("v1/auth/login")
    suspend fun login(
        @Body payload: LoginRequestDto,
    ): AuthSessionDto

    @POST("v1/auth/refresh")
    suspend fun refresh(
        @Body payload: RefreshRequestDto,
    ): AuthSessionDto

    @GET("v1/bootstrap")
    suspend fun bootstrap(
        @Header(NetworkConfig.APP_AUTH_HEADER_NAME) authorization: String,
    ): BootstrapDto

    @POST("v1/sync/push")
    suspend fun pushChanges(
        @Header(NetworkConfig.APP_AUTH_HEADER_NAME) authorization: String,
        @Body payload: SyncPushRequestDto,
    ): SyncPushResponseDto

    @GET("v1/sync/pull")
    suspend fun pullChanges(
        @Header(NetworkConfig.APP_AUTH_HEADER_NAME) authorization: String,
        @Query("book_id") bookId: String,
        @Query("cursor") cursor: String? = null,
    ): SyncPullResponseDto

    @POST("v1/nlp/parse-natural-language")
    suspend fun parseNaturalLanguage(
        @Header(NetworkConfig.APP_AUTH_HEADER_NAME) authorization: String,
        @Body payload: CloudParseRequestDto,
    ): CloudParseResponseDto

    companion object {
        fun create(baseUrl: String = NetworkConfig.DEFAULT_BASE_URL): LedgerApiService {
            val json = Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            val domainGateInterceptor = Interceptor { chain ->
                val request = chain.request()
                val nextRequest = if (request.url.host == NetworkConfig.DEFAULT_HOST) {
                    request.newBuilder()
                        .header(
                            "Authorization",
                            Credentials.basic(
                                NetworkConfig.DOMAIN_GATE_USERNAME,
                                NetworkConfig.DOMAIN_GATE_PASSWORD,
                            ),
                        )
                        .build()
                } else {
                    request
                }
                chain.proceed(nextRequest)
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(domainGateInterceptor)
                .addInterceptor(logging)
                .build()

            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(LedgerApiService::class.java)
        }
    }
}
