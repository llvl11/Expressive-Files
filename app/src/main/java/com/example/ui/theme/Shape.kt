package com.example.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Material 3 Expressive Cookie and Squircle Shapes.
 */
val CookieShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(32.dp),
    extraLarge = RoundedCornerShape(40.dp)
)

/**
 * 8-Petal Scalloped / Cookie shape for expressive buttons, icons, and hero cards.
 */
class ExpressiveCookieShape(private val lobes: Int = 8, private val lobeDepthRatio: Float = 0.12f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val baseRadius = minOf(centerX, centerY)
        val step = (2 * PI / (lobes * 16)).toFloat()

        var first = true
        var angle = 0f
        while (angle < 2 * PI) {
            val r = baseRadius * (1f - lobeDepthRatio * (1f + cos(lobes * angle)) / 2f)
            val x = centerX + r * cos(angle)
            val y = centerY + r * sin(angle)
            if (first) {
                path.moveTo(x, y)
                first = false
            } else {
                path.lineTo(x, y)
            }
            angle += step
        }
        path.close()
        return Outline.Generic(path)
    }
}

/**
 * Chunky Squircle / Organic Rounded Shape.
 */
fun ChunkyCookieShape(radiusDp: Float = 28f) = RoundedCornerShape(radiusDp.dp)

val CookieLobeShape = ExpressiveCookieShape(lobes = 8, lobeDepthRatio = 0.08f)
val FlowerCookieShape = ExpressiveCookieShape(lobes = 12, lobeDepthRatio = 0.06f)
val PillShape = RoundedCornerShape(100.dp)
val ChunkyTileShape = RoundedCornerShape(26.dp)
val ChunkyIconShape = RoundedCornerShape(20.dp)
val ChunkySheetShape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
