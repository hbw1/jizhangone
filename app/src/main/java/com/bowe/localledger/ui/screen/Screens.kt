package com.bowe.localledger.ui.screen

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import com.bowe.localledger.data.remote.BOOK_NAME_MAX_LENGTH
import com.bowe.localledger.data.remote.CLOUD_DISPLAY_NAME_MAX_LENGTH
import com.bowe.localledger.data.remote.CLOUD_DISPLAY_NAME_MIN_LENGTH
import com.bowe.localledger.data.remote.CLOUD_PASSWORD_MAX_LENGTH
import com.bowe.localledger.data.remote.CLOUD_PASSWORD_MIN_LENGTH
import com.bowe.localledger.data.remote.CLOUD_USERNAME_MAX_LENGTH
import com.bowe.localledger.data.remote.CLOUD_USERNAME_MIN_LENGTH
import com.bowe.localledger.data.remote.ENTITY_NAME_MAX_LENGTH
import com.bowe.localledger.data.remote.NLP_RAW_TEXT_MAX_LENGTH
import com.bowe.localledger.data.remote.clampToMaxLength
import com.bowe.localledger.data.remote.maxLengthHint
import com.bowe.localledger.data.remote.rangeLengthHint
import com.bowe.localledger.data.DashboardData
import com.bowe.localledger.data.ReportValue
import com.bowe.localledger.data.ReportPeriodPreset
import com.bowe.localledger.data.ReportPeriodState
import com.bowe.localledger.data.ReportsData
import com.bowe.localledger.data.TrendValue
import com.bowe.localledger.data.TransactionListItem
import com.bowe.localledger.data.local.entity.AccountEntity
import com.bowe.localledger.data.local.entity.BookEntity
import com.bowe.localledger.data.local.entity.CategoryEntity
import com.bowe.localledger.data.local.entity.MemberEntity
import com.bowe.localledger.data.local.entity.TransactionType
import com.bowe.localledger.data.nlp.NaturalLanguageParseResult
import com.bowe.localledger.data.nlp.ParseMode
import com.bowe.localledger.data.nlp.ParsedTransactionCandidate
import com.bowe.localledger.ui.AddTransactionState
import com.bowe.localledger.ui.CloudUiState
import java.text.DecimalFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.absoluteValue

@Composable
fun DashboardScreen(
    contentPadding: PaddingValues,
    books: List<BookEntity>,
    currentBookId: Long?,
    dashboard: DashboardData?,
    addTransactionState: AddTransactionState,
    onBookSelected: (Long) -> Unit,
    onAddBook: (String) -> Unit,
    onRenameBook: (String) -> Unit,
    onAddTransaction: (TransactionType, Double, Long, Long, Long, Instant, String) -> Unit,
    onOpenNaturalLanguage: () -> Unit,
) {
    var showBookSheet by rememberSaveable { mutableStateOf(false) }
    var showRenameBookSheet by rememberSaveable { mutableStateOf(false) }
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    ScreenContainer(contentPadding = contentPadding) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DashboardBookMenu(
                books = books,
                currentBookId = currentBookId,
                onBookSelected = onBookSelected,
                onAddBook = { showBookSheet = true },
                onRenameBook = { showRenameBookSheet = true },
            )
            HeroDashboardCard(
                modifier = Modifier.weight(1f),
                bookName = dashboard?.bookName ?: "加载中",
                income = formatCurrency(dashboard?.income ?: 0.0),
                expense = formatCurrency(dashboard?.expense ?: 0.0),
                balance = formatSignedCurrency(dashboard?.balance ?: 0.0),
                onRecord = { showAddSheet = true },
                onOpenNaturalLanguage = onOpenNaturalLanguage,
            )
        }
    }
    if (showBookSheet) {
        SimpleInputSheet(
            title = "新增账本",
            fieldLabel = "账本名称",
            maxLength = BOOK_NAME_MAX_LENGTH,
            requirementText = maxLengthHint(BOOK_NAME_MAX_LENGTH),
            onDismiss = { showBookSheet = false },
            onConfirm = { name ->
                onAddBook(name)
                showBookSheet = false
            },
        )
    }
    if (showAddSheet) {
        AddTransactionSheet(
            state = addTransactionState,
            editingTransaction = null,
            onDismiss = { showAddSheet = false },
            onConfirm = { type, amount, memberId, accountId, categoryId, occurredAt, note ->
                onAddTransaction(type, amount, memberId, accountId, categoryId, occurredAt, note)
                showAddSheet = false
            },
        )
    }
    if (showRenameBookSheet) {
        SimpleInputSheet(
            title = "重命名账本",
            fieldLabel = "账本名称",
            initialValue = dashboard?.bookName.orEmpty(),
            maxLength = BOOK_NAME_MAX_LENGTH,
            requirementText = maxLengthHint(BOOK_NAME_MAX_LENGTH),
            onDismiss = { showRenameBookSheet = false },
            onConfirm = { name ->
                onRenameBook(name)
                showRenameBookSheet = false
            },
        )
    }
}

@Composable
fun TransactionsScreen(
    contentPadding: PaddingValues,
    transactions: List<TransactionListItem>,
    addTransactionState: AddTransactionState,
    onAddTransaction: (TransactionType, Double, Long, Long, Long, Instant, String) -> Unit,
    onUpdateTransaction: (TransactionListItem, TransactionType, Double, Long, Long, Long, Instant, String) -> Unit,
    onDeleteTransaction: (TransactionListItem) -> Unit,
) {
    var showSheet by rememberSaveable { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<TransactionListItem?>(null) }
    var selectedType by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedMember by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedAccount by rememberSaveable { mutableStateOf<String?>(null) }
    val today = remember { LocalDate.now() }
    val chartDays = remember(today) { (6 downTo 0).map { today.minusDays(it.toLong()) } }
    val weekFormatter = remember { DateTimeFormatter.ofPattern("M-d") }
    val shortDayFormatter = remember { DateTimeFormatter.ofPattern("E") }

    val filteredTransactions = remember(transactions, selectedType, selectedMember, selectedAccount) {
        transactions.filter { item ->
            val typePass = selectedType == null || item.type.name == selectedType
            val memberPass = selectedMember == null || item.memberName == selectedMember
            val accountPass = selectedAccount == null || item.accountName == selectedAccount
            typePass && memberPass && accountPass
        }
    }
    val summaryLabel = remember(selectedType) {
        when (selectedType) {
            "INCOME" -> "总收入"
            "EXPENSE" -> "总支出"
            else -> "总流水"
        }
    }
    val summaryAmount = remember(filteredTransactions, selectedType) {
        when (selectedType) {
            "INCOME" -> filteredTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
            "EXPENSE" -> filteredTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
            else -> filteredTransactions.sumOf { it.amount }
        }
    }
    val weeklySummaryValues = remember(filteredTransactions, chartDays, selectedType) {
        chartDays.map { date ->
            filteredTransactions
                .filter { item ->
                    when (selectedType) {
                        "INCOME" -> item.type == TransactionType.INCOME
                        "EXPENSE" -> item.type == TransactionType.EXPENSE
                        else -> true
                    }
                }
                .filter { it.occurredAt.atZone(ZoneId.systemDefault()).toLocalDate() == date }
                .sumOf { it.amount }
        }
    }
    val summaryRangeText = remember(chartDays, weekFormatter) {
        "${chartDays.first().format(weekFormatter)} - ${chartDays.last().format(weekFormatter)}"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ScreenContainer(contentPadding = contentPadding) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    TransactionsHeroHeader(
                        selectedType = selectedType,
                        onSelectType = { selectedType = it },
                    )
                }
                item {
                    Text(
                        text = summaryRangeText,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                    )
                }
                item {
                    TransactionsSummaryCard(
                        label = summaryLabel,
                        amount = formatCurrency(summaryAmount),
                        values = weeklySummaryValues,
                        labels = chartDays.map { it.format(shortDayFormatter) },
                    )
                }
                item {
                    TransactionFilters(
                        members = addTransactionState.members.map { it.name },
                        accounts = addTransactionState.accounts.map { it.name },
                        selectedType = null,
                        selectedMember = selectedMember,
                        selectedAccount = selectedAccount,
                        onTypeSelected = {},
                        onMemberSelected = { selectedMember = it },
                        onAccountSelected = { selectedAccount = it },
                    )
                }
                if (filteredTransactions.isEmpty()) {
                    item {
                        EmptyCard("当前筛选条件下暂无交易")
                    }
                } else {
                    items(filteredTransactions, key = { it.id }) { transaction ->
                        TransactionCard(
                            transaction = transaction,
                            onEdit = { editingTransaction = transaction },
                            onDelete = { onDeleteTransaction(transaction) },
                        )
                    }
                }
            }
        }
        FloatingActionButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 24.dp),
            onClick = { showSheet = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Text("记一笔")
        }

        if (showSheet) {
            AddTransactionSheet(
                state = addTransactionState,
                onDismiss = { showSheet = false },
                editingTransaction = null,
                onConfirm = { type, amount, memberId, accountId, categoryId, occurredAt, note ->
                    onAddTransaction(type, amount, memberId, accountId, categoryId, occurredAt, note)
                    showSheet = false
                },
            )
        }
        if (editingTransaction != null) {
            AddTransactionSheet(
                state = addTransactionState,
                editingTransaction = editingTransaction,
                onDismiss = { editingTransaction = null },
                onConfirm = { type, amount, memberId, accountId, categoryId, occurredAt, note ->
                    editingTransaction?.let {
                        onUpdateTransaction(it, type, amount, memberId, accountId, categoryId, occurredAt, note)
                    }
                    editingTransaction = null
                },
            )
        }
    }
}

