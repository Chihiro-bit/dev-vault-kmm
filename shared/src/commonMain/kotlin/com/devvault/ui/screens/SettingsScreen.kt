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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devvault.AppStore
import com.devvault.model.Project
import com.devvault.ui.PROJECT_COLORS
import com.devvault.ui.components.BtnKind
import com.devvault.ui.components.ConfirmDialog
import com.devvault.ui.components.Dot
import com.devvault.ui.components.DvButton
import com.devvault.ui.components.DvTextField
import com.devvault.ui.components.FieldLabel
import com.devvault.ui.components.Hairline
import com.devvault.ui.components.PageHeader
import com.devvault.ui.components.SectionTitle
import com.devvault.ui.components.Segmented
import com.devvault.ui.components.dvCard
import com.devvault.ui.theme.Dv
import com.devvault.ui.theme.DvIcon
import com.devvault.ui.theme.DvIconView
import com.devvault.ui.theme.parseColor
import com.devvault.util.Dates
import kotlinx.coroutines.launch

const val APP_VERSION = "1.0.0"

@Composable
fun SettingsScreen(onEditProject: (Project) -> Unit, onNewProject: () -> Unit) {
    val store = remember { AppStore.get() }
    val projects by store.projects.collectAsState()
    val cloudState by store.cloud.state.collectAsState()
    val c = Dv.colors
    Column(Modifier.fillMaxSize()) {
        PageHeader("设置")
        Hairline()
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            SectionTitle("账号与云同步")
            CloudCard()

            if (cloudState.loggedIn) {
                Spacer(Modifier.height(20.dp))
                SectionTitle("账号安全策略")
                PolicyCard()
            }

            Spacer(Modifier.height(20.dp))
            SectionTitle("安全")
            SecurityCard()

            Spacer(Modifier.height(20.dp))
            SectionTitle("项目") {
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onNewProject).padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DvIconView(DvIcon.Plus, c.primary, 15.dp)
                    Spacer(Modifier.width(4.dp))
                    Text("新建项目", color = c.primary, fontSize = 14.sp)
                }
            }
            Column(Modifier.fillMaxWidth().dvCard()) {
                if (projects.isEmpty()) {
                    Text("还没有项目。项目用于把账号和待办按业务分组。", fontSize = 13.sp, color = c.muted, modifier = Modifier.padding(16.dp))
                }
                projects.forEachIndexed { i, p ->
                    if (i > 0) Hairline()
                    Row(
                        Modifier.fillMaxWidth().clickable { onEditProject(p) }.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Dot(parseColor(p.color), 10.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontSize = 15.sp, color = c.text)
                            if (p.description.isNotBlank()) Text(p.description, fontSize = 12.sp, color = c.muted, maxLines = 1)
                        }
                        Text("${p.entryCount} 账号 · ${p.todoCount} 待办", fontSize = 12.sp, color = c.muted)
                        Spacer(Modifier.width(6.dp))
                        DvIconView(DvIcon.Right, c.muted, 16.dp)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionTitle("关于")
            Column(Modifier.fillMaxWidth().dvCard().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(c.primary),
                        contentAlignment = Alignment.Center,
                    ) { DvIconView(DvIcon.Key, Color.White, 22.dp) }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("DevVault", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.text)
                        Text("开发账号 & 待办助手 · 手机版 $APP_VERSION", fontSize = 12.sp, color = c.muted)
                    }
                }
                Text(
                    "数据默认保存在本机，密码、动态码密钥和敏感字段使用系统密钥库（Android Keystore / iOS 钥匙串）加密。" +
                        "登录云同步后可与电脑端 DevVault 共用同一账号，数据端到端加密，服务器无法查看。",
                    fontSize = 13.sp, color = c.text2, lineHeight = 20.sp,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CloudCard() {
    val store = remember { AppStore.get() }
    val state by store.cloud.state.collectAsState()
    val scope = rememberCoroutineScope()
    val c = Dv.colors
    var confirmLogout by remember { mutableStateOf(false) }
    var changingPassword by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().dvCard().padding(16.dp)) {
        val user = state.user
        if (state.loggedIn && user != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(c.primaryWeak), contentAlignment = Alignment.Center) {
                    Text(user.name.take(1).uppercase(), color = c.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(user.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                    Text("@${user.username} · ${state.serverUrl.removePrefix("https://").removePrefix("http://")}", fontSize = 12.sp, color = c.muted, maxLines = 1)
                }
            }
            Spacer(Modifier.height(14.dp))
            val s = state.sync
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (s.error != null) c.dangerWeak else c.bg)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (s.syncing) {
                    CircularProgressIndicator(Modifier.size(14.dp), color = c.primary, strokeWidth = 2.dp)
                } else {
                    DvIconView(if (s.error != null) DvIcon.X else DvIcon.Cloud, if (s.error != null) c.danger else c.ok, 15.dp)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        s.syncing -> "正在同步…"
                        s.error != null -> s.error
                        s.lastSyncAt != null -> "上次同步 ${Dates.formatIsoShort(s.lastSyncAt)}" + if (s.pending > 0) " · ${s.pending} 项待上传" else ""
                        s.pending > 0 -> "${s.pending} 项待上传"
                        else -> "已开启自动同步"
                    },
                    fontSize = 13.sp, color = if (s.error != null) c.danger else c.text2, modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DvButton("立即同步", { scope.launch { store.sync.syncNow() } }, kind = BtnKind.Primary, icon = DvIcon.Refresh,
                    enabled = !s.syncing, modifier = Modifier.weight(1f))
                DvButton("退出登录", { confirmLogout = true }, icon = DvIcon.Logout, modifier = Modifier.weight(1f))
            }
            Text(
                "修改主密码", color = c.primary, fontSize = 14.sp,
                modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(6.dp)).clickable { changingPassword = true }
                    .padding(vertical = 4.dp, horizontal = 2.dp),
            )
        } else {
            LoginForm(state.serverUrl)
        }
    }

    if (changingPassword) ChangePasswordDialog { changingPassword = false }
    val policy by store.policyState.collectAsState()
    var unsent by remember { mutableStateOf<Int?>(null) }
    if (confirmLogout) {
        ConfirmDialog(
            "退出登录",
            if (policy.wipeOnLogout) "按账号安全策略，退出后将清除本机的账号密码和待办；重新登录后会从云端恢复已同步的数据。"
            else "退出后本机数据保留，但不再与云端同步。",
            confirm = if (policy.wipeOnLogout) "退出并清除" else "退出",
            danger = policy.wipeOnLogout,
            onDismiss = { confirmLogout = false },
        ) {
            scope.launch {
                // 需要清除本机数据时先同步，仍有未上传的修改则再次确认
                val pending = store.prepareLogout()
                if (pending > 0) unsent = pending else store.cloud.logout()
            }
        }
    }
    unsent?.let { n ->
        ConfirmDialog(
            "还有 $n 项修改未能上传",
            "退出后会清除本机数据，这些修改将丢失。请检查网络后重试，或确认直接退出。",
            confirm = "仍然退出",
            onDismiss = { unsent = null },
        ) { scope.launch { store.cloud.logout() } }
    }
}

