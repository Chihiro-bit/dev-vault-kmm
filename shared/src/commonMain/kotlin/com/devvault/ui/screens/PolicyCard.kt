package com.devvault.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devvault.AppStore
import com.devvault.data.Policy
import com.devvault.ui.components.DvTextField
import com.devvault.ui.components.FieldLabel
import com.devvault.ui.components.Hairline
import com.devvault.ui.components.dvCard
import com.devvault.ui.theme.Dv
import kotlinx.coroutines.launch

/** 修改策略的草稿；changeCode 为 true 时只更换安全码 */
private data class PolicyDraft(val allowExport: Boolean, val wipeOnLogout: Boolean, val changeCode: Boolean = false)

/** 账号安全策略（对应桌面端「账号安全策略」），对该账号登录的所有设备生效 */
@Composable
fun PolicyCard() {
    val store = remember { AppStore.get() }
    val policy by store.policyState.collectAsState()
    val c = Dv.colors
    var draft by remember { mutableStateOf<PolicyDraft?>(null) }

    Column(Modifier.fillMaxWidth().dvCard()) {
        PolicySwitchRow(
            "允许导出", "关闭后禁止导出文件、加密备份和「复制全部信息」；复制单个字段不受影响",
            policy.allowExport,
        ) { draft = PolicyDraft(it, policy.wipeOnLogout) }
        Hairline()
        PolicySwitchRow(
            "退出登录时清除本机数据", "退出或登录失效时清除本机的账号密码和待办；重新登录后从云端恢复已同步的数据",
            policy.wipeOnLogout,
        ) { draft = PolicyDraft(policy.allowExport, it) }
        Hairline()
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("安全码", fontSize = 15.sp, color = c.text)
                Text(if (policy.hasCode) "已设置，修改策略时需要输入" else "尚未设置，首次修改策略时设置", fontSize = 12.sp, color = c.muted)
            }
            if (policy.hasCode) {
                Text(
                    "更换", color = c.primary, fontSize = 14.sp,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp))
                        .clickable { draft = PolicyDraft(policy.allowExport, policy.wipeOnLogout, changeCode = true) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
    Text(
        "只能约束通过 DevVault 客户端进行的操作，无法阻止同时掌握主密码并能修改客户端的人。",
        fontSize = 12.sp, color = c.muted, lineHeight = 17.sp, modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )

    draft?.let { d -> PolicyDialog(d, policy.hasCode) { draft = null } }
}

@Composable
private fun PolicySwitchRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Dv.colors
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = c.text)
            Text(desc, fontSize = 12.sp, color = c.muted, lineHeight = 17.sp)
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = c.primary, checkedThumbColor = Color.White,
                uncheckedTrackColor = c.border, uncheckedThumbColor = Color.White, uncheckedBorderColor = c.border,
            ),
        )
    }
}

@Composable
private fun PolicyDialog(draft: PolicyDraft, hasCode: Boolean, onDismiss: () -> Unit) {
    val store = remember { AppStore.get() }
    val scope = rememberCoroutineScope()
    val c = Dv.colors
    val needNew = !hasCode || draft.changeCode
    var code by remember { mutableStateOf("") }
    var newCode by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (needNew) {
            if (newCode.length < Policy.MIN_CODE_LENGTH) { error = "安全码至少 ${Policy.MIN_CODE_LENGTH} 位"; return }
            if (newCode != confirm) { error = "两次输入的安全码不一致"; return }
        }
        busy = true
        error = null
        scope.launch {
            try {
                store.updatePolicy(if (hasCode) code else null, if (needNew) newCode else null, draft.allowExport, draft.wipeOnLogout)
                store.show(if (draft.changeCode) "安全码已更换" else "安全策略已更新，将同步到该账号的所有设备")
                onDismiss()
            } catch (e: Exception) {
                error = e.message ?: "修改失败"
            } finally {
                busy = false
            }
        }
    }

    val title = when {
        draft.changeCode -> "更换安全码"
        !hasCode -> "设置安全码"
        else -> "修改安全策略"
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = c.panel,
        title = { Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = c.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!draft.changeCode) {
                    Text(
                        "允许导出：${if (draft.allowExport) "是" else "否"}　退出时清除本机数据：${if (draft.wipeOnLogout) "是" else "否"}",
                        fontSize = 13.sp, color = c.text2,
                    )
                }
                if (hasCode) Column { FieldLabel("当前安全码"); DvTextField(code, { code = it }, password = true) }
                if (needNew) {
                    Column { FieldLabel("新安全码（至少 ${Policy.MIN_CODE_LENGTH} 位）"); DvTextField(newCode, { newCode = it }, password = true) }
                    Column { FieldLabel("再次输入新安全码"); DvTextField(confirm, { confirm = it }, password = true) }
                }
                Text(
                    if (hasCode && !draft.changeCode) "连续输错 5 次需等待 30 秒" else "安全码无法找回，也无法重置。忘记后将无法再修改此策略，只能新建账号并迁移数据。",
                    fontSize = 12.sp, color = if (needNew) c.warn else c.muted, lineHeight = 18.sp,
                )
                error?.let { Text(it, fontSize = 13.sp, color = c.danger) }
            }
        },
        confirmButton = {
            TextButton(::submit, enabled = !busy && (!hasCode || code.isNotEmpty()) && (!needNew || newCode.isNotEmpty())) {
                Text(if (busy) "请稍候…" else "保存")
            }
        },
        dismissButton = { TextButton({ if (!busy) onDismiss() }) { Text("取消", color = c.text2) } },
    )
}