@Composable
private fun TransactionsHeroHeader(
    selectedType: String?,
    onSelectType: (String?) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(999.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(null to "全部", "EXPENSE" to "支出", "INCOME" to "收入").forEach { item ->
                Button(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    onClick = { onSelectType(item.first) },
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedType == item.first) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Transparent
                        },
                        contentColor = if (selectedType == item.first) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    ),
                    elevation = null,
                ) {
                    Text(item.second, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun TransactionsSummaryCard(
    label: String,
    amount: String,
    values: List<Double>,
    labels: List<String>,
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MoneyAmountText(
                    value = amount,
                    prominent = true,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            WeeklyValueChart(values = values, labels = labels)
        }
    }
}

@Composable
private fun WeeklyValueChart(
    values: List<Double>,
    labels: List<String>,
) {
    val maxValue = values.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEachIndexed { index, value ->
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(((value / maxValue) * 84.0).toFloat().coerceAtLeast(10f).dp)
                        .background(
                            brush = Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.secondary,
                                    MaterialTheme.colorScheme.primary,
                                ),
                            ),
                            shape = RoundedCornerShape(14.dp),
                        ),
                )
                Text(
                    text = labels.getOrElse(index) { "" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun ReportsScreen(
    contentPadding: PaddingValues,
    reports: ReportsData?,
    periodState: ReportPeriodState,
    onPeriodSelected: (ReportPeriodPreset) -> Unit,
    onCustomRangeSelected: (LocalDate, LocalDate) -> Unit,
) {
    var showCustomRangeSheet by rememberSaveable { mutableStateOf(false) }
    val today = remember { LocalDate.now() }
    val rangeFormatter = remember { DateTimeFormatter.ofPattern("yyyy.MM.dd") }
    val range = remember(periodState, today) { periodState.resolvedRange(today) }
    val income = reports?.income ?: 0.0
    val expense = reports?.expense ?: 0.0
    val totalBalance = reports?.accountBalances?.sumOf { it.value } ?: (income - expense)
    val balanceText = if (totalBalance < 0) formatSignedCurrency(totalBalance) else formatCurrency(totalBalance)
    val spendRatio = when {
        income > 0.0 -> (expense / income).coerceIn(0.0, 1.0)
        expense > 0.0 -> 1.0
        else -> 0.0
    }
    val spendRatioText = "${(spendRatio * 100).toInt()}%"
    val rangeText = range.format(rangeFormatter)
    val periodLabel = when (periodState.preset) {
        ReportPeriodPreset.CUSTOM -> "自定义"
        else -> periodState.preset.label
    }
    val periodOptions = ReportPeriodPreset.entries

    ScreenContainer(contentPadding = contentPadding) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ReportsHeadline(
                    periodLabel = periodLabel,
                    rangeText = rangeText,
                )
            }
            item {
                ReportHeroCard(
                    expense = formatCurrency(expense),
                    income = formatCurrency(income),
                    balance = balanceText,
                    periodLabel = periodLabel,
                    rangeText = rangeText,
                    ratioText = spendRatioText,
                    progress = spendRatio.toFloat(),
                )
            }
            item {
                PeriodFilterCard(
                    selectedLabel = periodLabel,
                    rangeText = rangeText,
                    options = periodOptions,
                    selected = periodState.preset,
                    onSelect = { option ->
                        if (option == ReportPeriodPreset.CUSTOM) {
                            showCustomRangeSheet = true
                        } else {
                            onPeriodSelected(option)
                        }
                    },
                )
            }
            item {
                ReportSectionHeader(
                    title = "详细分析",
                    subtitle = "分类、成员和账户分布",
                )
            }
            item {
                TrendCard("月度走势", reports?.monthTrend.orEmpty())
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatsSummaryCard(
                        modifier = Modifier.weight(1f),
                        title = "收入",
                        value = formatCurrency(income),
                    )
                    StatsSummaryCard(
                        modifier = Modifier.weight(1f),
                        title = "支出",
                        value = formatCurrency(expense),
                    )
                }
            }
            item {
                ReportCard("支出分类", reports?.categoryExpense.orEmpty())
            }
            item {
                ReportCard("成员统计", reports?.memberNet.orEmpty())
            }
            item {
                ReportCard("账户余额", reports?.accountBalances.orEmpty())
            }
        }
    }
    if (showCustomRangeSheet) {
        CustomRangeSheet(
            initialStart = periodState.customStart ?: range.startDate,
            initialEnd = periodState.customEnd ?: range.endDateInclusive,
            onDismiss = { showCustomRangeSheet = false },
            onConfirm = { start, end ->
                onCustomRangeSelected(start, end)
                showCustomRangeSheet = false
            },
        )
    }
}

@Composable
private fun ReportsHeadline(
    periodLabel: String,
    rangeText: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
                        MaterialTheme.colorScheme.background.copy(alpha = 0f),
                    ),
                    radius = 520f,
                ),
            ),
    ) {
        Column(
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "统计分析",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = "$periodLabel 收支概览",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = rangeText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PeriodFilterCard(
    selectedLabel: String,
    rangeText: String,
    options: List<ReportPeriodPreset>,
    selected: ReportPeriodPreset,
    onSelect: (ReportPeriodPreset) -> Unit,
) {
    val rows = options.chunked(3)

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "时间筛选",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "当前：$selectedLabel · $rangeText",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            rows.forEach { rowOptions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowOptions.forEach { option ->
                        PeriodOptionButton(
                            modifier = Modifier.weight(1f),
                            label = option.label,
                            selected = option == selected,
                            onClick = { onSelect(option) },
                        )
                    }
                    repeat(3 - rowOptions.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodOptionButton(
    modifier: Modifier = Modifier,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Button(
        modifier = modifier.height(56.dp),
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        ),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomRangeSheet(
    initialStart: LocalDate,
    initialEnd: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit,
) {
    var startText by rememberSaveable { mutableStateOf(initialStart.toString()) }
    var endText by rememberSaveable { mutableStateOf(initialEnd.toString()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "自定义周期",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            OutlinedTextField(
                value = startText,
                onValueChange = { startText = it },
                label = { Text("开始日期") },
                placeholder = { Text("2026-03-01") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = endText,
                onValueChange = { endText = it },
                label = { Text("结束日期") },
                placeholder = { Text("2026-03-31") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss,
                ) {
                    Text("取消")
                }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val start = runCatching { LocalDate.parse(startText) }.getOrNull() ?: return@Button
                        val end = runCatching { LocalDate.parse(endText) }.getOrNull() ?: return@Button
                        onConfirm(start, end)
                    },
                ) {
                    Text("应用")
                }
            }
        }
    }
}

@Composable
private fun ReportHeroCard(
    expense: String,
    income: String,
    balance: String,
    periodLabel: String,
    rangeText: String,
    ratioText: String,
    progress: Float,
) {
    Card(
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = "$periodLabel 支出",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            MoneyAmountText(
                value = expense,
                prominent = true,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Text(
                text = "$rangeText · 这是当前周期总花销",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.84f),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .background(
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(999.dp),
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0.06f, 1f))
                        .height(14.dp)
                        .background(
                            brush = Brush.horizontalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.secondary,
                                    MaterialTheme.colorScheme.tertiaryContainer,
                                ),
                            ),
                            shape = RoundedCornerShape(999.dp),
                        ),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "支出占收入 $ratioText",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f),
                )
                Card(
                    shape = RoundedCornerShape(999.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f),
                    ),
                ) {
                    Text(
                        text = ratioText,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DarkMetricCard(
                    modifier = Modifier.weight(1f),
                    label = "收入",
                    value = income,
                )
                DarkMetricCard(
                    modifier = Modifier.weight(1f),
                    label = "结余",
                    value = balance,
                )
            }
        }
    }
}

