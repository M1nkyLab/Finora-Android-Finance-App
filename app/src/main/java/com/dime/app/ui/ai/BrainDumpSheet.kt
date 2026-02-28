package com.dime.app.ui.ai

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dime.app.data.ai.BrainDumpItem
import com.dime.app.ui.components.bounceClick
import com.dime.app.util.LocalCurrency
import kotlinx.coroutines.delay

// ── Palette ────────────────────────────────────────────────────────────────
private val BgDeep       = Color(0xFF000000)  // true OLED black
private val BgCard       = Color(0xFF0F0F14)  // near-black card surface
private val BgCardAlt    = Color(0xFF14141C)  // subtle variation
private val AccentPurple = Color(0xFFA855F7)  // lavender indigo
private val AccentBlue   = Color(0xFF5B8FFF)
private val AccentGreen  = Color(0xFF34D399)  // mint sage green — premium & calming
private val AccentRed    = Color(0xFFEF4444)
private val TextPrimary  = Color(0xFFF0F0F5)
private val TextSub      = Color(0xFF6B6B80)
private val DividerCol   = Color(0xFF1E1E28)

private val AiGradient = Brush.linearGradient(listOf(AccentPurple, AccentBlue))

// Radial glow that sits behind the textarea
private val GlowBrush = Brush.radialGradient(
    colors = listOf(
        Color(0x26A855F7),  // rgba(168,85,247, 0.15)
        Color(0x00000000)
    ),
    radius = 600f
)

// ── Main Sheet ─────────────────────────────────────────────────────────────────

@Composable
fun BrainDumpSheet(
    onDismiss: () -> Unit,
    viewModel: BrainDumpViewModel = hiltViewModel()
) {
    val state    by viewModel.uiState.collectAsStateWithLifecycle()
    val currency  = LocalCurrency.current
    val keyboard  = LocalSoftwareKeyboardController.current
    val focus     = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(300)
        focus.requestFocus()
    }

    LaunchedEffect(state.savedSuccessfully) {
        if (state.savedSuccessfully) {
            delay(1600)
            viewModel.reset()
            onDismiss()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f)
            .background(BgDeep)
    ) {
        // ── Header ─────────────────────────────────────────────────────────────
        BrainDumpHeader()

        // ── Error banner ───────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = state.errorMessage != null,
            enter   = slideInVertically() + fadeIn(),
            exit    = slideOutVertically() + fadeOut()
        ) {
            BrainDumpErrorBanner(
                message   = state.errorMessage ?: "",
                onDismiss = viewModel::dismissError
            )
        }

        // ── Success banner ─────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = state.savedSuccessfully,
            enter   = slideInVertically() + fadeIn(),
            exit    = slideOutVertically() + fadeOut()
        ) {
            BrainDumpSuccessBanner(count = state.savedCount)
        }

        // ── Textarea (60 % of available space) ────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.42f)
                .drawBehind { drawRect(GlowBrush) }
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            BrainDumpTextArea(
                value              = state.textInput,
                onValueChange      = viewModel::onTextChange,
                isProcessing       = state.isProcessing || state.isWaitingToProcess,
                isWaitingToProcess = state.isWaitingToProcess,
                onSend             = {
                    keyboard?.hide()
                    viewModel.processInput()
                },
                focusRequester = focus
            )
        }

        // ── Detection pills (scrollable, middle section) ───────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.40f)
        ) {
            when {
                state.isProcessing       -> BrainDumpProcessing()
                state.isWaitingToProcess -> BrainDumpWaiting()
                state.hasResults         -> DetectionPillList(
                    items    = state.visibleItems,
                    currency = currency,
                    onRemove = viewModel::removeItem
                )
                else -> BrainDumpPlaceholderHint()
            }
        }

        // ── Net summary bar (fixed bottom) ────────────────────────────────────
        NetSummaryBar(
            totalExpense = state.totalExpense,
            totalIncome  = state.totalIncome,
            itemCount    = state.itemCount,
            currency     = currency,
            onConfirm    = viewModel::confirmAndSaveAll,
            enabled      = state.hasResults && !state.isProcessing && !state.savedSuccessfully
        )

        Spacer(Modifier.height(8.dp))
    }
}

// ── Header ─────────────────────────────────────────────────────────────────────

