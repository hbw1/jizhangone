package com.bowe.localledger.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.bowe.localledger.LocalLedgerApp
import com.bowe.localledger.ui.screen.DashboardScreen
import com.bowe.localledger.ui.screen.CloudAuthScreen
import com.bowe.localledger.ui.screen.NaturalLanguageEntryScreen
import com.bowe.localledger.ui.screen.ReportsScreen
import com.bowe.localledger.ui.screen.SettingsScreen
import com.bowe.localledger.ui.screen.TransactionsScreen

private enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: @Composable () -> Unit,
) {
    Dashboard(
        route = "dashboard",
        label = "首页",
        icon = { Icon(Icons.Outlined.Home, contentDescription = "首页") },
    ),
    NaturalLanguage(
        route = "nl-entry",
        label = "记账",
        icon = { Icon(Icons.Outlined.EditNote, contentDescription = "一句话记账") },
    ),
    Transactions(
        route = "transactions",
        label = "明细",
        icon = { Icon(Icons.AutoMirrored.Outlined.ListAlt, contentDescription = "明细") },
    ),
    Reports(
        route = "reports",
        label = "报表",
        icon = { Icon(Icons.Outlined.Assessment, contentDescription = "报表") },
    ),
    Settings(
        route = "settings",
        label = "设置",
        icon = { Icon(Icons.Outlined.Settings, contentDescription = "设置") },
    ),
}

private const val CloudAuthRoute = "cloud-auth"

@Composable
fun LocalLedgerAppRoot() {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as LocalLedgerApp
    val viewModel: AppViewModel = viewModel(
        factory = AppViewModelFactory(
            repository = app.repository,
            cloudAuthRepository = app.cloudAuthRepository,
            cloudSyncRepository = app.cloudSyncRepository,
            remoteLedgerDataSource = app.remoteDataSource,
        ),
    )
    val navController = rememberNavController()
    val backStackEntry = navController.currentBackStackEntryAsState().value
    val currentRoute = backStackEntry?.destination?.route
    val destinations = TopLevelDestination.entries
    val books by viewModel.books.collectAsStateWithLifecycle()
    val currentBookId by viewModel.currentBookId.collectAsStateWithLifecycle()
    val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val reports by viewModel.reports.collectAsStateWithLifecycle()
    val reportPeriodState by viewModel.reportPeriodState.collectAsStateWithLifecycle()
    val addTransactionState by viewModel.addTransactionState.collectAsStateWithLifecycle()
    val members by viewModel.members.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val allCategories by viewModel.allCategories.collectAsStateWithLifecycle()
    val backupJsonPreview by viewModel.backupJsonPreview.collectAsStateWithLifecycle()
    val cloudUiState by viewModel.cloudUiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (currentRoute != CloudAuthRoute) {
            Card(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                shape = RoundedCornerShape(30.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            ) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    destinations.forEach { destination ->
                        val isNaturalLanguage = destination == TopLevelDestination.NaturalLanguage
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = destination.icon,
                            label = { Text(destination.label) },
                            colors = if (isNaturalLanguage) {
                                NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.primary,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                NavigationBarItemDefaults.colors()
                            },
                        )
                    }
                }
            }
            }
        },
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.Dashboard.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(PaddingValues(bottom = 4.dp)),
        ) {
            composable(TopLevelDestination.Dashboard.route) {
                DashboardScreen(
                    contentPadding = paddingValues,
                    books = books,
                    currentBookId = currentBookId,
                    dashboard = dashboard,
                    transactions = transactions,
                    addTransactionState = addTransactionState,
                    onBookSelected = viewModel::selectBook,
                    onAddBook = viewModel::addBook,
                    onRenameBook = viewModel::renameCurrentBook,
                    onAddTransaction = viewModel::addTransaction,
                    onOpenNaturalLanguage = { navController.navigate(TopLevelDestination.NaturalLanguage.route) },
                )
            }
            composable(TopLevelDestination.NaturalLanguage.route) {
                NaturalLanguageEntryScreen(
                    contentPadding = paddingValues,
                    state = addTransactionState,
                    onBack = { navController.navigate(TopLevelDestination.Dashboard.route) },
                    onParse = viewModel::parseNaturalLanguage,
                    onSave = viewModel::saveNaturalLanguageEntry,
                )
            }
            composable(TopLevelDestination.Transactions.route) {
                TransactionsScreen(
                    contentPadding = paddingValues,
                    transactions = transactions,
                    addTransactionState = addTransactionState,
                    onAddTransaction = viewModel::addTransaction,
                    onUpdateTransaction = viewModel::updateTransaction,
                    onDeleteTransaction = viewModel::deleteTransaction,
                )
            }
            composable(TopLevelDestination.Reports.route) {
                ReportsScreen(
                    contentPadding = paddingValues,
                    reports = reports,
                    periodState = reportPeriodState,
                    onPeriodSelected = viewModel::selectReportPeriod,
                    onCustomRangeSelected = viewModel::updateCustomReportRange,
                )
            }
            composable(TopLevelDestination.Settings.route) {
                SettingsScreen(
                    contentPadding = paddingValues,
                    cloudState = cloudUiState,
                    members = members,
                    accounts = accounts,
                    categories = allCategories,
                    backupJsonPreview = backupJsonPreview,
                    onOpenCloudAuth = { navController.navigate(CloudAuthRoute) },
                    onRefreshCloud = viewModel::refreshCloudSession,
                    onSyncCloud = viewModel::syncCloudNow,
                    onLogoutCloud = viewModel::logoutCloud,
                    onAddMember = viewModel::addMember,
                    onAddAccount = viewModel::addAccount,
                    onAddCategory = viewModel::addCategory,
                    onRenameMember = viewModel::renameMember,
                    onDeactivateMember = viewModel::deactivateMember,
                    onRenameAccount = viewModel::renameAccount,
                    onDeactivateAccount = viewModel::deactivateAccount,
                    onRenameCategory = viewModel::renameCategory,
                    onDeactivateCategory = viewModel::deactivateCategory,
                    onBuildBackupJson = viewModel::buildBackupJson,
                    onImportBackupJson = viewModel::importBackupJson,
                    onGenerateBackupPreview = viewModel::generateBackupPreview,
                    onClearBackupPreview = viewModel::clearBackupPreview,
                )
            }
            composable(CloudAuthRoute) {
                CloudAuthScreen(
                    contentPadding = paddingValues,
                    cloudState = cloudUiState,
                    onBack = { navController.popBackStack() },
                    onClearError = viewModel::clearCloudError,
                    onSaveBaseUrl = viewModel::saveCloudServerBaseUrl,
                    onLogin = { username, password, onResult ->
                        viewModel.loginToCloud(username, password, onResult)
                    },
                    onRegister = { username, password, displayName, onResult ->
                        viewModel.registerToCloud(username, password, displayName, onResult)
                    },
                )
            }
        }
    }
}
