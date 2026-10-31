package com.devvault.platform

/** 平台相关能力：Android 与 iOS 各自实现 */
expect object Platform {
    /** 例如 "DevVault · Android 15 · Pixel 8"，用于登录时的设备名 */
    val deviceName: String

    val isIos: Boolean

    fun randomBytes(size: Int): ByteArray

    /** AES-256-GCM 加密，返回 密文 ‖ 16 字节标签 */
    fun aesGcmSeal(key: ByteArray, iv: ByteArray, plain: ByteArray, aad: ByteArray): ByteArray

    /** AES-256-GCM 解密，校验失败抛出异常 */
    fun aesGcmOpen(key: ByteArray, iv: ByteArray, sealed: ByteArray, aad: ByteArray): ByteArray

    /** Unicode NFKC 规范化（与桌面端 password.normalize('NFKC') 一致） */
    fun nfkc(input: String): String

    /** 本机数据加密密钥（32 字节），由 Android Keystore / iOS Keychain 保护，首次调用时生成 */
    fun deviceKey(): ByteArray

    fun databasePath(): String
}
