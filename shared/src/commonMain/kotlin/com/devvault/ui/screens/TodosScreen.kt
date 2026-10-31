package com.devvault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devvault.AppStore
import com.devvault.model.Scope
import com.devvault.model.Todo
import com.devvault.model.TodoInput
import com.devvault.model.Repeat
import com.devvault.ui.Navigator
import com.devvault.ui.Overlay
import com.devvault.ui.components.CheckCircle
import com.devvault.ui.components.DueText
import com.devvault.ui.components.DvTextField
import com.devvault.ui.components.EmptyState
import com.devvault.ui.components.Hairline
import com.devvault.ui.components.MetaIcon
import com.devvault.ui.components.PBadge
import com.devvault.ui.components.PageHeader
import com.devvault.ui.components.ProjTag
import com.devvault.ui.components.SearchField
import com.devvault.ui.components.Segmented
import com.devvault.ui.components.StatusPill
import com.devvault.ui.components.dvCard
import com.devvault.ui.matchesKeyword
import com.devvault.ui.scopeLabel
import com.devvault.ui.splitTags
import com.devvault.ui.theme.Dv
import com.devvault.ui.theme.DvIcon
import com.devvault.ui.theme.DvIconView
import com.devvault.ui.theme.parseColor
import com.devvault.ui.theme.weak
import com.devvault.util.Dates

private enum class StatFilter { Active, Top, Today, Overdue, Done }

private enum class GroupBy(val label: String) { Status("按状态"), Priority("按优先级"), Project("按项目"), None("不分组") }

private class Group(val key: String, val name: String, val color: String?, val items: List<Todo>)

