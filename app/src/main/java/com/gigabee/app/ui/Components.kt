package com.gigabee.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    stroke: Dp = 18.dp,
    track: Color = Bee.Surface2,
    color: Color = Bee.Yellow,
    glow: Boolean = true,
) {
    Canvas(modifier) {
        val sw = stroke.toPx()
        val inset = if (glow) sw else sw / 2f
        val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
        val topLeft = Offset(inset, inset)
        drawArc(color = track, startAngle = -90f, sweepAngle = 360f, useCenter = false,
            topLeft = topLeft, size = arcSize, style = Stroke(width = sw))
        if (progress > 0f) {
            val sweep = 360f * progress
            if (glow) {
                drawArc(color = color.copy(alpha = 0.14f), startAngle = -90f, sweepAngle = sweep, useCenter = false,
                    topLeft = topLeft, size = arcSize, style = Stroke(width = sw * 2f, cap = StrokeCap.Round))
            }
            drawArc(color = color, startAngle = -90f, sweepAngle = sweep, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(width = sw, cap = StrokeCap.Round))
        }
    }
}

@Composable
fun BeeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(64.dp),
        shape = RoundedCornerShape(32.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Bee.Yellow,
            contentColor = Bee.Black,
            disabledContainerColor = Bee.Surface2,
            disabledContentColor = Bee.Muted,
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), color = Bee.Muted, strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun Pill(text: String, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(50)
    Text(
        text = text,
        color = if (selected) Bee.Black else Bee.Text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) Bee.Yellow else Bee.Surface)
            .border(1.dp, if (selected) Bee.Yellow else Bee.Line, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
fun SectionLabel(text: String) {
    Text(text, color = Bee.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp))
}
