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
import androidx.compose.runtime.LaunchedEffect
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
import com.bowe.localledger.ui.screen.BackupSettingsScreen
import com.bowe.localledger.ui.screen.CategoriesSettingsScreen
import com.bowe.localledger.ui.screen.CloudSyncSettingsScreen
import com.bowe.localledger.ui.screen.CloudAuthScreen
import com.bowe.localledger.ui.screen.AccountsSettingsScreen
import com.bowe.localledger.ui.screen.MembersSettingsScreen
import com.bowe.localledger.ui.screen.NaturalLanguageEntryScreen
import com.bowe.localledger.ui.screen.ReportsScreen
import com.bowe.localledger.ui.screen.SettingsScreen
import com.bowe.localledger.ui.screen.TransactionsScreen
import java.time.Instant

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
private const val BootRoute = "boot"
private const val SettingsCloudRoute = "settings/cloud"
private const val SettingsMembersRoute = "settings/members"
private const val SettingsAccountsRoute = "settings/accounts"
private const val SettingsCategoriesRoute = "settings/categories"
private const val SettingsBackupRoute = "settings/backup"

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
    val startupDestination by viewModel.startupDestination.collectAsStateWithLifecycle()
    val canGoBackFromCloud = books.isNotEmpty() || cloudUiState.isAuthenticated
    val selectedTopLevelRoute = when {
        currentRoute?.startsWith(TopLevelDestination.Settings.route) == true -> TopLevelDestination.Settings.route
        else -> currentRoute
    }

    LaunchedEffect(startupDestination, currentRoute) {
        if (currentRoute != BootRoute) return@LaunchedEffect
        when (startupDestination) {
            StartupDestination.APP -> {
                navController.navigate(TopLevelDestination.Dashboard.route) {
                    popUpTo(BootRoute) {
                        inclusive = true
                    }
                    launchSingleTop = true
                }
            }
            StartupDestination.AUTH -> {
                navController.navigate(CloudAuthRoute) {
                    popUpTo(BootRoute) {
                        inclusive = true
                    }
                    launchSingleTop = true
                }
            }
            StartupDestination.LOADING -> Unit
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (currentRoute != CloudAuthRoute && currentRoute != BootRoute) {
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
                            selected = selectedTopLevelRoute == destination.route,
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
            startDestination = BootRoute,
            modifier = Modifier
                .fillMaxSize()
                .padding(PaddingValues(bottom = 4.dp)),
        ) {
            composable(BootRoute) {
                PaddingValues(bottom = 4.dp)
                Text("")
            }
            composable(TopLevelDestination.Dashboard.route) {
                DashboardScreen(
                    contentPadding = paddingValues,
                    books = books,
                    currentBookId = currentBookId,
                    dashboard = dashboard,
                    addTransactionState = addTransactionState,
                    onBookSelected = viewModel::selectBook,
                    onAddBook = viewModel::addBook,
                    onRenameBook = viewModel::renameCurrentBook,
                    onAddTransaction = { type, amount, memberId, accountId, categoryId, occurredAt, note ->
                        viewModel.addTransaction(type, amount, memberId, accountId, categoryId, occurredAt, note)
                    },
                    onOpenNaturalLanguage = { navController.navigate(TopLevelDestination.NaturalLanguage.route) },
                )
            }
            composable(TopLevelDestination.NaturalLanguage.route) {
                NaturalLanguageEntryScreen(
                    contentPadding = paddingValues,
                    state = addTransactionState,
                    onBack = { navController.navigate(TopLevelDestination.Dashboard.route) },
                    onAddTransaction = { type, amount, memberId, accountId, categoryId, occurredAt, note ->
                        viewModel.addTransaction(type, amount, memberId, accountId, categoryId, occurredAt, note)
                    },
                    onParse = viewModel::parseNaturalLanguage,
                    onSave = viewModel::saveNaturalLanguageEntry,
                )
            }
            composable(TopLevelDestination.Transactions.route) {
                TransactionsScreen(
                    contentPadding = paddingValues,
                    transactions = transactions,
                    addTransactionState = addTransactionState,
                    onAddTransaction = { type, amount, memberId, accountId, categoryId, occurredAt, note ->
                        viewModel.addTransaction(type, amount, memberId, accountId, categoryId, occurredAt, note)
                    },
                    onUpdateTransaction = { transaction, type, amount, memberId, accountId, categoryId, occurredAt, note ->
                        viewModel.updateTransaction(transaction, type, amount, memberId, accountId, categoryId, occurredAt, note)
                    },
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
                    onOpenCloudSync = { navController.navigate(SettingsCloudRoute) },
                    onOpenMembers = { navController.navigate(SettingsMembersRoute) },
                    onOpenAccounts = { navController.navigate(SettingsAccountsRoute) },
                    onOpenCategories = { navController.navigate(SettingsCategoriesRoute) },
                    onOpenBackup = { navController.navigate(SettingsBackupRoute) },
                )
            }
            composable(SettingsCloudRoute) {
                CloudSyncSettingsScreen(
                    contentPadding = paddingValues,
                    cloudState = cloudUiState,
                    onBack = { navController.popBackStack() },
                    onOpenCloudAuth = {
                        navController.navigate(CloudAuthRoute) {
                            launchSingleTop = true
                        }
                    },
                    onRefreshCloud = viewModel::refreshCloudSession,
                    onSyncCloud = viewModel::syncCloudNow,
                    onLogoutCloud = { onResult ->
                        viewModel.logoutCloud { result ->
                            result.onSuccess {
                                navController.navigate(CloudAuthRoute) {
                                    popUpTo(SettingsCloudRoute) {
                                        inclusive = true
                                    }
                                    launchSingleTop = true
                                }
                            }
                            onResult(result)
                        }
                    },
                )
            }
            composable(SettingsMembersRoute) {
                MembersSettingsScreen(
                    contentPadding = paddingValues,
                    members = members,
                    onBack = { navController.popBackStack() },
                    onAddMember = viewModel::addMember,
                    onRenameMember = viewModel::renameMember,
                    onDeactivateMember = viewModel::deactivateMember,
                )
            }
            composable(SettingsAccountsRoute) {
                AccountsSettingsScreen(
                    contentPadding = paddingValues,
                    accounts = accounts,
                    onBack = { navController.popBackStack() },
                    onAddAccount = viewModel::addAccount,
                    onRenameAccount = viewModel::renameAccount,
                    onDeactivateAccount = viewModel::deactivateAccount,
                )
            }
            composable(SettingsCategoriesRoute) {
                CategoriesSettingsScreen(
                    contentPadding = paddingValues,
                    categories = allCategories,
                    onBack = { navController.popBackStack() },
                    onAddCategory = viewModel::addCategory,
                    onRenameCategory = viewModel::renameCategory,
                    onDeactivateCategory = viewModel::deactivateCategory,
                )
            }
            composable(SettingsBackupRoute) {
                BackupSettingsScreen(
                    contentPadding = paddingValues,
                    backupJsonPreview = backupJsonPreview,
                    onBack = { navController.popBackStack() },
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
                    canGoBack = canGoBackFromCloud,
                    onBack = { navController.popBackStack() },
                    onClearError = viewModel::clearCloudError,
                    onSaveBaseUrl = viewModel::saveCloudServerBaseUrl,
                    onEnterApp = {
                        navController.navigate(TopLevelDestination.Dashboard.route) {
                            popUpTo(CloudAuthRoute) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    },
                    onResumeCloud = { onResult ->
                        viewModel.resumeCloudSession(onResult)
                    },
                    onUseLocalMode = { onResult ->
                        viewModel.enterLocalMode(onResult)
                    },
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
