package com.devvault.android

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import android.widget.EditText
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.devvault.core.ui.R
import com.devvault.core.ui.databinding.PageFormBinding
import java.time.LocalDate

/**
 * page_form.xml 外壳：标题栏（返回 / 删除 / 保存）+ 可滚动的 content。
 * 表单内容由调用方用自己的 XML 布局 inflate 到 [content]：`FormXxxBinding.inflate(layoutInflater, form.content, true)`。
 * fullScreen 为全屏页面（账号编辑、待办详情），否则为居中弹窗。
 */
class NativeForm(val activity: VaultActivity, title: String, val fullScreen: Boolean = false) {
    val page = PageFormBinding.inflate(activity.layoutInflater)
    val content get() = page.content
    val dialog: Dialog = if (fullScreen) Dialog(activity, R.style.Theme_DevVault) else Dialog(activity)
    private val dismissCallbacks = mutableListOf<() -> Unit>()

    init {
        dialog.setContentView(page.root); page.pageTitle.text = title
        page.back.setImageResource(if (fullScreen) R.drawable.ic_x else R.drawable.ic_back)
        page.back.contentDescription = activity.getString(if (fullScreen) R.string.close else R.string.back)
        page.back.setOnClickListener { dialog.dismiss() }
        page.root.isSaveEnabled = false
    }

    fun onDismiss(block: () -> Unit) { dismissCallbacks += block }
    fun onSave(block: () -> Unit) { page.save.isVisible = true; page.save.setOnClickListener { block() } }
    fun onDelete(description: String, block: () -> Unit) { page.delete.isVisible = true; page.delete.contentDescription = description; page.delete.setOnClickListener { block() } }
    fun dismiss() = dialog.dismiss()

    fun show(): NativeForm {
        activity.track(dialog) { dismissCallbacks.forEach { it() } }; dialog.show()
        dialog.window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            @Suppress("DEPRECATION") setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            if (fullScreen) {
                setLayout(-1, -1); WindowCompat.setDecorFitsSystemWindows(this, false)
                ViewCompat.setOnApplyWindowInsetsListener(page.root) { view, insets ->
                    val e = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
                    view.setPadding(e.left, e.top, e.right, e.bottom); insets
                }
            } else setLayout(activity.cappedWidth(.92f, R.dimen.dialog_max_width), -2)
        }
        return this
    }
}

/** 弹窗宽度：屏幕宽度的 fraction，但不超过 maxRes（大屏上不拉伸） */
fun android.content.Context.cappedWidth(fraction: Float, maxRes: Int): Int =
    minOf((resources.displayMetrics.widthPixels * fraction).toInt(), resources.getDimensionPixelSize(maxRes))

/** 必填校验：为空时在输入框上显示错误 */
fun EditText.requireText(message: String): Boolean = value().isNotBlank().also { if (!it) { error = message; requestFocus() } }

fun VaultActivity.pickDate(value: String, onPick: (String) -> Unit) {
    val date = try { LocalDate.parse(value) } catch (_: Exception) { LocalDate.now() }
    val dialog = android.app.DatePickerDialog(this, { _, y, m, d -> onPick(LocalDate.of(y, m + 1, d).toString()) }, date.year, date.monthValue - 1, date.dayOfMonth)
    dialog.setButton(android.content.DialogInterface.BUTTON_NEUTRAL, getString(R.string.clear)) { _, _ -> onPick("") }
    track(dialog); dialog.show()
}