@Composable
private fun ReportSectionHeader(
    title: String,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DarkMetricCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.84f),
            )
            MoneyAmountText(
                value = value,
                prominent = false,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun HeroActionButton(
    modifier: Modifier = Modifier,
    label: String,
    emphasized: Boolean,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    Button(
        modifier = if (compact) modifier.height(64.dp) else modifier.fillMaxHeight(),
        shape = RoundedCornerShape(24.dp),
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (emphasized) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f)
            },
            contentColor = if (emphasized) {
                MaterialTheme.colorScheme.onSecondary
            } else {
                MaterialTheme.colorScheme.onPrimary
            },
        ),
    ) {
        Text(
            text = label,
            style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = if (compact) 1 else 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun StatsSummaryCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
) {
    Card(
        modifier = modifier.height(148.dp),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MoneyAmountText(
                value = value,
                prominent = false,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun MoneyAmountText(
    value: String,
    prominent: Boolean,
    color: Color,
) {
    val wholePart = remember(value) { value.substringBefore('.') }
    val fractionPart = remember(value) {
        value.substringAfter('.', "").takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
    }
    val wholeStyle = when {
        prominent && wholePart.length <= 8 -> MaterialTheme.typography.displayLarge
        prominent -> MaterialTheme.typography.displaySmall
        wholePart.length <= 8 -> MaterialTheme.typography.headlineSmall
        else -> MaterialTheme.typography.titleLarge
    }
    val fractionStyle = if (prominent) {
        MaterialTheme.typography.titleLarge
    } else {
        MaterialTheme.typography.titleMedium
    }

    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = wholePart,
            style = wholeStyle,
            fontWeight = FontWeight.Black,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (fractionPart.isNotEmpty()) {
            Text(
                text = fractionPart,
                style = fractionStyle,
                fontWeight = FontWeight.Bold,
                color = color.copy(alpha = 0.92f),
                maxLines = 1,
            )
        }
    }
}

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    cloudState: CloudUiState,
    members: List<MemberEntity>,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    onOpenCloudSync: () -> Unit,
    onOpenMembers: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenBackup: () -> Unit,
) {
    ScreenContainer(contentPadding = contentPadding) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SecondarySettingsHeader(
                    title = "设置",
                    subtitle = "同步与管理",
                    onBack = {},
                    showBack = false,
                )
            }
            item {
                StackedSurfaceCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                ) {
                    Text(
                        text = "鸿运账本",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = if (cloudState.isAuthenticated) "云端已连接" else "本地优先",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SettingsHeroMetric(
                            modifier = Modifier.weight(1f),
                            label = "成员",
                            value = "${members.size}",
                        )
                        SettingsHeroMetric(
                            modifier = Modifier.weight(1f),
                            label = "账户",
                            value = "${accounts.size}",
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SettingsHeroMetric(
                            modifier = Modifier.weight(1f),
                            label = "分类",
                            value = "${categories.size}",
                        )
                        SettingsHeroMetric(
                            modifier = Modifier.weight(1f),
                            label = "账本",
                            value = if (cloudState.isAuthenticated) "${cloudState.books.size}" else "本地",
                        )
                    }
                }
            }
            item {
                SettingsEntryCard(
                    title = "云端同步",
                    subtitle = if (cloudState.isAuthenticated) {
                        "${cloudState.username ?: cloudState.displayName.orEmpty()} · ${cloudState.books.size} 本账本"
                    } else {
                        "登录后同步账本"
                    },
                    onClick = onOpenCloudSync,
                )
            }
            item {
                SettingsEntryCard(
                    title = "成员管理",
                    subtitle = "${members.size} 位成员",
                    onClick = onOpenMembers,
                )
            }
            item {
                SettingsEntryCard(
                    title = "账户管理",
                    subtitle = "${accounts.size} 个账户",
                    onClick = onOpenAccounts,
                )
            }
            item {
                SettingsEntryCard(
                    title = "分类管理",
                    subtitle = "${categories.size} 个分类",
                    onClick = onOpenCategories,
                )
            }
            item {
                SettingsEntryCard(
                    title = "本地备份",
                    subtitle = "导入与导出",
                    onClick = onOpenBackup,
                )
            }
            item {
                EmptyCard("本地优先，云端可选。")
            }
        }
    }
}

@Composable
fun CloudSyncSettingsScreen(
    contentPadding: PaddingValues,
    cloudState: CloudUiState,
    onBack: () -> Unit,
    onOpenCloudAuth: () -> Unit,
    onRefreshCloud: () -> Unit,
    onSyncCloud: ((Result<Unit>) -> Unit) -> Unit,
    onLogoutCloud: ((Result<Unit>) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    ScreenContainer(contentPadding = contentPadding) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SecondarySettingsHeader(
                title = "云端同步",
                subtitle = "登录、同步、退出",
                onBack = onBack,
            )
            StackedSurfaceCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when {
                    cloudState.isCheckingSession -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.width(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Text("正在继续上次登录")
                            }
                        }
                    }
                    cloudState.isAuthenticated -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("当前账号", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                            Text(
                                cloudState.displayName ?: cloudState.username.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                SettingsHeroMetric(
                                    modifier = Modifier.weight(1f),
                                    label = "账号",
                                    value = cloudState.username ?: "-",
                                )
                                SettingsHeroMetric(
                                    modifier = Modifier.weight(1f),
                                    label = "账本",
                                    value = "${cloudState.books.size}",
                                )
                            }
                            cloudState.lastSyncSummary?.takeIf { it.isNotBlank() }?.let { summary ->
                                Text(
                                    text = "最近同步：$summary",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !cloudState.isSyncing,
                                onClick = {
                                    onSyncCloud { result ->
                                        result.onSuccess { toast(context, "同步完成") }
                                        result.onFailure { toast(context, it.message ?: "同步失败") }
                                    }
                                },
                            ) {
                                if (cloudState.isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.width(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                } else {
                                    Text("立即同步")
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    modifier = Modifier.weight(1f),
                                    onClick = onOpenCloudAuth,
                                ) {
                                    Text("账号信息")
                                }
                                OutlinedButton(
                                    modifier = Modifier.weight(1f),
                                    onClick = onRefreshCloud,
                                ) {
                                    Text("刷新状态")
                                }
                            }
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    onLogoutCloud { result ->
                                        result.onFailure { toast(context, it.message ?: "退出失败") }
                                    }
                                },
                            ) {
                                Text("退出当前账号")
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                    else -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("云端账号", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                            Text("当前仍是本地模式。")
                            Text(
                                text = "登录后同步账本。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            cloudState.errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = onOpenCloudAuth,
                            ) {
                                Text("登录 / 注册")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MembersSettingsScreen(
    contentPadding: PaddingValues,
    members: List<MemberEntity>,
    onBack: () -> Unit,
    onAddMember: (String) -> Unit,
    onRenameMember: (MemberEntity, String) -> Unit,
    onDeactivateMember: (MemberEntity) -> Unit,
) {
    var showMemberSheet by rememberSaveable { mutableStateOf(false) }
    var editingMember by remember { mutableStateOf<MemberEntity?>(null) }

    ScreenContainer(contentPadding = contentPadding) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SecondarySettingsHeader(
                    title = "成员管理",
                    subtitle = "新增、改名、停用",
                    onBack = onBack,
                )
            }
            item {
                StackedSurfaceCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                ) {
                    Text("成员管理", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text("新增、改名、停用", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SettingsHeroMetric(
                        modifier = Modifier.fillMaxWidth(),
                        label = "当前成员",
                        value = "${members.size}",
                    )
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { showMemberSheet = true },
                    ) {
                        Text("新增成员")
                    }
                }
            }
            if (members.isEmpty()) {
                item { EmptyCard("暂无成员") }
            } else {
                items(members, key = { it.id }) { member ->
                    SettingsListItemCard(
                        title = member.name,
                        subtitle = "启用中",
                        onEdit = { editingMember = member },
                        onDeactivate = { onDeactivateMember(member) },
                    )
                }
            }
        }
    }

    if (showMemberSheet) {
        SimpleInputSheet(
            title = "新增成员",
            fieldLabel = "成员名称",
            maxLength = ENTITY_NAME_MAX_LENGTH,
            requirementText = maxLengthHint(ENTITY_NAME_MAX_LENGTH),
            onDismiss = { showMemberSheet = false },
            onConfirm = { name ->
                onAddMember(name)
                showMemberSheet = false
            },
        )
    }
    if (editingMember != null) {
        SimpleInputSheet(
            title = "重命名成员",
            fieldLabel = "成员名称",
            initialValue = editingMember?.name.orEmpty(),
            maxLength = ENTITY_NAME_MAX_LENGTH,
            requirementText = maxLengthHint(ENTITY_NAME_MAX_LENGTH),
            onDismiss = { editingMember = null },
            onConfirm = { name ->
                editingMember?.let { onRenameMember(it, name) }
                editingMember = null
            },
        )
    }
}

@Composable
fun AccountsSettingsScreen(
    contentPadding: PaddingValues,
    accounts: List<AccountEntity>,
    onBack: () -> Unit,
    onAddAccount: (String, Double) -> Unit,
    onRenameAccount: (AccountEntity, String) -> Unit,
    onDeactivateAccount: (AccountEntity) -> Unit,
) {
    var showAccountSheet by rememberSaveable { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<AccountEntity?>(null) }

    ScreenContainer(contentPadding = contentPadding) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SecondarySettingsHeader(
                    title = "账户管理",
                    subtitle = "新增、改名、停用",
                    onBack = onBack,
                )
            }
            item {
                StackedSurfaceCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                ) {
                    Text("账户管理", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text("新增、改名、停用", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SettingsHeroMetric(
                        modifier = Modifier.fillMaxWidth(),
                        label = "当前账户",
                        value = "${accounts.size}",
                    )
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { showAccountSheet = true },
                    ) {
                        Text("新增账户")
                    }
                }
            }
            if (accounts.isEmpty()) {
                item { EmptyCard("暂无账户") }
            } else {
                items(accounts, key = { it.id }) { account ->
                    SettingsListItemCard(
                        title = account.name,
                        subtitle = "初始 ${formatCurrency(account.initialBalance)}",
                        onEdit = { editingAccount = account },
                        onDeactivate = { onDeactivateAccount(account) },
                    )
                }
            }
        }
    }

    if (showAccountSheet) {
        AccountInputSheet(
            onDismiss = { showAccountSheet = false },
            onConfirm = { name, initialBalance ->
                onAddAccount(name, initialBalance)
                showAccountSheet = false
            },
        )
    }
    if (editingAccount != null) {
        SimpleInputSheet(
            title = "重命名账户",
            fieldLabel = "账户名称",
            initialValue = editingAccount?.name.orEmpty(),
            maxLength = ENTITY_NAME_MAX_LENGTH,
            requirementText = maxLengthHint(ENTITY_NAME_MAX_LENGTH),
            onDismiss = { editingAccount = null },
            onConfirm = { name ->
                editingAccount?.let { onRenameAccount(it, name) }
                editingAccount = null
            },
        )
    }
}

