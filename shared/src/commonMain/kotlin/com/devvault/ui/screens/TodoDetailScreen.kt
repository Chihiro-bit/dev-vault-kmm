package com.devvault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devvault.AppStore
import com.devvault.model.ProjectChange
import com.devvault.model.Repeat
import com.devvault.model.Todo
import com.devvault.model.TodoComment
import com.devvault.model.TodoInput
import com.devvault.model.TodoPatch
import com.devvault.ui.Navigator
import com.devvault.ui.Overlay
import com.devvault.ui.components.CheckCircle
import com.devvault.ui.components.ConfirmDialog
import com.devvault.ui.components.DatePickerDialogDv
import com.devvault.ui.components.Dot
import com.devvault.ui.components.DvSelect
import com.devvault.ui.components.DvTextField
import com.devvault.ui.components.Hairline
import com.devvault.ui.components.IconBtn
import com.devvault.ui.components.OverlayPage
import com.devvault.ui.components.ProgressBar
import com.devvault.ui.components.ProjTag
import com.devvault.ui.components.Segmented
import com.devvault.ui.components.TimePickerDialogDv
import com.devvault.ui.theme.Dv
import com.devvault.ui.theme.DvIcon
import com.devvault.ui.theme.DvIconView
import com.devvault.ui.theme.parseColor
import com.devvault.ui.theme.weak
import com.devvault.util.Dates

private class Detail(val todo: Todo?, val subtasks: List<Todo>, val comments: List<TodoComment>)

