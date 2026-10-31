package com.devvault.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devvault.AppStore
import com.devvault.model.Entry
import com.devvault.model.Project
import com.devvault.model.Scope
import com.devvault.ui.components.Dot
import com.devvault.ui.components.Hairline
import com.devvault.ui.components.ToastHost
import com.devvault.ui.components.noRippleClick
import com.devvault.ui.screens.EntriesScreen
import com.devvault.ui.screens.EntryEditScreen
import com.devvault.ui.screens.LockScreen
import com.devvault.ui.screens.ProjectEditDialog
import com.devvault.ui.screens.SettingsScreen
import com.devvault.ui.screens.TodoDetailScreen
import com.devvault.ui.screens.TodosScreen
import com.devvault.ui.theme.DevVaultTheme
import com.devvault.ui.theme.Dv
import com.devvault.ui.theme.DvIcon
import com.devvault.ui.theme.DvIconView
import com.devvault.ui.theme.parseColor

enum class Tab(val label: String, val icon: DvIcon) {
    Entries("账号密码", DvIcon.Key), Todos("待办事项", DvIcon.Check), Settings("设置", DvIcon.Gear)
}

/** 覆盖在主界面上的全屏页面 */
sealed interface Overlay {
    /** entry 为 null 时新建；copy 为 true 时复制为新记录 */
    data class EditEntry(val entry: Entry?, val copy: Boolean = false) : Overlay
    data class TodoDetail(val id: Long) : Overlay
}

class Navigator {
    val stack = mutableStateListOf<Overlay>()
    fun push(o: Overlay) { stack.add(o) }
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
}

@Composable
fun App() {
    DevVaultTheme {
        val store = remember { AppStore.get() }
        val nav = remember { Navigator() }
        var tab by remember { mutableStateOf(Tab.Entries) }
        var projectSheet by remember { mutableStateOf(false) }
        var editingProject by remember { mutableStateOf<Project?>(null) }
        var creatingProject by remember { mutableStateOf(false) }
        val toast by store.toast.collectAsState()
        val locked by store.locked.collectAsState()
        val stats by store.stats.collectAsState()
        val c = Dv.colors

        PlatformBackHandler(enabled = nav.stack.isNotEmpty()) { nav.pop() }

        // 打开 / 关闭全屏页面时清除焦点，避免键盘输入落到被遮住的页面上
        val focusManager = LocalFocusManager.current
        LaunchedEffect(nav.stack.size) { focusManager.clearFocus(force = true) }

        Box(Modifier.fillMaxSize().background(c.bg)) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
                    when (tab) {
                        Tab.Entries -> EntriesScreen(nav, onPickProject = { projectSheet = true })
                        Tab.Todos -> TodosScreen(nav, onPickProject = { projectSheet = true })
                        Tab.Settings -> SettingsScreen(
                            onEditProject = { editingProject = it },
                            onNewProject = { creatingProject = true },
                        )
                    }
                }
                BottomBar(tab, todoBadge = stats.todos) { tab = it }
            }

            val top = nav.stack.lastOrNull()
            AnimatedContent(
                targetState = top,
                transitionSpec = {
                    val forward = targetState != null && (initialState == null || nav.stack.size > 1)
                    if (forward) (slideInHorizontally { it } + fadeIn()) togetherWith fadeOut()
                    else fadeIn() togetherWith (slideOutHorizontally { it } + fadeOut())
                },
            ) { overlay ->
                if (overlay == null) {
                    Box(Modifier)
                    return@AnimatedContent
                }
                // 拦截所有触摸，防止点击穿透到下层页面
                Box(Modifier.fillMaxSize().noRippleClick {}) {
                when (overlay) {
                    is Overlay.EditEntry -> EntryEditScreen(
                        overlay.entry, overlay.copy,
                        onClose = { nav.pop() },
                        onDuplicate = { nav.pop(); nav.push(Overlay.EditEntry(it, copy = true)) },
                    )
                    is Overlay.TodoDetail -> TodoDetailScreen(overlay.id, nav)
                }
                }
            }

            if (locked) LockScreen()

            ToastHost(toast) { store.toast.value = null }
        }

        if (projectSheet) {
            ProjectSheet(
                onDismiss = { projectSheet = false },
                onNew = { projectSheet = false; creatingProject = true },
            )
        }
        if (creatingProject || editingProject != null) {
            ProjectEditDialog(editingProject, onDismiss = { creatingProject = false; editingProject = null })
        }
    }
}

