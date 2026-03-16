package com.bowe.localledger.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bowe.localledger.data.AddTransactionInput
import com.bowe.localledger.data.BackupAccount
import com.bowe.localledger.data.BackupCategory
import com.bowe.localledger.data.BackupSnapshot
import com.bowe.localledger.data.BackupTransaction
import com.bowe.localledger.data.DashboardData
import com.bowe.localledger.data.LedgerRepository
import com.bowe.localledger.data.ReportPeriodPreset
import com.bowe.localledger.data.ReportPeriodState
import com.bowe.localledger.data.ReportsData
import com.bowe.localledger.data.TransactionListItem
import com.bowe.localledger.data.UpdateTransactionInput
import com.bowe.localledger.data.local.entity.AccountEntity
import com.bowe.localledger.data.local.entity.BookEntity
import com.bowe.localledger.data.local.entity.CategoryEntity
import com.bowe.localledger.data.local.entity.MemberEntity
import com.bowe.localledger.data.local.entity.TransactionType
import com.bowe.localledger.data.nlp.NaturalLanguageParseRequest
import com.bowe.localledger.data.nlp.NaturalLanguageParseResult
import com.bowe.localledger.data.nlp.ParsedTransactionCandidate
import com.bowe.localledger.data.nlp.PlaceholderNaturalLanguageLedgerParser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(
    private val repository: LedgerRepository,
) : ViewModel() {
    private val naturalLanguageParser = PlaceholderNaturalLanguageLedgerParser()
    private val selectedBookId = MutableStateFlow<Long?>(null)
    private val backupPreview = MutableStateFlow<String?>(null)
    private val reportPeriod = MutableStateFlow(ReportPeriodState())

    val books: StateFlow<List<BookEntity>> = repository.observeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val currentBookId: StateFlow<Long?> = combine(books, selectedBookId) { books, selectedId ->
        when {
            books.isEmpty() -> null
            selectedId != null && books.any { it.id == selectedId } -> selectedId
            else -> books.first().id
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val dashboard: StateFlow<DashboardData?> = currentBookId
        .filterNotNull()
        .flatMapLatest(repository::observeDashboard)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val reportPeriodState: StateFlow<ReportPeriodState> = reportPeriod

    val reports: StateFlow<ReportsData?> = combine(
        currentBookId.filterNotNull(),
        reportPeriod,
    ) { bookId, period ->
        bookId to period
    }
        .flatMapLatest { (bookId, period) ->
            repository.observeReports(bookId, period.resolvedRange())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val transactions: StateFlow<List<TransactionListItem>> = currentBookId
        .filterNotNull()
        .flatMapLatest(repository::observeTransactions)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val members: StateFlow<List<MemberEntity>> = currentBookId
        .filterNotNull()
        .flatMapLatest(repository::observeMembers)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val accounts: StateFlow<List<AccountEntity>> = currentBookId
        .filterNotNull()
        .flatMapLatest(repository::observeAccounts)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val expenseCategories: StateFlow<List<CategoryEntity>> = currentBookId
        .filterNotNull()
        .flatMapLatest { repository.observeCategories(it, TransactionType.EXPENSE) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val incomeCategories: StateFlow<List<CategoryEntity>> = currentBookId
        .filterNotNull()
        .flatMapLatest { repository.observeCategories(it, TransactionType.INCOME) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allCategories: StateFlow<List<CategoryEntity>> = combine(
        expenseCategories,
        incomeCategories,
    ) { expense, income ->
        (expense + income).sortedWith(compareBy<CategoryEntity> { it.type.name }.thenBy { it.sortOrder }.thenBy { it.name })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val addTransactionState: StateFlow<AddTransactionState> = combine(
        members,
        accounts,
        expenseCategories,
        incomeCategories,
        transactions,
    ) { members, accounts, expenseCategories, incomeCategories, transactions ->
        val latest = transactions.firstOrNull()
        AddTransactionState(
            members = members,
            accounts = accounts,
            expenseCategories = expenseCategories,
            incomeCategories = incomeCategories,
            recentMemberId = latest?.memberId,
            recentAccountId = latest?.accountId,
            recentExpenseCategoryId = transactions.firstOrNull { it.type == TransactionType.EXPENSE }?.categoryId,
            recentIncomeCategoryId = transactions.firstOrNull { it.type == TransactionType.INCOME }?.categoryId,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddTransactionState())

    val backupJsonPreview: StateFlow<String?> = backupPreview

    init {
        viewModelScope.launch {
            repository.ensureSeedData()
        }
    }

    fun selectBook(bookId: Long) {
        selectedBookId.value = bookId
    }

    fun selectReportPeriod(preset: ReportPeriodPreset) {
        reportPeriod.value = when (preset) {
            ReportPeriodPreset.CUSTOM -> reportPeriod.value.copy(preset = ReportPeriodPreset.CUSTOM)
            else -> ReportPeriodState(preset = preset)
        }
    }

    fun updateCustomReportRange(start: LocalDate, end: LocalDate) {
        val normalizedStart = if (end.isBefore(start)) end else start
        val normalizedEnd = if (end.isBefore(start)) start else end
        reportPeriod.value = ReportPeriodState(
            preset = ReportPeriodPreset.CUSTOM,
            customStart = normalizedStart,
            customEnd = normalizedEnd,
        )
    }

    fun addTransaction(
        type: TransactionType,
        amount: Double,
        memberId: Long,
        accountId: Long,
        categoryId: Long,
        note: String,
    ) {
        val bookId = currentBookId.value ?: return
        viewModelScope.launch {
            repository.addTransaction(
                AddTransactionInput(
                    bookId = bookId,
                    memberId = memberId,
                    accountId = accountId,
                    categoryId = categoryId,
                    type = type,
                    amount = amount,
                    occurredAt = Instant.now(),
                    note = note.trim(),
                ),
            )
        }
    }

    fun updateTransaction(
        transaction: TransactionListItem,
        type: TransactionType,
        amount: Double,
        memberId: Long,
        accountId: Long,
        categoryId: Long,
        note: String,
    ) {
        val bookId = currentBookId.value ?: return
        viewModelScope.launch {
            repository.updateTransaction(
                UpdateTransactionInput(
                    id = transaction.id,
                    bookId = bookId,
                    memberId = memberId,
                    accountId = accountId,
                    categoryId = categoryId,
                    type = type,
                    amount = amount,
                    occurredAt = transaction.occurredAt,
                    note = note.trim(),
                    createdAt = transaction.createdAt,
                ),
            )
        }
    }

    fun deleteTransaction(transaction: TransactionListItem) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction.id)
        }
    }

    fun parseNaturalLanguage(text: String): NaturalLanguageParseResult {
        val bookId = currentBookId.value ?: 0L
        return naturalLanguageParser.parse(
            NaturalLanguageParseRequest(
                bookId = bookId,
                rawText = text,
                memberNames = members.value.map { it.name },
                expenseCategoryNames = expenseCategories.value.map { it.name },
                incomeCategoryNames = incomeCategories.value.map { it.name },
            ),
        )
    }

    fun saveNaturalLanguageEntry(
        rawText: String,
        accountId: Long,
        candidates: List<ParsedTransactionCandidate>,
        onResult: (Result<Unit>) -> Unit,
    ) {
        val bookId = currentBookId.value ?: return
        viewModelScope.launch {
            val result = runCatching {
                repository.saveNaturalLanguageEntry(
                    bookId = bookId,
                    rawText = rawText,
                    candidates = candidates,
                    memberNameToId = members.value.associate { it.name to it.id },
                    accountId = accountId,
                    expenseCategoryNameToId = expenseCategories.value.associate { it.name to it.id },
                    incomeCategoryNameToId = incomeCategories.value.associate { it.name to it.id },
                )
            }
            onResult(result)
        }
    }

    fun addMember(name: String) {
        val bookId = currentBookId.value ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.addMember(bookId, name)
        }
    }

    fun addAccount(name: String, initialBalance: Double) {
        val bookId = currentBookId.value ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.addAccount(bookId, name, initialBalance)
        }
    }

    fun addCategory(type: TransactionType, name: String) {
        val bookId = currentBookId.value ?: return
        if (name.isBlank()) return
        val nextSortOrder = if (type == TransactionType.EXPENSE) {
            expenseCategories.value.size + 1
        } else {
            incomeCategories.value.size + 1
        }
        viewModelScope.launch {
            repository.addCategory(bookId, type, name, nextSortOrder)
        }
    }

    fun addBook(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val newBookId = repository.addBook(name)
            selectedBookId.value = newBookId
        }
    }

    fun renameCurrentBook(name: String) {
        val currentBook = books.value.firstOrNull { it.id == currentBookId.value } ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.renameBook(currentBook, name)
        }
    }

    fun buildBackupJson(): String? {
        val currentBook = books.value.firstOrNull { it.id == currentBookId.value } ?: return null
        return BackupSnapshot(
            bookName = currentBook.name,
            members = members.value.map { it.name },
            accounts = accounts.value.map { BackupAccount(it.name, it.initialBalance) },
            categories = allCategories.value.map { BackupCategory(it.name, it.type) },
            transactions = transactions.value.map {
                BackupTransaction(
                    categoryName = it.categoryName,
                    memberName = it.memberName,
                    accountName = it.accountName,
                    type = it.type,
                    amount = it.amount,
                    occurredAtEpochMs = it.occurredAt.toEpochMilli(),
                    note = it.note,
                )
            },
        ).toJsonString()
    }

    fun generateBackupPreview() {
        backupPreview.value = buildBackupJson()
    }

    fun clearBackupPreview() {
        backupPreview.value = null
    }

    fun importBackupJson(
        json: String,
        onResult: (Result<String>) -> Unit,
    ) {
        val snapshot = runCatching { BackupSnapshot.fromJsonString(json) }
            .getOrElse { error ->
                onResult(Result.failure(error))
                return
            }

        viewModelScope.launch {
            val result = runCatching {
                val importedBookId = repository.importBackup(snapshot)
                selectedBookId.value = importedBookId
                "${snapshot.bookName}（导入）"
            }
            onResult(result)
        }
    }

    fun renameMember(member: MemberEntity, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.renameMember(member, name)
        }
    }

    fun deactivateMember(member: MemberEntity) {
        viewModelScope.launch {
            repository.deactivateMember(member)
        }
    }

    fun renameAccount(account: AccountEntity, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.renameAccount(account, name)
        }
    }

    fun deactivateAccount(account: AccountEntity) {
        viewModelScope.launch {
            repository.deactivateAccount(account)
        }
    }

    fun renameCategory(category: CategoryEntity, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.renameCategory(category, name)
        }
    }

    fun deactivateCategory(category: CategoryEntity) {
        viewModelScope.launch {
            repository.deactivateCategory(category)
        }
    }
}

data class AddTransactionState(
    val members: List<MemberEntity> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    val expenseCategories: List<CategoryEntity> = emptyList(),
    val incomeCategories: List<CategoryEntity> = emptyList(),
    val recentMemberId: Long? = null,
    val recentAccountId: Long? = null,
    val recentExpenseCategoryId: Long? = null,
    val recentIncomeCategoryId: Long? = null,
) {
    fun categoriesFor(type: TransactionType): List<CategoryEntity> {
        return if (type == TransactionType.EXPENSE) expenseCategories else incomeCategories
    }

    fun recentCategoryIdFor(type: TransactionType): Long? {
        return if (type == TransactionType.EXPENSE) recentExpenseCategoryId else recentIncomeCategoryId
    }
}

class AppViewModelFactory(
    private val repository: LedgerRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AppViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
