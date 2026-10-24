package live.collog.app.shared.services

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import live.collog.app.shared.services.api.ApiError
import live.collog.app.shared.services.api.CollogJson
import live.collog.app.shared.services.api.TokenResponse

interface SessionCredentialStorage {
    fun read(): TokenResponse?
    fun write(response: TokenResponse)
    fun delete()
}

/**
 * iOS Keychain(`AfterFirstUnlockThisDeviceOnly`) 대신 Android Keystore AES-GCM 키로 세션을 암호화해
 * 앱 전용 SharedPreferences에 둔다. 키는 기기 밖으로 나가지 않고, 매니페스트에서 백업을 막는다.
 */
class CredentialStore(context: Context) : SessionCredentialStorage {
    private val prefs = context.getSharedPreferences("collog.session", Context.MODE_PRIVATE)

    override fun read(): TokenResponse? {
        val stored = prefs.getString(VALUE_KEY, null) ?: return null
        return try {
            val (iv, ciphertext) = stored.split(':').map { Base64.decode(it, Base64.NO_WRAP) }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            CollogJson.decodeFromString<TokenResponse>(String(cipher.doFinal(ciphertext), Charsets.UTF_8))
        } catch (error: Exception) {
            throw ApiError.Transport("저장된 로그인 정보를 가져오지 못했어요")
        }
    }

    override fun write(response: TokenResponse) {
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val ciphertext = cipher.doFinal(CollogJson.encodeToString(response).toByteArray(Charsets.UTF_8))
            val value = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
                Base64.encodeToString(ciphertext, Base64.NO_WRAP)
            if (!prefs.edit().putString(VALUE_KEY, value).commit()) error("commit failed")
        } catch (error: Exception) {
            throw ApiError.Transport("로그인 정보를 안전하게 저장하지 못했어요")
        }
    }

    override fun delete() {
        if (!prefs.edit().remove(VALUE_KEY).commit()) {
            throw ApiError.Transport("저장된 로그인 정보를 삭제하지 못했어요")
        }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "collog.session.key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val VALUE_KEY = "session"
    }
}
