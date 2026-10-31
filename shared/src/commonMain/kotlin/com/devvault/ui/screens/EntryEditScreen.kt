package com.devvault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devvault.AppStore
import com.devvault.model.Entry
import com.devvault.model.ExtraField
import com.devvault.model.Scope
import com.devvault.totp.Totp
import com.devvault.ui.CAT_MAP
import com.devvault.ui.CATEGORIES
import com.devvault.ui.ENVS
import com.devvault.ui.components.BtnKind
import com.devvault.ui.components.ConfirmDialog
import com.devvault.ui.components.DatePickerDialogDv
import com.devvault.ui.components.Dot
import com.devvault.ui.components.DvButton
import com.devvault.ui.components.DvSelect
import com.devvault.ui.components.DvTextField
import com.devvault.ui.components.ExpiryTag
import com.devvault.ui.components.FieldLabel
import com.devvault.ui.components.IconBtn
import com.devvault.ui.components.OverlayPage
import com.devvault.ui.components.dvCard
import com.devvault.ui.envColor
import com.devvault.ui.generatePassword
import com.devvault.ui.theme.Dv
import com.devvault.ui.theme.DvIcon
import com.devvault.ui.theme.DvIconView
import com.devvault.ui.theme.parseColor
import com.devvault.ui.theme.weak