@Composable
private fun BrainDumpHeader() {
    val infiniteTransition = rememberInfiniteTransition(label = "header_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue   = 0.5f,
        targetValue    = 1f,
        animationSpec  = infiniteRepeatable(tween(1400, easing = EaseInOutSine), RepeatMode.Reverse),
        label          = "icon_alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .drawBehind {
                    drawCircle(color = AccentPurple.copy(alpha = alpha * 0.18f))
                    drawCircle(
                        color = AccentPurple.copy(alpha = alpha * 0.5f),
                        style = Stroke(width = 1.dp.toPx())
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = "Brain Dump",
                tint     = AccentPurple,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer { this.alpha = alpha }
            )
        }

        Spacer(Modifier.width(12.dp))

        Column {
            Text(
                "Brain Dump",
                fontSize   = 18.sp,
                fontWeight = FontWeight.Bold,
                color      = TextPrimary
            )
            Text(
                "Log everything at once — AI detects each item",
                fontSize = 12.sp,
                color    = TextSub
            )
        }
    }
}

// ── Textarea ───────────────────────────────────────────────────────────────────

@Composable
private fun BrainDumpTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    isProcessing: Boolean,
    isWaitingToProcess: Boolean,
    onSend: () -> Unit,
    focusRequester: FocusRequester
) {
    // TextFieldValue is the single source of truth — never re-keyed on `value`
    // so the cursor position survives recompositions triggered by ViewModel updates.
    var textFieldValue by remember { mutableStateOf(TextFieldValue(buildHighlightedText(value))) }

    // Only resync from outside when the ViewModel actually clears/resets the text
    LaunchedEffect(value) {
        if (value != textFieldValue.text) {
            textFieldValue = TextFieldValue(buildHighlightedText(value))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            BasicTextField(
                value         = textFieldValue,
                onValueChange = { newVal ->
                    textFieldValue = TextFieldValue(
                        annotatedString = buildHighlightedText(newVal.text),
                        selection       = newVal.selection,
                        composition     = newVal.composition
                    )
                    onValueChange(newVal.text)
                },
                // Keep editable while waiting — only lock during actual API call
                enabled       = !isProcessing || isWaitingToProcess,
                textStyle     = TextStyle(
                    color         = TextPrimary,
                    fontSize      = 26.sp,
                    fontWeight    = FontWeight.SemiBold,
                    letterSpacing = (-0.5).sp
                ),
                cursorBrush     = SolidColor(AccentPurple),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction      = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                singleLine      = false,
                modifier        = Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester),
                decorationBox   = { innerTextField ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                "e.g. Lunch 15, Petrol 40, allowance 900…",
                                color         = TextSub,
                                fontSize      = 22.sp,
                                fontWeight    = FontWeight.SemiBold,
                                letterSpacing = (-0.5).sp,
                                lineHeight    = 34.sp
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }

        Spacer(Modifier.height(10.dp))

        // Status row — shows auto-detect hint or a pulsing "analysing" badge
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            // Left: context label
            when {
                isWaitingToProcess -> TypingStatusBadge()
                isProcessing       -> Text(
                    "Analysing…",
                    fontSize = 11.sp,
                    color    = AccentPurple
                )
                value.isNotBlank() -> Text(
                    "Auto-detecting • tap ↑ to force",
                    fontSize = 11.sp,
                    color    = TextSub
                )
                else -> Spacer(Modifier.width(1.dp))
            }

            // Right: manual send button (always visible as fallback)
            val canSend = value.isNotBlank() && !isProcessing
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (canSend) AiGradient
                        else Brush.linearGradient(listOf(DividerCol, DividerCol))
                    )
                    .then(
                        if (canSend) Modifier.bounceClick { onSend() } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.Send,
                    contentDescription = "Force detect",
                    tint     = if (canSend) Color.White else TextSub,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ── Typing status badge (shown in textarea row while debounce ticks) ─────────

@Composable
private fun TypingStatusBadge() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val dot1 = infiniteTransition.animateFloat(
        0.3f, 1f, infiniteRepeatable(tween(400), RepeatMode.Reverse), label = "t1"
    )
    val dot2 = infiniteTransition.animateFloat(
        0.3f, 1f, infiniteRepeatable(tween(400, delayMillis = 130), RepeatMode.Reverse), label = "t2"
    )
    val dot3 = infiniteTransition.animateFloat(
        0.3f, 1f, infiniteRepeatable(tween(400, delayMillis = 260), RepeatMode.Reverse), label = "t3"
    )
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("Detecting in", fontSize = 11.sp, color = TextSub)
        Spacer(Modifier.width(2.dp))
        listOf(dot1, dot2, dot3).forEach { a ->
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .graphicsLayer { alpha = a.value }
                    .clip(CircleShape)
                    .background(AccentPurple)
            )
        }
    }
}

// ── Waiting state for middle section (debounce timer running) ─────────────────

