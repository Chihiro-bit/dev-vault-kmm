package com.devvault.platform

import com.devvault.crypto.SoftAesGcm
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFTypeRefVar
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.precomposedStringWithCompatibilityMapping
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecRandomDefault
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.UIKit.UIDevice
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual object Platform {
    private var cachedDeviceKey: ByteArray? = null

    actual val deviceName: String
        get() = "DevVault · iOS ${UIDevice.currentDevice.systemVersion} · ${UIDevice.currentDevice.model}".take(128)

    actual val isIos: Boolean = true

    actual fun randomBytes(size: Int): ByteArray {
        val out = ByteArray(size)
        if (size == 0) return out
        val status = out.usePinned { SecRandomCopyBytes(kSecRandomDefault, size.toULong(), it.addressOf(0)) }
        check(status == errSecSuccess) { "无法生成随机数" }
        return out
    }

    actual fun aesGcmSeal(key: ByteArray, iv: ByteArray, plain: ByteArray, aad: ByteArray): ByteArray =
        SoftAesGcm.seal(key, iv, plain, aad)

    actual fun aesGcmOpen(key: ByteArray, iv: ByteArray, sealed: ByteArray, aad: ByteArray): ByteArray =
        SoftAesGcm.open(key, iv, sealed, aad)

    actual fun nfkc(input: String): String = NSString.create(string = input).precomposedStringWithCompatibilityMapping

    private const val SERVICE = "com.devvault.app"
    private const val ACCOUNT = "deviceKey"

    actual fun deviceKey(): ByteArray {
        cachedDeviceKey?.let { return it }
        val key = readKeychain() ?: randomBytes(32).also { writeKeychain(it) }
        cachedDeviceKey = key
        return key
    }

    private fun readKeychain(): ByteArray? = memScoped {
        val query = mapOf<Any?, Any?>(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to SERVICE,
            kSecAttrAccount to ACCOUNT,
            kSecReturnData to true,
            kSecMatchLimit to kSecMatchLimitOne,
        )
        val result = alloc<CFTypeRefVar>()
        @Suppress("UNCHECKED_CAST")
        val cfQuery = CFBridgingRetain(query) as CFDictionaryRef?
        val status = SecItemCopyMatching(cfQuery, result.ptr)
        CFBridgingRelease(cfQuery)
        if (status != errSecSuccess) return@memScoped null
        val data = CFBridgingRelease(result.value) as? NSData ?: return@memScoped null
        data.toByteArray()
    }

    private fun writeKeychain(key: ByteArray) {
        val attrs = mapOf<Any?, Any?>(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to SERVICE,
            kSecAttrAccount to ACCOUNT,
            kSecValueData to key.toNSData(),
            kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        )
        @Suppress("UNCHECKED_CAST")
        val cfAttrs = CFBridgingRetain(attrs) as CFDictionaryRef?
        val status = SecItemAdd(cfAttrs, null)
        CFBridgingRelease(cfAttrs)
        check(status == errSecSuccess) { "无法写入钥匙串（$status）" }
    }

    actual fun databasePath(): String {
        @Suppress("UNCHECKED_CAST")
        val dirs = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true) as List<String>
        val dir = dirs.first()
        NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
        return "$dir/devvault.db"
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val out = ByteArray(size)
    if (size > 0) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData = usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }
