package com.devvault.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devvault.AppStore
import com.devvault.Toast
import com.devvault.ui.theme.Dv
import com.devvault.ui.theme.DvIcon
import com.devvault.ui.theme.DvIconView
import com.devvault.ui.theme.parseColor
import com.devvault.ui.theme.weak
import com.devvault.util.Dates
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------- 容器 ----------

/** .card：白色面板、1px 边框、10px 圆角、轻微阴影 */
@Composable
fun Modifier.dvCard(radius: Dp = Dv.radius): Modifier {
    val c = Dv.colors
    val shape = RoundedCornerShape(radius)
    return this
        .shadow(if (c.isDark) 0.dp else 1.dp, shape, ambientColor = Color(0x140F172A), spotColor = Color(0x140F172A))
        .clip(shape)
        .background(c.panel)
        .border(1.dp, c.border, shape)
}

fun Modifier.noRippleClick(onClick: () -> Unit): Modifier = this.clickable(
    interactionSource = MutableInteractionSource(),
    indication = null,
    onClick = onClick,
)

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Dv.colors.text, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** 页面顶部：大标题 + 副标题（当前项目） */
@Composable
fun PageHeader(
    title: String,
    subtitle: String? = null,
    onSubtitleClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val c = Dv.colors
    Row(
        Modifier.fillMaxWidth().background(c.panel).padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.text)
            if (subtitle != null) {
                Row(
                    Modifier.padding(top = 2.dp).then(if (onSubtitleClick != null) Modifier.noRippleClick(onSubtitleClick) else Modifier),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(subtitle, fontSize = 13.sp, color = c.muted)
                    if (onSubtitleClick != null) DvIconView(DvIcon.Down, c.muted, 14.dp, Modifier.padding(start = 2.dp))
                }
            }
        }
        trailing()
    }
}

// ---------- 按钮 ----------

enum class BtnKind { Primary, Default, Ghost, Danger }

