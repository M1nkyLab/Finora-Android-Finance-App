package com.dime.app.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.dime.app.ui.components.bounceClick
import androidx.navigation.compose.rememberNavController
import com.dime.app.ui.ai.AiInputSheet
import com.dime.app.ui.budget.BudgetScreen
import com.dime.app.ui.dashboard.DashboardScreen
import com.dime.app.ui.insights.InsightsScreen
import com.dime.app.ui.settings.SettingsScreen
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

private val AiGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF9B6FFF), Color(0xFF5B8FFF))
)

/**
 * Root composable — replaces iOS HomeView + CustomTabBar.
 *
 *  - NavHost  → iOS TabView
 *  - DimeNavigationBar → iOS CustomTabBar
 *  - FAB ("+") → iOS TransactionView trigger
 *  - ModalBottomSheet → iOS TransactionView sheet presentation
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    startOpenAiInput: Boolean = false,
    onAiInputOpened: () -> Unit = {}
) {
    val navController  = rememberNavController()
    val sheetState     = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val aiSheetState   = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope          = rememberCoroutineScope()

    // ID of transaction being edited; null = add mode
    var showAiSheet       by remember { mutableStateOf(false) }

    LaunchedEffect(startOpenAiInput) {
        if (startOpenAiInput) {
            showAiSheet = true
            onAiInputOpened()
        }
    }

    val openAiSheet: () -> Unit = {
        showAiSheet = true
    }



    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            DimeNavigationBar(
                navController = navController,
                tabs = tabs,
                onAiClick = openAiSheet
            )
        }
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = Route.LOG,
            modifier         = Modifier.padding(innerPadding),
        ) {
            composable(Route.LOG,
                enterTransition  = { fadeIn(tween(200)) },
                exitTransition   = { fadeOut(tween(200)) }
            ) {
                DashboardScreen()
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
    }


    // ── AI Input bottom sheet ──────────────────────────────────────────────────
    if (showAiSheet) {
        ModalBottomSheet(
            onDismissRequest  = { showAiSheet = false },
            sheetState        = aiSheetState,
            containerColor    = Color(0xFF0D0D0F),
            dragHandle        = { Box(Modifier.padding(top = 8.dp).size(40.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF2A2A35))) },
            shape             = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            AiInputSheet(
                onDismiss = {
                    scope.launch { aiSheetState.hide() }.invokeOnCompletion {
                        showAiSheet = false
                    }
                }
            )
        }
    }
}

// ── Bottom navigation bar ──────────────────────────────────────────────────────

@Composable
private fun DimeNavigationBar(
    navController: androidx.navigation.NavController,
    tabs: List<TabItem>,
    onAiClick: () -> Unit = {}
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
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
            shape = RoundedCornerShape(50.dp),
            tonalElevation = 0.dp,
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
            modifier = Modifier
                .height(64.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Layout 5 items: 2 tabs, AI btn, 2 tabs
                (0 until 5).forEach { index ->
                    if (index == 2) {
                        // AI sparkle button (Primary Action)
                        Box(
                            modifier = Modifier.weight(1.2f),
                            contentAlignment = Alignment.Center
                        ) {
                            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                            val pulseScale by infiniteTransition.animateFloat(
                                initialValue = 1f,
                                targetValue = 1.08f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1200, easing = EaseInOut),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "pulse_scale"
                            )

                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .graphicsLayer {
                                        scaleX = pulseScale
                                        scaleY = pulseScale
                                    }
                                    .clip(CircleShape)
                                    .background(AiGradient)
                                    .bounceClick { onAiClick() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.AutoAwesome,
                                    contentDescription = "AI Input",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    } else {
                        val tabIndex = when {
                            index < 2 -> index
                            else -> index - 1  // Account for AI btn
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