@Composable
fun TodoDetailScreen(id: Long, nav: Navigator) {
    val store = remember { AppStore.get() }
    val version by store.version.collectAsState()
    val statuses by store.statuses.collectAsState()
    val priorities by store.priorities.collectAsState()
    val projects by store.projects.collectAsState()
    var detail by remember(id) { mutableStateOf<Detail?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val c = Dv.colors

    LaunchedEffect(id, version) {
        store.read({ d: Detail ->
            detail = d
            if (d.todo == null) nav.pop() // 已被删除（例如在其他设备上）
        }) { Detail(getTodo(id), listSubtasks(id), listComments(id)) }
    }

    val t = detail?.todo
    fun patch(p: TodoPatch) = store.mutate<Unit> { patchTodo(id, p) }

    OverlayPage(
        title = "",
        onBack = { nav.pop() },
        titleContent = {
            if (t != null) {
                if (t.projectName != null) ProjTag(t.projectName, t.projectColor, 14)
                else Text("未分组", fontSize = 14.sp, color = c.muted)
            }
        },
        actions = { IconBtn(DvIcon.Trash, { confirmDelete = true }, size = 19.dp, box = 44.dp) },
    ) {
        if (t == null) return@OverlayPage
        val d = detail!!

        Column(Modifier.background(c.panel).padding(horizontal = 16.dp).padding(top = 14.dp, bottom = 18.dp)) {
            if (t.parentId != null) {
                Row(
                    Modifier.clip(RoundedCornerShape(6.dp)).clickable { nav.push(Overlay.TodoDetail(t.parentId)) }
                        .padding(vertical = 4.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DvIconView(DvIcon.Up, c.muted, 14.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(t.parentTitle ?: "父任务", fontSize = 13.sp, color = c.muted)
                }
            }
            // 标题
            Row(verticalAlignment = Alignment.Top) {
                CheckCircle(t.done, parseColor(t.priorityColor, c.borderStrong), size = 24.dp) {
                    store.mutate<Unit> { toggleTodo(id) }
                }
                var title by remember(t.id, t.title) { mutableStateOf(t.title) }
                androidx.compose.foundation.text.BasicTextField(
                    title, { title = it },
                    modifier = Modifier.weight(1f).padding(top = 8.dp, start = 4.dp)
                        .onFocusLost { if (title.isNotBlank() && title != t.title) patch(TodoPatch(title = title)) },
                    textStyle = TextStyle(
                        fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp,
                        color = if (t.done) c.muted else c.text,
                        textDecoration = if (t.done) TextDecoration.LineThrough else null,
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(c.primary),
                )
            }
            Spacer(Modifier.height(14.dp))

            PropRow("状态") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    statuses.forEach { s ->
                        val on = s.id == t.statusId
                        val col = parseColor(s.color)
                        Row(
                            Modifier.height(30.dp).clip(RoundedCornerShape(15.dp))
                                .background(col.weak(if (on) 0.16f else 0.07f))
                                .border(if (on) 1.5.dp else 0.dp, if (on) col else Color.Transparent, RoundedCornerShape(15.dp))
                                .clickable { if (!on) patch(TodoPatch(statusId = s.id)) }
                                .padding(horizontal = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Dot(col, 7.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(s.name, fontSize = 13.sp, color = if (on) col else col.copy(alpha = 0.75f), fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium)
                        }
                    }
                }
            }
            PropRow("优先级") {
                Segmented(
                    priorities.map { it.id }, t.priority,
                    label = { pid -> priorities.first { it.id == pid }.label },
                    color = { pid -> parseColor(priorities.first { it.id == pid }.color) },
                    onSelect = { patch(TodoPatch(priority = it)) },
                )
            }
            var pickStart by remember { mutableStateOf(false) }
            var pickDue by remember { mutableStateOf(false) }
            PropRow("日期") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DateChip(t.startDate.ifEmpty { "开始" }, t.startDate.isEmpty()) { pickStart = true }
                    Text("→", color = c.muted, modifier = Modifier.padding(horizontal = 8.dp))
                    DateChip(t.dueDate.ifEmpty { "截止" }, t.dueDate.isEmpty()) { pickDue = true }
                }
                Dates.dueInfo(t.dueDate, t.done)?.takeIf { it.tone != Dates.DueTone.NORMAL }?.let {
                    Text(
                        it.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = if (it.tone == Dates.DueTone.OVERDUE) c.danger else c.warn,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            if (pickStart) DatePickerDialogDv(t.startDate, { pickStart = false }, { patch(TodoPatch(startDate = it)) })
            if (pickDue) DatePickerDialogDv(t.dueDate, { pickDue = false }, { patch(TodoPatch(dueDate = it)) })

            RemindRow(t) { patch(TodoPatch(remindAt = it)) }

            PropRow("重复") {
                DvSelect(Repeat.entries, t.repeat, label = { it.label }, onSelect = { patch(TodoPatch(repeat = it)) })
            }
            if (t.parentId == null) {
                PropRow("项目") {
                    DvSelect(
                        listOf<Long?>(null) + projects.map { it.id }, t.projectId,
                        label = { pid -> projects.firstOrNull { it.id == pid }?.name ?: "未分组" },
                        onSelect = { patch(TodoPatch(project = ProjectChange(it))) },
                        leading = { pid ->
                            val p = projects.firstOrNull { it.id == pid }
                            if (p != null) Dot(parseColor(p.color)) else DvIconView(DvIcon.Inbox, c.muted, 14.dp)
                        },
                    )
                }
            }
            PropRow("标签") {
                var tags by remember(t.id, t.tags) { mutableStateOf(t.tags.replace(",", " ")) }
                DvTextField(
                    tags, { tags = it }, placeholder = "空格分隔",
                    onFocusLost = { if (tags.trim() != t.tags.replace(",", " ")) patch(TodoPatch(tags = tags)) },
                    imeAction = ImeAction.Done,
                )
            }
        }

        // 描述
        Section("描述") {
            var notes by remember(t.id, t.notes) { mutableStateOf(t.notes) }
            DvTextField(
                notes, { notes = it }, singleLine = false, minLines = 3, placeholder = "添加描述…",
                onFocusLost = { if (notes != t.notes) patch(TodoPatch(notes = notes)) },
            )
        }

        // 子任务
        if (t.parentId == null || d.subtasks.isNotEmpty()) {
            Section("子任务", if (d.subtasks.isNotEmpty()) "${t.subtaskDone}/${t.subtaskTotal}" else null) {
                if (d.subtasks.isNotEmpty()) {
                    ProgressBar(t.subtaskDone.toFloat() / t.subtaskTotal.coerceAtLeast(1), modifier = Modifier.padding(bottom = 6.dp))
                    d.subtasks.forEach { s ->
                        TodoRow(s, showStatus = false, showProject = false, compact = true, onOpen = { nav.push(Overlay.TodoDetail(s.id)) })
                    }
                }
                var sub by remember { mutableStateOf("") }
                fun addSub() {
                    val v = sub.trim()
                    if (v.isEmpty()) return
                    store.mutate<Long>(onDone = { sub = "" }) { createTodo(TodoInput(projectId = t.projectId, parentId = id, title = v, priority = t.priority)) }
                }
                DvTextField(
                    sub, { sub = it }, placeholder = "添加子任务，回车保存", imeAction = ImeAction.Done, onImeAction = ::addSub,
                    leading = { DvIconView(DvIcon.Plus, c.muted, 16.dp) },
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        // 进展记录
        Section("进展记录", if (d.comments.isNotEmpty()) d.comments.size.toString() else null) {
            d.comments.forEach { cm ->
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(c.primary))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(cm.content, fontSize = 14.sp, color = c.text, lineHeight = 21.sp)
                        Text(cm.createdAt.take(16), fontSize = 12.sp, color = c.muted, modifier = Modifier.padding(top = 2.dp))
                    }
                    IconBtn(DvIcon.X, { store.mutate<Unit> { deleteComment(cm.id) } }, size = 13.dp, box = 28.dp)
                }
            }
            var comment by remember { mutableStateOf("") }
            fun send() {
                val v = comment.trim()
                if (v.isEmpty()) return
                store.mutate<Long>(onDone = { comment = "" }) { addComment(id, v) }
            }
            DvTextField(
                comment, { comment = it }, placeholder = "记录进展…", singleLine = false,
                trailing = { IconBtn(DvIcon.Send, ::send, tint = if (comment.isBlank()) c.muted else c.primary, size = 17.dp) },
            )
        }
        Text(
            "创建于 ${t.createdAt.take(16)}" + (t.doneAt?.let { " · 完成于 ${it.take(16)}" } ?: ""),
            fontSize = 12.sp, color = c.muted, modifier = Modifier.padding(16.dp),
        )
    }

    if (confirmDelete && t != null) {
        ConfirmDialog(
            "删除待办",
            if (t.subtaskTotal > 0) "确定删除「${t.title}」及其 ${t.subtaskTotal} 个子任务吗？" else "确定删除「${t.title}」吗？",
            onDismiss = { confirmDelete = false },
        ) {
            nav.pop()
            store.mutate<Unit>(success = "已删除") { deleteTodo(id) }
        }
    }
}

@Composable
private fun PropRow(label: String, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
        Text(label, fontSize = 14.sp, color = Dv.colors.muted, modifier = Modifier.width(64.dp).padding(top = 8.dp))
        Column(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun Section(title: String, badge: String? = null, content: @Composable () -> Unit) {
    val c = Dv.colors
    Hairline()
    Column(Modifier.fillMaxWidth().background(c.panel).padding(16.dp)) {
        Row(Modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.text)
            if (badge != null) {
                Spacer(Modifier.width(8.dp))
                Text(badge, fontSize = 13.sp, color = c.muted)
            }
        }
        content()
    }
}

@Composable
private fun DateChip(text: String, placeholder: Boolean, onClick: () -> Unit) {
    val c = Dv.colors
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier.height(38.dp).clip(shape).background(c.panel).border(1.dp, c.borderStrong, shape)
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DvIconView(DvIcon.Calendar, c.muted, 14.dp)
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 14.sp, color = if (placeholder) c.muted else c.text)
    }
}

/** 提醒：日期 + 时间，另有常用快捷项（与桌面端一致） */
@Composable
private fun RemindRow(t: Todo, onChange: (String) -> Unit) {
    val c = Dv.colors
    var pickDate by remember { mutableStateOf(false) }
    var pendingDate by remember { mutableStateOf<String?>(null) }
    PropRow("提醒") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DateChip(if (t.remindAt.isEmpty()) "不提醒" else t.remindAt.replace('T', ' '), t.remindAt.isEmpty()) { pickDate = true }
            if (t.remindAt.isNotEmpty()) IconBtn(DvIcon.X, { onChange("") }, size = 14.dp)
        }
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val today = Dates.today()
            QuickChip("今天 18:00") { onChange("${today}T18:00") }
            QuickChip("明天 09:00") { onChange("${Dates.addDays(today, 1)}T09:00") }
            if (t.dueDate.isNotEmpty()) QuickChip("截止日 09:00") { onChange("${t.dueDate}T09:00") }
        }
        Text("提醒在电脑端（DevVault 桌面版）弹出通知", fontSize = 11.sp, color = c.muted, modifier = Modifier.padding(top = 6.dp))
    }
    if (pickDate) DatePickerDialogDv(t.remindAt.take(10), { pickDate = false }, { d -> if (d.isEmpty()) onChange("") else pendingDate = d })
    pendingDate?.let { d ->
        TimePickerDialogDv(t.remindAt.drop(11).ifEmpty { "09:00" }, { pendingDate = null }, { time -> onChange("${d}T$time") })
    }
}

@Composable
private fun QuickChip(label: String, onClick: () -> Unit) {
    val c = Dv.colors
    Text(
        label, fontSize = 13.sp, color = c.text2,
        modifier = Modifier.clip(RoundedCornerShape(15.dp)).dashedBorder(c.borderStrong, 15.dp)
            .clickable(onClick = onClick).padding(horizontal = 11.dp, vertical = 6.dp),
    )
}

/** 失去焦点时回调（详情页逐字段保存） */
@Composable
private fun Modifier.onFocusLost(block: () -> Unit): Modifier {
    var had by remember { mutableStateOf(false) }
    return this.onFocusChanged {
        if (it.isFocused) had = true
        else if (had) { had = false; block() }
    }
}