/** 底部导航，相当于桌面端侧边栏的「账号密码 / 待办事项」 */
@Composable
private fun BottomBar(tab: Tab, todoBadge: Int, onSelect: (Tab) -> Unit) {
    val c = Dv.colors
    Column(Modifier.fillMaxWidth().background(c.panel).windowInsetsPadding(WindowInsets.navigationBars)) {
        Hairline()
        Row(Modifier.fillMaxWidth().height(58.dp)) {
            Tab.entries.forEach { t ->
                val on = t == tab
                Column(
                    Modifier.weight(1f).fillMaxSize().clickable { onSelect(t) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box {
                        DvIconView(t.icon, if (on) c.primary else c.muted, 22.dp)
                        if (t == Tab.Todos && todoBadge > 0) {
                            Box(
                                Modifier.align(Alignment.TopEnd).padding(start = 18.dp)
                                    .clip(RoundedCornerShape(8.dp)).background(c.danger).padding(horizontal = 5.dp),
                            ) {
                                Text(if (todoBadge > 99) "99+" else "$todoBadge", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(t.label, fontSize = 11.sp, color = if (on) c.primary else c.muted, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

/** 项目切换，相当于桌面端侧边栏的「项目」区域 */
@Composable
private fun ProjectSheet(onDismiss: () -> Unit, onNew: () -> Unit) {
    val store = remember { AppStore.get() }
    val projects by store.projects.collectAsState()
    val stats by store.stats.collectAsState()
    val scope by store.projectScope.collectAsState()
    val c = Dv.colors
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.side) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("项目", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.muted, modifier = Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onNew).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DvIconView(DvIcon.Plus, c.primary, 15.dp)
                Spacer(Modifier.width(4.dp))
                Text("新建项目", color = c.primary, fontSize = 14.sp)
            }
        }
        LazyColumn(Modifier.padding(horizontal = 12.dp).padding(bottom = 24.dp)) {
            item {
                NavItem("全部项目", stats.entries + stats.todos, scope == Scope.All, icon = DvIcon.Layers) {
                    store.setScope(Scope.All); onDismiss()
                }
            }
            item {
                NavItem("未分组", stats.noneEntries + stats.noneTodos, scope == Scope.None, icon = DvIcon.Inbox) {
                    store.setScope(Scope.None); onDismiss()
                }
            }
            items(projects, key = { it.id }) { p ->
                NavItem(p.name, p.entryCount + p.todoCount, scope == Scope.Project(p.id), color = parseColor(p.color)) {
                    store.setScope(Scope.Project(p.id)); onDismiss()
                }
            }
        }
    }
}

/** .nav-item：选中时白底、加粗、轻阴影 */
@Composable
private fun NavItem(label: String, count: Int, active: Boolean, icon: DvIcon? = null, color: Color? = null, onClick: () -> Unit) {
    val c = Dv.colors
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).height(48.dp)
            .then(if (active) Modifier.shadow(1.dp, shape).background(c.panel, shape) else Modifier)
            .clip(shape).clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(20.dp), contentAlignment = Alignment.Center) {
            if (icon != null) DvIconView(icon, if (active) c.primary else c.text2, 18.dp)
            else if (color != null) Dot(color, 10.dp)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            label, fontSize = 15.sp, modifier = Modifier.weight(1f),
            color = if (active) c.text else c.text2, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        )
        Text(count.toString(), fontSize = 13.sp, color = c.muted)
    }
}

/** 当前项目范围的显示名称 */
@Composable
fun scopeLabel(): String {
    val store = remember { AppStore.get() }
    val scope by store.projectScope.collectAsState()
    val projects by store.projects.collectAsState()
    return when (val s = scope) {
        Scope.All -> "全部项目"
        Scope.None -> "未分组"
        is Scope.Project -> projects.firstOrNull { it.id == s.id }?.name ?: "全部项目"
    }
}
