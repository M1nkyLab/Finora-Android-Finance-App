package com.dime.app.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.dime.app.ui.components.bounceClick
import androidx.navigation.compose.rememberNavController
import com.dime.app.ui.addtransaction.AddTransactionSheet
import com.dime.app.ui.budget.BudgetScreen
import com.dime.app.ui.dashboard.DashboardScreen
import com.dime.app.ui.dashboard.DashboardViewModel
import com.dime.app.ui.insights.InsightsScreen
import com.dime.app.ui.settings.SettingsScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

// ── Route constants ─────────────────────────────────────────────────────────────
object Route {
    const val LOG      = "log"
    const val INSIGHTS = "insights"
    const val BUDGET   = "budget"
    const val SETTINGS = "settings"
}

private data class TabItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon
)

private val tabs = listOf(
    TabItem(Route.LOG,      "Log",      Icons.Rounded.Receipt),
    TabItem(Route.INSIGHTS, "Insights", Icons.Rounded.BarChart),
    TabItem(Route.BUDGET,   "Budget",   Icons.Rounded.Wallet),
    TabItem(Route.SETTINGS, "Settings", Icons.Rounded.Settings)
)

/**
 * Root composable — replaces iOS HomeView + CustomTabBar.
 *
 *  - NavHost  → iOS TabView
 *  - DimeNavigationBar → iOS CustomTabBar
 *  - FAB ("+") → opens AddTransactionSheet
 *  - ModalBottomSheet → manual transaction entry
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    startAddTransaction: Boolean = false,
    onAddTransactionOpened: () -> Unit = {}
) {
    val navController  = rememberNavController()
    val scope          = rememberCoroutineScope()

    // Hoist DashboardViewModel here so AddTransactionSheet can share account state
    val dashVm: DashboardViewModel = hiltViewModel()
    val accounts       by dashVm.accounts.collectAsStateWithLifecycle()
    val selectedAccId  by dashVm.selectedAccountId.collectAsStateWithLifecycle()

    var showAddTransaction by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(startAddTransaction) {
        if (startAddTransaction) {
            showAddTransaction = true
            onAddTransactionOpened()
        }
    }

    val openAddTransaction: () -> Unit = {
        showAddTransaction = true
    }

    // Full-screen Box so the nav bar can float over the content
    Box(modifier = Modifier.fillMaxSize()) {
        // ── Main screen content (fills the entire screen) ────────────────────
        NavHost(
            navController    = navController,
            startDestination = Route.LOG,
            modifier         = Modifier.fillMaxSize(),
        ) {
            composable(Route.LOG,
                enterTransition  = { fadeIn(tween(200)) },
                exitTransition   = { fadeOut(tween(200)) }
            ) {
                // Pass the same hoisted ViewModel so DashboardScreen shares state
                DashboardScreen(viewModel = dashVm)
            }
            composable(Route.INSIGHTS,
                enterTransition  = { fadeIn(tween(200)) },
                exitTransition   = { fadeOut(tween(200)) }
            ) {
                InsightsScreen()
            }
            composable(Route.BUDGET,
                enterTransition  = { fadeIn(tween(200)) },
                exitTransition   = { fadeOut(tween(200)) }
            ) {
                BudgetScreen()
            }
            composable(Route.SETTINGS,
                enterTransition  = { fadeIn(tween(200)) },
                exitTransition   = { fadeOut(tween(200)) }
            ) {
                SettingsScreen()
            }
        }

        // ── Floating nav bar overlaid at the bottom ───────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()  // Respect gesture/button bar inset
        ) {
            DimeNavigationBar(
                navController = navController,
                tabs          = tabs,
                onAddClick    = openAddTransaction
            )
        }
    }


    // ── Add Transaction bottom sheet ───────────────────────────────────────────
    if (showAddTransaction) {
        ModalBottomSheet(
            onDismissRequest  = { showAddTransaction = false },
            sheetState        = sheetState,
            containerColor    = MaterialTheme.colorScheme.background,
            dragHandle        = { Box(Modifier.padding(top = 8.dp).size(40.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.outline)) },
            shape             = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            AddTransactionSheet(
                onDismiss = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        showAddTransaction = false
                    }
                },
                accounts          = accounts,
                selectedAccountId = selectedAccId
            )
        }
    }
}

// ── Bottom navigation bar ──────────────────────────────────────────────────────

@Composable
private fun DimeNavigationBar(
    navController: androidx.navigation.NavController,
    tabs: List<TabItem>,
    onAddClick: () -> Unit = {}
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination


    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
            shape = RoundedCornerShape(50.dp),
            tonalElevation = 0.dp,
            shadowElevation = 12.dp,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            modifier = Modifier
                .height(68.dp)  // Slightly taller for premium feel
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Layout 5 items: 2 tabs, Add btn, 2 tabs
                (0 until 5).forEach { index ->
                    if (index == 2) {
                        // Central "+" Add Transaction button
                        Box(
                            modifier = Modifier.weight(1.2f),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .bounceClick { onAddClick() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Add,
                                    contentDescription = "Add Transaction",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    } else {
                        val tabIndex = when {
                            index < 2 -> index
                            else -> index - 1  // Account for Add btn
                        }
                        val tab = tabs[tabIndex]
                        val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = rememberRipple(bounded = false, radius = 28.dp)
                                ) {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = if (selected) MaterialTheme.colorScheme.primary 
                                       else MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