@Composable
fun TodosScreen(nav: Navigator, onPickProject: () -> Unit) {
    val store = remember { AppStore.get() }
    val all by store.todos.collectAsState()
    val statuses by store.statuses.collectAsState()
    val priorities by store.priorities.collectAsState()
    val projectScope by store.projectScope.collectAsState()
    var keyword by rememberSaveable { mutableStateOf("") }
    var stat by remember { mutableStateOf(StatFilter.Active) }
    var groupBy by remember { mutableStateOf(GroupBy.Status) }
    val collapsed = remember { mutableStateMapOf<String, Boolean>() }
    val c = Dv.colors
    val today = Dates.today()

    // 列表只展示顶层任务，子任务在详情页中查看
    val top = remember(all, keyword) {
        all.filter { it.parentId == null && matchesKeyword(keyword, it.title, it.notes, it.tags, it.projectName) }
    }
    val topLevel = priorities.firstOrNull()
    val undone = top.filter { !it.done }
    val counts = mapOf(
        StatFilter.Active to undone.size,
        StatFilter.Top to undone.count { topLevel != null && it.priority == topLevel.id },
        StatFilter.Today to undone.count { it.dueDate == today },
        StatFilter.Overdue to undone.count { it.dueDate.isNotEmpty() && it.dueDate < today },
        StatFilter.Done to top.count { it.done },
    )
    val shown = when (stat) {
        StatFilter.Active -> undone
        StatFilter.Top -> undone.filter { topLevel != null && it.priority == topLevel.id }
        StatFilter.Today -> undone.filter { it.dueDate == today }
        StatFilter.Overdue -> undone.filter { it.dueDate.isNotEmpty() && it.dueDate < today }
        StatFilter.Done -> top.filter { it.done }
    }
    val groups: List<Group> = when (groupBy) {
        GroupBy.Status -> statuses.map { s -> Group("s${s.id}", s.name, s.color, shown.filter { it.statusId == s.id }) } +
            listOf(Group("s?", "其他", null, shown.filter { t -> statuses.none { it.id == t.statusId } }))
        GroupBy.Priority -> priorities.map { p -> Group("p${p.id}", "${p.label} ${p.name}", p.color, shown.filter { it.priority == p.id }) } +
            listOf(Group("p?", "未定级", null, shown.filter { t -> priorities.none { it.id == t.priority } }))
        GroupBy.Project -> shown.groupBy { it.projectId }.map { (id, list) ->
            Group("j$id", list.first().projectName ?: "未分组", list.first().projectColor, list)
        }.sortedBy { if (it.key == "jnull") 1 else 0 }
        GroupBy.None -> listOf(Group("all", "", null, shown))
    }.filter { it.items.isNotEmpty() }

    Column(Modifier.fillMaxSize()) {
        PageHeader("待办事项", scopeLabel(), onSubtitleClick = onPickProject)
        Column(Modifier.background(c.panel).padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
            SearchField(keyword, { keyword = it }, "搜索待办...")
        }
        Hairline()
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            // 统计卡片（.stat），点击筛选
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item { StatCard(counts.getValue(StatFilter.Active), "进行中", c.primary, stat == StatFilter.Active) { stat = StatFilter.Active } }
                    item {
                        StatCard(counts.getValue(StatFilter.Top), topLevel?.let { "${it.label} ${it.name}" } ?: "最高优先级",
                            parseColor(topLevel?.color, c.danger), stat == StatFilter.Top) { stat = StatFilter.Top }
                    }
                    item { StatCard(counts.getValue(StatFilter.Today), "今天到期", Color(0xFFE38B00), stat == StatFilter.Today) { stat = StatFilter.Today } }
                    item { StatCard(counts.getValue(StatFilter.Overdue), "已逾期", Color(0xFFC2410C), stat == StatFilter.Overdue) { stat = StatFilter.Overdue } }
                    item { StatCard(counts.getValue(StatFilter.Done), "已完成", c.ok, stat == StatFilter.Done) { stat = StatFilter.Done } }
                }
            }
            item { QuickAdd(projectScope) }
            item {
                Row(Modifier.padding(horizontal = 16.dp).padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("分组", fontSize = 13.sp, color = c.muted)
                    Spacer(Modifier.width(6.dp))
                    GroupByMenu(groupBy) { groupBy = it }
                    Spacer(Modifier.weight(1f))
                    if (stat == StatFilter.Done && shown.isNotEmpty()) {
                        Text(
                            "清理已完成", fontSize = 13.sp, color = c.danger,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable {
                                store.mutate(onDone = { n: Long -> store.show("已清理 $n 项") }) { clearDoneTodos(projectScope) }
                            }.padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            if (groups.isEmpty()) {
                item {
                    EmptyState(
                        DvIcon.Check,
                        if (all.isEmpty()) "还没有待办" else "这里空空如也",
                        if (all.isEmpty()) "在上方输入框中添加第一条待办" else "换个筛选条件看看",
                    )
                }
            }
            groups.forEach { g ->
                val isCollapsed = collapsed[g.key] == true
                if (groupBy != GroupBy.None) {
                    item(key = "h-${g.key}") {
                        Row(
                            Modifier.fillMaxWidth().clickable { collapsed[g.key] = !isCollapsed }
                                .padding(start = 14.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DvIconView(DvIcon.Down, c.muted, 16.dp, Modifier.rotate(if (isCollapsed) -90f else 0f))
                            Spacer(Modifier.width(8.dp))
                            StatusPill(g.name, g.color)
                            Spacer(Modifier.width(8.dp))
                            Text(g.items.size.toString(), fontSize = 13.sp, color = c.muted)
                        }
                    }
                }
                if (!isCollapsed) {
                    item(key = "g-${g.key}") {
                        Column(Modifier.padding(horizontal = 16.dp).padding(top = if (groupBy == GroupBy.None) 10.dp else 0.dp).dvCard()) {
                            g.items.forEachIndexed { i, t ->
                                if (i > 0) Hairline()
                                TodoRow(
                                    t,
                                    showStatus = groupBy != GroupBy.Status,
                                    showProject = projectScope == Scope.All && groupBy != GroupBy.Project,
                                    onOpen = { nav.push(Overlay.TodoDetail(t.id)) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** .stat：数字 + 说明的统计卡片，选中时边框与外发光为该卡片的色调 */
@Composable
private fun StatCard(value: Int, label: String, tone: Color, on: Boolean, onClick: () -> Unit) {
    val c = Dv.colors
    val shape = RoundedCornerShape(Dv.radius)
    Column(
        Modifier.widthIn(min = 112.dp)
            .then(if (on) Modifier.border(4.dp, tone.weak(0.14f), RoundedCornerShape(Dv.radius + 3.dp)).padding(3.dp) else Modifier.padding(3.dp))
            .shadow(if (c.isDark) 0.dp else 1.dp, shape).clip(shape).background(c.panel)
            .border(1.dp, if (on) tone else c.border, shape)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(value.toString(), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = tone, lineHeight = 30.sp)
        Text(label, fontSize = 12.sp, color = c.muted)
    }
}

@Composable
private fun GroupByMenu(value: GroupBy, onChange: (GroupBy) -> Unit) {
    val c = Dv.colors
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(8.dp)).background(c.panel).border(1.dp, c.border, RoundedCornerShape(8.dp))
                .clickable { open = true }.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(value.label, fontSize = 13.sp, color = c.text2)
            Spacer(Modifier.width(4.dp))
            DvIconView(DvIcon.Down, c.muted, 14.dp)
        }
        DropdownMenu(open, { open = false }, Modifier.background(c.panel)) {
            GroupBy.entries.forEach { g ->
                DropdownMenuItem({ Text(g.label, color = if (g == value) c.primary else c.text) }, { open = false; onChange(g) })
            }
        }
    }
}

/** .quick-add：输入标题 + 选择优先级，回车添加 */
@Composable
private fun QuickAdd(projectScope: Scope) {
    val store = remember { AppStore.get() }
    val priorities by store.priorities.collectAsState()
    var title by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf<Long?>(null) }
    val c = Dv.colors
    val selected = priority ?: priorities.firstOrNull { it.isDefault }?.id ?: priorities.firstOrNull()?.id

    fun add() {
        val t = title.trim()
        if (t.isEmpty()) return
        store.mutate<Long>(onDone = { title = "" }) {
            createTodo(TodoInput(projectId = (projectScope as? Scope.Project)?.id, title = t, priority = selected))
        }
    }

    Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().dvCard().padding(10.dp)) {
        DvTextField(
            title, { title = it }, placeholder = "添加待办，回车保存", imeAction = ImeAction.Done, onImeAction = ::add,
            leading = { DvIconView(DvIcon.Plus, c.muted, 16.dp) },
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Segmented(
                priorities.map { it.id }, selected,
                label = { id -> priorities.first { it.id == id }.label },
                color = { id -> parseColor(priorities.first { it.id == id }.color) },
                onSelect = { priority = it },
            )
            Spacer(Modifier.weight(1f))
            com.devvault.ui.components.DvButton("添加", ::add, kind = com.devvault.ui.components.BtnKind.Primary, small = true, enabled = title.isNotBlank())
        }
    }
}

/** .todo：列表中的一行待办 */
@Composable
fun TodoRow(t: Todo, showStatus: Boolean, showProject: Boolean, compact: Boolean = false, onOpen: () -> Unit) {
    val store = remember { AppStore.get() }
    val c = Dv.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 4.dp, end = 14.dp, top = 6.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        CheckCircle(t.done, parseColor(t.priorityColor, c.borderStrong), size = if (compact) 18.dp else 20.dp) {
            store.mutate<Unit> { toggleTodo(t.id) }
        }
        Column(Modifier.weight(1f).padding(top = 7.dp)) {
            com.devvault.ui.components.StrikeText(t.title, t.done, if (compact) 14 else 15)
            val tags = splitTags(t.tags)
            val hasMeta = !compact || t.dueDate.isNotEmpty()
            if (hasMeta) {
                FlowRow(
                    Modifier.padding(top = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (!compact) PBadge(t.priorityLabel, t.priorityColor, dim = t.done)
                    if (showStatus && !compact) StatusPill(t.statusName, t.statusColor, Modifier.height(20.dp))
                    DueText(t.dueDate, t.done)
                    if (showProject && !compact) ProjTag(t.projectName, t.projectColor)
                    if (t.subtaskTotal > 0) {
                        MetaIcon(DvIcon.Subtask, "${t.subtaskDone}/${t.subtaskTotal}", if (t.subtaskDone == t.subtaskTotal) c.ok else c.muted)
                    }
                    if (t.commentCount > 0) MetaIcon(DvIcon.Message, t.commentCount.toString())
                    if (t.repeat != Repeat.NONE) DvIconView(DvIcon.Repeat, c.muted, 12.dp, Modifier.padding(top = 2.dp))
                    if (t.remindAt.isNotEmpty() && !t.done) DvIconView(DvIcon.Bell, c.muted, 12.dp, Modifier.padding(top = 2.dp))
                    tags.forEach { Text("#$it", fontSize = 12.sp, color = c.primary) }
                }
            }
            if (!compact && t.notes.isNotBlank()) {
                Text(
                    t.notes.lineSequence().first(), fontSize = 13.sp, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (compact) Box(Modifier.padding(top = 9.dp)) { PBadge(t.priorityLabel, t.priorityColor, dim = t.done) }
    }
}
