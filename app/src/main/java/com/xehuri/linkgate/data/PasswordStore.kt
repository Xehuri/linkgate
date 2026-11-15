package com.xehuri.linkgate.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * 密码库存储：
 * - 整个凭据列表用 **Android Keystore 里的 AES-256-GCM 密钥**加密后存 SharedPreferences；
 * - 明文只在内存里出现，不写任何日志；
 * - 按 origin 精确匹配，跨站不串数据。
 *
 * 说明：这是「设备内本地密码库」，没有主密码/云同步；设备被 root 或 App 数据被拷走时，
 * 密钥在 Keystore 里仍受保护，但能拿到解密后的内存仍需系统级权限。
 */
class PasswordStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun all(): List<PasswordEntry> {
        val blob = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
        val plain = decrypt(blob) ?: return emptyList()
        return PasswordCodec.decode(plain)
    }

    fun find(origin: String): List<PasswordEntry> =
        all().filter { it.origin.equals(origin, ignoreCase = true) }

    fun count(): Int = all().size

    /** 同一 origin + username 覆盖更新，其余保留。 */
    fun save(entry: PasswordEntry) {
        val current = all().toMutableList()
        val index = current.indexOfFirst {
            it.origin.equals(entry.origin, ignoreCase = true) &&
                it.username.equals(entry.username, ignoreCase = true)
        }
        if (index >= 0) current[index] = entry else current.add(entry)
        persist(current)
    }

    fun remove(origin: String, username: String) {
        val next = all().filterNot {
            it.origin.equals(origin, ignoreCase = true) &&
                it.username.equals(username, ignoreCase = true)
        }
        persist(next)
    }

    fun clear() {
        prefs.edit().remove(KEY_ENTRIES).apply()
    }

    private fun persist(entries: List<PasswordEntry>) {
        if (entries.isEmpty()) {
            prefs.edit().remove(KEY_ENTRIES).apply()
            return
        }
        val encrypted = encrypt(PasswordCodec.encode(entries)) ?: run {
            Log.w(TAG, "密码加密失败，未保存")
            return
        }
        prefs.edit().putString(KEY_ENTRIES, encrypted).apply()
    }

    private fun encrypt(plain: String): String? = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val payload = Base64.encodeToString(cipher.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        "$iv:$payload"
    } catch (e: Throwable) {
        Log.w(TAG, "加密失败", e)
        null
    }

    private fun decrypt(blob: String): String? = try {
        val parts = blob.split(":")
        if (parts.size != 2) null else {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val payload = Base64.decode(parts[1], Base64.NO_WRAP)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), javax.crypto.spec.GCMParameterSpec(128, iv))
            String(cipher.doFinal(payload), Charsets.UTF_8)
        }
    } catch (e: Throwable) {
        Log.w(TAG, "解密失败", e)
        null
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val TAG = "LinkGatePassword"
        private const val PREFS_NAME = "linkgate_passwords"
        private const val KEY_ENTRIES = "entries_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "linkgate-password-key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
