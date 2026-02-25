package com.dime.app.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize

/**
 * A custom modifier that applies a subtle scaling bounce effect when pressed,
 * replacing the default ripple for a more premium feel.
 */
fun Modifier.bounceClick(
    scaleDown: Float = 0.95f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "BounceClickScale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null, // Remove default ripple
            onClick = onClick
        )
}

/**
 * A modifier that adds a shimmering skeleton loader effect using opacity/translation (via Brush offsets).
 * Perfect for replacing traditional spinners in premium UI.
 */
fun Modifier.shimmerEffect(): Modifier = composed {
    var size by remember {
        mutableStateOf(IntSize.Zero)
    }
    val transition = rememberInfiniteTransition(label = "shimmer_transition")
    val startOffsetX by transition.animateFloat(
        initialValue = -2 * size.width.toFloat(),
        targetValue = 2 * size.width.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )

    val shimmerColors = listOf(
        Color(0xFF2A2A35).copy(alpha = 0.6f),
        Color(0xFF2A2A35),
        Color(0xFF2A2A35).copy(alpha = 0.6f)
    )

    this
        .drawWithContent {
            drawContent()
            drawRect(
                brush = Brush.linearGradient(
                    colors = shimmerColors,
                    start = Offset(startOffsetX, 0f),
                    end = Offset(startOffsetX + size.width.toFloat(), size.height.toFloat())
                )
            )
        }
        .onGloballyPositioned {
            size = it.size
        }
}

/**
 * A specialized wrapper for rendering an animated money amount that ticks up/down.
 * It encapsulates `animateFloatAsState` to ensure only this specific Text node recomposes
 * during the animation, saving immense layout overhead across the rest of the screen.
 */
@Composable
fun AnimatedAmountText(
    amount: Float,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontWeight: androidx.compose.ui.text.font.FontWeight,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    letterSpacing: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    animationDuration: Int = 400
) {
    val animatedAmount by androidx.compose.animation.core.animateFloatAsState(
        targetValue = amount,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = animationDuration),
        label = "animatedAmount"
    )

    val numberFormat = remember { 
        java.text.NumberFormat.getNumberInstance().apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
    }
    androidx.compose.material3.Text(
        text = numberFormat.format(animatedAmount),
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = color,
        modifier = modifier,
        letterSpacing = letterSpacing
    )
}
