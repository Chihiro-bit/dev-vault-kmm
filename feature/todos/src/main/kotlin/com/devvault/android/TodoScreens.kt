package com.devvault.android

import android.app.TimePickerDialog
import android.graphics.Color
import android.graphics.Paint
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.devvault.core.ui.R
import com.devvault.feature.todos.databinding.CardTodoGroupBinding
import com.devvault.feature.todos.databinding.ItemTodoBinding
import com.devvault.feature.todos.databinding.ItemTodoCommentBinding
import com.devvault.feature.todos.databinding.ItemTodoGroupHeaderBinding
import com.devvault.feature.todos.databinding.ItemTodoMetaBinding
import com.devvault.feature.todos.databinding.ItemTodoStatBinding
import com.devvault.feature.todos.databinding.PageTodoDetailBinding
import com.devvault.feature.todos.databinding.ViewTodoCheckBinding
import com.devvault.feature.todos.databinding.ViewTodoHeaderBinding
import com.devvault.model.ProjectChange
import com.devvault.model.Repeat
import com.devvault.model.Scope
import com.devvault.model.Todo
import com.devvault.model.TodoComment
import com.devvault.model.TodoInput
import com.devvault.model.TodoPatch
import com.devvault.util.Dates

/** 待办：列表（view_todo_header + item_todo）与详情页（page_todo_detail.xml） */
class TodoScreens(private val a: VaultActivity) {
    private val store get() = a.store
    private var stat = 0
    private var groupBy = 0
    private var priority: Long? = null
    private val collapsed = mutableSetOf<String>()
    /** 顶部区域缓存：重绘列表时保留快速添加输入框中的内容 */
    private var header: ViewTodoHeaderBinding? = null

    private data class Group(val key: String, val name: String, val color: String?, val items: List<Todo>)

    fun rows(query: String): List<View> {
        val all = store.todos.value
        val top = all.filter { it.parentId == null && matchesKeyword(query, it.title, it.notes, it.tags, it.projectName) }
        val undone = top.filter { !it.done }
        val highest = store.priorities.value.firstOrNull()
        val today = Dates.today()
        val groups = listOf(
            undone,
            undone.filter { it.priority == highest?.id },
            undone.filter { it.dueDate == today },
            undone.filter { it.dueDate.isNotEmpty() && it.dueDate < today },
            top.filter { it.done },
        )
        val shown = groups[stat]
        val views = mutableListOf<View>(header(groups.map { it.size }, highest?.let { "${it.label} ${it.name}" }, highest?.color, shown.isNotEmpty()))

        val grouped = when (groupBy) {
            0 -> store.statuses.value.map { s -> Group("s${s.id}", s.name, s.color, shown.filter { it.statusId == s.id }) } +
                Group("s?", "其他", null, shown.filter { t -> store.statuses.value.none { it.id == t.statusId } })
            1 -> store.priorities.value.map { p -> Group("p${p.id}", "${p.label} ${p.name}", p.color, shown.filter { it.priority == p.id }) } +
                Group("p?", "未定级", null, shown.filter { t -> store.priorities.value.none { it.id == t.priority } })
            2 -> shown.groupBy { it.projectId }.map { (id, list) -> Group("j$id", list.first().projectName ?: "未分组", list.first().projectColor, list) }.sortedBy { it.key == "jnull" }
            else -> listOf(Group("all", "", null, shown))
        }.filter { it.items.isNotEmpty() }

        if (grouped.isEmpty()) views += a.emptyView(
            DvIcon.Check,
            if (all.isEmpty()) "还没有待办" else "这里空空如也",
            if (all.isEmpty()) "在上方输入框中添加第一条待办" else "换个筛选条件看看",
        )
        grouped.forEach { group ->
            if (groupBy != 3) views += groupHeader(group)
            if (group.key !in collapsed) {
                val card = CardTodoGroupBinding.inflate(a.layoutInflater).root
                group.items.forEach { t -> card.addView(todoRow(card, t, showStatus = groupBy != 0, showProject = store.projectScope.value == Scope.All && groupBy != 2) { detail(t.id) }) }
                views += card
            }
        }
        return views
    }

