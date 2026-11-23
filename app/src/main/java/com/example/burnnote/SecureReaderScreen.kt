package com.example.burnnote

import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.sin

private const val TOTAL_DURATION_MS = 30_000L

@Composable
fun SecureReaderScreen(
    buffer: SecretNoteBuffer?,
    deviceWatermark: String,
    burnReason: String?,
    onBurnTriggered: (String) -> Unit,
    onExit: () -> Unit
) {
    var remainingMs by remember { mutableLongStateOf(TOTAL_DURATION_MS) }
    var isHolding by remember { mutableStateOf(false) }
    var scrollOffsetPx by remember { mutableFloatStateOf(0f) }
    var frameTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(burnReason) {
        if (burnReason != null) return@LaunchedEffect
        val startElapsed = SystemClock.elapsedRealtime()
        while (isActive) {
            val elapsed = SystemClock.elapsedRealtime() - startElapsed
            remainingMs = (TOTAL_DURATION_MS - elapsed).coerceAtLeast(0L)
            if (remainingMs == 0L) {
                onBurnTriggered("TIMER_EXPIRED")
                break
            }
            delay(100L)
        }
    }

    LaunchedEffect(isHolding, burnReason) {
        while (isActive && isHolding && burnReason == null) {
            withFrameNanos {
                frameTick = (frameTick + 1) % 10_000
            }
        }
    }

    val textPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color(0xFFB8B8B8).toArgb()
            textSize = 44f
            typeface = Typeface.MONOSPACE
            letterSpacing = 0.03f
        }
    }

    val watermarkPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color(0x18FFFFFF).toArgb()
            textSize = 30f
            typeface = Typeface.MONOSPACE
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(burnReason) {
                if (burnReason != null) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isHolding = true
                    var lastY = down.position.y
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val activePointer = event.changes.firstOrNull { it.pressed } ?: break
                            val rawDelta = activePointer.position.y - lastY
                            lastY = activePointer.position.y

                            val clampedDelta = rawDelta.coerceIn(-10f, 10f)
                            val lineCount = buffer?.getLines()?.size ?: 0
                            val maxScroll = ((lineCount - 1).coerceAtLeast(0)) * 120f
                            scrollOffsetPx = (scrollOffsetPx - clampedDelta).coerceIn(0f, maxScroll)

                            activePointer.consume()
                        }
                    } finally {
                        isHolding = false
                    }
                }
            }
    ) {
        if (burnReason != null) {
            BurnedView(reason = burnReason, onExit = onExit)
        } else {
            val lines = buffer?.getLines().orEmpty()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, start = 20.dp, end = 20.dp)
            ) {
                Text(
                    text = "BURN IN ${(remainingMs / 1000L) + 1}s",
                    color = Color(0xFF8A2B2B),
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
                Text(
                    text = if (isHolding) "READING (SLOW DRAG)" else "HOLD & DRAG TO REVEAL",
                    color = Color(0xFF444444),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerY = size.height / 2f
                val lineSpacingPx = 120f
                val slitHalfHeight = 52f

                // Scale text size dynamically so 26 monospace chars always fit screen width
                textPaint.textSize = ((size.width - 96f) / (26f * 0.64f)).coerceIn(28f, 48f)

                val guideColor = if (isHolding) Color(0xFF222222) else Color(0xFF141414)
                drawLine(
                    color = guideColor,
                    start = Offset(32f, centerY - slitHalfHeight),
                    end = Offset(size.width - 32f, centerY - slitHalfHeight),
                    strokeWidth = 2f
                )
                drawLine(
                    color = guideColor,
                    start = Offset(32f, centerY + slitHalfHeight),
                    end = Offset(size.width - 32f, centerY + slitHalfHeight),
                    strokeWidth = 2f
                )

                if (isHolding && lines.isNotEmpty()) {
                    val jitterX = sin(frameTick * 0.55f) * 1.8f
                    val jitterY = sin(frameTick * 0.35f) * 0.9f
                    val phase = frameTick % 2

                    clipRect(
                        left = 0f,
                        top = centerY - slitHalfHeight,
                        right = size.width,
                        bottom = centerY + slitHalfHeight
                    ) {
                        lines.forEachIndexed { index, charArray ->
                            val baselineY = centerY + (index * lineSpacingPx) - scrollOffsetPx + 14f
                            if (baselineY in (centerY - slitHalfHeight - 60f)..(centerY + slitHalfHeight + 60f)) {
                                drawContext.canvas.nativeCanvas.drawText(
                                    charArray,
                                    0,
                                    charArray.size,
                                    48f + jitterX,
                                    baselineY + jitterY,
                                    textPaint
                                )
                            }
                        }

                        val barStep = 4f
                        var y = (centerY - slitHalfHeight) + (phase * 2f)
                        while (y < centerY + slitHalfHeight) {
                            drawLine(
                                color = Color(0x88000000),
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 2f
                            )
                            y += barStep
                        }
                    }
                }

                val native = drawContext.canvas.nativeCanvas
                native.save()
                native.rotate(-30f, size.width / 2f, size.height / 2f)
                val stepX = 340f
                val stepY = 220f
                var wy = -size.height * 0.5f
                while (wy < size.height * 1.5f) {
                    var wx = -size.width * 0.5f
                    while (wx < size.width * 1.5f) {
                        native.drawText(deviceWatermark, wx, wy, watermarkPaint)
                        wx += stepX
                    }
                    wy += stepY
                }
                native.restore()
            }
        }
    }
}

@Composable
private fun BurnedView(reason: String, onExit: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "NOTE DESTROYED",
            color = Color(0xFFB33939),
            fontSize = 20.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = "Reason: $reason\nMemory wiped.",
            color = Color(0xFF555555),
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 12.dp, bottom = 28.dp)
        )
        OutlinedButton(
            onClick = onExit,
            border = BorderStroke(1.dp, Color(0xFF33333A)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = "RETURN HOME",
                color = Color(0xFFB8B8B8),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
        }
    }
}
