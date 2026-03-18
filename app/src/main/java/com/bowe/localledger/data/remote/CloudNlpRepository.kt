package com.bowe.localledger.data.remote

import com.bowe.localledger.data.local.entity.TransactionType
import com.bowe.localledger.data.nlp.NaturalLanguageParseResult
import com.bowe.localledger.data.nlp.ParseMode
import com.bowe.localledger.data.nlp.ParsedTransactionCandidate
import com.bowe.localledger.data.remote.dto.CloudParseRequestDto
import com.bowe.localledger.data.remote.dto.CloudParseResponseDto
import kotlinx.coroutines.flow.first
import retrofit2.HttpException
import java.time.LocalDate

class CloudNlpRepository(
    private val tokenStore: TokenStore,
    private val remoteDataSource: RemoteLedgerDataSource,
) {
    suspend fun parseNaturalLanguage(
        remoteBookId: String,
        rawText: String,
        today: LocalDate = LocalDate.now(),
        timezone: String = "Asia/Shanghai",
    ): Result<NaturalLanguageParseResult> = runCatching {
        runWithValidToken { accessToken ->
            remoteDataSource.parseNaturalLanguage(
                accessToken = accessToken,
                payload = CloudParseRequestDto(
                    bookId = remoteBookId,
                    rawText = rawText.trim(),
                    today = today.toString(),
                    timezone = timezone,
                ),
            ).toParseResult()
        }
    }.mapFailureMessage()

    private suspend fun <T> runWithValidToken(block: suspend (String) -> T): T {
        val accessToken = tokenStore.accessToken.first()
            ?: throw IllegalStateException("请先登录云端")

        return try {
            block(accessToken)
        } catch (_: Exception) {
            val refreshToken = tokenStore.refreshToken.first()
                ?: throw IllegalStateException("云端登录已失效，请重新登录")
            val refreshed = remoteDataSource.refresh(refreshToken)
            tokenStore.saveSession(refreshed.accessToken, refreshed.refreshToken)
            block(refreshed.accessToken)
        }
    }

    private fun CloudParseResponseDto.toParseResult(): NaturalLanguageParseResult =
        NaturalLanguageParseResult(
            rawText = rawText,
            diaryText = diaryText,
            candidates = candidates.mapNotNull { candidate ->
                val type = when (candidate.type.lowercase()) {
                    "expense" -> TransactionType.EXPENSE
                    "income" -> TransactionType.INCOME
                    else -> null
                } ?: return@mapNotNull null

                ParsedTransactionCandidate(
                    type = type,
                    amount = candidate.amount,
                    occurredOn = candidate.occurredOn?.let(LocalDate::parse),
                    memberName = candidate.memberName,
                    categoryName = candidate.categoryName,
                    note = candidate.note,
                    confidence = candidate.confidence,
                    sourceSnippet = candidate.sourceSnippet,
                )
            },
            warnings = warnings,
            parseMode = ParseMode.CLOUD,
            providerLabel = "${provider.uppercase()} ${model}",
        )

    private fun <T> Result<T>.mapFailureMessage(): Result<T> {
        val error = exceptionOrNull() ?: return this
        val readable = when (error) {
            is HttpException -> when (error.code()) {
                401 -> "云端登录已失效，请重新登录"
                403 -> "当前账本没有智能解析权限"
                502 -> "智能解析服务暂时不可用"
                503 -> "云端智能解析还没有配置好"
                else -> "智能解析失败 (${error.code()})"
            }

            else -> error.message ?: "智能解析失败"
        }
        return Result.failure(IllegalStateException(readable, error))
    }
}
