package com.bowe.localledger.data.remote

import com.bowe.localledger.data.remote.dto.BootstrapDto
import com.bowe.localledger.data.remote.dto.LoginRequestDto
import com.bowe.localledger.data.remote.dto.RefreshRequestDto
import com.bowe.localledger.data.remote.dto.RegisterRequestDto
import com.bowe.localledger.data.remote.dto.SyncPullResponseDto
import com.bowe.localledger.data.remote.dto.SyncPushRequestDto
import com.bowe.localledger.data.remote.dto.SyncPushResponseDto

class RemoteLedgerDataSource(
    private val networkSettingsStore: NetworkSettingsStore,
) {
    suspend fun register(payload: RegisterRequestDto) = api().register(payload)

    suspend fun login(payload: LoginRequestDto) = api().login(payload)

    suspend fun refresh(refreshToken: String) = api().refresh(RefreshRequestDto(refreshToken))

    suspend fun bootstrap(accessToken: String): BootstrapDto =
        api().bootstrap(accessToken.asBearer())

    suspend fun pushChanges(accessToken: String, payload: SyncPushRequestDto): SyncPushResponseDto =
        api().pushChanges(accessToken.asBearer(), payload)

    suspend fun pullChanges(
        accessToken: String,
        bookId: String,
        cursor: String?,
    ): SyncPullResponseDto = api().pullChanges(
        authorization = accessToken.asBearer(),
        bookId = bookId,
        cursor = cursor,
    )

    suspend fun currentBaseUrl(): String = networkSettingsStore.getBaseUrl()

    suspend fun updateBaseUrl(baseUrl: String) {
        networkSettingsStore.saveBaseUrl(baseUrl)
    }

    private suspend fun api(): LedgerApiService = LedgerApiService.create(networkSettingsStore.getBaseUrl())

    private fun String.asBearer(): String = "Bearer $this"
}
