package com.devvault.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSOperationQueue
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
import kotlin.coroutines.resume

@OptIn(ExperimentalForeignApi::class)
actual object DeviceAuth {
    /** 面容 / 触控 ID，失败时可回退到锁屏密码 */
    actual fun isAvailable(): Boolean = LAContext().canEvaluatePolicy(LAPolicyDeviceOwnerAuthentication, error = null)

    actual suspend fun authenticate(reason: String): Boolean = suspendCancellableCoroutine { cont ->
        LAContext().evaluatePolicy(LAPolicyDeviceOwnerAuthentication, localizedReason = reason) { success, _ ->
            // 回调在后台线程，切回主线程再恢复协程
            NSOperationQueue.mainQueue.addOperationWithBlock {
                if (cont.isActive) cont.resume(success)
            }
        }
    }
}
