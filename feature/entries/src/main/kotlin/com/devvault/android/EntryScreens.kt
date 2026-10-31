package com.devvault.android

import android.content.Intent
import android.net.Uri
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.EditText
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.devvault.core.ui.R
import com.devvault.feature.entries.databinding.CardEntryBinding
import com.devvault.feature.entries.databinding.FormEntryBinding
import com.devvault.feature.entries.databinding.ItemEntryFieldBinding
import com.devvault.feature.entries.databinding.ItemEntryTotpBinding
import com.devvault.feature.entries.databinding.ItemExtraFieldBinding
import com.devvault.model.Entry
import com.devvault.model.ExtraField
import com.devvault.totp.Totp
import com.devvault.totp.TotpConfig
import com.devvault.util.Dates

/** 账号密码：列表卡片（card_entry.xml）与编辑页（form_entry.xml） */
class EntryScreens(private val activity: VaultActivity) {
    private val store get() = activity.store

    /** 当前显示明文的敏感字段；锁定时清空 */
    private val visibleSecrets = mutableSetOf<String>()
    fun clearSecrets() {
        visibleSecrets.clear()
    }

    fun card(entry: Entry): View = with(activity) {
        val b = CardEntryBinding.inflate(layoutInflater)
        val m = CAT_MAP.getValue(entry.category)
        b.root.setOnClickListener { edit(entry) }
        b.title.text = entry.title
        b.categoryIcon.setIcon(m.icon, m.color); b.categoryIcon.background = shape(weak(m.color), 9)
        if (entry.env.isNotBlank()) {
            val tone = envColor(entry.env)
            b.env.isVisible = true; b.env.text = entry.env
            b.env.setTextColor(tone ?: color(R.color.text2)); b.env.background =
                shape(weak(tone ?: color(R.color.muted)), 4)
        }
        b.categoryName.text = m.label
        entry.projectName?.let {
            b.projectName.showText("● $it"); b.projectName.setTextColor(
            parseColor(entry.projectColor)
        )
        }
        if (entry.expiresAt.isNotBlank()) b.expiry.showText(if (Dates.daysUntil(entry.expiresAt) < 0) "已过期" else entry.expiresAt)
        b.favorite.setImageResource(if (entry.favorite) R.drawable.ic_star_filled else R.drawable.ic_star)
        b.favorite.tint(color(if (entry.favorite) R.color.star else R.color.border_strong))
        b.favorite.setOnClickListener { store.mutate<Unit> { toggleFavorite(entry.id) } }

        fun field(
            name: String,
            value: String,
            secret: Boolean = false,
            code: Boolean = false,
            key: String = name
        ) {
            if (value.isBlank()) return
            val row = ItemEntryFieldBinding.inflate(layoutInflater, b.fields, true)
            val token = "${entry.id}:$key"
            fun shown() = !secret || token in visibleSecrets
            fun draw() {
                row.fieldValue.text = if (shown()) value else "••••••••"
                row.fieldToggle.setImageResource(if (shown()) R.drawable.ic_eye_off else R.drawable.ic_eye)
            }
            row.fieldName.text = name
            if (code) {
                row.fieldValue.setTextColor(color(R.color.code_text)); row.fieldValue.setBackgroundResource(
                    R.drawable.bg_code
                ); row.fieldValue.setPadding(dp(8), dp(1), dp(8), dp(1))
            }
            row.fieldToggle.isVisible = secret
            row.fieldToggle.contentDescription = "显示或隐藏$name"
            row.fieldToggle.setOnClickListener {
                if (!visibleSecrets.remove(token)) visibleSecrets.add(
                    token
                ); draw()
            }
            row.fieldOpen.isVisible = key == "url" && isHttpUrl(value)
            row.fieldOpen.setOnClickListener {
                runCatching {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(value)
                        )
                    )
                }.onFailure { store.show("无法打开地址", true) }
            }
            row.fieldCopy.contentDescription = "复制$name"
            row.fieldCopy.setOnClickListener { copy(value) }
            row.root.setOnClickListener { copy(value) }
            draw()
        }
        field(m.urlLabel, entry.url, key = "url"); field(
        m.userLabel,
        entry.username
    ); field(m.pwdLabel, entry.password, secret = true)
        field(m.codeLabel, entry.verifyCode, code = true)
        if (entry.totp.isNotBlank()) totpRow(b, Totp.parse(entry.totp))
        entry.extra.forEachIndexed { index, f ->
            field(
                f.key.ifEmpty { "字段" },
                f.value,
                f.secret,
                key = "extra$index"
            )
        }
        b.notes.showText(entry.notes)
        b.tags.showText(splitTags(entry.tags).joinToString("  ") { "#$it" })
        b.root
    }

    private fun totpRow(card: CardEntryBinding, config: TotpConfig?) {
        val row = ItemEntryTotpBinding.inflate(activity.layoutInflater, card.fields, true)
        row.totpRing.max = config?.period ?: 30
        row.totpCopy.setOnClickListener {
            if (config != null) activity.copy(
                Totp.code(
                    config,
                    Dates.nowMs()
                )
            )
        }
        val tick = object : Runnable {
            override fun run() {
                val now = Dates.nowMs()
                row.totpCode.text =
                    if (config == null) "密钥无效" else Totp.format(Totp.code(config, now))
                row.totpRemaining.text =
                    if (config == null) "" else "${Totp.remaining(config, now)}s"
                row.totpRing.progress = config?.let { Totp.remaining(it, now) } ?: 0
                row.root.postDelayed(this, 1000)
            }
        }
        row.root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                v.post(tick)
            }

            override fun onViewDetachedFromWindow(v: View) {
                v.removeCallbacks(tick)
            }
        })
    }

    fun edit(entry: Entry): Unit = with(activity) {
        val form =
            NativeForm(this, if (entry.id == 0L) "新建记录" else "编辑记录", fullScreen = true)
        val b = FormEntryBinding.inflate(layoutInflater, form.content, true)
        var category = entry.category;
        var env = entry.env;
        var expiry = entry.expiresAt

        b.title.setText(entry.title)
        val project = b.project.bindProject(this, entry.projectId)
        fun drawEnvs() {
            b.envs.removeAllViews()
            (listOf("") + ENVS).forEach { v ->
                b.envs.addPill(
                    v.ifEmpty { "不区分" },
                    v == env,
                    envColor(v) ?: color(R.color.text2),
                    solid = false
                ) { env = v; drawEnvs() }
            }
        }
        drawEnvs()

        b.url.setText(entry.url); b.username.setText(entry.username); b.password.setText(entry.password)
        b.generate.setOnClickListener {
            b.password.setText(generatePassword()); b.password.transformationMethod = null
        }
        b.togglePassword.setOnClickListener {
            val hidden = b.password.transformationMethod != null
            b.password.transformationMethod =
                if (hidden) null else PasswordTransformationMethod.getInstance()
            b.togglePassword.setImageResource(if (hidden) R.drawable.ic_eye_off else R.drawable.ic_eye)
            b.password.setSelection(b.password.length())
        }
        b.verifyCode.setText(entry.verifyCode)
        b.totp.setText(entry.totp); b.totpGroup.isVisible = entry.totp.isNotBlank()
        b.totp.doAfterTextChanged {
            b.totpPreview.showText(
                Totp.parse(b.totp.value())
                    ?.let { "当前动态码 ${Totp.format(Totp.code(it, Dates.nowMs()))}" }
                    ?: if (b.totp.value().isBlank()) "" else "无法识别的密钥")
        }
        fun drawExpiry() {
            b.expiry.text = expiry.ifEmpty { "长期有效" }
        }
        drawExpiry(); b.expiry.setOnClickListener { pickDate(expiry) { expiry = it; drawExpiry() } }
        b.addVerify.setOnClickListener {
            b.verifyGroup.isVisible = true; b.verifyCode.requestFocus()
        }
        b.addTotp.setOnClickListener { b.totpGroup.isVisible = true; b.totp.requestFocus() }

        class Draft(val key: EditText, val value: EditText, var secret: Boolean)

        val extras = mutableListOf<Draft>()
        fun addExtra(f: ExtraField) {
            val row = ItemExtraFieldBinding.inflate(layoutInflater, b.extras, true)
            val d = Draft(row.extraKey, row.extraValue, f.secret); extras += d
            row.extraKey.setText(f.key); row.extraValue.setText(f.value)
            fun drawSecret() {
                row.extraValue.transformationMethod =
                    if (d.secret) PasswordTransformationMethod.getInstance() else null
                row.extraSecret.tint(color(if (d.secret) R.color.primary else R.color.muted))
            }
            drawSecret()
            row.extraSecret.setOnClickListener { d.secret = !d.secret; drawSecret() }
            row.extraDelete.setOnClickListener { extras.remove(d); b.extras.removeView(row.root) }
        }
        entry.extra.forEach(::addExtra)

        // 类型决定字段名、占位符、是否显示验证码和预设字段
        fun drawType() {
            b.types.removeAllViews()
            CATEGORIES.forEach { m ->
                b.types.addPill(
                    m.label,
                    category == m.key,
                    m.color,
                    m.icon,
                    solid = false
                ) { category = m.key; drawType() }
            }
            val m = CAT_MAP.getValue(category)
            b.urlLabel.text = m.urlLabel; b.url.hint = m.urlPlaceholder; b.userLabel.text =
                m.userLabel; b.passwordLabel.text = m.pwdLabel
            b.verifyGroup.isVisible = m.showCode || b.verifyCode.value().isNotBlank()
            b.presets.removeAllViews()
            m.presets.forEach { (k, secret) ->
                b.presets.addPill(
                    "+ $k",
                    false,
                    solid = false
                ) { if (extras.none { it.key.value() == k }) addExtra(ExtraField(k, "", secret)) }
            }
            b.presets.addPill("+ 自定义", false, solid = false) { addExtra(ExtraField()) }
        }
        drawType()

        b.notes.setText(entry.notes); b.tags.setText(entry.tags)

        form.onSave {
            if (!b.title.requireText("请输入名称")) return@onSave
            if (b.totp.value().isNotBlank() && Totp.parse(b.totp.value()) == null) {
                b.totp.error = "无效的 TOTP 密钥"; return@onSave
            }
            val updated = entry.copy(
                title = b.title.value(), projectId = project(), category = category, env = env,
                url = b.url.value(), username = b.username.value(), password = b.password.value(),
                verifyCode = b.verifyCode.value(), totp = b.totp.value(), expiresAt = expiry,
                tags = b.tags.value(), notes = b.notes.value(),
                extra = extras.map { ExtraField(it.key.value(), it.value.value(), it.secret) },
            )
            store.mutate("已保存", { form.dismiss() }) { saveEntry(updated) }
        }
        if (entry.id != 0L) {
            b.existingActions.isVisible = true
            b.clone.setOnClickListener {
                form.dismiss(); edit(
                entry.copy(
                    id = 0,
                    title = b.title.value() + " 副本"
                )
            )
            }
            b.delete.setOnClickListener {
                confirm(
                    "删除记录",
                    "确定删除「${entry.title}」吗？此操作不可撤销。"
                ) { store.mutate("已删除", { form.dismiss() }) { deleteEntry(entry.id) } }
            }
        }
        form.show(); Unit
    }
}