@Composable
private fun BrainDumpWaiting() {
    val infiniteTransition = rememberInfiniteTransition(label = "wait_pulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.3f,
        targetValue   = 0.8f,
        animationSpec = infiniteRepeatable(tween(700, easing = EaseInOutSine), RepeatMode.Reverse),
        label         = "wait_glow"
    )
    Column(
        modifier            = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .graphicsLayer { alpha = glowAlpha }
                .clip(CircleShape)
                .background(AccentPurple.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint     = AccentPurple,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Waiting for you to finish typing…",
            color    = TextSub,
            fontSize = 13.sp
        )
    }
}

/**
 * Syntax highlighting: wrap numbers with mint sage green span.
 */
private fun buildHighlightedText(raw: String): AnnotatedString {
    val builder = AnnotatedString.Builder()
    val regex   = Regex("""\d+(\.\d+)?""")
    var cursor  = 0
    regex.findAll(raw).forEach { match ->
        if (match.range.first > cursor) {
            builder.append(raw.substring(cursor, match.range.first))
        }
        builder.withStyle(
            SpanStyle(color = Color(0xFF34D399), fontWeight = FontWeight.Bold)  // Mint sage green
        ) {
            append(match.value)
        }
        cursor = match.range.last + 1
    }
    if (cursor < raw.length) builder.append(raw.substring(cursor))
    return builder.toAnnotatedString()
}

// ── Processing Indicator ───────────────────────────────────────────────────────

@Composable
private fun BrainDumpProcessing() {
    val infiniteTransition = rememberInfiniteTransition(label = "proc")

    val dot1 = infiniteTransition.animateFloat(
        0.3f, 1f,
        infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "d1"
    )
    val dot2 = infiniteTransition.animateFloat(
        0.3f, 1f,
        infiniteRepeatable(tween(500, delayMillis = 160), RepeatMode.Reverse),
        label = "d2"
    )
    val dot3 = infiniteTransition.animateFloat(
        0.3f, 1f,
        infiniteRepeatable(tween(500, delayMillis = 320), RepeatMode.Reverse),
        label = "d3"
    )

    Column(
        modifier             = Modifier.fillMaxSize(),
        horizontalAlignment  = Alignment.CenterHorizontally,
        verticalArrangement  = Arrangement.Center
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(dot1, dot2, dot3).forEach { alphaState ->
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .graphicsLayer { alpha = alphaState.value }
                        .clip(CircleShape)
                        .background(AccentPurple)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Detecting transactions…", color = TextSub, fontSize = 13.sp)
    }
}

// ── Placeholder hint when nothing is typed yet ────────────────────────────────

@Composable
private fun BrainDumpPlaceholderHint() {
    Column(
        modifier            = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Rounded.FlashOn,
            contentDescription = null,
            tint     = AccentPurple.copy(alpha = 0.3f),
            modifier = Modifier.size(32.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Detected items appear here",
            color    = TextSub.copy(alpha = 0.5f),
            fontSize = 13.sp
        )
    }
}

// ── Detection Pills ────────────────────────────────────────────────────────────

@Composable
private fun DetectionPillList(
    items: List<BrainDumpItem>,
    currency: com.dime.app.util.CurrencyConfig,
    onRemove: (String) -> Unit
) {
    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
            // Staggered spring entry
            val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }

            AnimatedVisibility(
                visibleState = visibleState,
                enter = slideInVertically(
                    animationSpec = spring(
                        stiffness = Spring.StiffnessLow,
                        dampingRatio = Spring.DampingRatioMediumBouncy
                    ),
                    initialOffsetY = { it }
                ) + fadeIn(animationSpec = tween(200))
            ) {
                TransactionDetectionPill(
                    item     = item,
                    currency = currency,
                    onRemove = { onRemove(item.id) }
                )
            }
        }
    }
}

