package com.devvault.android

import android.view.View
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.devvault.core.ui.R
import com.devvault.data.Policy
import com.devvault.feature.settings.databinding.FormPasswordBinding
import com.devvault.feature.settings.databinding.FormPolicyBinding
import com.devvault.feature.settings.databinding.FormProjectBinding
import com.devvault.feature.settings.databinding.ItemColorSwatchBinding
import com.devvault.feature.settings.databinding.ItemProjectBinding
import com.devvault.feature.settings.databinding.PageSettingsBinding
import com.devvault.feature.settings.databinding.ViewLoginBinding
import com.devvault.model.Project
import com.devvault.util.Dates

/** 设置页（page_settings.xml）及其弹窗：项目、修改主密码、安全策略 */
class SettingsScreens(private val activity: VaultActivity) {
    private val store get() = activity.store
    /** 整页缓存：重绘时只更新数据，保留登录表单中正在输入的内容 */
    private var page: PageSettingsBinding? = null
    private var register = false

    /** 锁定或登录成功时清空输入过的主密码 */
    fun clearSecrets() { page?.login?.let { it.password.setText(""); it.confirm.setText("") } }

    fun content(): View = with(activity) {
        val p = page ?: PageSettingsBinding.inflate(layoutInflater).also { page = it; setup(it) }
        val cloud = store.cloud.state.value
        p.login.root.isVisible = !cloud.loggedIn
        p.loggedIn.isVisible = cloud.loggedIn
        p.policySection.isVisible = cloud.loggedIn
        if (cloud.loggedIn) {
            clearSecrets()
            val user = cloud.user
            p.avatar.text = user?.name?.take(1)?.uppercase() ?: "D"
            p.userName.text = user?.name ?: "云同步"
            p.userServer.text = "@${user?.username} · ${cloud.serverUrl.removePrefix("https://").removePrefix("http://")}"
            val sync = cloud.sync
            val last = sync.lastSyncAt
            p.syncStatus.text = sync.error ?: when {
                sync.syncing -> "正在同步…"
                last != null -> "上次同步 ${Dates.formatIsoShort(last)}" + if (sync.pending > 0) " · ${sync.pending} 项待上传" else ""
                else -> "已开启自动同步"
            }
            p.syncStatus.setTextColor(color(if (sync.error == null) R.color.text2 else R.color.danger))
            p.syncNow.isEnabled = !sync.syncing
            val policy = store.policyState.value
            p.allowExport.isChecked = policy.allowExport
            p.wipeOnLogout.isChecked = policy.wipeOnLogout
            p.codeState.text = if (policy.hasCode) "已设置，修改策略时需要输入" else "尚未设置，首次修改策略时设置"
            p.changeCode.isVisible = policy.hasCode
        }
        p.appLock.setOnCheckedChangeListener(null)
        p.appLock.isChecked = store.lockEnabled.value
        p.appLock.setOnCheckedChangeListener { _, on -> store.setLockEnabled(on) }
        bindProjects(p)
        p.root
    }

    private fun setup(p: PageSettingsBinding) {
        setupLogin(p.login)
        p.syncNow.setOnClickListener { activity.runAction(p.syncNow) { store.sync.syncNow() } }
        p.logout.setOnClickListener { logout() }
        p.changePassword.setOnClickListener { password() }
        listOf(p.exportRow, p.wipeRow, p.changeCode).forEach { it.setOnClickListener { policy() } }
        p.newProject.setOnClickListener { project(null) }
    }

    private fun setupLogin(b: ViewLoginBinding) = with(activity) {
        b.server.setText(store.cloud.state.value.serverUrl)
        fun state() { b.submit.isEnabled = b.server.value().isNotBlank() && b.username.value().isNotBlank() && b.password.value().isNotEmpty() }
        listOf(b.server, b.username, b.password).forEach { it.doAfterTextChanged { state() } }
        fun drawTabs() {
            b.tabs.removeAllViews()
            listOf(false, true).forEach { r ->
                b.tabs.addPill(if (r) "注册" else "登录", register == r, solid = false) { register = r; drawTabs() }
                    .apply { (layoutParams as android.view.ViewGroup.MarginLayoutParams).marginEnd = dp(4) }
            }
            b.nameGroup.isVisible = register; b.confirmGroup.isVisible = register
            b.submit.text = if (register) "注册并开启同步" else "登录"
            b.error.isVisible = false
        }
        drawTabs()
        b.submit.setOnClickListener {
            b.error.isVisible = false
            if (register && b.password.value() != b.confirm.value()) { b.error.showText("两次输入的主密码不一致"); return@setOnClickListener }
            runAction(b.submit) {
                if (register) store.cloud.register(b.server.value(), b.username.value(), b.password.value(), b.nickname.value())
                else store.cloud.login(b.server.value(), b.username.value(), b.password.value())
                clearSecrets(); store.show("已开启云同步")
            }
        }
    }

