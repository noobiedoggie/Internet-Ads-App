package com.gigabee.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gigabee.app.data.formatHoney
import com.gigabee.app.ui.theme.Bee
import kotlin.math.cos
import kotlin.math.sin

/** The logo: a yellow honeycomb cell with two black stripes. */
@Composable
fun BeeMark(modifier: Modifier = Modifier, side: Dp = 28.dp) {
    Canvas(modifier.size(side)) {
        val r = this.size.minDimension / 2f
        val cx = center.x
        val cy = center.y
        val hex = Path().apply {
            for (i in 0 until 6) {
                val a = Math.toRadians(60.0 * i - 90.0)
                val x = cx + r * cos(a).toFloat()
                val y = cy + r * sin(a).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        drawPath(hex, Bee.Yellow)
        clipPath(hex) {
            val h = r * 0.22f
            drawRect(Bee.Black, topLeft = Offset(0f, cy - r * 0.36f), size = Size(this.size.width, h))
            drawRect(Bee.Black, topLeft = Offset(0f, cy + r * 0.12f), size = Size(this.size.width, h))
        }
    }
}

/** A honey drop glyph (used instead of an emoji so it looks the same on every phone). */
@Composable
fun HoneyDrop(modifier: Modifier = Modifier, size: Dp = 16.dp, color: Color = Bee.Yellow) {
    Canvas(modifier.size(width = size * 0.8f, height = size)) {
        val w = this.size.width
        val h = this.size.height
        val drop = Path().apply {
            moveTo(w * 0.5f, 0f)
            cubicTo(w * 0.5f, 0f, w * 0.02f, h * 0.46f, w * 0.02f, h * 0.66f)
            cubicTo(w * 0.02f, h * 0.88f, w * 0.24f, h, w * 0.5f, h)
            cubicTo(w * 0.76f, h, w * 0.98f, h * 0.88f, w * 0.98f, h * 0.66f)
            cubicTo(w * 0.98f, h * 0.46f, w * 0.5f, 0f, w * 0.5f, 0f)
            close()
        }
        drawPath(drop, color)
    }
}

/** Balances read "◆ 1 234"; signed amounts (with [prefix]) read "+22 ◆". */
@Composable
fun HoneyAmount(
    amount: Long,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 15.sp,
    color: Color = Bee.Text,
    dropColor: Color = Bee.Yellow,
    prefix: String = "",
    weight: FontWeight = FontWeight.Bold,
) {
    val gap = (fontSize.value * 0.3f).dp
    val drop = (fontSize.value * 0.95f).dp
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (prefix.isEmpty()) {
            HoneyDrop(size = drop, color = dropColor)
            Spacer(Modifier.width(gap))
            Text(formatHoney(amount), color = color, fontSize = fontSize, fontWeight = weight)
        } else {
            Text(prefix + formatHoney(amount), color = color, fontSize = fontSize, fontWeight = weight)
            Spacer(Modifier.width(gap))
            HoneyDrop(size = drop, color = dropColor)
        }
    }
}

@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    stroke: Dp = 12.dp,
    track: Color = Bee.Surface2,
    color: Color = Bee.Yellow,
) {
    Canvas(modifier) {
        val sw = stroke.toPx()
        val inset = sw / 2f
        val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
        val topLeft = Offset(inset, inset)
        drawArc(color = track, startAngle = -90f, sweepAngle = 360f, useCenter = false,
            topLeft = topLeft, size = arcSize, style = Stroke(width = sw))
        if (progress > 0f) {
            drawArc(color = color, startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(width = sw, cap = StrokeCap.Round))
        }
    }
}

/** Primary action: yellow pill, black text. Optional [trailing] content (e.g. a honey amount). */
@Composable
fun BeeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    loading: Boolean = false,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(60.dp),
        shape = RoundedCornerShape(30.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Bee.Yellow,
            contentColor = Bee.Black,
            disabledContainerColor = Bee.Surface2,
            disabledContentColor = Bee.Muted,
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(18.dp), color = Bee.Muted, strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}

/** Secondary action: outlined, yellow text. */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, if (enabled) Bee.Yellow.copy(alpha = 0.6f) else Bee.Line),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Bee.Yellow, disabledContentColor = Bee.Muted),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Small rounded chip. [content] replaces [text] when given. */
@Composable
fun Pill(
    text: String = "",
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: (@Composable RowScope.() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .clip(shape)
            .background(if (selected) Bee.YellowSoft else Bee.Surface)
            .border(1.dp, if (selected) Bee.Yellow.copy(alpha = 0.5f) else Bee.Line, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (content != null) {
            content()
        } else {
            Text(text, color = if (selected) Bee.Yellow else Bee.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Honey balance chip used in screen headers. */
@Composable
fun HoneyChip(amount: Long) {
    Pill(selected = true) { HoneyAmount(amount, fontSize = 14.sp, color = Bee.Yellow) }
}

/** Uppercase, spaced-out label above a section. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        color = Bee.Muted,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.4.sp,
        modifier = modifier.padding(bottom = 10.dp),
    )
}

/** Screen title row: big title on the left, optional chip on the right. */
@Composable
fun ScreenHeader(title: String, subtitle: String? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Bee.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
            if (subtitle != null) Text(subtitle, color = Bee.Muted, fontSize = 14.sp)
        }
        if (trailing != null) trailing()
    }
}

/** Dark rounded container with a hairline border. */
@Composable
fun BeeCard(
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    padding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Bee.Surface)
            .border(1.dp, if (highlight) Bee.Yellow.copy(alpha = 0.55f) else Bee.Line, shape)
            .padding(padding),
        verticalArrangement = Arrangement.Top,
        content = content,
    )
}
