package com.gipogo.rhctools.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Persiste la passphrase de SQLCipher envuelta por una clave AES no exportable
 * del Android Keystore.
 *
 * Las instalaciones anteriores guardaban la misma passphrase mediante
 * EncryptedSharedPreferences. Esa clave se lee una sola vez, se vuelve a
 * envolver con Keystore directo y el archivo antiguo sólo se elimina después
 * de verificar duraderamente la nueva copia.
 */
object DbKeyStore {

    private const val LEGACY_PREFS_NAME = "db_crypto_prefs"
    private const val LEGACY_KEY_PASSPHRASE_B64 = "db_passphrase_b64"

    private const val PREFS_NAME = "db_key_material_v2"
    private const val KEY_CIPHERTEXT_B64 = "wrapped_passphrase_b64"
    private const val KEY_IV_B64 = "wrapped_passphrase_iv_b64"
    private const val KEY_FORMAT_VERSION = "format_version"
    private const val FORMAT_VERSION = 2

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val WRAPPING_KEY_ALIAS = "gipogo_db_passphrase_wrap_v2"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val PASSPHRASE_BYTES = 32

    fun getOrCreatePassphraseBytes(context: Context): ByteArray =
        getOrCreatePassphraseBase64(context).toByteArray(StandardCharsets.UTF_8)

    /**
     * Indica si existe material persistido capaz de recuperar la passphrase.
     * No crea claves ni intenta descifrar, por lo que puede usarse antes de
     * invocar SQLCipher para detectar una restauración incompleta con seguridad.
     */
    fun hasStoredPassphraseMaterial(context: Context): Boolean {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hasV2Material = prefs.contains(KEY_CIPHERTEXT_B64) || prefs.contains(KEY_IV_B64)
        val legacyFile = File(appContext.applicationInfo.dataDir, "shared_prefs/$LEGACY_PREFS_NAME.xml")
        return hasV2Material || legacyFile.exists()
    }

    fun getOrCreatePassphraseText(
        context: Context,
        mayReplaceUnusableMaterial: Boolean = false,
        allowCreationIfMissing: Boolean = true
    ): String = try {
        getOrCreatePassphraseBase64(context, allowCreationIfMissing)
    } catch (error: DbKeyStoreException) {
        if (!mayReplaceUnusableMaterial) throw error
        clear(context)
        getOrCreatePassphraseBase64(context, allowCreationIfMissing = true)
    }

    @Synchronized
    fun clear(context: Context) {
        val appContext = context.applicationContext
        appContext.deleteSharedPreferences(PREFS_NAME)
        appContext.deleteSharedPreferences(LEGACY_PREFS_NAME)

        runCatching {
            keyStore().apply {
                if (containsAlias(WRAPPING_KEY_ALIAS)) {
                    deleteEntry(WRAPPING_KEY_ALIAS)
                }
            }
        }.getOrElse {
            throw DbKeyStoreException("No se pudo eliminar la clave de la base de datos", it)
        }
    }