@Composable
fun CategoriesSettingsScreen(
    contentPadding: PaddingValues,
    categories: List<CategoryEntity>,
    onBack: () -> Unit,
    onAddCategory: (TransactionType, String) -> Unit,
    onRenameCategory: (CategoryEntity, String) -> Unit,
    onDeactivateCategory: (CategoryEntity) -> Unit,
) {
    var showCategorySheet by rememberSaveable { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<CategoryEntity?>(null) }

    ScreenContainer(contentPadding = contentPadding) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SecondarySettingsHeader(
                    title = "分类管理",
                    subtitle = "新增、改名、停用",
                    onBack = onBack,
                )
            }
            item {
                StackedSurfaceCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                ) {
                    Text("分类管理", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text("新增、改名、停用", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SettingsHeroMetric(
                        modifier = Modifier.fillMaxWidth(),
                        label = "当前分类",
                        value = "${categories.size}",
                    )
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { showCategorySheet = true },
                    ) {
                        Text("新增分类")
                    }
                }
            }
            if (categories.isEmpty()) {
                item { EmptyCard("暂无分类") }
            } else {
                items(categories, key = { it.id }) { category ->
                    val typeLabel = if (category.type == TransactionType.EXPENSE) "支出" else "收入"
                    SettingsListItemCard(
                        title = category.name,
                        subtitle = typeLabel,
                        onEdit = { editingCategory = category },
                        onDeactivate = { onDeactivateCategory(category) },
                    )
                }
            }
        }
    }

    if (showCategorySheet) {
        CategoryInputSheet(
            onDismiss = { showCategorySheet = false },
            onConfirm = { type, name ->
                onAddCategory(type, name)
                showCategorySheet = false
            },
        )
    }
    if (editingCategory != null) {
        SimpleInputSheet(
            title = "重命名分类",
            fieldLabel = "分类名称",
            initialValue = editingCategory?.name.orEmpty(),
            maxLength = ENTITY_NAME_MAX_LENGTH,
            requirementText = maxLengthHint(ENTITY_NAME_MAX_LENGTH),
            onDismiss = { editingCategory = null },
            onConfirm = { name ->
                editingCategory?.let { onRenameCategory(it, name) }
                editingCategory = null
            },
        )
    }
}

@Composable
fun BackupSettingsScreen(
    contentPadding: PaddingValues,
    backupJsonPreview: String?,
    onBack: () -> Unit,
    onBuildBackupJson: () -> String?,
    onImportBackupJson: (String, (Result<String>) -> Unit) -> Unit,
    onGenerateBackupPreview: () -> Unit,
    onClearBackupPreview: () -> Unit,
) {
    var pendingExportContent by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val content = pendingExportContent
        if (uri != null && content != null) {
            if (writeBackupToUri(context, uri, content)) {
                toast(context, "备份已导出")
            } else {
                toast(context, "备份导出失败")
            }
        }
        pendingExportContent = null
    }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val content = readTextFromUri(context, uri)
        if (content == null) {
            toast(context, "备份读取失败")
            return@rememberLauncherForActivityResult
        }
        onImportBackupJson(content) { result ->
            result.onSuccess { bookName ->
                toast(context, "已导入到 $bookName")
            }.onFailure {
                toast(context, "备份导入失败")
            }
        }
    }

    ScreenContainer(contentPadding = contentPadding) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SecondarySettingsHeader(
                    title = "本地备份",
                    subtitle = "导出与导入",
                    onBack = onBack,
                )
            }
            item {
                StackedSurfaceCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (backupJsonPreview.isNullOrBlank()) 200.dp else 320.dp),
                ) {
                    Text("本地备份", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text("导出与导入", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (backupJsonPreview.isNullOrBlank()) {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onGenerateBackupPreview,
                        ) {
                            Text("生成预览")
                        }
                        Text("先生成一份本地备份。")
                    } else {
                        Text(
                            text = backupJsonPreview,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        ) {
                            TextButton(
                                onClick = {
                                    importLauncher.launch(arrayOf("application/json", "text/plain"))
                                },
                            ) {
                                Text("导入文件")
                            }
                            TextButton(
                                onClick = {
                                    val json = onBuildBackupJson() ?: return@TextButton
                                    pendingExportContent = json
                                    exportLauncher.launch(defaultBackupFileName())
                                },
                            ) {
                                Text("导出文件")
                            }
                            TextButton(onClick = onClearBackupPreview) {
                                Text("清空预览")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SecondarySettingsHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    showBack: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (showBack) {
            TextButton(onClick = onBack) {
                Text("返回")
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsHeroMetric(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun SettingsEntryCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = ">",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsListItemCard(
    title: String,
    subtitle: String,
    onEdit: () -> Unit,
    onDeactivate: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEdit) { Text("改名") }
                TextButton(onClick = onDeactivate) { Text("停用") }
            }
        }
    }
}

@Composable
private fun ManageableInfoRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun CloudAuthScreen(
    contentPadding: PaddingValues,
    cloudState: CloudUiState,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onClearError: () -> Unit,
    onSaveBaseUrl: (String, (Result<Unit>) -> Unit) -> Unit,
    onEnterApp: () -> Unit,
    onResumeCloud: ((Result<Unit>) -> Unit) -> Unit,
    onUseLocalMode: ((Result<Unit>) -> Unit) -> Unit,
    onLogin: (String, String, (Result<Unit>) -> Unit) -> Unit,
    onRegister: (String, String, String, (Result<Unit>) -> Unit) -> Unit,
    onLogout: ((Result<Unit>) -> Unit) -> Unit = {},
) {
    val context = LocalContext.current
    var mode by rememberSaveable { mutableStateOf(CloudAuthMode.LOGIN) }
    var baseUrl by rememberSaveable(cloudState.serverBaseUrl) { mutableStateOf(cloudState.serverBaseUrl) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var showAdvancedSettings by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(cloudState.errorMessage) {
        if (!cloudState.errorMessage.isNullOrBlank()) {
            toast(context, cloudState.errorMessage)
            onClearError()
        }
    }

    ScreenContainer(contentPadding = contentPadding) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (canGoBack) {
                TextButton(onClick = onBack) {
                    Text("返回")
                }
            }
            Text(
                text = if (cloudState.isAuthenticated) "云端账号" else "登录云端",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = if (cloudState.isAuthenticated) "查看当前账号与账本" else "登录后拉取账本",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (cloudState.isCheckingSession && !cloudState.isAuthenticated) {
                StackedSurfaceCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(22.dp),
                            strokeWidth = 2.dp,
                        )
                        Text("正在继续上次登录")
                    }
                }
            } else if (cloudState.hasSavedSession && !cloudState.isAuthenticated) {
                StackedSurfaceCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                ) {
                    Text(
                        text = "检测到上次云端登录",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = "点确认后继续。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            onResumeCloud { result ->
                                result.onSuccess { onEnterApp() }
                                result.onFailure { toast(context, it.message ?: "继续连接失败") }
                            }
                        },
                    ) {
                        Text("继续上次登录")
                    }
                }
            } else if (cloudState.isAuthenticated) {
                HighlightCard(
                    title = cloudState.displayName ?: "已连接",
                    rows = listOf(
                        "账号" to (cloudState.username ?: "-"),
                        "账本" to "${cloudState.books.size} 本",
                        "状态" to "已连接",
                    ),
                )
                if (cloudState.books.isNotEmpty()) {
                    StackedSurfaceCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                    ) {
                        Text(
                            text = "云端账本",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                        )
                        cloudState.books.forEachIndexed { index, book ->
                            ManageableInfoRow(book.name, "角色 ${book.role}")
                            if (index != cloudState.books.lastIndex) {
                                HorizontalDivider()
                            }
                        }
                    }
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onBack,
                ) {
                    Text("返回应用")
                }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        onLogout { result ->
                            result.onFailure { toast(context, it.message ?: "退出失败") }
                        }
                    },
                ) {
                    Text("退出当前账号")
                }
            } else {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    CloudAuthMode.entries.forEachIndexed { index, authMode ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index, CloudAuthMode.entries.size),
                            selected = authMode == mode,
                            onClick = { mode = authMode },
                            label = { Text(authMode.label) },
                        )
                    }
                }

                StackedSurfaceCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    Text(
                        text = "默认已连接菠萝屋云端。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        modifier = Modifier.align(Alignment.End),
                        onClick = { showAdvancedSettings = !showAdvancedSettings },
                    ) {
                        Text(if (showAdvancedSettings) "收起切换服务" else "切换服务")
                    }
                    if (showAdvancedSettings) {
                        OutlinedTextField(
                            value = baseUrl,
                            onValueChange = { baseUrl = it },
                            label = { Text("云端地址") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    baseUrl = com.bowe.localledger.data.remote.NetworkConfig.DEFAULT_BASE_URL
                                    onSaveBaseUrl(baseUrl) { result ->
                                        result.onSuccess { toast(context, "已恢复默认服务") }
                                        result.onFailure { toast(context, it.message ?: "恢复失败") }
                                    }
                                },
                            ) {
                                Text("恢复默认")
                            }
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    onSaveBaseUrl(baseUrl) { result ->
                                        result.onSuccess { toast(context, "云端地址已保存") }
                                        result.onFailure { toast(context, it.message ?: "保存失败") }
                                    }
                                },
                            ) {
                                Text("保存设置")
                            }
                        }
                    }
                    if (mode == CloudAuthMode.REGISTER) {
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = clampToMaxLength(it, CLOUD_DISPLAY_NAME_MAX_LENGTH) },
                            label = { Text("昵称") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            supportingText = {
                                LengthSupportingText(
                                    currentLength = displayName.length,
                                    maxLength = CLOUD_DISPLAY_NAME_MAX_LENGTH,
                                    requirementText = rangeLengthHint(
                                        CLOUD_DISPLAY_NAME_MIN_LENGTH,
                                        CLOUD_DISPLAY_NAME_MAX_LENGTH,
                                    ),
                                )
                            },
                        )
                    }
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = clampToMaxLength(it, CLOUD_USERNAME_MAX_LENGTH) },
                        label = { Text("账号") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = {
                            LengthSupportingText(
                                currentLength = username.length,
                                maxLength = CLOUD_USERNAME_MAX_LENGTH,
                                requirementText = rangeLengthHint(
                                    CLOUD_USERNAME_MIN_LENGTH,
                                    CLOUD_USERNAME_MAX_LENGTH,
                                ),
                            )
                        },
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = clampToMaxLength(it, CLOUD_PASSWORD_MAX_LENGTH) },
                        label = { Text("密码") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        supportingText = {
                            LengthSupportingText(
                                currentLength = password.length,
                                maxLength = CLOUD_PASSWORD_MAX_LENGTH,
                                requirementText = rangeLengthHint(
                                    CLOUD_PASSWORD_MIN_LENGTH,
                                    CLOUD_PASSWORD_MAX_LENGTH,
                                ),
                            )
                        },
                    )
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !cloudState.isSubmitting,
                        onClick = {
                            if (mode == CloudAuthMode.LOGIN) {
                                onLogin(username, password) { result ->
                                    result.onSuccess { onEnterApp() }
                                    result.onFailure { toast(context, it.message ?: "登录失败") }
                                }
                            } else {
                                onRegister(username, password, displayName) { result ->
                                    result.onSuccess { onEnterApp() }
                                    result.onFailure { toast(context, it.message ?: "注册失败") }
                                }
                            }
                        },
                    ) {
                        if (cloudState.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.width(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text(if (mode == CloudAuthMode.LOGIN) "登录并拉取账本" else "注册并初始化账本")
                        }
                    }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            onUseLocalMode { result ->
                                result.onSuccess {
                                    toast(context, "已进入本地模式")
                                    onEnterApp()
                                }
                                result.onFailure { toast(context, it.message ?: "进入本地模式失败") }
                            }
                        },
                    ) {
                        Text("暂用本地模式")
                    }
                }
            }
        }
    }
}

