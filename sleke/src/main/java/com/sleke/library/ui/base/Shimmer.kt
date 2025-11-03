package com.sleke.library.ui.base

import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize

private const val INITIAL_VALUE_ANIMATION = -1f
private const val TARGET_VALUE_ANIMATION = 2f

fun Modifier.shimmer(
    initialValue: Float = INITIAL_VALUE_ANIMATION,
    targetValue: Float = TARGET_VALUE_ANIMATION,
): Modifier = composed {
    var size by remember {
        mutableStateOf(IntSize.Zero)
    }
    val width = size.width.toFloat()
    val startOffsetX by animateTransitionFloat(
        initialValue = initialValue * width,
        targetValue = targetValue * width,
    )
    val transparent = Color.LightGray.copy(alpha = 0.2f)
    val white = Color.LightGray.copy(alpha = 0.6f)

    val colorStops = remember {
        arrayOf(
            0f to transparent,
            0.45f to white,
            0.55f to white,
            1f to transparent,
        )
    }

    background(
        brush = Brush.horizontalGradient(
            colorStops = colorStops,
            startX = startOffsetX,
            endX = startOffsetX + width,
        ),
    ).onGloballyPositioned {
        size = it.size
    }
}

@Composable
private fun animateTransitionFloat(
    initialValue: Float,
    targetValue: Float,
    durationMillis: Int = 2500,
    label: String = "animateTransitionFloat",
): State<Float> {
    val transition = rememberInfiniteTransition(label = "rememberInfiniteTransition")
    return transition.animateFloat(
        initialValue = initialValue,
        targetValue = targetValue,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = EaseOut),
        ),
        label = label,
    )
}