    private fun header(counts: List<Int>, topName: String?, topColor: String?, hasShown: Boolean): View = with(a) {
        val h = header ?: createHeader().also { header = it }
        val names = listOf("进行中", topName ?: "最高优先级", "今天到期", "已逾期", "已完成")
        val tones = listOf(color(R.color.primary), parseColor(topColor, color(R.color.danger)), color(R.color.warn), color(R.color.todo_overdue), color(R.color.ok))
        listOf(h.statActive, h.statTop, h.statToday, h.statOverdue, h.statDone).forEachIndexed { i, s -> bindStat(s, i, counts[i], names[i], tones[i]) }
        h.clearDone.isVisible = stat == 4 && hasShown
        drawQuickPriorities(h)
        h.root
    }

    private fun createHeader(): ViewTodoHeaderBinding = with(a) {
        val h = ViewTodoHeaderBinding.inflate(layoutInflater)
        h.groupBy.bindChoice(listOf(0, 1, 2, 3), groupBy, { listOf("按状态", "按优先级", "按项目", "不分组")[it] }) { groupBy = it; render() }
        h.clearDone.setOnClickListener { store.mutate<Long>(onDone = { store.show("已清理 $it 项") }) { clearDoneTodos(store.projectScope.value) } }
        fun add() {
            val value = h.quickTitle.value().trim(); if (value.isEmpty()) return
            val selected = priority ?: store.priorities.value.firstOrNull { it.isDefault }?.id
            store.mutate<Long>(onDone = { h.quickTitle.setText("") }) { createTodo(TodoInput(projectId = currentProject(), title = value, priority = selected)) }
        }
        h.quickAdd.setOnClickListener { add() }
        h.quickTitle.doAfterTextChanged { h.quickAdd.isEnabled = h.quickTitle.value().isNotBlank() }
        h.quickTitle.setOnEditorActionListener { _, action, event ->
            if (action == EditorInfo.IME_ACTION_DONE || event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) { add(); true } else false
        }
        h
    }

    private fun bindStat(s: ItemTodoStatBinding, index: Int, count: Int, name: String, tone: Int) = with(a) {
        val selected = stat == index
        s.root.background = if (selected) shape(weak(tone, .14f), 13) else null
        s.statCard.background = shape(color(R.color.panel), 10, if (selected) tone else color(R.color.border))
        s.statCount.text = count.toString(); s.statCount.setTextColor(tone)
        s.statName.text = name
        s.root.isSelected = selected
        s.root.setOnClickListener { stat = index; render() }
    }

    private fun drawQuickPriorities(h: ViewTodoHeaderBinding) {
        val selected = priority ?: store.priorities.value.firstOrNull { it.isDefault }?.id ?: store.priorities.value.firstOrNull()?.id
        h.quickPriorities.removeAllViews()
        store.priorities.value.forEach { p ->
            h.quickPriorities.addPill(p.label, p.id == selected, parseColor(p.color), solid = false) { priority = p.id; drawQuickPriorities(h) }
                .apply { (layoutParams as ViewGroup.MarginLayoutParams).marginEnd = a.dp(4); layoutParams.height = a.dp(30) }
        }
    }

    private fun groupHeader(group: Group): View = with(a) {
        val b = ItemTodoGroupHeaderBinding.inflate(layoutInflater)
        val tone = parseColor(group.color, color(R.color.muted))
        b.groupArrow.setImageResource(if (group.key in collapsed) R.drawable.ic_right else R.drawable.ic_down)
        b.groupName.text = "● ${group.name}"; b.groupName.setTextColor(tone); b.groupName.background = shape(weak(tone), 10)
        b.groupCount.text = group.items.size.toString()
        b.root.setOnClickListener { if (!collapsed.remove(group.key)) collapsed.add(group.key); render() }
        b.root
    }

    private fun bindCheck(check: ViewTodoCheckBinding, done: Boolean, tone: Int, action: () -> Unit) = with(a) {
        check.checkBox.background = shape(if (done) tone else Color.TRANSPARENT, 10, tone)
        if (done) check.checkBox.setImageResource(R.drawable.ic_tick) else check.checkBox.setImageDrawable(null)
        check.root.contentDescription = if (done) "标记未完成" else "标记完成"
        check.root.setOnClickListener { action() }
    }