@Composable
fun EntryEditScreen(source: Entry?, copy: Boolean, onClose: () -> Unit, onDuplicate: (Entry) -> Unit) {
    val store = remember { AppStore.get() }
    val projects by store.projects.collectAsState()
    val scope by store.projectScope.collectAsState()
    val c = Dv.colors
    val isNew = source == null || copy

    var e by remember(source) {
        mutableStateOf(
            when {
                source == null -> Entry(projectId = (scope as? Scope.Project)?.id)
                copy -> source.copy(id = 0, title = source.title + " 副本", favorite = false)
                else -> source
            },
        )
    }
    var showPwd by remember { mutableStateOf(isNew) }
    var pickExpiry by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val m = CAT_MAP.getValue(e.category)
    var showCode by remember(e.category) { mutableStateOf(m.showCode || e.verifyCode.isNotEmpty()) }
    var showTotp by remember { mutableStateOf(e.totp.isNotEmpty()) }

    fun save() {
        store.mutate(success = if (isNew) "已添加" else "已保存", onDone = { _: Long -> onClose() }) { saveEntry(e) }
    }

    OverlayPage(
        title = if (isNew) "新建记录" else "编辑记录",
        onBack = onClose,
        backIcon = DvIcon.X,
        actions = {
            DvButton("保存", ::save, kind = BtnKind.Primary, small = true, enabled = e.title.isNotBlank(), modifier = Modifier.padding(end = 8.dp))
        },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // 类型
            FormCard {
                FieldLabel("类型")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CATEGORIES.forEach { cat ->
                        val on = cat.key == e.category
                        val shape = RoundedCornerShape(8.dp)
                        Row(
                            Modifier.height(38.dp).clip(shape)
                                .background(if (on) cat.color.weak(0.12f) else c.panel)
                                .border(if (on) 1.5.dp else 1.dp, if (on) cat.color else c.border, shape)
                                .clickable { e = e.copy(category = cat.key) }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DvIconView(cat.icon, if (on) cat.color else c.text2, 16.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(cat.label, fontSize = 14.sp, color = if (on) cat.color else c.text2, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }
            }

            FormCard {
                Field("名称") { DvTextField(e.title, { e = e.copy(title = it) }, placeholder = "如：商城测试账号") }
                Field("项目") {
                    val items = listOf<Long?>(null) + projects.map { it.id }
                    DvSelect(
                        items, e.projectId,
                        label = { id -> projects.firstOrNull { it.id == id }?.name ?: "未分组" },
                        onSelect = { e = e.copy(projectId = it) },
                        leading = { id ->
                            val p = projects.firstOrNull { it.id == id }
                            if (p != null) Dot(parseColor(p.color)) else DvIconView(DvIcon.Inbox, c.muted, 14.dp)
                        },
                    )
                }
                Field("环境") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        (listOf("") + ENVS).forEach { env ->
                            val on = e.env == env
                            val col = envColor(env) ?: c.text2
                            val shape = RoundedCornerShape(16.dp)
                            Text(
                                env.ifEmpty { "不区分" }, fontSize = 13.sp,
                                color = if (on) col else c.text2,
                                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.clip(shape).background(if (on) col.weak(0.12f) else c.panel)
                                    .border(1.dp, if (on) col else c.border, shape)
                                    .clickable { e = e.copy(env = env) }
                                    .padding(horizontal = 14.dp, vertical = 7.dp),
                            )
                        }
                    }
                }
            }

            FormCard {
                Field(m.urlLabel) {
                    DvTextField(e.url, { e = e.copy(url = it) }, placeholder = m.urlPlaceholder, mono = true, keyboardType = KeyboardType.Uri)
                }
                Field(m.userLabel) { DvTextField(e.username, { e = e.copy(username = it) }, mono = true) }
                Field(m.pwdLabel) {
                    DvTextField(
                        e.password, { e = e.copy(password = it) }, mono = true, password = !showPwd,
                        trailing = {
                            IconBtn(DvIcon.Wand, { e = e.copy(password = generatePassword()); showPwd = true }, size = 16.dp)
                            IconBtn(if (showPwd) DvIcon.EyeOff else DvIcon.Eye, { showPwd = !showPwd }, size = 16.dp)
                        },
                    )
                }
                if (showCode) {
                    Field(m.codeLabel) { DvTextField(e.verifyCode, { e = e.copy(verifyCode = it) }, mono = true, placeholder = "如：123456") }
                }
                if (showTotp) {
                    Field("动态码密钥（TOTP）") {
                        DvTextField(e.totp, { e = e.copy(totp = it) }, mono = true, placeholder = "base32 密钥或 otpauth:// 链接")
                        if (e.totp.isNotBlank()) {
                            val cfg = remember(e.totp) { Totp.parse(e.totp) }
                            Text(
                                if (cfg != null) "当前动态码 ${Totp.format(Totp.code(cfg, com.devvault.util.Dates.nowMs()))}" else "无法识别的密钥",
                                fontSize = 12.sp, color = if (cfg != null) c.ok else c.danger, modifier = Modifier.padding(top = 6.dp, start = 2.dp),
                            )
                        }
                    }
                }
                Field("有效期") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DvButton(e.expiresAt.ifEmpty { "长期有效" }, { pickExpiry = true }, icon = DvIcon.Calendar, small = true)
                        Spacer(Modifier.width(10.dp))
                        ExpiryTag(e.expiresAt)
                    }
                }
                if (!showCode || !showTotp) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!showCode) AddChip("验证码") { showCode = true }
                        if (!showTotp) AddChip("动态码") { showTotp = true }
                    }
                }
            }

            // 自定义字段
            FormCard {
                FieldLabel("自定义字段")
                e.extra.forEachIndexed { i, f ->
                    Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        DvTextField(f.key, { v -> e = e.copy(extra = e.extra.toMutableList().also { it[i] = f.copy(key = v) }) },
                            Modifier.weight(0.38f), placeholder = "名称")
                        Spacer(Modifier.width(6.dp))
                        DvTextField(f.value, { v -> e = e.copy(extra = e.extra.toMutableList().also { it[i] = f.copy(value = v) }) },
                            Modifier.weight(0.62f), placeholder = "值", mono = true, password = f.secret)
                        IconBtn(DvIcon.Lock, { e = e.copy(extra = e.extra.toMutableList().also { it[i] = f.copy(secret = !f.secret) }) },
                            tint = if (f.secret) c.primary else c.muted, size = 16.dp)
                        IconBtn(DvIcon.Trash, { e = e.copy(extra = e.extra.toMutableList().also { it.removeAt(i) }) }, size = 16.dp)
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    m.presets.filter { (k, _) -> e.extra.none { it.key == k } }.forEach { (k, secret) ->
                        AddChip(k) { e = e.copy(extra = e.extra + ExtraField(k, "", secret)) }
                    }
                    AddChip("自定义", solid = true) { e = e.copy(extra = e.extra + ExtraField()) }
                }
                Text("点击锁形图标可标记为敏感字段，加密保存并默认隐藏", fontSize = 12.sp, color = c.muted, modifier = Modifier.padding(top = 8.dp))
            }

            FormCard {
                Field("备注") { DvTextField(e.notes, { e = e.copy(notes = it) }, singleLine = false, minLines = 3) }
                Field("标签") { DvTextField(e.tags, { e = e.copy(tags = it) }, placeholder = "多个标签用空格或逗号分隔") }
            }

            if (!isNew) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DvButton("复制为新记录", { onDuplicate(e) }, icon = DvIcon.Clone, modifier = Modifier.weight(1f))
                    DvButton("删除", { confirmDelete = true }, kind = BtnKind.Danger, icon = DvIcon.Trash, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (pickExpiry) DatePickerDialogDv(e.expiresAt, { pickExpiry = false }, { e = e.copy(expiresAt = it) })
    if (confirmDelete) {
        ConfirmDialog("删除记录", "确定删除「${e.title}」吗？此操作不可撤销。", onDismiss = { confirmDelete = false }) {
            store.mutate<Unit>(success = "已删除", onDone = { onClose() }) { deleteEntry(e.id) }
        }
    }
}

@Composable
fun FormCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().dvCard().padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
}

@Composable
fun Field(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        FieldLabel(label)
        content()
    }
}

/** .chip：虚线边框的「+ 添加」小按钮 */
@Composable
fun AddChip(label: String, solid: Boolean = false, onClick: () -> Unit) {
    val c = Dv.colors
    val shape = RoundedCornerShape(15.dp)
    Row(
        Modifier.height(30.dp).clip(shape)
            .then(
                if (solid) Modifier.border(1.dp, c.borderStrong, shape)
                else Modifier.dashedBorder(c.borderStrong, 15.dp),
            )
            .clickable(onClick = onClick).padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DvIconView(DvIcon.Plus, c.text2, 13.dp)
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 13.sp, color = c.text2)
    }
}

/** CSS 的 1px dashed 边框 */
fun Modifier.dashedBorder(color: Color, radius: Dp): Modifier = this.drawBehind {
    val stroke = Stroke(
        width = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
    )
    drawRoundRect(color, cornerRadius = CornerRadius(radius.toPx()), style = stroke)
}
