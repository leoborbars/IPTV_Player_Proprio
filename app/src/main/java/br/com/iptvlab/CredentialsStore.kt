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

data class SavedCredentials(
    val server: String,
    val username: String,
    val password: String
)

/** Stores login details only after an explicit opt-in, encrypted with Android Keystore. */
class CredentialsStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): SavedCredentials? = runCatching {
        val encoded = preferences.getString(PREFERENCES_KEY, null) ?: return@runCatching null
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        require(combined.size > IV_LENGTH_BYTES)

        val iv = combined.copyOfRange(0, IV_LENGTH_BYTES)
        val ciphertext = combined.copyOfRange(IV_LENGTH_BYTES, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        }
        val json = JSONObject(String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8))
        val server = json.optString("server").trim()
        val username = json.optString("username").trim()
        val password = json.optString("password")
        require(server.isNotBlank() && username.isNotBlank() && password.isNotBlank())
        SavedCredentials(server, username, password)
    }.getOrNull()

    fun save(credentials: SavedCredentials) {
        val plaintext = JSONObject()
            .put("server", credentials.server)
            .put("username", credentials.username)
            .put("password", credentials.password)
            .toString()
            .toByteArray(StandardCharsets.UTF_8)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        }
        val encrypted = cipher.doFinal(plaintext)
        val combined = cipher.iv + encrypted
        preferences.edit()
            .putString(PREFERENCES_KEY, Base64.encodeToString(combined, Base64.NO_WRAP))
            .apply()
    }

    fun clear() {
        preferences.edit().remove(PREFERENCES_KEY).apply()
        runCatching {
            KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }.deleteEntry(KEY_ALIAS)
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generateKey()
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "credentials"
        const val PREFERENCES_KEY = "encrypted_login"
        const val KEY_ALIAS = "iptv_lab_login_key"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH_BYTES = 12
        const val TAG_LENGTH_BITS = 128
    }
}
