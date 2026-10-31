package com.devvault

import androidx.compose.ui.window.ComposeUIViewController
import com.devvault.ui.App
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIViewController

/** iOS 入口：由 Swift 端的 ContentView 嵌入 */
fun MainViewController(): UIViewController {
    // 回到前台时检查应用锁并立即同步；进入后台时记录时间
    NSNotificationCenter.defaultCenter.addObserverForName(
        UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue,
    ) { _ -> AppStore.get().onResume() }
    NSNotificationCenter.defaultCenter.addObserverForName(
        UIApplicationDidEnterBackgroundNotification, null, NSOperationQueue.mainQueue,
    ) { _ -> AppStore.get().onBackground() }
    return ComposeUIViewController { App() }
}
