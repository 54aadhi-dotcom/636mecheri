package com.mecheri636.offline.ui.catalog

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class ProduceShape(val code: String, val labelTa: String, val labelEn: String) {
    ALL("ALL", "அனைத்தும்", "All"),
    ROUND("ROUND", "உருண்டை", "Round"),
    OVAL("OVAL", "முட்டை", "Oval"),
    LONG("LONG", "நீளம்", "Long"),
    POD("POD", "நெற்று", "Pod"),
    ROOT("ROOT", "கிழங்கு", "Root"),
    LEAFY("LEAFY", "கீரை", "Leafy"),
    BUNCH("BUNCH", "குலை", "Bunch")
}

@Composable
fun ShapeCanvasIcon(
    shapeCode: String,
    tint: Color,
    size: Dp = 20.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.11f, cap = StrokeCap.Round)
        when (shapeCode) {
            "ROUND" -> {
                drawCircle(color = tint, radius = w * 0.34f, center = Offset(w * 0.5f, h * 0.56f), style = stroke)
                drawLine(tint, Offset(w * 0.5f, h * 0.22f), Offset(w * 0.62f, h * 0.10f), strokeWidth = w * 0.10f, cap = StrokeCap.Round)
            }
            "OVAL" -> {
                drawOval(color = tint, topLeft = Offset(w * 0.22f, h * 0.14f), size = Size(w * 0.56f, h * 0.74f), style = stroke)
                drawLine(tint, Offset(w * 0.5f, h * 0.14f), Offset(w * 0.5f, h * 0.04f), strokeWidth = w * 0.10f, cap = StrokeCap.Round)
            }
            "LONG" -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.36f, h * 0.10f),
                    size = Size(w * 0.28f, h * 0.80f),
                    cornerRadius = CornerRadius(w * 0.14f, w * 0.14f),
                    style = stroke
                )
            }
            "POD" -> {
                val p = Path().apply {
                    moveTo(w * 0.22f, h * 0.78f)
                    quadraticBezierTo(w * 0.20f, h * 0.22f, w * 0.78f, h * 0.18f)
                    quadraticBezierTo(w * 0.80f, h * 0.72f, w * 0.22f, h * 0.78f)
                }
                drawPath(p, color = tint, style = stroke)
                drawCircle(tint, w * 0.06f, Offset(w * 0.42f, h * 0.56f))
                drawCircle(tint, w * 0.06f, Offset(w * 0.58f, h * 0.42f))
            }
            "ROOT" -> {
                val p = Path().apply {
                    moveTo(w * 0.26f, h * 0.30f)
                    lineTo(w * 0.74f, h * 0.30f)
                    lineTo(w * 0.50f, h * 0.88f)
                    close()
                }
                drawPath(p, color = tint, style = stroke)
                drawLine(tint, Offset(w * 0.40f, h * 0.30f), Offset(w * 0.32f, h * 0.12f), strokeWidth = w * 0.09f, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.60f, h * 0.30f), Offset(w * 0.68f, h * 0.12f), strokeWidth = w * 0.09f, cap = StrokeCap.Round)
            }
            "LEAFY" -> {
                val p = Path().apply {
                    moveTo(w * 0.20f, h * 0.82f)
                    quadraticBezierTo(w * 0.18f, h * 0.20f, w * 0.82f, h * 0.18f)
                    quadraticBezierTo(w * 0.80f, h * 0.80f, w * 0.20f, h * 0.82f)
                }
                drawPath(p, color = tint, style = stroke)
                drawLine(tint, Offset(w * 0.22f, h * 0.80f), Offset(w * 0.72f, h * 0.28f), strokeWidth = w * 0.08f, cap = StrokeCap.Round)
            }
            "BUNCH" -> {
                drawLine(tint, Offset(w * 0.5f, h * 0.10f), Offset(w * 0.5f, h * 0.26f), strokeWidth = w * 0.10f, cap = StrokeCap.Round)
                val r = w * 0.13f
                drawCircle(tint, r, Offset(w * 0.36f, h * 0.40f))
                drawCircle(tint, r, Offset(w * 0.64f, h * 0.40f))
                drawCircle(tint, r, Offset(w * 0.28f, h * 0.62f))
                drawCircle(tint, r, Offset(w * 0.50f, h * 0.62f))
                drawCircle(tint, r, Offset(w * 0.72f, h * 0.62f))
                drawCircle(tint, r, Offset(w * 0.50f, h * 0.82f))
            }
            else -> {
                val r = w * 0.13f
                drawCircle(tint, r, Offset(w * 0.30f, h * 0.30f))
                drawCircle(tint, r, Offset(w * 0.70f, h * 0.30f))
                drawCircle(tint, r, Offset(w * 0.30f, h * 0.70f))
                drawCircle(tint, r, Offset(w * 0.70f, h * 0.70f))
            }
        }
    }
}