@Composable
fun DvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: BtnKind = BtnKind.Default,
    icon: DvIcon? = null,
    enabled: Boolean = true,
    small: Boolean = false,
) {
    val c = Dv.colors
    val (bg, fg, border) = when (kind) {
        BtnKind.Primary -> Triple(c.primary, Color.White, c.primary)
        BtnKind.Default -> Triple(c.panel, c.text, c.borderStrong)
        BtnKind.Ghost -> Triple(Color.Transparent, c.text2, Color.Transparent)
        BtnKind.Danger -> Triple(c.panel, c.danger, c.borderStrong)
    }
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier
            .height(if (small) 32.dp else 42.dp)
            .clip(shape)
            .background(if (enabled) bg else bg.copy(alpha = 0.5f))
            .border(1.dp, if (enabled) border else border.copy(alpha = 0.5f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (small) 12.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            DvIconView(icon, fg, if (small) 14.dp else 16.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = fg, fontSize = if (small) 13.sp else 14.sp, fontWeight = if (kind == BtnKind.Primary) FontWeight.Medium else FontWeight.Normal)
    }
}

@Composable
fun IconBtn(icon: DvIcon, onClick: () -> Unit, tint: Color = Dv.colors.muted, size: Dp = 18.dp, filled: Boolean = false, box: Dp = 36.dp) {
    Box(
        Modifier.size(box).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { DvIconView(icon, tint, size, filled = filled) }
}

// ---------- 小标签 ----------

/** .filter：顶部筛选胶囊，选中时为主色 */
@Composable
fun FilterPill(
    label: String,
    count: Int? = null,
    selected: Boolean,
    icon: DvIcon? = null,
    activeColor: Color = Dv.colors.primary,
    onClick: () -> Unit,
) {
    val c = Dv.colors
    val shape = RoundedCornerShape(16.dp)
    val fg = if (selected) Color.White else c.text2
    Row(
        Modifier.height(32.dp).clip(shape)
            .background(if (selected) activeColor else c.panel)
            .border(1.dp, if (selected) activeColor else c.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            DvIconView(icon, fg, 14.dp)
            Spacer(Modifier.width(5.dp))
        }
        Text(label, color = fg, fontSize = 13.sp)
        if (count != null) {
            Spacer(Modifier.width(4.dp))
            Text(count.toString(), color = fg.copy(alpha = 0.7f), fontSize = 11.sp)
        }
    }
}

/** .env：环境标签（开发 / 测试 / 预发 / 生产） */
@Composable
fun EnvTag(env: String) {
    if (env.isEmpty()) return
    val col = com.devvault.ui.envColor(env) ?: Dv.colors.muted
    Text(
        env, color = col, fontSize = 11.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(col.weak()).padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/** .proj-tag：项目色点 + 名称 */
@Composable
fun ProjTag(name: String?, color: String?, fontSize: Int = 12) {
    if (name == null) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(parseColor(color, Dv.colors.primary)))
        Spacer(Modifier.width(4.dp))
        Text(name, color = Dv.colors.text2, fontSize = fontSize.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** .status-pill：带色点的状态胶囊 */
@Composable
fun StatusPill(name: String?, color: String?, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val col = parseColor(color, Dv.colors.muted)
    Row(
        modifier.height(24.dp).clip(RoundedCornerShape(12.dp)).background(col.weak(0.12f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(col))
        Spacer(Modifier.width(6.dp))
        Text(name ?: "—", color = col, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** .pbadge：优先级徽标（P0 / P1 …） */
@Composable
fun PBadge(label: String?, color: String?, dim: Boolean = false) {
    val col = parseColor(color, Dv.colors.muted)
    Box(
        Modifier.heightIn(min = 18.dp).widthIn(min = 26.dp).clip(RoundedCornerShape(5.dp))
            .background(col.weak(if (dim) 0.07f else 0.13f)).padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label ?: "—", color = if (dim) col.copy(alpha = 0.5f) else col, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/** .expiry：账号有效期提示 */
@Composable
fun ExpiryTag(expires: String) {
    val info = Dates.expiryInfo(expires) ?: return
    val c = Dv.colors
    val (fg, bg) = when (info.tone) {
        Dates.ExpiryTone.EXPIRED -> c.danger to c.dangerWeak
        Dates.ExpiryTone.SOON -> c.warn to c.warn.weak()
        Dates.ExpiryTone.OK -> c.muted to c.bg
    }
    Row(
        Modifier.clip(RoundedCornerShape(4.dp)).background(bg).padding(horizontal = 6.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DvIconView(DvIcon.Calendar, fg, 11.dp)
        Spacer(Modifier.width(3.dp))
        Text(
            info.text, color = fg, fontSize = 11.sp,
            fontWeight = if (info.tone == Dates.ExpiryTone.OK) FontWeight.Normal else FontWeight.SemiBold,
        )
    }
}

/** .due：截止日期（逾期红色、今天到期橙色） */
@Composable
fun DueText(dueDate: String, done: Boolean) {
    val info = Dates.dueInfo(dueDate, done) ?: return
    val c = Dv.colors
    val col = when (info.tone) {
        Dates.DueTone.OVERDUE -> c.danger
        Dates.DueTone.TODAY -> c.warn
        Dates.DueTone.NORMAL -> c.muted
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        DvIconView(DvIcon.Calendar, col, 12.dp)
        Spacer(Modifier.width(4.dp))
        Text(info.text, color = col, fontSize = 12.sp, fontWeight = if (info.tone == Dates.DueTone.TODAY) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
fun MetaIcon(icon: DvIcon, text: String, color: Color = Dv.colors.muted) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        DvIconView(icon, color, 12.dp)
        Spacer(Modifier.width(3.dp))
        Text(text, color = color, fontSize = 12.sp)
    }
}

/** .check：圆形勾选框，未完成时边框为优先级颜色 */
@Composable
fun CheckCircle(done: Boolean, ringColor: Color, size: Dp = 20.dp, onClick: () -> Unit) {
    val c = Dv.colors
    Box(
        Modifier.size(size + 16.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(size).clip(CircleShape)
                .background(if (done) c.ok else Color.Transparent)
                .border(2.dp, if (done) c.ok else ringColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (done) DvIconView(DvIcon.Tick, Color.White, size * 0.65f)
        }
    }
}

/** 分段选择（优先级 P0~P3、视图切换等） */
@Composable
fun <T> Segmented(
    items: List<T>,
    selected: T?,
    label: (T) -> String,
    color: (T) -> Color? = { null },
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Dv.colors
    val shape = RoundedCornerShape(8.dp)
    Row(modifier.clip(shape).background(c.bg).border(1.dp, c.border, shape).padding(3.dp)) {
        items.forEach { item ->
            val on = item == selected
            val col = color(item)
            Box(
                Modifier.height(30.dp).widthIn(min = 40.dp).clip(RoundedCornerShape(6.dp))
                    .then(if (on) Modifier.shadow(1.dp, RoundedCornerShape(6.dp)).background(c.panel) else Modifier)
                    .clickable { onSelect(item) }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(item),
                    color = if (on) (col ?: c.primary) else c.text2,
                    fontSize = 13.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

// ---------- 输入框 ----------

@Composable
fun DvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    mono: Boolean = false,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: (() -> Unit)? = null,
    onFocusLost: (() -> Unit)? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
    background: Color = Dv.colors.panel,
) {
    val c = Dv.colors
    var focused by remember { mutableStateOf(false) }
    var hadFocus by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        minLines = minLines,
        textStyle = TextStyle(
            color = c.text, fontSize = 15.sp, fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            lineHeight = 22.sp,
        ),
        cursorBrush = SolidColor(c.primary),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (password) KeyboardType.Password else keyboardType,
            imeAction = imeAction,
            autoCorrectEnabled = !password && !mono,
        ),
        // 未指定回调时保留系统默认行为（如「完成」收起键盘）
        keyboardActions = if (onImeAction != null) KeyboardActions(onAny = { onImeAction() }) else KeyboardActions.Default,
        modifier = modifier.onFocusChanged {
            focused = it.isFocused
            if (it.isFocused) hadFocus = true
            else if (hadFocus) { hadFocus = false; onFocusLost?.invoke() }
        },
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(shape).background(background)
                    .border(if (focused) 1.5.dp else 1.dp, if (focused) c.primary else c.borderStrong, shape)
                    .padding(start = if (leading != null) 10.dp else 12.dp, end = if (trailing != null) 4.dp else 12.dp),
                verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
            ) {
                if (leading != null) {
                    leading()
                    Spacer(Modifier.width(8.dp))
                }
                Box(Modifier.weight(1f).padding(vertical = 11.dp)) {
                    if (value.isEmpty()) Text(placeholder, color = c.muted, fontSize = 15.sp, maxLines = if (singleLine) 1 else 10)
                    inner()
                }
                if (trailing != null) trailing()
            }
        },
    )
}

/** .search：搜索框 */
@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val c = Dv.colors
    DvTextField(
        value, onValueChange, modifier, placeholder = placeholder, imeAction = ImeAction.Search,
        background = c.bg,
        leading = { DvIconView(DvIcon.Search, c.muted, 16.dp) },
        trailing = if (value.isNotEmpty()) ({ IconBtn(DvIcon.X, { onValueChange("") }, size = 14.dp, box = 32.dp) }) else null,
    )
}

/** 表单项：左侧标签 + 右侧内容（.field） */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Dv.colors.text2, fontSize = 13.sp, modifier = modifier.padding(bottom = 6.dp, start = 2.dp))
}

/** 下拉选择 */
@Composable
fun <T> DvSelect(
    items: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable (T) -> Unit)? = null,
) {
    val c = Dv.colors
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().height(44.dp).clip(shape).background(c.panel).border(1.dp, c.borderStrong, shape)
                .clickable { open = true }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading(selected)
                Spacer(Modifier.width(8.dp))
            }
            Text(label(selected), color = c.text, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            DvIconView(DvIcon.Down, c.muted, 16.dp)
        }
        DropdownMenu(open, onDismissRequest = { open = false }, modifier = Modifier.background(c.panel)) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (leading != null) {
                                leading(item)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(label(item), color = if (item == selected) c.primary else c.text, fontSize = 15.sp)
                        }
                    },
                    onClick = { open = false; onSelect(item) },
                )
            }
        }
    }
}

// ---------- 其他 ----------

@Composable
fun ProgressBar(progress: Float, color: Color = Dv.colors.ok, modifier: Modifier = Modifier) {
    val c = Dv.colors
    Box(modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.border)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(4.dp).clip(RoundedCornerShape(2.dp)).background(color))
    }
}

/** .totp-ring：动态码倒计时圆环，最后 5 秒变红 */
@Composable
fun TotpRing(remaining: Int, period: Int, size: Dp = 18.dp) {
    val c = Dv.colors
    val urgent = remaining <= 5
    Canvas(Modifier.size(size)) {
        val stroke = 2.5.dp.toPx()
        val inset = stroke / 2
        val arcSize = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke)
        val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
        drawArc(c.border, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
        drawArc(
            if (urgent) c.danger else c.primary, -90f, 360f * remaining / period, false, topLeft, arcSize,
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}

@Composable
fun EmptyState(icon: DvIcon, title: String, hint: String, modifier: Modifier = Modifier) {
    val c = Dv.colors
    Column(modifier.fillMaxWidth().padding(vertical = 56.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(c.primaryWeak), contentAlignment = Alignment.Center) {
            DvIconView(icon, c.primary, 26.dp)
        }
        Spacer(Modifier.height(14.dp))
        Text(title, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(hint, color = c.muted, fontSize = 13.sp)
    }
}

@Composable
fun Dot(color: Color, size: Dp = 8.dp) = Box(Modifier.size(size).clip(CircleShape).background(color))

/** 底部轻提示 */
@Composable
fun BoxScope.ToastHost(toast: Toast?, onDismiss: () -> Unit) {
    LaunchedEffect(toast?.id) {
        if (toast != null) {
            delay(2200)
            onDismiss()
        }
    }
    AnimatedVisibility(
        visible = toast != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 150.dp, start = 24.dp, end = 24.dp),
    ) {
        val t = toast ?: return@AnimatedVisibility
        val c = Dv.colors
        Row(
            Modifier.shadow(8.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp))
                .background(if (t.error) c.danger else Color(0xFF1C2028)).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DvIconView(if (t.error) DvIcon.X else DvIcon.Tick, Color.White, 14.dp)
            Spacer(Modifier.width(8.dp))
            Text(t.text, color = Color.White, fontSize = 14.sp)
        }
    }
}

/**
 * 复制到剪贴板。敏感内容（密码、动态码密钥）30 秒后自动清空（剪贴板内容未变时）。
 */
@Composable
fun rememberCopier(): (text: String, label: String, sensitive: Boolean) -> Unit {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    return remember(clipboard) {
        { text, label, sensitive ->
            if (text.isNotEmpty()) {
                clipboard.setText(AnnotatedString(text))
                AppStore.get().show(if (sensitive) "已复制$label，30 秒后清空" else "已复制$label")
                if (sensitive) {
                    scope.launch {
                        delay(30_000)
                        if (clipboard.getText()?.text == text) clipboard.setText(AnnotatedString(""))
                    }
                }
            }
        }
    }
}

@Composable
fun StrikeText(text: String, done: Boolean, fontSize: Int = 15) {
    val c = Dv.colors
    Text(
        text, fontSize = fontSize.sp, lineHeight = (fontSize + 6).sp,
        color = if (done) c.muted else c.text,
        textDecoration = if (done) TextDecoration.LineThrough else null,
    )
}

val CardPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)

@Composable
fun Hairline(modifier: Modifier = Modifier) = Box(modifier.fillMaxWidth().height(1.dp).background(Dv.colors.border))
