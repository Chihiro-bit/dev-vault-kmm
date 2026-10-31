package com.devvault.platform

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import java.text.Normalizer
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@SuppressLint("StaticFieldLeak")
actual object Platform {
    private lateinit var context: Context
    private val random = SecureRandom()
    private var cachedDeviceKey: ByteArray? = null

    /** 在 Activity 创建时调用 */
    fun init(context: Context) {
        this.context = context.applicationContext
    }

    actual val deviceName: String
        get() = "DevVault · Android ${Build.VERSION.RELEASE} · ${Build.MODEL}".take(128)

    actual val isIos: Boolean = false

    actual fun randomBytes(size: Int): ByteArray = ByteArray(size).also { random.nextBytes(it) }

    actual fun aesGcmSeal(key: ByteArray, iv: ByteArray, plain: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(aad)
        return cipher.doFinal(plain)
    }

    actual fun aesGcmOpen(key: ByteArray, iv: ByteArray, sealed: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(aad)
        return cipher.doFinal(sealed)
    }

    actual fun nfkc(input: String): String = Normalizer.normalize(input, Normalizer.Form.NFKC)

    private const val KEY_ALIAS = "devvault.device"
    private const val PREFS = "devvault.secure"
    private const val PREF_KEY = "deviceKey"

    /** 随机生成 32 字节数据密钥，用 Keystore 中不可导出的 AES 密钥包裹后存入 SharedPreferences */
    @Synchronized
    actual fun deviceKey(): ByteArray {
        cachedDeviceKey?.let { return it }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val wrapper = keystoreKey()
        val stored = prefs.getString(PREF_KEY, null)
        val key = if (stored != null) {
            val raw = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, wrapper, GCMParameterSpec(128, raw.copyOfRange(0, 12)))
            cipher.doFinal(raw.copyOfRange(12, raw.size))
        } else {
            val fresh = randomBytes(32)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, wrapper)
            val wrapped = cipher.iv + cipher.doFinal(fresh)
            prefs.edit().putString(PREF_KEY, Base64.encodeToString(wrapped, Base64.NO_WRAP)).commit()
            fresh
        }
        cachedDeviceKey = key
        return key
    }

    private fun keystoreKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    actual fun databasePath(): String =
        context.getDatabasePath("devvault.db").also { it.parentFile?.mkdirs() }.absolutePath
}