// Non-text visual trait marker for the 29 lookalike greens (ic_prod_077..105).
// Strictly avoids Tamil syllable tags and Unicode 14-15 emojis.
@Composable
fun GreensVisualTraitBadge(
    productId: Int,
    size: Dp = 18.dp,
    modifier: Modifier = Modifier
) {
    if (productId !in 77..105) return
    val darkGreen = Color(0xFF1B5E20)
    val brightGreen = Color(0xFF43A047)
    val redAccent = Color(0xFFD32F2F)
    val whiteFlower = Color(0xFFFFFDE7)
    val yellowDot = Color(0xFFFBC02D)
    val darkBerry = Color(0xFF212121)

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        drawCircle(color = Color(0xFFE8F5E9), radius = w * 0.48f)
        drawCircle(color = darkGreen.copy(alpha = 0.35f), radius = w * 0.48f, style = Stroke(w * 0.06f))

        when (productId) {
            84 -> { // Thandu Keerai: thick red stem
                drawLine(redAccent, Offset(w * 0.50f, h * 0.85f), Offset(w * 0.50f, h * 0.20f), strokeWidth = w * 0.22f, cap = StrokeCap.Round)
                drawCircle(brightGreen, w * 0.16f, Offset(w * 0.32f, h * 0.36f))
                drawCircle(brightGreen, w * 0.16f, Offset(w * 0.68f, h * 0.36f))
            }
            89 -> { // Red Ponnanganni: crimson leaves
                drawCircle(redAccent, w * 0.24f, Offset(w * 0.38f, h * 0.48f))
                drawCircle(redAccent.copy(alpha = 0.85f), w * 0.22f, Offset(w * 0.64f, h * 0.52f))
            }
            90 -> { // Agathi Keerai: distinctive white crescent flower
                drawOval(darkGreen, Offset(w * 0.18f, h * 0.52f), Size(w * 0.64f, h * 0.24f))
                drawCircle(whiteFlower, w * 0.20f, Offset(w * 0.52f, h * 0.34f))
                drawCircle(darkGreen, w * 0.20f, Offset(w * 0.52f, h * 0.34f), style = Stroke(w * 0.06f))
            }
            87 -> { // Manathakkali: dark berries
                drawLine(darkGreen, Offset(w * 0.5f, h * 0.20f), Offset(w * 0.5f, h * 0.78f), strokeWidth = w * 0.09f)
                drawCircle(darkBerry, w * 0.13f, Offset(w * 0.36f, h * 0.56f))
                drawCircle(darkBerry, w * 0.13f, Offset(w * 0.64f, h * 0.56f))
                drawCircle(darkBerry, w * 0.13f, Offset(w * 0.50f, h * 0.74f))
            }
            96 -> { // Thoothuvalai: thorny ticks
                drawLine(darkGreen, Offset(w * 0.22f, h * 0.78f), Offset(w * 0.78f, h * 0.22f), strokeWidth = w * 0.11f, cap = StrokeCap.Round)
                drawLine(yellowDot, Offset(w * 0.42f, h * 0.58f), Offset(w * 0.28f, h * 0.44f), strokeWidth = w * 0.10f, cap = StrokeCap.Round)
                drawLine(yellowDot, Offset(w * 0.58f, h * 0.42f), Offset(w * 0.72f, h * 0.56f), strokeWidth = w * 0.10f, cap = StrokeCap.Round)
            }
            97 -> { // Pirandai: segmented square node stem
                drawRect(brightGreen, Offset(w * 0.36f, h * 0.18f), Size(w * 0.28f, h * 0.64f))
                drawLine(darkGreen, Offset(w * 0.30f, h * 0.40f), Offset(w * 0.70f, h * 0.40f), strokeWidth = w * 0.10f)
                drawLine(darkGreen, Offset(w * 0.30f, h * 0.62f), Offset(w * 0.70f, h * 0.62f), strokeWidth = w * 0.10f)
            }
            94 -> { // Mudakathan: balloon pod
                drawCircle(brightGreen, w * 0.26f, Offset(w * 0.50f, h * 0.52f), style = Stroke(w * 0.11f))
                drawCircle(darkGreen, w * 0.09f, Offset(w * 0.50f, h * 0.52f))
            }
            95 -> { // Vallarai: fan / kidney leaf
                drawArc(brightGreen, 180f, 180f, true, Offset(w * 0.18f, h * 0.24f), Size(w * 0.64f, h * 0.56f))
                drawLine(darkGreen, Offset(w * 0.50f, h * 0.52f), Offset(w * 0.50f, h * 0.84f), strokeWidth = w * 0.10f)
            }
            77, 78 -> { // Coriander / Mint: aromatic star cluster
                val c = if (productId == 77) brightGreen else darkGreen
                drawCircle(c, w * 0.15f, Offset(w * 0.50f, h * 0.32f))
                drawCircle(c, w * 0.15f, Offset(w * 0.34f, h * 0.58f))
                drawCircle(c, w * 0.15f, Offset(w * 0.66f, h * 0.58f))
            }
            79, 80 -> { // Curry / Moringa: paired leaflets on twig
                drawLine(darkGreen, Offset(w * 0.50f, h * 0.18f), Offset(w * 0.50f, h * 0.84f), strokeWidth = w * 0.08f)
                val r = if (productId == 79) w * 0.13f else w * 0.09f
                listOf(0.32f, 0.54f, 0.74f).forEach { y ->
                    drawCircle(brightGreen, r, Offset(w * 0.32f, h * y))
                    drawCircle(brightGreen, r, Offset(w * 0.68f, h * y))
                }
            }
            104, 105 -> { // Betel leaf / Banana leaf: broad veined leaf
                val p = Path().apply {
                    moveTo(w * 0.50f, h * 0.16f)
                    lineTo(w * 0.80f, h * 0.56f)
                    lineTo(w * 0.50f, h * 0.86f)
                    lineTo(w * 0.20f, h * 0.56f)
                    close()
                }
                drawPath(p, color = if (productId == 104) darkGreen else brightGreen)
                drawLine(whiteFlower, Offset(w * 0.50f, h * 0.22f), Offset(w * 0.50f, h * 0.80f), strokeWidth = w * 0.07f)
            }
            91, 93, 98, 99, 100, 101, 102 -> { // Medicinal / tangy greens: yellow flower or red edge dot
                drawOval(brightGreen, Offset(w * 0.24f, h * 0.28f), Size(w * 0.52f, h * 0.50f))
                drawCircle(if (productId == 91) redAccent else yellowDot, w * 0.12f, Offset(w * 0.66f, h * 0.32f))
            }
            else -> { // Amaranth / Spinach / Purslane / Radish leaves: crisp leaf + center vein
                drawOval(brightGreen, Offset(w * 0.26f, h * 0.20f), Size(w * 0.48f, h * 0.62f))
                drawLine(darkGreen, Offset(w * 0.50f, h * 0.24f), Offset(w * 0.50f, h * 0.84f), strokeWidth = w * 0.09f, cap = StrokeCap.Round)
            }
        }
    }
}
