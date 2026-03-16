package com.bowe.localledger.data

import com.bowe.localledger.data.local.entity.TransactionType
import org.json.JSONArray
import org.json.JSONObject

data class BackupSnapshot(
    val bookName: String,
    val members: List<String>,
    val accounts: List<BackupAccount>,
    val categories: List<BackupCategory>,
    val transactions: List<BackupTransaction>,
) {
    fun toJsonString(): String {
        val root = JSONObject()
        root.put("bookName", bookName)
        root.put("members", JSONArray(members))
        root.put(
            "accounts",
            JSONArray().apply {
                accounts.forEach { account ->
                    put(
                        JSONObject()
                            .put("name", account.name)
                            .put("initialBalance", account.initialBalance),
                    )
                }
            },
        )
        root.put(
            "categories",
            JSONArray().apply {
                categories.forEach { category ->
                    put(
                        JSONObject()
                            .put("name", category.name)
                            .put("type", category.type.name),
                    )
                }
            },
        )
        root.put(
            "transactions",
            JSONArray().apply {
                transactions.forEach { transaction ->
                    put(
                        JSONObject()
                            .put("categoryName", transaction.categoryName)
                            .put("memberName", transaction.memberName)
                            .put("accountName", transaction.accountName)
                            .put("type", transaction.type.name)
                            .put("amount", transaction.amount)
                            .put("occurredAt", transaction.occurredAtEpochMs)
                            .put("note", transaction.note),
                    )
                }
            },
        )
        return root.toString(2)
    }

    companion object {
        fun fromJsonString(json: String): BackupSnapshot {
            val root = JSONObject(json)
            val members = root.optJSONArray("members").toStringList()
            val accounts = root.optJSONArray("accounts").toObjectList { item ->
                BackupAccount(
                    name = item.getString("name"),
                    initialBalance = item.optDouble("initialBalance", 0.0),
                )
            }
            val categories = root.optJSONArray("categories").toObjectList { item ->
                BackupCategory(
                    name = item.getString("name"),
                    type = TransactionType.valueOf(item.getString("type")),
                )
            }
            val transactions = root.optJSONArray("transactions").toObjectList { item ->
                BackupTransaction(
                    categoryName = item.getString("categoryName"),
                    memberName = item.getString("memberName"),
                    accountName = item.getString("accountName"),
                    type = TransactionType.valueOf(item.getString("type")),
                    amount = item.getDouble("amount"),
                    occurredAtEpochMs = item.getLong("occurredAt"),
                    note = item.optString("note", ""),
                )
            }
            return BackupSnapshot(
                bookName = root.optString("bookName", "导入账本"),
                members = members,
                accounts = accounts,
                categories = categories,
                transactions = transactions,
            )
        }
    }
}

data class BackupAccount(
    val name: String,
    val initialBalance: Double,
)

data class BackupCategory(
    val name: String,
    val type: TransactionType,
)

data class BackupTransaction(
    val categoryName: String,
    val memberName: String,
    val accountName: String,
    val type: TransactionType,
    val amount: Double,
    val occurredAtEpochMs: Long,
    val note: String,
)

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return buildList(length()) {
        for (index in 0 until length()) {
            add(optString(index))
        }
    }
}

private fun <T> JSONArray?.toObjectList(transform: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    return buildList(length()) {
        for (index in 0 until length()) {
            add(transform(getJSONObject(index)))
        }
    }
}
