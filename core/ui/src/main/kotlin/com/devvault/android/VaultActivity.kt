package com.devvault.android

import com.devvault.core.ui.R

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.res.Configuration
import android.os.Bundle
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.devvault.AppStore
import com.devvault.model.Scope
import com.devvault.platform.DeviceAuth
import com.devvault.platform.Platform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Feature modules depend on this host contract, never on the application module. */
abstract class VaultActivity : AppCompatActivity() {
    lateinit var store: AppStore
        private set
    private val dialogs = mutableSetOf<Dialog>()

    /** 打开中的全屏页面（账号编辑、待办详情），后打开的在末尾 */
    internal val pages = mutableListOf<NativeForm>()

    /** 宽屏时的右侧详情栏：全屏页面嵌入这里而不是弹出新窗口；窄屏为 null */
    open val paneHost: ViewGroup? get() = null

    /** 详情栏中有页面时，返回键先关闭最上层页面 */
    private val closePane = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() { pages.lastOrNull { it.inline }?.dismiss() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Platform.init(this); super.onCreate(savedInstanceState); DeviceAuth.attach(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE); store = AppStore.get()
        // 沉浸式：内容绘制到状态栏、导航栏下方，由各布局自行处理 insets
        WindowCompat.setDecorFitsSystemWindows(window, false)
        applySystemBarAppearance(window)
        onBackPressedDispatcher.addCallback(this, closePane)
    }
    abstract fun render()
    fun currentProject() = (store.projectScope.value as? Scope.Project)?.id
    fun track(dialog: Dialog, onDismiss: () -> Unit = {}) { dialogs.add(dialog); dialog.setOnDismissListener { dialogs.remove(dialog); onDismiss() } }
    fun untrack(dialog: Dialog) { dialogs.remove(dialog); dialog.setOnDismissListener(null) }

    /** 锁定时关闭所有可能显示敏感信息的弹窗和页面 */
    fun closeSensitivePages() { dialogs.toList().forEach { it.dismiss() }; pages.toList().forEach { it.dismiss() } }

    /** 关闭详情栏中的页面（在列表中选择了另一项时调用）；窄屏时不影响弹出的页面 */
    fun closePanePages() { pages.filter { it.inline }.asReversed().forEach { it.dismiss() } }

    /** 详情栏出现或消失后，把打开中的全屏页面迁移到新的形态 */
    fun relocatePages() { pages.toList().forEach { it.relocate() } }

    /** 详情栏中的页面增减时调用；子类可据此刷新占位内容 */
    open fun onPagesChanged() { closePane.isEnabled = pages.any { it.inline } }

    /** 系统栏图标颜色随深浅色主题切换（浅色背景用深色图标） */
    fun applySystemBarAppearance(target: Window) {
        val light = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK != Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(target, target.decorView).apply {
            isAppearanceLightStatusBars = light; isAppearanceLightNavigationBars = light
        }
    }

    override fun onDestroy() { closeSensitivePages(); super.onDestroy() }
    fun copy(value: String) {
        val clip = ClipData.newPlainText("DevVault", value)
        clip.description.extras = android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        getSystemService(ClipboardManager::class.java).setPrimaryClip(clip); store.show("已复制")
    }
    fun confirm(title: String, message: String, action: () -> Unit) {
        val dialog = AlertDialog.Builder(this).setTitle(title).setMessage(message).setNegativeButton("取消", null).setPositiveButton("确定") { _, _ -> action() }.create()
        track(dialog); dialog.show()
    }
    fun runAction(button: Button? = null, block: suspend () -> Unit) {
        button?.isEnabled = false
        lifecycleScope.launch {
            try { block() } catch (e: CancellationException) { throw e }
            catch (e: Exception) { store.show(e.message ?: "操作失败", true) }
            finally { button?.isEnabled = true }
        }
    }
}
