package com.dime.app.ui.ai

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dime.app.data.ai.ParsedTransaction
import com.dime.app.util.CurrencyConfig
import com.dime.app.util.LocalCurrency
import com.dime.app.ui.components.bounceClick
import kotlinx.coroutines.delay

// ── Palette (matches app theme) ────────────────────────────────────────────────
private val BgDeep       = Color(0xFF0D0D0F)
private val BgCard       = Color(0xFF19191E)
private val BgCardAlt    = Color(0xFF1F1F27)
private val AccentPurple = Color(0xFF9B6FFF)
private val AccentBlue   = Color(0xFF5B8FFF)
private val AccentGreen  = Color(0xFF3ECF72)
private val AccentRed    = Color(0xFFFF5C5C)
private val TextPrimary  = Color(0xFFF0F0F5)
private val TextSub      = Color(0xFF7A7A8C)
private val Divider      = Color(0xFF2A2A35)

private val AiGradient = Brush.linearGradient(
    colors = listOf(AccentPurple, AccentBlue)
)

// ── Main Sheet ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiInputSheet(
    onDismiss: () -> Unit,
    viewModel: AiInputViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currency = LocalCurrency.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    // Auto-focus the text field
    LaunchedEffect(Unit) {
        delay(300)
        focusRequester.requestFocus()
    }

    // Auto-dismiss on success after delay
    LaunchedEffect(state.savedSuccessfully) {
        if (state.savedSuccessfully) {
            delay(1500)
            viewModel.reset()
            onDismiss()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgDeep)
            .padding(bottom = 24.dp)
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        AiHeader()

        Spacer(Modifier.height(16.dp))

        // ── Success Banner ────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = state.savedSuccessfully,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut()
        ) {
            SuccessBanner()
        }

        // ── Error Banner ──────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = state.errorMessage != null,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut()
        ) {
            ErrorBanner(state.errorMessage ?: "", onDismiss = viewModel::dismissError)
        }

        // ── Input Field ───────────────────────────────────────────────────────
        AiTextField(
            value = state.textInput,
            onValueChange = viewModel::onTextChange,
            isProcessing = state.isProcessing,
            onSend = {
                keyboardController?.hide()
                viewModel.processInput()
            },
            focusRequester = focusRequester,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(Modifier.height(12.dp))

        // ── Quick Suggestions ─────────────────────────────────────────────────
        AnimatedVisibility(
            visible = state.parsedResult == null && !state.isProcessing,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            SuggestionChips(
                suggestions = quickSuggestions,
                onSelect = { viewModel.onTextChange(it) }
            )
        }

        Spacer(Modifier.height(8.dp))

        // ── Loading Indicator ─────────────────────────────────────────────────
        AnimatedVisibility(
            visible = state.isProcessing,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            ProcessingIndicator()
        }

        // ── Parsed Result Preview ─────────────────────────────────────────────
        AnimatedVisibility(
            visible = state.parsedResult != null && !state.savedSuccessfully,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically() + fadeOut()
        ) {
            state.parsedResult?.let { parsed ->
                ParsedResultCard(
                    parsed = parsed,
                    currency = currency,
                    onConfirm = viewModel::confirmAndSave,
                    onCancel = viewModel::reset,
                    onEditAmount = viewModel::editParsedAmount,
                    onEditTitle = viewModel::editParsedTitle
                )
            }
        }
    }
}

// ── Header ─────────────────────────────────────────────────────────────────────

@Composable
private fun AiHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Animated gradient icon
        val infiniteTransition = rememberInfiniteTransition(label = "ai_pulse")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.6f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_alpha"
        )

        Box(
            modifier = Modifier
                .size(40.dp)
                .drawWithContent {
                    drawCircle(color = AccentPurple.copy(alpha = alpha * 0.2f))
                    drawCircle(color = AccentPurple.copy(alpha = alpha * 0.5f), style = Stroke(width = 1.dp.toPx()))
                    drawContent()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = "AI",
                tint = AccentPurple,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer { this.alpha = alpha }
            )
        }

        Spacer(Modifier.width(12.dp))

        Column {
            Text(
                "AI Transaction Input",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                "Type naturally, AI will do the rest",
                fontSize = 13.sp,
                color = TextSub
            )
        }
    }
}

// ── Text Input ─────────────────────────────────────────────────────────────────

@Composable
private fun AiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    isProcessing: Boolean,
    onSend: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BgCard)
            .border(1.dp, Divider, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = !isProcessing,
            textStyle = TextStyle(
                color = TextPrimary,
                fontSize = 16.sp
            ),
            cursorBrush = SolidColor(AccentPurple),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Send
            ),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            singleLine = false,
            maxLines = 3,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp)
                .focusRequester(focusRequester),
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            "e.g. \"lunch nasi lemak RM8 today\"",
                            color = TextSub,
                            fontSize = 16.sp
                        )
                    }
                    innerTextField()
                }
            }
        )

        Spacer(Modifier.width(8.dp))

        // Send button
        IconButton(
            onClick = onSend,
            enabled = value.isNotBlank() && !isProcessing,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (value.isNotBlank() && !isProcessing) AiGradient
                    else Brush.linearGradient(listOf(Divider, Divider))
                )
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.Send,
                contentDescription = "Process",
                tint = if (value.isNotBlank()) Color.White else TextSub,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ── Suggestion Chips ───────────────────────────────────────────────────────────