    @Synchronized
    private fun getOrCreatePassphraseBase64(
        context: Context,
        allowCreationIfMissing: Boolean = true
    ): String {
        val appContext = context.applicationContext

        try {
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val ciphertextB64 = prefs.getString(KEY_CIPHERTEXT_B64, null)
            val ivB64 = prefs.getString(KEY_IV_B64, null)

            if (ciphertextB64 != null || ivB64 != null) {
                if (ciphertextB64.isNullOrBlank() || ivB64.isNullOrBlank()) {
                    throw DbKeyStoreException("El material protegido de la base de datos está incompleto")
                }
                if (prefs.getInt(KEY_FORMAT_VERSION, 0) != FORMAT_VERSION) {
                    throw DbKeyStoreException("La versión del material protegido no es compatible")
                }

                val wrappingKey = existingWrappingKey()
                    ?: throw DbKeyStoreException(
                        "La clave del Android Keystore no está disponible; no se generó otra para evitar perder datos"
                    )
                return decryptPassphrase(ciphertextB64, ivB64, wrappingKey)
            }

            val legacyPassphrase = readLegacyPassphraseOrNull(appContext)
            if (legacyPassphrase == null && !allowCreationIfMissing) {
                throw DbKeyStoreException(
                    "Falta una passphrase recuperable para la base clínica existente; " +
                        "no se generó otra para evitar pérdida de datos"
                )
            }
            val passphrase = legacyPassphrase ?: generatePassphrase()
            validatePassphrase(passphrase)

            val wrappingKey = existingWrappingKey() ?: createWrappingKey()
            val wrapped = encryptPassphrase(passphrase, wrappingKey)
            val committed = prefs.edit()
                .putInt(KEY_FORMAT_VERSION, FORMAT_VERSION)
                .putString(KEY_CIPHERTEXT_B64, wrapped.ciphertextB64)
                .putString(KEY_IV_B64, wrapped.ivB64)
                .commit()
            if (!committed) {
                throw DbKeyStoreException("No se pudo guardar de forma duradera la clave de la base de datos")
            }

            val persistedCiphertext = prefs.getString(KEY_CIPHERTEXT_B64, null)
                ?: throw DbKeyStoreException("La clave protegida guardada no pudo verificarse")
            val persistedIv = prefs.getString(KEY_IV_B64, null)
                ?: throw DbKeyStoreException("El vector de cifrado guardado no pudo verificarse")
            val verified = decryptPassphrase(persistedCiphertext, persistedIv, wrappingKey)
            if (verified != passphrase) {
                throw DbKeyStoreException("La clave protegida no coincide después de guardarla")
            }

            // Nunca se elimina el formato publicado hasta verificar completamente v2.
            appContext.deleteSharedPreferences(LEGACY_PREFS_NAME)
            return verified
        } catch (error: DbKeyStoreException) {
            throw error
        } catch (error: Throwable) {
            throw DbKeyStoreException("No se pudo acceder de forma segura a la clave de la base de datos", error)
        }
    }

    private fun generatePassphrase(): String {
        val bytes = ByteArray(PASSPHRASE_BYTES)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    @Suppress("DEPRECATION")
    private fun readLegacyPassphraseOrNull(context: Context): String? {
        val legacyFile = File(context.applicationInfo.dataDir, "shared_prefs/$LEGACY_PREFS_NAME.xml")
        if (!legacyFile.exists()) return null

        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val legacyPrefs = EncryptedSharedPreferences.create(
            context,
            LEGACY_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        return legacyPrefs.getString(LEGACY_KEY_PASSPHRASE_B64, null)?.also(::validatePassphrase)
    }

    private fun existingWrappingKey(): SecretKey? {
        val key = keyStore().getKey(WRAPPING_KEY_ALIAS, null) ?: return null
        return key as? SecretKey
            ?: throw DbKeyStoreException("La entrada del Android Keystore no es una clave AES válida")
    }

    private fun createWrappingKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            WRAPPING_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun encryptPassphrase(passphrase: String, key: SecretKey): WrappedPassphrase {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ciphertext = cipher.doFinal(passphrase.toByteArray(StandardCharsets.UTF_8))
        return WrappedPassphrase(
            ciphertextB64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            ivB64 = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        )
    }

    private fun decryptPassphrase(ciphertextB64: String, ivB64: String, key: SecretKey): String {
        val ciphertext = decodeBase64(ciphertextB64, "cifrado")
        val iv = decodeBase64(ivB64, "vector de cifrado")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val plaintext = cipher.doFinal(ciphertext).toString(StandardCharsets.UTF_8)
        validatePassphrase(plaintext)
        return plaintext
    }

    private fun decodeBase64(value: String, label: String): ByteArray =
        runCatching { Base64.decode(value, Base64.NO_WRAP) }
            .getOrElse { throw DbKeyStoreException("El $label guardado no es válido", it) }

    private fun validatePassphrase(value: String) {
        val decoded = decodeBase64(value, "material de clave")
        if (decoded.size != PASSPHRASE_BYTES) {
            throw DbKeyStoreException("La clave guardada tiene una longitud no válida")
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private data class WrappedPassphrase(
        val ciphertextB64: String,
        val ivB64: String
    )
}

class DbKeyStoreException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)
