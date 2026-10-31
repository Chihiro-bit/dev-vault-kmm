package com.devvault.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 设计变量，与桌面端 styles.css 的 :root（浅色 / 深色）一一对应 */
@Immutable
data class DvColors(
    val bg: Color,
    val panel: Color,
    val side: Color,
    val hover: Color,
    val text: Color,
    val text2: Color,
    val muted: Color,
    val border: Color,
    val borderStrong: Color,
    val primary: Color,
    val primaryHover: Color,
    val primaryWeak: Color,
    val danger: Color,
    val dangerWeak: Color,
    val warn: Color,
    val ok: Color,
    val codeBg: Color,
    val codeText: Color,
    val star: Color,
    val isDark: Boolean,
)

val LightColors = DvColors(
    bg = Color(0xFFF5F6F8),
    panel = Color(0xFFFFFFFF),
    side = Color(0xFFECEEF3),
    hover = Color(0x0D0F172A),
    text = Color(0xFF1C2028),
    text2 = Color(0xFF4B5260),
    muted = Color(0xFF8A919E),
    border = Color(0xFFE3E6EC),
    borderStrong = Color(0xFFD2D6DE),
    primary = Color(0xFF3B6CFF),
    primaryHover = Color(0xFF2F5AE0),
    primaryWeak = Color(0xFFE9EFFF),
    danger = Color(0xFFE5484D),
    dangerWeak = Color(0xFFFDECEC),
    warn = Color(0xFFE38B00),
    ok = Color(0xFF16A37B),
    codeBg = Color(0xFFFFF6DF),
    codeText = Color(0xFF9A5B00),
    star = Color(0xFFF5A524),
    isDark = false,
)

val DarkColors = DvColors(
    bg = Color(0xFF15171C),
    panel = Color(0xFF1D2027),
    side = Color(0xFF181A20),
    hover = Color(0x0DFFFFFF),
    text = Color(0xFFE6E8EC),
    text2 = Color(0xFFB4BAC4),
    muted = Color(0xFF7D8592),
    border = Color(0xFF2B2F38),
    borderStrong = Color(0xFF3A3F4A),
    primary = Color(0xFF5B84FF),
    primaryHover = Color(0xFF7095FF),
    primaryWeak = Color(0x295B84FF),
    danger = Color(0xFFF06368),
    dangerWeak = Color(0x24F06368),
    warn = Color(0xFFE38B00),
    ok = Color(0xFF16A37B),
    codeBg = Color(0x24E38B00),
    codeText = Color(0xFFF3B556),
    star = Color(0xFFF5A524),
    isDark = true,
)

val LocalDv = staticCompositionLocalOf { LightColors }

object Dv {
    val colors: DvColors @Composable get() = LocalDv.current
    val radius = 10.dp
    val mono = FontFamily.Monospace
}

/** 解析 "#3b6cff" 形式的颜色 */
fun parseColor(hex: String?, fallback: Color = Color(0xFF8A919E)): Color {
    val h = hex?.removePrefix("#") ?: return fallback
    val v = h.toLongOrNull(16) ?: return fallback
    return when (h.length) {
        6 -> Color(0xFF000000 or v)
        8 -> Color(v)
        3 -> {
            val r = (v shr 8) and 0xf; val g = (v shr 4) and 0xf; val b = v and 0xf
            Color(0xFF000000 or (r * 17 shl 16) or (g * 17 shl 8) or (b * 17))
        }
        else -> fallback
    }
}

/** 相当于 CSS color-mix(in srgb, c 13%, transparent) */
fun Color.weak(alpha: Float = 0.13f) = copy(alpha = alpha)

@Composable
fun DevVaultTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val c = if (dark) DarkColors else LightColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.primary, onPrimary = Color.White, primaryContainer = c.primaryWeak, onPrimaryContainer = c.primary,
            background = c.bg, onBackground = c.text, surface = c.panel, onSurface = c.text, surfaceVariant = c.bg,
            onSurfaceVariant = c.text2, outline = c.borderStrong, outlineVariant = c.border, error = c.danger,
            surfaceContainer = c.panel, surfaceContainerHigh = c.panel, surfaceContainerHighest = c.panel,
            surfaceContainerLow = c.panel, secondaryContainer = c.primaryWeak, onSecondaryContainer = c.primary,
        )
    } else {
        lightColorScheme(
            primary = c.primary, onPrimary = Color.White, primaryContainer = c.primaryWeak, onPrimaryContainer = c.primary,
            background = c.bg, onBackground = c.text, surface = c.panel, onSurface = c.text, surfaceVariant = c.bg,
            onSurfaceVariant = c.text2, outline = c.borderStrong, outlineVariant = c.border, error = c.danger,
            surfaceContainer = c.panel, surfaceContainerHigh = c.panel, surfaceContainerHighest = c.panel,
            surfaceContainerLow = c.panel, secondaryContainer = c.primaryWeak, onSecondaryContainer = c.primary,
        )
    }
    CompositionLocalProvider(LocalDv provides c) {
        MaterialTheme(
            colorScheme = scheme,
            typography = MaterialTheme.typography.copy(
                bodyLarge = TextStyle(fontSize = 15.sp, color = c.text),
                bodyMedium = TextStyle(fontSize = 14.sp, color = c.text),
            ),
            content = content,
        )
    }
}