@Composable
private fun SuggestionChips(
    suggestions: List<String>,
    onSelect: (String) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(suggestions) { suggestion ->
            SuggestionChip(suggestion, onClick = { onSelect(suggestion) })
        }
    }
}

@Composable
private fun SuggestionChip(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(BgCardAlt)
            .border(1.dp, Divider, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text, fontSize = 13.sp, color = TextSub)
    }
}

// ── Processing Indicator ───────────────────────────────────────────────────────

@Composable
private fun ProcessingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "proc")
    val dotAlpha1 = infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "d1"
    )
    val dotAlpha2 = infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 200), RepeatMode.Reverse),
        label = "d2"
    )
    val dotAlpha3 = infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 400), RepeatMode.Reverse),
        label = "d3"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ProcessingDot(dotAlpha1)
            ProcessingDot(dotAlpha2)
            ProcessingDot(dotAlpha3)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "AI is processing...",
            color = TextSub,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun ProcessingDot(alphaState: androidx.compose.runtime.State<Float>) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .graphicsLayer { alpha = alphaState.value }
            .clip(CircleShape)
            .background(AccentPurple)
    )
}

// ── Parsed Result Card ─────────────────────────────────────────────────────────

@Composable
private fun ParsedResultCard(
    parsed: ParsedTransaction,
    currency: CurrencyConfig,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onEditAmount: (Double) -> Unit,
    onEditTitle: (String) -> Unit
) {
    val isIncome = parsed.type == "income"
    val accentColor = if (isIncome) AccentGreen else AccentRed
    val sign = if (isIncome) "+" else "−"

    var isEditing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var titleText by androidx.compose.runtime.remember(parsed.title) { androidx.compose.runtime.mutableStateOf(parsed.title) }
    var amountText by androidx.compose.runtime.remember(parsed.amount) { androidx.compose.runtime.mutableStateOf(parsed.amount.toString()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        // Preview label
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = AccentGreen,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "AI Result Preview",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = AccentGreen
            )
        }

        // Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(BgCard)
                .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            // Amount row
            if (isEditing) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    androidx.compose.material3.OutlinedTextField(
                        value = titleText,
                        onValueChange = { titleText = it; onEditTitle(it) },
                        label = { Text("Title", color = TextSub) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = AccentPurple,
                            unfocusedBorderColor = TextSub.copy(alpha = 0.5f)
                        ),
                        singleLine = true
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value = amountText,
                        onValueChange = { 
                            amountText = it
                            it.toDoubleOrNull()?.let { num -> onEditAmount(num) }
                        },
                        label = { Text("Amount", color = TextSub) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = AccentPurple,
                            unfocusedBorderColor = TextSub.copy(alpha = 0.5f)
                        ),
                        singleLine = true
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        parsed.title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "$sign${currency.format(parsed.amount)}",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Detail chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetailChipSmall(
                    icon = if (isIncome) Icons.Rounded.TrendingUp else Icons.Rounded.TrendingDown,
                    text = if (isIncome) "Income" else "Expense",
                    color = accentColor
                )
                DetailChipSmall(
                    icon = Icons.Rounded.Category,
                    text = parsed.category,
                    color = AccentPurple
                )
                DetailChipSmall(
                    icon = Icons.Rounded.CalendarToday,
                    text = parsed.date.toString(),
                    color = AccentBlue
                )
            }

            // Recurring badge (if detected)
            if (parsed.recurring != "none") {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val recurLabel = when (parsed.recurring) {
                        "daily"   -> "🔁 Daily"
                        "weekly"  -> "🔁 Weekly"
                        "monthly" -> "🔁 Monthly"
                        "yearly"  -> "🔁 Yearly"
                        else      -> ""
                    }
                    DetailChipSmall(
                        icon = Icons.Rounded.Repeat,
                        text = recurLabel,
                        color = Color(0xFFFFB800)
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Cancel
                if (!isEditing) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .bounceClick { onCancel() }
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Transparent)
                            .border(1.dp, TextSub.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Close, contentDescription = null, tint = TextSub, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Cancel", color = TextSub, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                // Edit Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .bounceClick { isEditing = !isEditing }
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isEditing) TextSub.copy(alpha = 0.2f) else Color.Transparent)
                        .border(1.dp, TextSub.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (isEditing) Icons.Rounded.Close else Icons.Rounded.Edit, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isEditing) "Done" else "Edit", color = TextPrimary, fontWeight = FontWeight.Medium)
                    }
                }

                // Confirm
                if (!isEditing) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .bounceClick { onConfirm() }
                            .clip(RoundedCornerShape(12.dp))
                            .background(AccentGreen)
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Confirm", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

// ── Detail Chip ────────────────────────────────────────────────────────────────

@Composable
private fun DetailChipSmall(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    color: Color
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(text, fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

// ── Success Banner ─────────────────────────────────────────────────────────────

@Composable
private fun SuccessBanner() {
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
            tint = AccentGreen,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "Transaction saved successfully!",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = AccentGreen
        )
    }
}

// ── Error Banner ───────────────────────────────────────────────────────────────

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AccentRed.copy(alpha = 0.12f))
            .border(1.dp, AccentRed.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = AccentRed,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            message,
            fontSize = 13.sp,
            color = AccentRed,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Rounded.Close, contentDescription = "Dismiss", tint = AccentRed, modifier = Modifier.size(16.dp))
        }
    }
}
