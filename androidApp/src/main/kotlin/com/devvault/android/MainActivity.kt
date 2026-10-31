package com.devvault.android

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.devvault.AppStore
import com.devvault.platform.DeviceAuth
import com.devvault.platform.Platform
import com.devvault.ui.App

/** FragmentActivity：应用锁使用的 BiometricPrompt 需要 */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 必须在创建 AppStore 之前注册，启动时才能正确判断是否需要锁定
        Platform.init(this)
        DeviceAuth.attach(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { App() }
    }

    /** 深浅色切换时 Activity 不重建（清单中声明了 uiMode），需重新设置状态栏图标颜色 */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        enableEdgeToEdge()
    }

    override fun onResume() {
        super.onResume()
        AppStore.get().onResume()
    }

    override fun onStop() {
        super.onStop()
        AppStore.get().onBackground()
    }
}
