package com.devvault.platform

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import java.lang.ref.WeakReference
import kotlin.coroutines.resume

actual object DeviceAuth {
    private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
    private var activityRef: WeakReference<FragmentActivity>? = null

    /** 由 MainActivity 在 onCreate 中注册（BiometricPrompt 需要 FragmentActivity） */
    fun attach(activity: FragmentActivity) {
        activityRef = WeakReference(activity)
    }

    actual fun isAvailable(): Boolean {
        val activity = activityRef?.get() ?: return false
        return BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
    }

    actual suspend fun authenticate(reason: String): Boolean {
        val activity = activityRef?.get() ?: return false
        return suspendCancellableCoroutine { cont ->
            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (cont.isActive) cont.resume(false)
                    }
                    // onAuthenticationFailed：单次识别失败，系统会继续等待，不结束
                },
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle("解锁 DevVault")
                .setSubtitle(reason)
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build()
            prompt.authenticate(info)
            cont.invokeOnCancellation { prompt.cancelAuthentication() }
        }
    }
}
