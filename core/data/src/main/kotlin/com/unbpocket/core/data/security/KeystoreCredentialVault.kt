package com.unbpocket.core.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.unbpocket.core.data.local.CredentialVault
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Serializable
data class StoredCredentials(
    val matricula: String,
    val password: String,
)

/**
 * Cofre de credenciais cifrado via Android Keystore (`AES/GCM/NoPadding`)
 * armazenado em `noBackupFilesDir` para impedir vazamento via Auto Backup.
 */
class KeystoreCredentialVault(
    context: Context,
) : CredentialVault {
    private val vaultFile = File(context.noBackupFilesDir, VAULT_FILE_NAME)
    private val json = Json { ignoreUnknownKeys = true }

    override fun save(matricula: String, password: String) {
        val plaintext = json.encodeToString(StoredCredentials(matricula, password)).toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plaintext)

        val payload = ByteArray(1 + iv.size + encrypted.size)
        payload[0] = iv.size.toByte()
        System.arraycopy(iv, 0, payload, 1, iv.size)
        System.arraycopy(encrypted, 0, payload, 1 + iv.size, encrypted.size)
        vaultFile.writeBytes(payload)
    }

    override fun load(): StoredCredentials? {
        if (!vaultFile.exists()) return null
        return runCatching {
            val payload = vaultFile.readBytes()
            if (payload.size < 2) return null
            val ivSize = payload[0].toInt() and 0xFF
            if (payload.size <= 1 + ivSize) return null
            val iv = payload.copyOfRange(1, 1 + ivSize)
            val encrypted = payload.copyOfRange(1 + ivSize, payload.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            val plaintext = cipher.doFinal(encrypted)
            json.decodeFromString<StoredCredentials>(String(plaintext, Charsets.UTF_8))
        }.getOrNull()
    }

    override fun exists(): Boolean = vaultFile.exists() && vaultFile.length() > 0L

    override fun clear() {
        if (vaultFile.exists()) {
            vaultFile.delete()
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "unb_pocket_sigaa_credentials_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val VAULT_FILE_NAME = "sigaa_vault.bin"
    }
}
