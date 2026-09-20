package com.baiel.expressivefiles.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * App geometry. The getters are @Composable so existing call sites keep
 * compiling unchanged; the shapes are the stock M3 Expressive "cookie"
 * geometry.
 */
private val PixelCookieShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(32.dp),
    extraLarge = RoundedCornerShape(40.dp)
)
private val PixelTileShape: Shape = RoundedCornerShape(26.dp)
private val PixelIconShape: Shape get() = ExpressiveCookieShape
private val PixelSheetShape: Shape =
    RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp, bottomStart = 0.dp, bottomEnd = 0.dp)

/** Capsule: the shared button shape. */
val PillShape: Shape = RoundedCornerShape(100.dp)

val ExpressiveRoundedShape: Shape = RoundedCornerShape(16.dp)
val ExpressiveAsymmetricShape: Shape = RoundedCornerShape(
    topStart = 24.dp, topEnd = 10.dp, bottomEnd = 24.dp, bottomStart = 10.dp
)
/** Leaf: rounded on the opposite diagonal from the asymmetric shape. */
val ExpressiveLeafShape: Shape = RoundedCornerShape(
    topStart = 6.dp, topEnd = 24.dp, bottomEnd = 6.dp, bottomStart = 24.dp
)

/** A smooth ten-lobed cookie, normalized to any button's measured bounds. */
val ExpressiveCookieShape: Shape = object : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path()
        for (i in 0..240) {
            val angle = i * Math.PI * 2.0 / 240 - Math.PI / 2
            val radius = 0.94 + 0.06 * cos(10 * angle)
            val x = (size.width / 2 * (1 + radius * cos(angle))).toFloat()
            val y = (size.height / 2 * (1 + radius * sin(angle))).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

val CookieShapes: Shapes
    @Composable get() = PixelCookieShapes

val ChunkyTileShape: Shape
    @Composable get() = PixelTileShape

val ChunkyIconShape: Shape
    @Composable get() = PixelIconShape

val ChunkySheetShape: Shape
    @Composable get() = PixelSheetShape

/** Icon buttons use a shape suited to the action (circle by default via caller). */
@Composable
fun osIconButtonShape(pixelShape: Shape = ExpressiveCookieShape): Shape = pixelShape

/** File list/grid/expressive cards: the Pixel roundness. */
@Composable
fun osCardShape(expressive: Boolean = false): Shape =
    RoundedCornerShape(if (expressive) 26.dp else 24.dp)