@Composable
private fun TransactionDetectionPill(
    item: BrainDumpItem,
    currency: com.dime.app.util.CurrencyConfig,
    onRemove: () -> Unit
) {
    val isIncome     = item.type == "income"
    val accentColor  = if (isIncome) AccentGreen else AccentRed
    val bgColor      = if (isIncome) AccentGreen.copy(alpha = 0.15f) else AccentRed.copy(alpha = 0.15f)
    val sign         = if (isIncome) "+" else "−"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))  // More premium, rounder pill
            .background(BgCard)
            .border(1.dp, DividerCol, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon bubble
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector    = categoryIcon(item.category, isIncome),
                contentDescription = item.category,
                tint           = accentColor,
                modifier       = Modifier.size(20.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        // Text
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = item.description,
                color      = TextPrimary,
                fontSize   = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text     = item.category,
                    color    = TextSub,
                    fontSize = 11.sp
                )
                if (item.recurring != "none") {
                    Text("·", color = TextSub, fontSize = 11.sp)
                    Text(
                        text     = "🔁 ${item.recurring.replaceFirstChar { it.uppercase() }}",
                        color    = Color(0xFFFFB800),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Amount
        Text(
            text       = "$sign ${currency.format(item.amount)}",
            color      = accentColor,
            fontSize   = 17.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.width(8.dp))

        // Remove button
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(TextSub.copy(alpha = 0.1f))
                .bounceClick { onRemove() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "Remove",
                tint     = TextSub,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/** Map category string → Material icon */
private fun categoryIcon(category: String, isIncome: Boolean): androidx.compose.ui.graphics.vector.ImageVector {
    return when (category.lowercase()) {
        "food"                          -> Icons.Rounded.Restaurant
        "transport"                     -> Icons.Rounded.DirectionsCar
        "subscriptions"                 -> Icons.Rounded.Repeat
        "shopping"                      -> Icons.Rounded.ShoppingCart
        "bills"                         -> Icons.Rounded.Receipt
        "health"                        -> Icons.Rounded.Favorite
        "entertainment"                 -> Icons.Rounded.PlayCircle
        "income", "salary", "freelance" -> Icons.Rounded.Savings
        "gift"                          -> Icons.Rounded.Redeem
        else                            -> if (isIncome) Icons.Rounded.Savings else Icons.Rounded.Category
    }
}

// ── Net Summary Bar ─────────────────────────────────────────────────────────────

@Composable
private fun NetSummaryBar(
    totalExpense: Double,
    totalIncome: Double,
    itemCount: Int,
    currency: com.dime.app.util.CurrencyConfig,
    onConfirm: () -> Unit,
    enabled: Boolean
) {
    // Animated glow on the bar when enabled
    val glowAlpha by animateFloatAsState(
        targetValue   = if (enabled) 1f else 0f,
        animationSpec = tween(400),
        label         = "glow"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(DividerCol)
        )

        Spacer(Modifier.height(12.dp))

        // Totals row
        if (enabled || totalExpense > 0.0 || totalIncome > 0.0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(BgCard)
                    .border(1.dp, DividerCol, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Expenses
                Column(horizontalAlignment = Alignment.Start) {
                    Text("Expenses", fontSize = 10.sp, color = TextSub)
                    Text(
                        "−${currency.format(totalExpense)}",
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color      = AccentRed
                    )
                }

                // Net
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Net", fontSize = 10.sp, color = TextSub)
                    val net = totalIncome - totalExpense
                    Text(
                        (if (net >= 0) "+" else "") + currency.format(net),
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color      = if (net >= 0) AccentGreen else AccentRed
                    )
                }

                // Income
                Column(horizontalAlignment = Alignment.End) {
                    Text("Income", fontSize = 10.sp, color = TextSub)
                    Text(
                        "+${currency.format(totalIncome)}",
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color      = AccentGreen
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
        }

        // Confirm button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(100.dp))  // True pill shape
                .background(
                    if (enabled) AiGradient
                    else Brush.linearGradient(listOf(BgCard, BgCard))
                )
                .drawBehind {
                    // Purple glow shadow when enabled
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(
                                AccentPurple.copy(alpha = 0.35f * glowAlpha),
                                Color.Transparent
                            ),
                            radius = 300f
                        )
                    )
                }
                .then(
                    if (enabled) Modifier.bounceClick { onConfirm() } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint     = if (enabled) Color.White else TextSub,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = if (itemCount > 0) "Confirm $itemCount Item${if (itemCount > 1) "s" else ""}"
                           else "Detect Items First",
                    color      = if (enabled) Color.White else TextSub,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 15.sp
                )
            }
        }
    }
}

// ── Error Banner ───────────────────────────────────────────────────────────────

@Composable
private fun BrainDumpErrorBanner(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AccentRed.copy(alpha = 0.12f))
            .border(1.dp, AccentRed.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint     = AccentRed,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(message, fontSize = 13.sp, color = AccentRed, modifier = Modifier.weight(1f))
        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Rounded.Close, contentDescription = "Dismiss", tint = AccentRed, modifier = Modifier.size(14.dp))
        }
    }
}

// ── Success Banner ─────────────────────────────────────────────────────────────

@Composable
private fun BrainDumpSuccessBanner(count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AccentGreen.copy(alpha = 0.12f))
            .border(1.dp, AccentGreen.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint     = AccentGreen,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "$count transaction${if (count > 1) "s" else ""} saved successfully!",
            fontSize   = 14.sp,
            fontWeight = FontWeight.Medium,
            color      = AccentGreen
        )
    }
}
