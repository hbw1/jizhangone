package com.bowe.localledger.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CloudParseRequestDto(
    @SerialName("book_id") val bookId: String,
    @SerialName("raw_text") val rawText: String,
    val today: String? = null,
    val timezone: String = "Asia/Shanghai",
)

@Serializable
data class CloudParsedTransactionCandidateDto(
    val type: String,
    val amount: Double? = null,
    @SerialName("occurred_on") val occurredOn: String? = null,
    @SerialName("member_name") val memberName: String? = null,
    @SerialName("category_name") val categoryName: String? = null,
    val note: String = "",
    val confidence: Float = 0f,
    @SerialName("source_snippet") val sourceSnippet: String = "",
)

@Serializable
data class CloudParseResponseDto(
    val provider: String,
    val model: String,
    @SerialName("raw_text") val rawText: String,
    @SerialName("diary_text") val diaryText: String,
    val candidates: List<CloudParsedTransactionCandidateDto>,
    val warnings: List<String> = emptyList(),
)
