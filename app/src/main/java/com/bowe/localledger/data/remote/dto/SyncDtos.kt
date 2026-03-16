package com.bowe.localledger.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class SyncOperationRequestDto(
    @SerialName("client_mutation_id") val clientMutationId: String,
    @SerialName("entity_type") val entityType: String,
    @SerialName("operation_type") val operationType: String,
    val payload: JsonObject,
)

@Serializable
data class SyncPushRequestDto(
    @SerialName("book_id") val bookId: String,
    val operations: List<SyncOperationRequestDto>,
)

@Serializable
data class SyncOperationAckDto(
    val sequence: Int,
    @SerialName("entity_type") val entityType: String,
    @SerialName("operation_type") val operationType: String,
    @SerialName("entity_id") val entityId: String,
    @SerialName("client_mutation_id") val clientMutationId: String,
    val status: String,
)

@Serializable
data class SyncPushResponseDto(
    @SerialName("book_id") val bookId: String,
    val applied: List<SyncOperationAckDto>,
    @SerialName("next_cursor") val nextCursor: String,
)

@Serializable
data class SyncChangeDto(
    val sequence: Int,
    @SerialName("entity_type") val entityType: String,
    @SerialName("operation_type") val operationType: String,
    @SerialName("entity_id") val entityId: String,
    @SerialName("client_mutation_id") val clientMutationId: String,
    @SerialName("created_at") val createdAt: String,
    val payload: JsonObject,
)

@Serializable
data class SyncPullResponseDto(
    @SerialName("book_id") val bookId: String,
    val changes: List<SyncChangeDto>,
    @SerialName("next_cursor") val nextCursor: String,
)
