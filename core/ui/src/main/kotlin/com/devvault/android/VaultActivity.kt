package com.devvault.android

import com.devvault.core.ui.R

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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
    override fun onCreate(savedInstanceState: Bundle?) {
        Platform.init(this); super.onCreate(savedInstanceState); DeviceAuth.attach(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE); store = AppStore.get()
    }
    abstract fun render()
    fun currentProject() = (store.projectScope.value as? Scope.Project)?.id
    fun track(dialog: Dialog, onDismiss: () -> Unit = {}) { dialogs.add(dialog); dialog.setOnDismissListener { dialogs.remove(dialog); onDismiss() } }
    fun closeSensitivePages() { dialogs.toList().forEach { it.dismiss() } }
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