private enum class CloudAuthMode(val label: String) {
    LOGIN("登录"),
    REGISTER("注册"),
}

@Composable
private fun ScreenContainer(
    contentPadding: PaddingValues,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                        MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
                    ),
                ),
            )
            .padding(contentPadding),
    ) {
        content()
    }
}

private fun writeBackupToUri(context: Context, uri: Uri, content: String): Boolean {
    return runCatching {
        context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
            writer.write(content)
        } ?: error("Unable to open output stream")
    }.isSuccess
}

private fun readTextFromUri(context: Context, uri: Uri): String? {
    return runCatching {
        context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
            reader.readText()
        }
    }.getOrNull()
}

private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

private fun defaultBackupFileName(): String {
    val formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    return "local-ledger-backup-${java.time.LocalDateTime.now().format(formatter)}.json"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTransactionSheet(
    state: AddTransactionState,
    editingTransaction: TransactionListItem?,
    onDismiss: () -> Unit,
    onConfirm: (TransactionType, Double, Long, Long, Long, Instant, String) -> Unit,
) {
    val context = LocalContext.current
    val zoneId = remember { ZoneId.systemDefault() }
    val initialOccurredAt = editingTransaction?.occurredAt ?: Instant.now()
    var type by rememberSaveable(editingTransaction?.id) { mutableStateOf(editingTransaction?.type ?: TransactionType.EXPENSE) }
    var amountText by rememberSaveable(editingTransaction?.id) { mutableStateOf(editingTransaction?.amount?.toString().orEmpty()) }
    var note by rememberSaveable(editingTransaction?.id) { mutableStateOf(editingTransaction?.note.orEmpty()) }
    var selectedMemberId by rememberSaveable(editingTransaction?.id) { mutableStateOf(editingTransaction?.memberId) }
    var selectedAccountId by rememberSaveable(editingTransaction?.id) { mutableStateOf(editingTransaction?.accountId) }
    var selectedCategoryId by rememberSaveable(editingTransaction?.id) { mutableStateOf(editingTransaction?.categoryId) }
    var occurredAtEpochMillis by rememberSaveable(editingTransaction?.id) { mutableStateOf(initialOccurredAt.toEpochMilli()) }
    var occurredAtTouched by rememberSaveable(editingTransaction?.id) { mutableStateOf(editingTransaction != null) }

    val categories = state.categoriesFor(type)
    val quickAmounts = if (type == TransactionType.EXPENSE) listOf("20", "50", "100", "200") else listOf("500", "1000", "3000", "5000")
    val quickNotes = if (type == TransactionType.EXPENSE) listOf("午饭", "咖啡", "打车") else listOf("工资", "奖金", "报销")
    val selectedDateTime = remember(occurredAtEpochMillis) {
        Instant.ofEpochMilli(occurredAtEpochMillis).atZone(zoneId)
    }

    LaunchedEffect(type, state.members, state.accounts, categories) {
        if (selectedMemberId == null) selectedMemberId = state.recentMemberId ?: state.members.firstOrNull()?.id
        if (selectedAccountId == null) selectedAccountId = state.recentAccountId ?: state.accounts.firstOrNull()?.id
        if (selectedCategoryId == null || categories.none { it.id == selectedCategoryId }) {
            selectedCategoryId = state.recentCategoryIdFor(type) ?: categories.firstOrNull()?.id
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StackedSurfaceCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp),
            ) {
                Text(
                    text = if (editingTransaction == null) "记一笔" else "编辑交易",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = "选择类型",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    listOf(TransactionType.EXPENSE to "支出", TransactionType.INCOME to "收入").forEach { item ->
                        Button(
                            modifier = Modifier
                                .weight(1f)
                                .height(72.dp),
                            onClick = { type = item.first },
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (type == item.first) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                contentColor = if (type == item.first) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            ),
                        ) {
                            Text(
                                text = item.second,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("金额") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true,
                    prefix = { Text("¥", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                    textStyle = MaterialTheme.typography.displaySmall,
                )
                QuickChipRow(
                    title = "快捷金额",
                    values = quickAmounts,
                    onClick = { amountText = it },
                )
            }

            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "账目信息",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                    )
                    DateTimeSelectorRow(
                        dateText = selectedDateTime.toLocalDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                        timeText = selectedDateTime.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")),
                        onDateClick = {
                            DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    occurredAtTouched = true
                                    val localDateTime = LocalDateTime.of(
                                        LocalDate.of(year, month + 1, dayOfMonth),
                                        selectedDateTime.toLocalTime(),
                                    )
                                    occurredAtEpochMillis = localDateTime.atZone(zoneId).toInstant().toEpochMilli()
                                },
                                selectedDateTime.year,
                                selectedDateTime.monthValue - 1,
                                selectedDateTime.dayOfMonth,
                            ).show()
                        },
                        onTimeClick = {
                            TimePickerDialog(
                                context,
                                { _, hourOfDay, minute ->
                                    occurredAtTouched = true
                                    val localDateTime = LocalDateTime.of(
                                        selectedDateTime.toLocalDate(),
                                        LocalTime.of(hourOfDay, minute),
                                    )
                                    occurredAtEpochMillis = localDateTime.atZone(zoneId).toInstant().toEpochMilli()
                                },
                                selectedDateTime.hour,
                                selectedDateTime.minute,
                                true,
                            ).show()
                        },
                        onResetNow = {
                            occurredAtTouched = true
                            occurredAtEpochMillis = Instant.now().toEpochMilli()
                        },
                    )
                    LabeledDropdown(
                        label = "成员",
                        options = state.members,
                        selectedId = selectedMemberId,
                        optionLabel = { it.name },
                        optionId = { it.id },
                        onSelected = { selectedMemberId = it.id },
                    )
                    LabeledDropdown(
                        label = "账户",
                        options = state.accounts,
                        selectedId = selectedAccountId,
                        optionLabel = { it.name },
                        optionId = { it.id },
                        onSelected = { selectedAccountId = it.id },
                    )
                    LabeledDropdown(
                        label = "分类",
                        options = categories,
                        selectedId = selectedCategoryId,
                        optionLabel = { it.name },
                        optionId = { it.id },
                        onSelected = { selectedCategoryId = it.id },
                    )
                }
            }

            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "备注",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("备注") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true,
                    )
                    QuickChipRow(
                        title = "常用备注",
                        values = quickNotes,
                        onClick = { note = it },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp),
                    shape = RoundedCornerShape(22.dp),
                    onClick = onDismiss,
                ) { Text("返回") }
                Button(
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp),
                    shape = RoundedCornerShape(22.dp),
                    onClick = {
                        val amount = amountText.toDoubleOrNull() ?: return@Button
                        val memberId = selectedMemberId ?: return@Button
                        val accountId = selectedAccountId ?: return@Button
                        val categoryId = selectedCategoryId ?: return@Button
                        val occurredAt = if (editingTransaction == null && !occurredAtTouched) {
                            Instant.now()
                        } else {
                            Instant.ofEpochMilli(occurredAtEpochMillis)
                        }
                        onConfirm(
                            type,
                            amount,
                            memberId,
                            accountId,
                            categoryId,
                            occurredAt,
                            note,
                        )
                    },
                ) {
                    Text(if (editingTransaction == null) "确认记账" else "确认修改")
                }
            }
        }
    }
}