    private fun bindProjects(p: PageSettingsBinding) = with(activity) {
        // 保留第一个子 view（空状态提示），其余为项目行
        while (p.projects.childCount > 1) p.projects.removeViewAt(1)
        p.projectsEmpty.isVisible = store.projects.value.isEmpty()
        store.projects.value.forEach { item ->
            val row = ItemProjectBinding.inflate(layoutInflater, p.projects, true)
            row.projectDot.setTextColor(parseColor(item.color))
            row.projectName.text = item.name
            row.projectDescription.showText(item.description)
            row.projectCounts.text = "${item.entryCount} 账号 · ${item.todoCount} 待办"
            row.root.setOnClickListener { project(item) }
        }
    }

    fun project(project: Project?) = with(activity) {
        val form = NativeForm(this, if (project == null) "新建项目" else "编辑项目")
        val b = FormProjectBinding.inflate(layoutInflater, form.content, true)
        b.name.setText(project?.name ?: "")
        b.color.setText(project?.color ?: PROJECT_COLORS.first())
        b.description.setText(project?.description ?: "")
        fun preview() { b.colorPreview.background = shape(parseColor(b.color.value(), color(R.color.border)), 12) }
        preview(); b.color.doAfterTextChanged { preview() }
        PROJECT_COLORS.forEach { hex ->
            ItemColorSwatchBinding.inflate(layoutInflater, b.palette, true).root.apply {
                background = shape(parseColor(hex), 14); contentDescription = hex
                setOnClickListener { b.color.setText(hex) }
            }
        }
        b.save.setOnClickListener {
            if (!b.name.requireText("请输入项目名称")) return@setOnClickListener
            if (!Regex("#[0-9a-fA-F]{6}").matches(b.color.value())) { b.color.error = "请输入六位十六进制颜色"; return@setOnClickListener }
            store.mutate("已保存", { _: Long -> form.dismiss() }) { saveProject(project?.id, b.name.value(), b.color.value(), b.description.value()) }
        }
        if (project != null) {
            b.delete.isVisible = true
            b.delete.setOnClickListener {
                confirm("删除项目", "删除「${project.name}」后，其下账号和待办会移入未分组。") { store.mutate("已删除", { form.dismiss() }) { deleteProject(project.id) } }
            }
        }
        form.show(); Unit
    }

    private fun password() = with(activity) {
        val form = NativeForm(this, "修改主密码")
        val b = FormPasswordBinding.inflate(layoutInflater, form.content, true)
        b.submit.setOnClickListener {
            if (b.next.value().length < 8 || b.next.value() != b.confirm.value()) { b.confirm.error = "新密码至少 8 位且两次输入一致"; return@setOnClickListener }
            runAction(b.submit) { store.cloud.changePassword(b.current.value(), b.next.value()); form.dismiss(); store.show("主密码已修改") }
        }
        form.show(); Unit
    }

    private fun policy() = with(activity) {
        val policy = store.policyState.value
        val form = NativeForm(this, "账号安全策略")
        val b = FormPolicyBinding.inflate(layoutInflater, form.content, true)
        b.allowExport.isChecked = policy.allowExport
        b.wipeOnLogout.isChecked = policy.wipeOnLogout
        b.currentCodeGroup.isVisible = policy.hasCode
        b.newCodeLabel.text = if (policy.hasCode) "新安全码（留空不更换）" else "设置安全码（无法找回或重置）"
        b.save.setOnClickListener {
            val next = b.newCode.value()
            if ((!policy.hasCode || next.isNotEmpty()) && (next.length < Policy.MIN_CODE_LENGTH || next != b.confirmCode.value())) {
                b.confirmCode.error = "安全码至少 ${Policy.MIN_CODE_LENGTH} 位且两次输入一致"; return@setOnClickListener
            }
            runAction(b.save) {
                store.updatePolicy(b.currentCode.value().takeIf { policy.hasCode }, next.takeIf { it.isNotEmpty() }, b.allowExport.isChecked, b.wipeOnLogout.isChecked)
                form.dismiss(); store.show("安全策略已更新")
            }
        }
        form.show(); Unit
    }

    private fun logout() {
        activity.confirm("退出登录", if (store.policyState.value.wipeOnLogout) "按安全策略，退出后清除本机数据；会先尝试同步。" else "退出后保留本机数据，停止云同步。") {
            activity.runAction {
                val pending = store.prepareLogout()
                if (pending > 0) activity.confirm("还有 $pending 项修改未上传", "继续退出将清除本机数据并丢失这些修改，仍然退出？") { activity.runAction { store.cloud.logout() } }
                else store.cloud.logout()
            }
        }
    }
}