    private fun todoRow(parent: ViewGroup, t: Todo, showStatus: Boolean, showProject: Boolean, onOpen: () -> Unit): View = with(a) {
        val b = ItemTodoBinding.inflate(layoutInflater, parent, false)
        bindCheck(b.check, t.done, parseColor(t.priorityColor, color(R.color.border_strong))) { store.mutate<Unit> { toggleTodo(t.id) } }
        b.todoTitle.text = t.title
        b.todoTitle.setTextColor(color(if (t.done) R.color.muted else R.color.text))
        b.todoTitle.paintFlags = if (t.done) b.todoTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG else b.todoTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        fun meta(value: String?, tone: Int = color(R.color.muted)) {
            if (value.isNullOrBlank()) return
            ItemTodoMetaBinding.inflate(layoutInflater, b.todoMeta, true).root.apply { text = value; setTextColor(tone) }
        }
        meta(t.priorityLabel, parseColor(t.priorityColor))
        if (showStatus) meta(t.statusName, parseColor(t.statusColor))
        Dates.dueInfo(t.dueDate, t.done)?.let { meta(it.text, color(if (it.tone == Dates.DueTone.OVERDUE) R.color.danger else R.color.muted)) }
        if (showProject) meta(t.projectName, parseColor(t.projectColor))
        if (t.subtaskTotal > 0) meta("${t.subtaskDone}/${t.subtaskTotal} 子任务")
        if (t.commentCount > 0) meta("${t.commentCount} 进展")
        if (t.repeat != Repeat.NONE) meta("↻")
        if (t.remindAt.isNotBlank() && !t.done) meta("提醒")
        splitTags(t.tags).forEach { meta("#$it", color(R.color.primary)) }
        b.todoMeta.isVisible = b.todoMeta.childCount > 0
        b.todoNotes.showText(t.notes.lineSequence().firstOrNull { it.isNotBlank() })
        b.root.setOnClickListener { onOpen() }
        b.root
    }

    fun detail(id: Long) {
        store.read(onDone = { snapshot: Triple<Todo?, List<Todo>, List<TodoComment>> ->
            if (a.isFinishing || a.isDestroyed || store.locked.value) return@read
            val (todo, subtasks, comments) = snapshot
            showDetail(todo ?: return@read, subtasks, comments)
        }) { Triple(getTodo(id), listSubtasks(id), listComments(id)) }
    }