@Composable
private fun DateTimeSelectorRow(
    dateText: String,
    timeText: String,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    onResetNow: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "日期时间",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DateTimeActionButton(
            label = "日期",
            value = dateText,
            onClick = onDateClick,
        )
        DateTimeActionButton(
            label = "时间",
            value = timeText,
            onClick = onTimeClick,
        )
        OutlinedButton(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(20.dp),
            onClick = onResetNow,
        ) {
            Text("恢复到当前时间", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DateTimeActionButton(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        shape = RoundedCornerShape(22.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun LengthSupportingText(
    currentLength: Int,
    maxLength: Int?,
    requirementText: String? = null,
) {
    if (maxLength == null && requirementText.isNullOrBlank()) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = requirementText.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (maxLength != null) {
            Text(
                text = "$currentLength/$maxLength",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleInputSheet(
    title: String,
    fieldLabel: String,
    initialValue: String = "",
    maxLength: Int? = null,
    requirementText: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by rememberSaveable(initialValue) { mutableStateOf(initialValue) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = text,
                onValueChange = { next ->
                    text = maxLength?.let { clampToMaxLength(next, it) } ?: next
                },
                label = { Text(fieldLabel) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = {
                    LengthSupportingText(
                        currentLength = text.length,
                        maxLength = maxLength,
                        requirementText = requirementText,
                    )
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Button(
                    onClick = {
                        if (text.isNotBlank()) onConfirm(text)
                    },
                ) {
                    Text("保存")
                }
            }
        }
    }
}

@Composable
private fun ManageableRow(
    label: String,
    value: String,
    onEdit: () -> Unit,
    onDeactivate: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SimpleRow(label, value)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onEdit) { Text("改名") }
            TextButton(onClick = onDeactivate) { Text("停用") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountInputSheet(
    onDismiss: () -> Unit,
    onConfirm: (String, Double) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var initialBalance by rememberSaveable { mutableStateOf("0") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("新增账户", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = name,
                onValueChange = { name = clampToMaxLength(it, ENTITY_NAME_MAX_LENGTH) },
                label = { Text("账户名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = {
                    LengthSupportingText(
                        currentLength = name.length,
                        maxLength = ENTITY_NAME_MAX_LENGTH,
                        requirementText = maxLengthHint(ENTITY_NAME_MAX_LENGTH),
                    )
                },
            )
            OutlinedTextField(
                value = initialBalance,
                onValueChange = { initialBalance = it },
                label = { Text("初始余额") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Button(
                    onClick = {
                        val balance = initialBalance.toDoubleOrNull() ?: return@Button
                        if (name.isNotBlank()) onConfirm(name, balance)
                    },
                ) {
                    Text("保存")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryInputSheet(
    onDismiss: () -> Unit,
    onConfirm: (TransactionType, String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(TransactionType.EXPENSE) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("新增分类", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf(TransactionType.EXPENSE to "支出", TransactionType.INCOME to "收入").forEachIndexed { index, item ->
                    SegmentedButton(
                        selected = type == item.first,
                        onClick = { type = item.first },
                        shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(index = index, count = 2),
                    ) {
                        Text(item.second)
                    }
                }
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = clampToMaxLength(it, ENTITY_NAME_MAX_LENGTH) },
                label = { Text("分类名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = {
                    LengthSupportingText(
                        currentLength = name.length,
                        maxLength = ENTITY_NAME_MAX_LENGTH,
                        requirementText = maxLengthHint(ENTITY_NAME_MAX_LENGTH),
                    )
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Button(
                    onClick = {
                        if (name.isNotBlank()) onConfirm(type, name)
                    },
                ) {
                    Text("保存")
                }
            }
        }
    }
}

@Composable
private fun TransactionFilters(
    members: List<String>,
    accounts: List<String>,
    selectedType: String?,
    selectedMember: String?,
    selectedAccount: String?,
    onTypeSelected: (String?) -> Unit,
    onMemberSelected: (String?) -> Unit,
    onAccountSelected: (String?) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("筛选", fontWeight = FontWeight.SemiBold)
            ChipRow(
                options = listOf("全部", "EXPENSE", "INCOME"),
                selected = selectedType ?: "全部",
                label = {
                    when (it) {
                        "EXPENSE" -> "支出"
                        "INCOME" -> "收入"
                        else -> "全部"
                    }
                },
                onSelected = {
                    onTypeSelected(if (it == "全部") null else it)
                },
            )
            ChipRow(
                options = listOf("全部") + members,
                selected = selectedMember ?: "全部",
                label = { it },
                onSelected = {
                    onMemberSelected(if (it == "全部") null else it)
                },
            )
            ChipRow(
                options = listOf("全部") + accounts,
                selected = selectedAccount ?: "全部",
                label = { it },
                onSelected = {
                    onAccountSelected(if (it == "全部") null else it)
                },
            )
        }
    }
}

@Composable
private fun ChipRow(
    options: List<String>,
    selected: String,
    label: (String) -> String,
    onSelected: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.take(4).forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelected(option) },
                label = { Text(label(option)) },
            )
        }
    }
}

@Composable
private fun DashboardBookMenu(
    books: List<BookEntity>,
    currentBookId: Long?,
    onBookSelected: (Long) -> Unit,
    onAddBook: () -> Unit,
    onRenameBook: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentBook = books.firstOrNull { it.id == currentBookId } ?: books.firstOrNull()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            IconButton(onClick = { expanded = true }) {
                Icon(
                    imageVector = Icons.Outlined.Menu,
                    contentDescription = "账本菜单",
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                books.forEach { book ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (book.id == currentBook?.id) "${book.name} · 当前" else book.name,
                            )
                        },
                        onClick = {
                            onBookSelected(book.id)
                            expanded = false
                        },
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("新建账本") },
                    onClick = {
                        expanded = false
                        onAddBook()
                    },
                )
                DropdownMenuItem(
                    text = { Text("重命名当前账本") },
                    onClick = {
                        expanded = false
                        onRenameBook()
                    },
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "账本",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = currentBook?.name ?: "未选择账本",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("DEPRECATION")
@Composable
private fun <T> LabeledDropdown(
    label: String,
    options: List<T>,
    selectedId: Long?,
    optionLabel: (T) -> String,
    optionId: (T) -> Long,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { optionId(it) == selectedId }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = selected?.let(optionLabel) ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    actionText: String,
    onAction: () -> Unit,
    content: @Composable () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onAction) {
                    Text(actionText)
                }
            }
            content()
        }
    }
}

@Composable
private fun HeroDashboardCard(
    modifier: Modifier = Modifier,
    bookName: String,
    income: String,
    expense: String,
    balance: String,
    onRecord: () -> Unit,
    onOpenNaturalLanguage: () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.06f),
                            Color.Transparent,
                        ),
                    ),
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    WalletBackdropCard(
                        modifier = Modifier
                            .fillMaxWidth(0.94f)
                            .fillMaxHeight(0.88f)
                            .offset(y = 28.dp),
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    )
                    WalletBackdropCard(
                        modifier = Modifier
                            .fillMaxWidth(0.97f)
                            .fillMaxHeight(0.90f)
                            .offset(y = 14.dp),
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                    )
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.90f),
                        shape = RoundedCornerShape(30.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    brush = Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.tertiary,
                                        ),
                                    ),
                                ),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 24.dp, vertical = 22.dp),
                                verticalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = bookName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.84f),
                                    )
                                    Text(
                                        text = "本月结余",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f),
                                    )
                                }
                                MoneyAmountText(
                                    value = balance,
                                    prominent = true,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    HomeMetricBadge(
                                        modifier = Modifier.weight(1f),
                                        label = "收入",
                                        value = income,
                                    )
                                    HomeMetricBadge(
                                        modifier = Modifier.weight(1f),
                                        label = "支出",
                                        value = expense,
                                    )
                                }
                            }
                        }
                    }
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(104.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        HomeShortcutTile(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .height(104.dp),
                            label = "一句话记账",
                            subtitle = "自然输入",
                            onClick = onOpenNaturalLanguage,
                        )
                        HomeShortcutTile(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .height(104.dp),
                            label = "记一笔",
                            subtitle = "手动录入",
                            onClick = onRecord,
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(104.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        HomeSummaryTile(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .height(104.dp),
                            label = "收入",
                            value = income,
                        )
                        HomeSummaryTile(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .height(104.dp),
                            label = "支出",
                            value = expense,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WalletBackdropCard(
    modifier: Modifier = Modifier,
    containerColor: Color,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {}
}

@Composable
private fun StackedSurfaceCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier) {
        WalletBackdropCard(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.90f)
                .align(Alignment.TopCenter)
                .offset(y = 24.dp),
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        )
        WalletBackdropCard(
            modifier = Modifier
                .fillMaxWidth(0.97f)
                .fillMaxHeight(0.92f)
                .align(Alignment.TopCenter)
                .offset(y = 12.dp),
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
        )
        Card(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.TopCenter),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun HomeMetricBadge(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f),
            )
            MoneyAmountText(
                value = value,
                prominent = false,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun HomeShortcutTile(
    modifier: Modifier = Modifier,
    label: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Button(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HomeSummaryTile(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MoneyAmountText(
                value = value,
                prominent = false,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
fun NaturalLanguageEntryScreen(
    contentPadding: PaddingValues,
    state: AddTransactionState,
    onBack: () -> Unit,
    onAddTransaction: (TransactionType, Double, Long, Long, Long, Instant, String) -> Unit,
    onParse: (String) -> NaturalLanguageParseResult,
    onCloudParse: (String, (Result<NaturalLanguageParseResult>) -> Unit) -> Unit,
    canUseCloudParse: Boolean,
    onSave: (String, Long, List<ParsedTransactionCandidate>, (Result<Unit>) -> Unit) -> Unit,
) {
    var rawText by rememberSaveable { mutableStateOf("") }
    var parseResult by remember { mutableStateOf<NaturalLanguageParseResult?>(null) }
    var selectedAccountId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showDirectSheet by rememberSaveable { mutableStateOf(false) }
    var isCloudParsing by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val recentMemberName = remember(state.members, state.recentMemberId) {
        state.members.firstOrNull { it.id == state.recentMemberId }?.name.orEmpty()
    }
    val recentExpenseCategoryName = remember(state.expenseCategories, state.recentExpenseCategoryId) {
        state.expenseCategories.firstOrNull { it.id == state.recentExpenseCategoryId }?.name.orEmpty()
    }
    val recentIncomeCategoryName = remember(state.incomeCategories, state.recentIncomeCategoryId) {
        state.incomeCategories.firstOrNull { it.id == state.recentIncomeCategoryId }?.name.orEmpty()
    }
    val candidateDrafts = remember(
        parseResult,
        recentMemberName,
        recentExpenseCategoryName,
        recentIncomeCategoryName,
    ) {
        parseResult?.candidates
            ?.mapIndexed { index, candidate ->
                candidate.toEditableDraft(
                    index = index,
                    defaultMemberName = recentMemberName,
                    defaultExpenseCategoryName = recentExpenseCategoryName,
                    defaultIncomeCategoryName = recentIncomeCategoryName,
                )
            }
            ?.toMutableStateList()
            ?: emptyList<EditableParsedCandidate>().toMutableStateList()
    }

    LaunchedEffect(state.accounts, state.recentAccountId) {
        if (selectedAccountId == null) {
            selectedAccountId = state.recentAccountId ?: state.accounts.firstOrNull()?.id
        }
    }

    ScreenContainer(contentPadding = contentPadding) {
        if (parseResult == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionHeader("记账", action = "首页", onAction = onBack)
                StackedSurfaceCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        HeroActionButton(
                            modifier = Modifier.weight(1f),
                            label = "一句话",
                            emphasized = true,
                            compact = true,
                            onClick = {},
                        )
                        HeroActionButton(
                            modifier = Modifier.weight(1f),
                            label = "直接记一笔",
                            emphasized = false,
                            compact = true,
                            onClick = { showDirectSheet = true },
                        )
                    }
                    Text(
                        text = "一句话记账",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    NaturalLanguageRawTextField(
                        rawText = rawText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        onValueChange = {
                            rawText = it
                            parseResult = null
                        },
                    )
                    LabeledDropdown(
                        label = "账户",
                        options = state.accounts,
                        selectedId = selectedAccountId,
                        optionLabel = { it.name },
                        optionId = { it.id },
                        onSelected = { selectedAccountId = it.id },
                    )
                    ParseActionRow(
                        canUseCloudParse = canUseCloudParse,
                        isCloudParsing = isCloudParsing,
                        onLocalParse = {
                            parseResult = onParse(rawText)
                        },
                        onCloudParse = {
                            isCloudParsing = true
                            onCloudParse(rawText) { result ->
                                isCloudParsing = false
                                result.onSuccess { parseResult = it }
                                    .onFailure { toast(context, it.message ?: "智能解析失败") }
                            }
                        },
                        onClear = {
                            rawText = ""
                            parseResult = null
                        },
                    )
                    if (!canUseCloudParse) {
                        Text(
                            text = "登录云端后可用智能解析",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            val result = requireNotNull(parseResult)
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    SectionHeader("记账", action = "首页", onAction = onBack)
                }
                item {
                    StackedSurfaceCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillParentMaxHeight(0.68f),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            HeroActionButton(
                                modifier = Modifier.weight(1f),
                                label = "一句话",
                                emphasized = true,
                                compact = true,
                                onClick = {},
                            )
                            HeroActionButton(
                                modifier = Modifier.weight(1f),
                                label = "直接记一笔",
                                emphasized = false,
                                compact = true,
                                onClick = { showDirectSheet = true },
                            )
                        }
                        Text(
                            text = "一句话记账",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        NaturalLanguageRawTextField(
                            rawText = rawText,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            onValueChange = {
                                rawText = it
                                parseResult = null
                            },
                        )
                        LabeledDropdown(
                            label = "账户",
                            options = state.accounts,
                            selectedId = selectedAccountId,
                            optionLabel = { it.name },
                            optionId = { it.id },
                            onSelected = { selectedAccountId = it.id },
                        )
                        ParseActionRow(
                            canUseCloudParse = canUseCloudParse,
                            isCloudParsing = isCloudParsing,
                            onLocalParse = {
                                parseResult = onParse(rawText)
                            },
                            onCloudParse = {
                                isCloudParsing = true
                                onCloudParse(rawText) { result ->
                                    isCloudParsing = false
                                    result.onSuccess { parseResult = it }
                                        .onFailure { toast(context, it.message ?: "智能解析失败") }
                                }
                            },
                            onClear = {
                                rawText = ""
                                parseResult = null
                            },
                        )
                        if (!canUseCloudParse) {
                            Text(
                                text = "登录云端后可用智能解析",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item {
                    ParseResultStatusCard(result = result)
                }
                item {
                    if (result.warnings.isNotEmpty()) {
                        EmptyCard(result.warnings.joinToString("\n"))
                    }
                }
                item {
                    SectionHeader(
                        title = "${candidateDrafts.count { it.included }} 条待记账",
                        action = "返回",
                        onAction = onBack,
                    )
                }
                item {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    val accountId = selectedAccountId
                                    if (accountId == null) {
                                        toast(context, "请先选择默认账户")
                                        return@Button
                                    }
                                    val includedDrafts = candidateDrafts.filter { it.included }
                                    if (includedDrafts.isEmpty()) {
                                        toast(context, "请至少保留一条候选账单")
                                        return@Button
                                    }
                                    val invalidCount = includedDrafts.count { !it.isReadyToSave() }
                                    if (invalidCount > 0) {
                                        toast(context, "请补齐金额和分类后再记账")
                                        return@Button
                                    }
                                    onSave(
                                        result.rawText,
                                        accountId,
                                        includedDrafts.mapNotNull { it.toParsedCandidateOrNull() },
                                    ) { saveResult ->
                                        saveResult.onSuccess {
                                            toast(context, "已记入账本")
                                            onBack()
                                        }.onFailure {
                                            toast(context, "记账失败")
                                        }
                                    }
                                },
                            ) {
                                Text("记入账本")
                            }
                        }
                    }
                }
                if (candidateDrafts.isEmpty()) {
                    item { EmptyCard("没有识别出可保存的账单。") }
                } else {
                    items(candidateDrafts, key = { it.id }) { draft ->
                        EditableParsedCandidateCard(
                            draft = draft,
                            members = state.members,
                            expenseCategories = state.expenseCategories,
                            incomeCategories = state.incomeCategories,
                            onUpdate = { updated ->
                                val index = candidateDrafts.indexOfFirst { it.id == updated.id }
                                if (index >= 0) candidateDrafts[index] = updated
                            },
                        )
                    }
                }
            }
        }
    }

    if (showDirectSheet) {
        AddTransactionSheet(
            state = state,
            editingTransaction = null,
            onDismiss = { showDirectSheet = false },
            onConfirm = { type, amount, memberId, accountId, categoryId, occurredAt, note ->
                onAddTransaction(type, amount, memberId, accountId, categoryId, occurredAt, note)
                showDirectSheet = false
                toast(context, "已记入账本")
                onBack()
            },
        )
    }
}

@Composable
private fun NaturalLanguageRawTextField(
    rawText: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = rawText,
        onValueChange = { onValueChange(clampToMaxLength(it, NLP_RAW_TEXT_MAX_LENGTH)) },
        placeholder = { Text("例如：昨天买菜 86，今天打车 30") },
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        supportingText = {
            LengthSupportingText(
                currentLength = rawText.length,
                maxLength = NLP_RAW_TEXT_MAX_LENGTH,
                requirementText = maxLengthHint(NLP_RAW_TEXT_MAX_LENGTH),
            )
        },
    )
}

@Composable
private fun ParseActionRow(
    canUseCloudParse: Boolean,
    isCloudParsing: Boolean,
    onLocalParse: () -> Unit,
    onCloudParse: () -> Unit,
    onClear: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(20.dp),
                onClick = onLocalParse,
            ) {
                Text("快速解析")
            }
            Button(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(20.dp),
                enabled = canUseCloudParse && !isCloudParsing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                onClick = onCloudParse,
            ) {
                if (isCloudParsing) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                } else {
                    Text("智能解析")
                }
            }
        }
        OutlinedButton(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(18.dp),
            onClick = onClear,
        ) {
            Text("清空")
        }
    }
}

@Composable
private fun EditableParsedCandidateCard(
    draft: EditableParsedCandidate,
    members: List<MemberEntity>,
    expenseCategories: List<CategoryEntity>,
    incomeCategories: List<CategoryEntity>,
    onUpdate: (EditableParsedCandidate) -> Unit,
) {
    val categoryOptions = if (draft.type == TransactionType.EXPENSE) expenseCategories else incomeCategories
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ConfidencePill(confidence = draft.confidence)
                Text(
                    text = draft.sourceSnippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = draft.note.ifBlank { "未命名记录" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                FilterChip(
                    selected = draft.included,
                    onClick = { onUpdate(draft.copy(included = !draft.included)) },
                    label = { Text(if (draft.included) "保留" else "跳过") },
                )
            }
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf(TransactionType.EXPENSE to "支出", TransactionType.INCOME to "收入").forEachIndexed { index, item ->
                    SegmentedButton(
                        selected = draft.type == item.first,
                        onClick = {
                            val nextCategories = if (item.first == TransactionType.EXPENSE) expenseCategories else incomeCategories
                            val nextCategoryName = if (nextCategories.any { it.name == draft.categoryName }) {
                                draft.categoryName
                            } else {
                                nextCategories.firstOrNull()?.name.orEmpty()
                            }
                            onUpdate(
                                draft.copy(
                                    type = item.first,
                                    categoryName = nextCategoryName,
                                ),
                            )
                        },
                        shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(index = index, count = 2),
                    ) {
                        Text(item.second)
                    }
                }
            }
            OutlinedTextField(
                value = draft.amountText,
                onValueChange = { onUpdate(draft.copy(amountText = it)) },
                label = { Text("金额") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                singleLine = true,
            )
            OutlinedTextField(
                value = draft.occurredOnText,
                onValueChange = { onUpdate(draft.copy(occurredOnText = it)) },
                label = { Text("日期") },
                placeholder = { Text("YYYY-MM-DD") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                singleLine = true,
            )
            LabeledDropdown(
                label = "成员",
                options = members,
                selectedId = members.firstOrNull { it.name == draft.memberName }?.id,
                optionLabel = { it.name },
                optionId = { it.id },
                onSelected = { onUpdate(draft.copy(memberName = it.name)) },
            )
            LabeledDropdown(
                label = "分类",
                options = categoryOptions,
                selectedId = categoryOptions.firstOrNull { it.name == draft.categoryName }?.id,
                optionLabel = { it.name },
                optionId = { it.id },
                onSelected = { onUpdate(draft.copy(categoryName = it.name)) },
            )
            OutlinedTextField(
                value = draft.note,
                onValueChange = { onUpdate(draft.copy(note = it)) },
                label = { Text("备注") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
            )
        }
    }
}

@Composable
private fun ParseResultStatusCard(
    result: NaturalLanguageParseResult,
) {
    val averageConfidence = remember(result.candidates) {
        if (result.candidates.isEmpty()) 0f
        else result.candidates.map { it.confidence }.average().toFloat()
    }
    val modeLabel = if (result.parseMode == ParseMode.CLOUD) "智能解析" else "快速解析"
    val sourceLabel = result.providerLabel ?: if (result.parseMode == ParseMode.CLOUD) "云端模型" else "本地规则"

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = modeLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = sourceLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ConfidencePill(confidence = averageConfidence)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MiniMetricCard(
                    modifier = Modifier.weight(1f),
                    label = "候选",
                    value = "${result.candidates.size} 条",
                )
                MiniMetricCard(
                    modifier = Modifier.weight(1f),
                    label = "原文",
                    value = if (result.diaryText.length > 10) "${result.diaryText.length} 字" else result.diaryText.ifBlank { "0 字" },
                )
            }
        }
    }
}