@Composable
private fun LoginForm(savedServer: String) {
    val store = remember { AppStore.get() }
    val scope = rememberCoroutineScope()
    val c = Dv.colors
    var register by rememberSaveable { mutableStateOf(false) }
    var server by rememberSaveable(savedServer) { mutableStateOf(savedServer) }
    var username by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (busy) return
        if (register && password != confirm) { error = "两次输入的主密码不一致"; return }
        busy = true
        error = null
        scope.launch {
            try {
                if (register) store.cloud.register(server, username, password, name)
                else store.cloud.login(server, username, password)
                store.show(if (register) "注册成功，已开启云同步" else "登录成功，正在同步")
                password = ""
                confirm = ""
            } catch (e: Exception) {
                error = e.message ?: "登录失败"
            } finally {
                busy = false
            }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(c.bg).border(1.dp, c.border, CircleShape), contentAlignment = Alignment.Center) {
            DvIconView(DvIcon.Cloud, c.muted, 18.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("本地模式", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.text)
            Text("登录后与电脑端同步账号密码和待办", fontSize = 12.sp, color = c.muted)
        }
    }
    Spacer(Modifier.height(16.dp))
    Segmented(listOf(false, true), register, label = { if (it) "注册" else "登录" }, onSelect = { register = it; error = null })
    Spacer(Modifier.height(14.dp))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            FieldLabel("服务器地址")
            DvTextField(server, { server = it }, placeholder = "https://vault.example.com", mono = true, keyboardType = KeyboardType.Uri)
        }
        Column {
            FieldLabel("账号")
            DvTextField(username, { username = it }, placeholder = "登录名", keyboardType = KeyboardType.Email)
        }
        if (register) {
            Column {
                FieldLabel("昵称（可选）")
                DvTextField(name, { name = it })
            }
        }
        Column {
            FieldLabel("主密码")
            DvTextField(password, { password = it }, password = true, imeAction = if (register) ImeAction.Next else ImeAction.Done,
                onImeAction = { if (!register) submit() })
        }
        if (register) {
            Column {
                FieldLabel("确认主密码")
                DvTextField(confirm, { confirm = it }, password = true, imeAction = ImeAction.Done, onImeAction = ::submit)
            }
        }
    }
    if (error != null) {
        Text(error!!, color = c.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
    }
    Spacer(Modifier.height(14.dp))
    DvButton(
        if (busy) (if (register) "正在注册…" else "正在登录…") else (if (register) "注册并开启同步" else "登录"),
        ::submit, kind = BtnKind.Primary, enabled = !busy && server.isNotBlank() && username.isNotBlank() && password.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        "数据在本机用主密码派生的密钥加密后才上传，服务器只保存密文。主密码无法找回，请牢记。" +
            if (busy) "\n正在计算密钥，可能需要几秒…" else "",
        fontSize = 12.sp, color = c.muted, lineHeight = 18.sp, modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
private fun SecurityCard() {
    val store = remember { AppStore.get() }
    val enabled by store.lockEnabled.collectAsState()
    val c = Dv.colors
    Row(
        Modifier.fillMaxWidth().dvCard().clickable { store.setLockEnabled(!enabled) }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DvIconView(DvIcon.Lock, c.text2, 18.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("应用锁", fontSize = 15.sp, color = c.text)
            Text("使用指纹 / 面容 / 锁屏密码解锁，离开超过 30 秒需重新验证", fontSize = 12.sp, color = c.muted, lineHeight = 17.sp)
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = enabled, onCheckedChange = { store.setLockEnabled(it) },
            colors = SwitchDefaults.colors(
                checkedTrackColor = c.primary, checkedThumbColor = Color.White,
                uncheckedTrackColor = c.border, uncheckedThumbColor = Color.White, uncheckedBorderColor = c.border,
            ),
        )
    }
}

/** 修改主密码：其他设备需重新登录，已上传的数据无需重新加密 */
@Composable
private fun ChangePasswordDialog(onDismiss: () -> Unit) {
    val store = remember { AppStore.get() }
    val scope = rememberCoroutineScope()
    val c = Dv.colors
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = c.panel,
        title = { Text("修改主密码", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = c.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column { FieldLabel("当前主密码"); DvTextField(current, { current = it }, password = true) }
                Column { FieldLabel("新主密码（至少 8 位）"); DvTextField(next, { next = it }, password = true) }
                Column { FieldLabel("确认新主密码"); DvTextField(confirm, { confirm = it }, password = true) }
                Text("修改后其他设备需要用新密码重新登录。主密码无法找回，请牢记。", fontSize = 12.sp, color = c.muted, lineHeight = 18.sp)
                error?.let { Text(it, fontSize = 13.sp, color = c.danger) }
            }
        },
        confirmButton = {
            TextButton(
                {
                    if (next != confirm) {
                        error = "两次输入的新密码不一致"
                    } else {
                        busy = true
                        error = null
                        scope.launch {
                            try {
                                store.cloud.changePassword(current, next)
                                store.show("主密码已修改")
                                onDismiss()
                            } catch (e: Exception) {
                                error = e.message ?: "修改失败"
                            } finally {
                                busy = false
                            }
                        }
                    }
                },
                enabled = !busy && current.isNotEmpty() && next.isNotEmpty(),
            ) { Text(if (busy) "正在修改…" else "确定") }
        },
        dismissButton = { TextButton({ if (!busy) onDismiss() }) { Text("取消", color = c.text2) } },
    )
}

/** 新建 / 编辑项目（对应桌面端 ProjectModal） */
@Composable
fun ProjectEditDialog(project: Project?, onDismiss: () -> Unit) {
    val store = remember { AppStore.get() }
    val c = Dv.colors
    var name by remember { mutableStateOf(project?.name ?: "") }
    var color by remember { mutableStateOf(project?.color ?: PROJECT_COLORS.first()) }
    var desc by remember { mutableStateOf(project?.description ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.panel,
        title = { Text(if (project == null) "新建项目" else "编辑项目", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = c.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column {
                    FieldLabel("名称")
                    DvTextField(name, { name = it }, placeholder = "如：星云商城")
                }
                Column {
                    FieldLabel("颜色")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PROJECT_COLORS.forEach { hex ->
                            val on = hex.equals(color, ignoreCase = true)
                            Box(
                                Modifier.size(30.dp).clip(CircleShape)
                                    .border(if (on) 2.dp else 0.dp, if (on) c.text else Color.Transparent, CircleShape)
                                    .padding(4.dp).clip(CircleShape).background(parseColor(hex))
                                    .clickable { color = hex },
                            )
                        }
                    }
                }
                Column {
                    FieldLabel("描述（可选）")
                    DvTextField(desc, { desc = it })
                }
                if (project != null) {
                    Text(
                        "删除项目", color = c.danger, fontSize = 14.sp,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { confirmDelete = true }.padding(vertical = 6.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                {
                    store.mutate<Long>(success = if (project == null) "已创建项目" else "已保存", onDone = { onDismiss() }) {
                        saveProject(project?.id, name, color, desc)
                    }
                },
                enabled = name.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onDismiss) { Text("取消", color = c.text2) } },
    )

    if (confirmDelete && project != null) {
        ConfirmDialog(
            "删除项目",
            "确定删除「${project.name}」吗？其下的账号和待办会移入「未分组」，不会丢失。",
            onDismiss = { confirmDelete = false },
        ) {
            store.mutate<Unit>(success = "已删除项目", onDone = { onDismiss() }) { deleteProject(project.id) }
        }
    }
}
