package com.bowe.localledger.data.local.query

import com.bowe.localledger.data.local.entity.TransactionType

data class TransactionListRow(
    val transactionId: Long,
    val memberId: Long,
    val accountId: Long,
    val categoryId: Long,
    val type: TransactionType,
    val amount: Double,
    val occurredAt: Long,
    val createdAt: Long,
    val note: String,
    val categoryName: String,
    val memberName: String,
    val accountName: String,
)

data class MonthSummaryRow(
    val income: Double,
    val expense: Double,
)

data class CategoryTotalRow(
    val label: String,
    val total: Double,
)

data class MemberTotalRow(
    val label: String,
    val total: Double,
)

data class AccountBalanceRow(
    val accountId: Long,
    val accountName: String,
    val balance: Double,
)

data class MonthTrendRow(
    val month: String,
    val income: Double,
    val expense: Double,
)