@Composable
private fun ConfidencePill(confidence: Float) {
    val percentage = (confidence * 100).toInt().coerceIn(0, 99)
    val label = when {
        percentage >= 80 -> "高"
        percentage >= 60 -> "中"
        else -> "低"
    }
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Text(
            text = "识别$label · $percentage%",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private data class EditableParsedCandidate(
    val id: String,
    val included: Boolean,
    val type: TransactionType,
    val amountText: String,
    val occurredOnText: String,
    val memberName: String,
    val categoryName: String,
    val note: String,
    val confidence: Float,
    val sourceSnippet: String,
)

private fun ParsedTransactionCandidate.toEditableDraft(
    index: Int,
    defaultMemberName: String,
    defaultExpenseCategoryName: String,
    defaultIncomeCategoryName: String,
): EditableParsedCandidate {
    val fallbackCategoryName = if (type == TransactionType.EXPENSE) {
        defaultExpenseCategoryName
    } else {
        defaultIncomeCategoryName
    }
    return EditableParsedCandidate(
        id = "$index-$sourceSnippet",
        included = amount != null,
        type = type,
        amountText = amount?.toEditableAmount().orEmpty(),
        occurredOnText = occurredOn?.toString().orEmpty(),
        memberName = memberName ?: defaultMemberName,
        categoryName = categoryName ?: fallbackCategoryName,
        note = note,
        confidence = confidence,
        sourceSnippet = sourceSnippet,
    )
}

private fun EditableParsedCandidate.isReadyToSave(): Boolean {
    return amountText.toDoubleOrNull() != null && categoryName.isNotBlank()
}

private fun EditableParsedCandidate.toParsedCandidateOrNull(): ParsedTransactionCandidate? {
    val parsedAmount = amountText.toDoubleOrNull() ?: return null
    val parsedDate = occurredOnText
        .takeIf { it.isNotBlank() }
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    return ParsedTransactionCandidate(
        type = type,
        amount = parsedAmount,
        occurredOn = parsedDate,
        memberName = memberName.ifBlank { null },
        categoryName = categoryName.ifBlank { null },
        note = note.ifBlank { sourceSnippet },
        confidence = confidence,
        sourceSnippet = sourceSnippet,
    )
}

private fun Double.toEditableAmount(): String {
    val longValue = toLong()
    return if (this == longValue.toDouble()) longValue.toString() else toString()
}

@Composable
private fun QuickChipRow(
    title: String,
    values: List<String>,
    onClick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            values.forEach { value ->
                OutlinedButton(
                    onClick = { onClick(value) },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.height(44.dp),
                ) {
                    Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun QuickInsightsRow(items: List<Pair<String, String>>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items.forEach { (label, value) ->
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        TextButton(onClick = onAction) {
            Text(action, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun MiniMetricCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MetricColumn(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun HighlightCard(title: String, rows: List<Pair<String, String>>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            rows.forEach { (label, value) ->
                SimpleRow(label, value)
            }
        }
    }
}

@Composable
private fun EmptyCard(message: String) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TransactionCard(transaction: TransactionListItem) {
    TransactionCard(transaction = transaction, onEdit = {}, onDelete = {})
}

@Composable
private fun TransactionCard(
    transaction: TransactionListItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val formatter = remember { DateTimeFormatter.ofPattern("MM-dd HH:mm") }
    val accentBrush = if (transaction.type == TransactionType.INCOME) {
        Brush.linearGradient(listOf(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary))
    } else {
        Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))
    }
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(52.dp)
                        .height(52.dp)
                        .background(accentBrush, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (transaction.type == TransactionType.INCOME) "入" else "支",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(transaction.categoryName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${transaction.memberName} · ${transaction.accountName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = if (transaction.type == TransactionType.INCOME) {
                            "+${formatCurrency(transaction.amount)}"
                        } else {
                            "-${formatCurrency(transaction.amount)}"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (transaction.type == TransactionType.INCOME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                    Text(
                        transaction.occurredAt.atZone(ZoneId.systemDefault()).format(formatter),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                transaction.note.ifBlank { "暂无备注" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEdit) { Text("编辑") }
                TextButton(onClick = onDelete) { Text("删除") }
                }
            }
        }
    }
}

@Composable
private fun ReportCard(title: String, items: List<ReportValue>) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (items.isEmpty()) {
                Text("暂无数据", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items.forEachIndexed { index, item ->
                ReportBarRow(
                    label = item.label,
                    value = formatSignedCurrency(item.value),
                    fraction = reportFraction(item.value, items),
                )
                if (index != items.lastIndex) HorizontalDivider(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun TrendCard(title: String, items: List<TrendValue>) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (items.isEmpty()) {
                Text("暂无趋势数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                items.forEach { item ->
                    val maxValue = maxOf(item.income, item.expense, 1.0)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(item.label, fontWeight = FontWeight.Bold)
                            Text(
                                "收 ${formatCurrency(item.income)} / 支 ${formatCurrency(item.expense)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TrendBar(
                            label = "收入",
                            fraction = (item.income / maxValue).toFloat(),
                            brush = Brush.horizontalGradient(
                                listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary),
                            ),
                        )
                        TrendBar(
                            label = "支出",
                            fraction = (item.expense / maxValue).toFloat(),
                            brush = Brush.horizontalGradient(
                                listOf(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.error),
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendBar(
    label: String,
    fraction: Float,
    brush: Brush,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .background(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(999.dp),
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0.06f, 1f))
                    .height(10.dp)
                    .background(brush = brush, shape = RoundedCornerShape(999.dp)),
            )
        }
    }
}

@Composable
private fun ReportBarRow(label: String, value: String, fraction: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(999.dp),
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0.08f, 1f))
                    .height(8.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary,
                            ),
                        ),
                        shape = RoundedCornerShape(999.dp),
                    ),
            )
        }
    }
}

@Composable
private fun SimpleRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

private fun formatCurrency(value: Double): String {
    val normalized = value.absoluteValue
    return "¥${DecimalFormat("#,##0.00").format(normalized)}"
}

private fun formatSignedCurrency(value: Double): String {
    return if (value < 0) "-${formatCurrency(value)}" else formatCurrency(value)
}

private fun reportFraction(value: Double, items: List<ReportValue>): Float {
    val max = items.maxOfOrNull { kotlin.math.abs(it.value) } ?: return 0f
    if (max == 0.0) return 0f
    return (kotlin.math.abs(value) / max).toFloat()
}
