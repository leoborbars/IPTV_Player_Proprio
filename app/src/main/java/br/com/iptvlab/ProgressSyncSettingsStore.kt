package br.com.iptvlab

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class ProgressSyncSettings(val endpoint: String, val token: String)

/** Encrypts the NAS endpoint and shared token with a device-bound Android Keystore key. */
class ProgressSyncSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("progress_sync_settings", Context.MODE_PRIVATE)

    fun load(): ProgressSyncSettings? {
        val defaultEndpoint = BuildConfig.DEFAULT_PROGRESS_SYNC_ENDPOINT
        val defaultToken = BuildConfig.DEFAULT_PROGRESS_SYNC_TOKEN
        if (!preferences.getBoolean(DEFAULTS_SEEDED_KEY, false) && defaultToken.length >= 32) {
            val defaults = ProgressSyncSettings(defaultEndpoint, defaultToken)
            return runCatching {
                save(defaults)
                preferences.edit().putBoolean(DEFAULTS_SEEDED_KEY, true).apply()
                defaults
            }.getOrNull()
        }

        val value = preferences.getString(SETTINGS_KEY, null)
        if (value == null) {
            return null
        }
        return runCatching {
            val combined = Base64.decode(value, Base64.NO_WRAP)
            require(combined.size > IV_LENGTH_BYTES)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, combined.copyOfRange(0, IV_LENGTH_BYTES)))
            }
            val json = JSONObject(String(cipher.doFinal(combined.copyOfRange(IV_LENGTH_BYTES, combined.size)), StandardCharsets.UTF_8))
            ProgressSyncSettings(json.getString("endpoint"), json.getString("token"))
        }.getOrNull()
    }

    fun save(settings: ProgressSyncSettings) {
        val plaintext = JSONObject().put("endpoint", settings.endpoint).put("token", settings.token)
            .toString().toByteArray(StandardCharsets.UTF_8)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, getOrCreateKey()) }
        val encoded = Base64.encodeToString(cipher.iv + cipher.doFinal(plaintext), Base64.NO_WRAP)
        preferences.edit().putString(SETTINGS_KEY, encoded).apply()
    }

    fun clear() {
        preferences.edit().remove(SETTINGS_KEY).apply()
        runCatching { KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }.deleteEntry(KEY_ALIAS) }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).run {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
            generateKey()
        }
    }

    private companion object {
        const val SETTINGS_KEY = "encrypted_nas_settings"
        const val DEFAULTS_SEEDED_KEY = "build_defaults_seeded"
        const val KEY_ALIAS = "iptv_lab_progress_sync_key"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH_BYTES = 12
        const val TAG_LENGTH_BITS = 128
    }
}
