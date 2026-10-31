package com.devvault.ui.theme

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 图标与桌面端 Icon.tsx 相同（24×24 视图、2px 描边、圆角端点） */
enum class DvIcon(vararg val paths: String) {
    Copy(rect(9f, 9f, 13f, 13f, 2f), "M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"),
    Clone(rect(3f, 3f, 13f, 13f, 2f), "M8 21h11a2 2 0 0 0 2-2V8"),
    Eye("M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z", circle(12f, 12f, 3f)),
    EyeOff(
        "M17.94 17.94A10.07 10.07 0 0 1 12 19c-6.5 0-10-7-10-7a18.5 18.5 0 0 1 5.06-5.94",
        "M9.9 4.24A9.12 9.12 0 0 1 12 4c6.5 0 10 7 10 7a18.5 18.5 0 0 1-2.16 3.19",
        "M14.12 14.12a3 3 0 1 1-4.24-4.24",
        "M1 1l22 22",
    ),
    Star("M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"),
    Edit("M12 20h9", "M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4 12.5-12.5z"),
    Trash("M3 6h18", "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2", "M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"),
    Plus("M12 5v14M5 12h14"),
    Search(circle(11f, 11f, 7f), "M21 21l-4.35-4.35"),
    External("M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6", "M15 3h6v6", "M10 14L21 3"),
    X("M18 6L6 18M6 6l12 12"),
    Up("M18 15l-6-6-6 6"),
    Down("M6 9l6 6 6-6"),
    Right("M9 18l6-6-6-6"),
    Message("M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"),
    ListIcon("M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01"),
    Back("M19 12H5M12 19l-7-7 7-7"),
    Repeat("M17 1l4 4-4 4", "M3 11V9a4 4 0 0 1 4-4h14", "M7 23l-4-4 4-4", "M21 13v2a4 4 0 0 1-4 4H3"),
    Subtask("M15 10l5 5-5 5", "M4 4v7a4 4 0 0 0 4 4h12"),
    Send("M22 2L11 13", "M22 2l-7 20-4-9-9-4 20-7z"),
    Gear(
        circle(12f, 12f, 3f),
        "M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z",
    ),
    Cloud("M18 10h-1.26A8 8 0 1 0 9 20h9a5 5 0 0 0 0-10z"),
    Logout("M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4", "M16 17l5-5-5-5", "M21 12H9"),
    Sliders("M4 21v-7M4 10V3M12 21v-9M12 8V3M20 21v-5M20 12V3", "M1 14h6M9 8h6M17 16h6"),
    Layers("M12 2l10 5-10 5L2 7l10-5z", "M2 17l10 5 10-5", "M2 12l10 5 10-5"),
    Inbox(
        "M22 12h-6l-2 3h-4l-2-3H2",
        "M5.45 5.11L2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z",
    ),
    Key(circle(7.5f, 15.5f, 4.5f), "M21 2l-9.6 9.6", "M15.5 7.5l3 3L22 7l-3-3"),
    Check("M9 11l3 3L22 4", "M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"),
    Tick("M20 6L9 17l-5-5"),
    Refresh("M23 4v6h-6", "M20.49 15a9 9 0 1 1-2.12-9.36L23 10"),
    Calendar(rect(3f, 4f, 18f, 18f, 2f), "M16 2v4M8 2v4M3 10h18"),
    Lock(rect(3f, 11f, 18f, 11f, 2f), "M7 11V7a5 5 0 0 1 10 0v4"),
    User("M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2", circle(12f, 7f, 4f)),
    Git(circle(18f, 18f, 3f), circle(6f, 6f, 3f), "M6 21V9a9 9 0 0 0 9 9"),
    Shield("M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"),
    Server(rect(2f, 2f, 20f, 8f, 2f), rect(2f, 14f, 20f, 8f, 2f), "M6 6h.01M6 18h.01"),
    Database(ellipse(12f, 5f, 9f, 3f), "M21 12c0 1.66-4 3-9 3s-9-1.34-9-3", "M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5"),
    Bell("M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9", "M13.73 21a2 2 0 0 1-3.46 0"),
    Wand("M15 4V2M15 16v-2M8 9h2M20 9h2M17.8 11.8L19 13M17.8 6.2L19 5M12.2 6.2L11 5", "M3 21l9-9"),
}

private fun f(v: Float) = if (v == v.toInt().toFloat()) v.toInt().toString() else v.toString()

private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) =
    "M${f(x + r)} ${f(y)}H${f(x + w - r)}A${f(r)} ${f(r)} 0 0 1 ${f(x + w)} ${f(y + r)}V${f(y + h - r)}" +
        "A${f(r)} ${f(r)} 0 0 1 ${f(x + w - r)} ${f(y + h)}H${f(x + r)}A${f(r)} ${f(r)} 0 0 1 ${f(x)} ${f(y + h - r)}" +
        "V${f(y + r)}A${f(r)} ${f(r)} 0 0 1 ${f(x + r)} ${f(y)}Z"

private fun circle(cx: Float, cy: Float, r: Float) = ellipse(cx, cy, r, r)

private fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float) =
    "M${f(cx - rx)} ${f(cy)}A${f(rx)} ${f(ry)} 0 1 0 ${f(cx + rx)} ${f(cy)}A${f(rx)} ${f(ry)} 0 1 0 ${f(cx - rx)} ${f(cy)}Z"

private val cache = HashMap<Pair<DvIcon, Boolean>, ImageVector>()

private fun vectorOf(icon: DvIcon, filled: Boolean): ImageVector = cache.getOrPut(icon to filled) {
    val b = ImageVector.Builder(icon.name, 24.dp, 24.dp, 24f, 24f)
    for (d in icon.paths) {
        b.addPath(
            pathData = PathParser().parsePathString(d).toNodes(),
            fill = if (filled) SolidColor(Color.Black) else null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    b.build()
}

@Composable
fun DvIconView(icon: DvIcon, tint: Color, size: Dp = 16.dp, modifier: Modifier = Modifier, filled: Boolean = false) {
    val vector = remember(icon, filled) { vectorOf(icon, filled) }
    Icon(rememberVectorPainter(vector), contentDescription = null, tint = tint, modifier = modifier.size(size))
}
