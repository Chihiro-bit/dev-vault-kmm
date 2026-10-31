package com.devvault.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devvault.ui.theme.Dv
import com.devvault.ui.theme.DvIcon
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/** 全屏页面：顶部栏（返回 + 标题 + 操作）+ 可滚动内容，自动避开状态栏与键盘 */
@Composable
fun OverlayPage(
    title: String,
    onBack: () -> Unit,
    backIcon: DvIcon = DvIcon.Back,
    titleContent: (@Composable RowScope.() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Dv.colors
    Column(Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
        Row(
            Modifier.fillMaxWidth().background(c.panel).height(56.dp).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBtn(backIcon, onBack, tint = c.text2, size = 20.dp, box = 44.dp)
            Row(Modifier.weight(1f).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (titleContent != null) titleContent()
                else Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            actions()
        }
        Hairline()
        Box(Modifier.weight(1f)) {
            Column(
                Modifier.fillMaxSize().then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier),
                content = content,
            )
        }
    }
}

private fun dateToMillis(date: String): Long? = try {
    LocalDate.parse(date).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
} catch (e: Exception) {
    null
}

private fun millisToDate(ms: Long): String = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.UTC).date.toString()

/** 日期选择对话框（YYYY-MM-DD），可清除 */
@Composable
fun DatePickerDialogDv(value: String, onDismiss: () -> Unit, onPick: (String) -> Unit, allowClear: Boolean = true) {
    val state = rememberDatePickerState(initialSelectedDateMillis = dateToMillis(value) ?: dateToMillis(com.devvault.util.Dates.today()))
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton({ state.selectedDateMillis?.let { onPick(millisToDate(it)) }; onDismiss() }) { Text("确定") }
        },
        dismissButton = {
            Row {
                if (allowClear) TextButton({ onPick(""); onDismiss() }) { Text("清除", color = Dv.colors.danger) }
                TextButton(onDismiss) { Text("取消") }
            }
        },
    ) {
        DatePicker(
            state, showModeToggle = false,
            title = { Text("选择日期", modifier = Modifier.padding(start = 24.dp, top = 16.dp), color = Dv.colors.text2) },
        )
    }
}

/** 时间选择对话框（HH:mm） */
@Composable
fun TimePickerDialogDv(value: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val h = value.substringBefore(':').toIntOrNull() ?: 9
    val m = value.substringAfter(':', "").toIntOrNull() ?: 0
    val state = rememberTimePickerState(h, m, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Dv.colors.panel,
        confirmButton = {
            TextButton({
                onPick("${state.hour.toString().padStart(2, '0')}:${state.minute.toString().padStart(2, '0')}")
                onDismiss()
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onDismiss) { Text("取消") } },
        text = { TimePicker(state) },
    )
}

/** 确认对话框（删除等不可撤销操作） */
@Composable
fun ConfirmDialog(title: String, message: String, confirm: String = "删除", danger: Boolean = true, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val c = Dv.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.panel,
        title = { Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = c.text) },
        text = { Text(message, fontSize = 14.sp, color = c.text2) },
        confirmButton = { TextButton({ onDismiss(); onConfirm() }) { Text(confirm, color = if (danger) c.danger else c.primary) } },
        dismissButton = { TextButton(onDismiss) { Text("取消", color = c.text2) } },
    )
}