    private fun showDetail(t: Todo, subtasks: List<Todo>, comments: List<TodoComment>) = with(a) {
        val id = t.id
        val form = NativeForm(this, t.projectName ?: getString(R.string.unassigned), fullScreen = true)
        form.content.setPadding(0, 0, 0, dp(24))
        val b = PageTodoDetailBinding.inflate(layoutInflater, form.content, true)
        fun patch(p: TodoPatch) { store.mutate<Unit> { patchTodo(id, p) } }
        fun reload() { form.dismiss(); detail(id) }

        form.onDelete("删除待办") {
            confirm("删除待办", "确定删除「${t.title}」及其子任务和进展记录？") { store.mutate("已删除", { form.dismiss() }) { deleteTodo(id) } }
        }
        t.parentId?.let { parentId ->
            b.parent.isVisible = true; b.parent.text = t.parentTitle ?: "父任务"
            b.parent.setOnClickListener { detail(parentId) }
        }
        bindCheck(b.check, t.done, parseColor(t.priorityColor, color(R.color.border_strong))) { store.mutate<Unit>(onDone = { reload() }) { toggleTodo(id) } }
        b.title.setText(t.title)
        b.title.setOnFocusChangeListener { _, focus -> if (!focus && b.title.value().isNotBlank() && b.title.value() != t.title) patch(TodoPatch(title = b.title.value())) }
        b.title.setOnEditorActionListener { v, action, _ -> if (action == EditorInfo.IME_ACTION_DONE) { v.clearFocus(); false } else false }

        var statusId = t.statusId
        fun drawStatus() {
            b.statuses.removeAllViews()
            store.statuses.value.forEach { s -> b.statuses.addPill("● ${s.name}", s.id == statusId, parseColor(s.color), solid = false) { statusId = s.id; patch(TodoPatch(statusId = s.id)); drawStatus() } }
        }
        drawStatus()
        var priorityId = t.priority
        fun drawPriority() {
            b.priorities.removeAllViews()
            store.priorities.value.forEach { p -> b.priorities.addPill(p.label, p.id == priorityId, parseColor(p.color), solid = false) { priorityId = p.id; patch(TodoPatch(priority = p.id)); drawPriority() } }
        }
        drawPriority()

        var start = t.startDate; var due = t.dueDate; var remind = t.remindAt
        fun drawDates() {
            b.startDate.text = start.ifEmpty { "开始" }; b.dueDate.text = due.ifEmpty { "截止" }
            b.remind.text = remind.replace('T', ' ').ifEmpty { "不提醒" }
        }
        drawDates()
        b.startDate.setOnClickListener { pickDate(start) { start = it; drawDates(); patch(TodoPatch(startDate = it)) } }
        b.dueDate.setOnClickListener { pickDate(due) { due = it; drawDates(); patch(TodoPatch(dueDate = it)) } }
        b.remind.setOnClickListener {
            pickDate(remind.take(10)) { day ->
                if (day.isBlank()) { remind = ""; drawDates(); patch(TodoPatch(remindAt = "")); return@pickDate }
                val time = TimePickerDialog(this, { _, h, m -> remind = day + "T%02d:%02d".format(h, m); drawDates(); patch(TodoPatch(remindAt = remind)) }, 9, 0, true)
                track(time); time.show()
            }
        }
        b.repeat.bindChoice(Repeat.entries, t.repeat, { it.label }) { patch(TodoPatch(repeat = it)) }
        b.projectRow.isVisible = t.parentId == null
        if (t.parentId == null) b.project.bindProject(this, t.projectId) { patch(TodoPatch(project = ProjectChange(it))) }
        b.tags.setText(t.tags.replace(',', ' '))
        b.tags.setOnFocusChangeListener { _, focus -> if (!focus) patch(TodoPatch(tags = b.tags.value())) }
        b.notes.setText(t.notes)
        b.notes.setOnFocusChangeListener { _, focus -> if (!focus && b.notes.value() != t.notes) patch(TodoPatch(notes = b.notes.value())) }

        // 子任务
        b.subtasksSection.isVisible = t.parentId == null || subtasks.isNotEmpty()
        b.subtasksTitle.text = "子任务" + if (subtasks.isEmpty()) "" else "  ${t.subtaskDone}/${t.subtaskTotal}"
        b.subtaskProgress.isVisible = subtasks.isNotEmpty()
        b.subtaskProgress.max = t.subtaskTotal.coerceAtLeast(1); b.subtaskProgress.progress = t.subtaskDone
        subtasks.forEach { child ->
            b.subtaskList.addView(todoRow(b.subtaskList, child, showStatus = false, showProject = false) { detail(child.id) })
        }
        b.subtaskList.isVisible = subtasks.isNotEmpty()
        b.subtaskInput.isVisible = t.parentId == null
        b.subtaskInput.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE && b.subtaskInput.value().isNotBlank()) {
                store.mutate<Long>(onDone = { reload() }) { createTodo(TodoInput(projectId = t.projectId, parentId = id, title = b.subtaskInput.value(), priority = t.priority)) }; true
            } else false
        }

        // 进展记录
        b.progressTitle.text = "进展记录" + if (comments.isEmpty()) "" else "  ${comments.size}"
        comments.forEach { cm ->
            val row = ItemTodoCommentBinding.inflate(layoutInflater, b.commentList, true)
            row.commentContent.text = cm.content; row.commentTime.text = cm.createdAt.take(16).replace('T', ' ')
            row.commentDelete.setOnClickListener { store.mutate<Unit>(onDone = { reload() }) { deleteComment(cm.id) } }
        }
        b.sendComment.setOnClickListener {
            if (b.commentInput.value().isNotBlank()) store.mutate<Long>(onDone = { reload() }) { addComment(id, b.commentInput.value()) }
        }
        b.createdInfo.text = "创建于 ${t.createdAt.take(16).replace('T', ' ')}" + (t.doneAt?.let { " · 完成于 ${it.take(16).replace('T', ' ')}" } ?: "")

        form.onDismiss { b.title.clearFocus(); b.tags.clearFocus(); b.notes.clearFocus() }
        form.show()
    }

}
