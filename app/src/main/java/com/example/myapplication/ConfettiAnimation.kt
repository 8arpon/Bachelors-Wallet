package com.example.myapplication

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun ConfettiExplosion(trigger: Boolean, onFinished: () -> Unit) {
    if (!trigger) return

    val particles = remember {
        List(80) {
            val angle = Math.random() * 2 * Math.PI
            val speed = 5 + Math.random() * 15
            val vx = speed * Math.cos(angle)
            val vy = -speed * Math.sin(angle) // Fly upwards first
            val color = Color(
                red = (100..255).random() / 255f,
                green = (100..255).random() / 255f,
                blue = (100..255).random() / 255f
            )
            ConfettiParticle(
                vx = vx.toFloat(), vy = vy.toFloat(),
                color = color,
                size = (6..16).random().dp
            )
        }
    }

    var elapsed by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < 1500) {
            delay(16)
            elapsed = (System.currentTimeMillis() - start).toInt()
        }
        onFinished()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2
            val centerY = size.height / 3 // Explode slightly above middle for better drop
            particles.forEach { p ->
                val time = elapsed / 1000f
                // Apply simple 2D motion with gravity
                val x = centerX + p.vx * time * 120
                val y = centerY + (p.vy * time + 0.5f * 9.8f * time * time * 80) * 120

                drawRect(
                    color = p.color,
                    topLeft = Offset(x, y),
                    size = Size(p.size.toPx(), p.size.toPx())
                )
            }
        }
    }
}

data class ConfettiParticle(
    val vx: Float, val vy: Float,
    val color: Color,
    val size: Dp
)
