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
import com.bowe.localledger.data.remote.CloudAuthRepository
import com.bowe.localledger.data.remote.CloudBook
import com.bowe.localledger.data.remote.CloudSession
import com.bowe.localledger.data.remote.CloudSyncRepository
import com.bowe.localledger.data.remote.RemoteLedgerDataSource
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
    private val cloudAuthRepository: CloudAuthRepository,
    private val cloudSyncRepository: CloudSyncRepository,
    private val remoteLedgerDataSource: RemoteLedgerDataSource,
) : ViewModel() {
    private val naturalLanguageParser = PlaceholderNaturalLanguageLedgerParser()
    private val selectedBookId = MutableStateFlow<Long?>(null)
    private val backupPreview = MutableStateFlow<String?>(null)
    private val reportPeriod = MutableStateFlow(ReportPeriodState())
    private val cloudState = MutableStateFlow(CloudUiState(isCheckingSession = true))

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
    val cloudUiState: StateFlow<CloudUiState> = cloudState

    init {
        viewModelScope.launch {
            repository.ensureSeedData()
        }
        viewModelScope.launch {
            restoreCloudSession()
        }
        viewModelScope.launch {
            val baseUrl = remoteLedgerDataSource.currentBaseUrl()
            cloudState.value = cloudState.value.copy(serverBaseUrl = baseUrl)
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
            triggerCloudSync()
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
            triggerCloudSync()
        }
    }

    fun deleteTransaction(transaction: TransactionListItem) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction.id)
            triggerCloudSync()
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
                triggerCloudSync()
            }
            onResult(result)
        }
    }

    fun addMember(name: String) {
        val bookId = currentBookId.value ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.addMember(bookId, name)
            triggerCloudSync()
        }
    }

    fun addAccount(name: String, initialBalance: Double) {
        val bookId = currentBookId.value ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.addAccount(bookId, name, initialBalance)
            triggerCloudSync()
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
            triggerCloudSync()
        }
    }

    fun addBook(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val newBookId = repository.addBook(name)
            selectedBookId.value = newBookId
            triggerCloudSync()
        }
    }

    fun renameCurrentBook(name: String) {
        val currentBook = books.value.firstOrNull { it.id == currentBookId.value } ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.renameBook(currentBook, name)
            triggerCloudSync()
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
            triggerCloudSync()
        }
    }

    fun deactivateMember(member: MemberEntity) {
        viewModelScope.launch {
            repository.deactivateMember(member)
            triggerCloudSync()
        }
    }

    fun renameAccount(account: AccountEntity, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.renameAccount(account, name)
            triggerCloudSync()
        }
    }

    fun deactivateAccount(account: AccountEntity) {
        viewModelScope.launch {
            repository.deactivateAccount(account)
            triggerCloudSync()
        }
    }

    fun renameCategory(category: CategoryEntity, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.renameCategory(category, name)
            triggerCloudSync()
        }
    }

    fun deactivateCategory(category: CategoryEntity) {
        viewModelScope.launch {
            repository.deactivateCategory(category)
            triggerCloudSync()
        }
    }

    fun loginToCloud(
        username: String,
        password: String,
        onResult: (Result<Unit>) -> Unit,
    ) {
        if (username.isBlank() || password.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("请输入账号和密码")))
            return
        }
        viewModelScope.launch {
            cloudState.value = cloudState.value.copy(isSubmitting = true, errorMessage = null)
            val result = cloudAuthRepository.login(username, password)
            result.onSuccess { session ->
                applyCloudSession(session)
                viewModelScope.launch {
                    syncCloudBootstrap(session)
                }
                onResult(Result.success(Unit))
            }.onFailure { error ->
                cloudState.value = cloudState.value.copy(
                    isSubmitting = false,
                    errorMessage = error.message ?: "云端登录失败",
                )
                onResult(Result.failure(error))
            }
        }
    }

    fun registerToCloud(
        username: String,
        password: String,
        displayName: String,
        onResult: (Result<Unit>) -> Unit,
    ) {
        if (username.isBlank() || password.isBlank() || displayName.isBlank()) {
            onResult(Result.failure(IllegalArgumentException("请完整填写注册信息")))
            return
        }
        viewModelScope.launch {
            cloudState.value = cloudState.value.copy(isSubmitting = true, errorMessage = null)
            val result = cloudAuthRepository.register(username, password, displayName)
            result.onSuccess { session ->
                applyCloudSession(session)
                viewModelScope.launch {
                    syncCloudBootstrap(session)
                }
                onResult(Result.success(Unit))
            }.onFailure { error ->
                cloudState.value = cloudState.value.copy(
                    isSubmitting = false,
                    errorMessage = error.message ?: "云端注册失败",
                )
                onResult(Result.failure(error))
            }
        }
    }

    fun refreshCloudSession() {
        viewModelScope.launch {
            restoreCloudSession()
        }
    }

    fun syncCloudNow(onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            cloudState.value = cloudState.value.copy(isSyncing = true, errorMessage = null)
            val result = cloudSyncRepository.syncAll()
            result.onSuccess { summary ->
                cloudState.value = cloudState.value.copy(
                    isSyncing = false,
                    lastSyncSummary = "推送 ${summary.pushedOperations} 条，拉取 ${summary.pulledChanges} 条",
                )
                onResult(Result.success(Unit))
            }.onFailure { error ->
                cloudState.value = cloudState.value.copy(
                    isSyncing = false,
                    errorMessage = error.message ?: "同步失败",
                )
                onResult(Result.failure(error))
            }
        }
    }

    fun logoutCloud(onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            runCatching {
                cloudAuthRepository.logout()
            }.onSuccess {
                cloudState.value = CloudUiState(
                    isCheckingSession = false,
                    isAuthenticated = false,
                )
                onResult(Result.success(Unit))
            }.onFailure { error ->
                onResult(Result.failure(error))
            }
        }
    }

    fun clearCloudError() {
        cloudState.value = cloudState.value.copy(errorMessage = null)
    }

    fun saveCloudServerBaseUrl(
        baseUrl: String,
        onResult: (Result<Unit>) -> Unit,
    ) {
        viewModelScope.launch {
            val result = runCatching {
                remoteLedgerDataSource.updateBaseUrl(baseUrl)
                val normalized = remoteLedgerDataSource.currentBaseUrl()
                cloudState.value = cloudState.value.copy(serverBaseUrl = normalized)
            }
            onResult(result)
        }
    }

    private suspend fun restoreCloudSession() {
        cloudState.value = cloudState.value.copy(isCheckingSession = true, errorMessage = null)
        val result = cloudAuthRepository.restoreSession()
        result.onSuccess { session ->
            if (session == null) {
                cloudState.value = CloudUiState(isCheckingSession = false)
            } else {
                applyCloudSession(session)
                syncCloudBootstrap(session)
            }
        }.onFailure { error ->
            cloudState.value = CloudUiState(
                isCheckingSession = false,
                isAuthenticated = false,
                errorMessage = error.message ?: "云端状态恢复失败",
            )
        }
    }

    private fun applyCloudSession(session: CloudSession) {
        cloudState.value = CloudUiState(
            isCheckingSession = false,
            isSubmitting = false,
            isAuthenticated = true,
            displayName = session.user.displayName,
            username = session.user.username,
            books = session.books,
            errorMessage = null,
            serverBaseUrl = cloudState.value.serverBaseUrl,
        )
    }

    private suspend fun syncCloudBootstrap(session: CloudSession) {
        val importedBookIds = cloudSyncRepository.bootstrapBooks(session.books)
        importedBookIds.firstOrNull()?.let { importedId ->
            selectedBookId.value = importedId
        }
        syncCloudNow()
    }

    private fun triggerCloudSync() {
        if (!cloudState.value.isAuthenticated) return
        syncCloudNow()
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

data class CloudUiState(
    val isCheckingSession: Boolean = false,
    val isSubmitting: Boolean = false,
    val isSyncing: Boolean = false,
    val isAuthenticated: Boolean = false,
    val displayName: String? = null,
    val username: String? = null,
    val books: List<CloudBook> = emptyList(),
    val lastSyncSummary: String? = null,
    val serverBaseUrl: String = "",
    val errorMessage: String? = null,
)

class AppViewModelFactory(
    private val repository: LedgerRepository,
    private val cloudAuthRepository: CloudAuthRepository,
    private val cloudSyncRepository: CloudSyncRepository,
    private val remoteLedgerDataSource: RemoteLedgerDataSource,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AppViewModel(
                repository,
                cloudAuthRepository,
                cloudSyncRepository,
                remoteLedgerDataSource,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
