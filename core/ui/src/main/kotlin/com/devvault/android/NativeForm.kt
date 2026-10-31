package com.devvault.android

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.devvault.core.ui.R
import com.devvault.core.ui.databinding.PageFormBinding
import java.time.LocalDate

/**
 * page_form.xml 外壳：标题栏（返回 / 删除 / 保存）+ 可滚动的 content。
 * 表单内容由调用方用自己的 XML 布局 inflate 到 [content]：`FormXxxBinding.inflate(layoutInflater, form.content, true)`。
 *
 * fullScreen 为全屏页面（账号编辑、待办详情），否则为居中弹窗。
 * 全屏页面在宽屏（Activity 提供了 [VaultActivity.paneHost]）上不弹出新窗口，而是嵌入右侧详情栏；
 * 窗口宽度变化时由 [VaultActivity.relocatePages] 在两种形态之间迁移，正在编辑的内容不会丢失。
 */
class NativeForm(val activity: VaultActivity, title: String, val fullScreen: Boolean = false) {
    val page = PageFormBinding.inflate(activity.layoutInflater)
    val content get() = page.content
    private var dialog: Dialog? = null
    private var host: ViewGroup? = null
    private var closed = false
    private val dismissCallbacks = mutableListOf<() -> Unit>()

    /** 当前是否嵌入在详情栏中 */
    val inline get() = host != null

    init {
        page.pageTitle.text = title
        page.back.setImageResource(if (fullScreen) R.drawable.ic_x else R.drawable.ic_back)
        page.back.contentDescription = activity.getString(if (fullScreen) R.string.close else R.string.back)
        page.back.setOnClickListener { dismiss() }
        page.root.isSaveEnabled = false
        // 嵌入详情栏时盖住下层内容，触摸不能穿透
        page.root.isClickable = true
    }

    fun onDismiss(block: () -> Unit) { dismissCallbacks += block }
    fun onSave(block: () -> Unit) { page.save.isVisible = true; page.save.setOnClickListener { block() } }
    fun onDelete(description: String, block: () -> Unit) { page.delete.isVisible = true; page.delete.contentDescription = description; page.delete.setOnClickListener { block() } }

    fun show(): NativeForm {
        if (fullScreen) activity.pages.add(this)
        attach(); return this
    }

    fun dismiss() {
        if (closed) return
        dialog?.let { it.dismiss(); return } // 关闭回调在 dismiss 监听中执行
        detachInline(); finish()
    }

    /** 宽度变化后按当前布局重新选择形态（详情栏 / 全屏弹窗） */
    internal fun relocate() {
        if (closed || !fullScreen) return
        val target = activity.paneHost
        if (if (target == null) dialog != null else host === target) return
        detachInline()
        // 迁移时关闭旧弹窗，不能触发关闭回调（Dialog 的 dismiss 监听是异步执行的，先解除监听）
        dialog?.let { activity.untrack(it); it.dismiss() }; dialog = null
        attach()
    }

    private fun finish() {
        closed = true; activity.pages.remove(this); activity.onPagesChanged()
        dismissCallbacks.forEach { it() }
    }

    private fun attach() {
        (page.root.parent as? ViewGroup)?.removeView(page.root)
        val pane = if (fullScreen) activity.paneHost else null
        if (pane != null) {
            host = pane
            ViewCompat.setOnApplyWindowInsetsListener(page.root, null)
            page.root.setPadding(0, 0, 0, 0); page.toolbar.updateLayoutParams { height = activity.dp(56) }
            page.toolbar.setPadding(page.toolbar.paddingLeft, 0, page.toolbar.paddingRight, 0)
            pane.addView(page.root, ViewGroup.LayoutParams(-1, -1))
            activity.onPagesChanged()
        } else showDialog()
    }

    private fun detachInline() {
        host?.removeView(page.root); host = null
    }

    private fun showDialog() {
        val d = if (fullScreen) Dialog(activity, R.style.Theme_DevVault) else Dialog(activity)
        dialog = d
        d.setContentView(page.root)
        activity.track(d) { if (dialog === d) dialog = null; finish() }
        d.show()
        d.window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            @Suppress("DEPRECATION") setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            if (fullScreen) {
                setLayout(-1, -1); WindowCompat.setDecorFitsSystemWindows(this, false)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                    attributes = attributes.apply { layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES }
                activity.applySystemBarAppearance(this)
                // 沉浸式：标题栏背景延伸到状态栏下方，内容避开导航栏和输入法
                val toolbarHeight = activity.dp(56)
                ViewCompat.setOnApplyWindowInsetsListener(page.root) { view, insets ->
                    val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                    val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
                    view.setPadding(bars.left, 0, bars.right, maxOf(bars.bottom, ime.bottom))
                    page.toolbar.setPadding(page.toolbar.paddingLeft, bars.top, page.toolbar.paddingRight, 0)
                    page.toolbar.updateLayoutParams { height = toolbarHeight + bars.top }
                    insets
                }
                ViewCompat.requestApplyInsets(page.root)
            } else setLayout(activity.cappedWidth(.92f, R.dimen.dialog_max_width), -2)
        }
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
