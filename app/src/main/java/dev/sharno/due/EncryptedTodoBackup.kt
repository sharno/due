package dev.sharno.due

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object EncryptedTodoBackup {
    const val FILE_NAME = "due-todos.encrypted.json"
    const val MIME_TYPE = "application/json"

    private const val FORMAT = "dev.sharno.due.encrypted-backup"
    private const val VERSION = 1
    private const val KDF = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    fun encode(todos: List<Todo>, passphrase: String): String {
        require(passphrase.isNotEmpty()) { "A backup passphrase is required" }
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val iv = ByteArray(IV_BYTES).also(SecureRandom()::nextBytes)
        val plaintext = TodoBackup.encode(todos).toByteArray(StandardCharsets.UTF_8)
        val ciphertext = cipher(Cipher.ENCRYPT_MODE, passphrase, salt, iv).doFinal(plaintext)

        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("kdf", KDF)
            .put("iterations", ITERATIONS)
            .put("salt", encodeBase64(salt))
            .put("iv", encodeBase64(iv))
            .put("ciphertext", encodeBase64(ciphertext))
            .toString(2)
    }

    fun decode(raw: String, passphrase: String): List<Todo> {
        require(passphrase.isNotEmpty()) { "A backup passphrase is required" }
        val root = JSONObject(raw)
        require(root.getString("format") == FORMAT) { "This is not an encrypted Due backup" }
        require(root.getInt("version") == VERSION) { "Unsupported encrypted backup version" }
        require(root.getString("kdf") == KDF) { "Unsupported encrypted backup algorithm" }
        require(root.getInt("iterations") == ITERATIONS) { "Unsupported encrypted backup parameters" }

        val salt = decodeBase64(root.getString("salt"))
        val iv = decodeBase64(root.getString("iv"))
        val ciphertext = decodeBase64(root.getString("ciphertext"))
        require(salt.size == SALT_BYTES) { "Encrypted backup has an invalid salt" }
        require(iv.size == IV_BYTES) { "Encrypted backup has an invalid IV" }

        val plaintext = try {
            cipher(Cipher.DECRYPT_MODE, passphrase, salt, iv).doFinal(ciphertext)
        } catch (_: AEADBadTagException) {
            throw IllegalArgumentException("Incorrect passphrase or corrupted backup")
        } catch (_: BadPaddingException) {
            throw IllegalArgumentException("Incorrect passphrase or corrupted backup")
        } catch (error: GeneralSecurityException) {
            throw IllegalArgumentException("Unable to decrypt backup", error)
        }
        return TodoBackup.decode(String(plaintext, StandardCharsets.UTF_8))
    }

    private fun cipher(mode: Int, passphrase: String, salt: ByteArray, iv: ByteArray): Cipher {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            mode,
            deriveKey(passphrase, salt),
            GCMParameterSpec(TAG_BITS, iv),
        )
        cipher.updateAAD("$FORMAT:$VERSION".toByteArray(StandardCharsets.UTF_8))
        return cipher
    }

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKey {
        val specification = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return try {
            val generated = SecretKeyFactory.getInstance(KDF).generateSecret(specification).encoded
            SecretKeySpec(generated, "AES")
        } finally {
            specification.clearPassword()
        }
    }

    private fun encodeBase64(value: ByteArray): String = Base64.getEncoder().encodeToString(value)

    private fun decodeBase64(value: String): ByteArray = try {
        Base64.getDecoder().decode(value)
    } catch (error: IllegalArgumentException) {
        throw IllegalArgumentException("Encrypted backup contains invalid base64", error)
    }
}

internal object BackupPassphraseProtector {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "dev.sharno.due.backup-passphrase"
    private const val TAG_BITS = 128

    fun encrypt(passphrase: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ciphertext = cipher.doFinal(passphrase.toByteArray(StandardCharsets.UTF_8))
        return "${encodeBase64(cipher.iv)}.${encodeBase64(ciphertext)}"
    }

    fun decrypt(value: String): String {
        val parts = value.split('.')
        require(parts.size == 2) { "Stored backup passphrase is invalid" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(TAG_BITS, decodeBase64(parts[0])),
        )
        return String(cipher.doFinal(decodeBase64(parts[1])), StandardCharsets.UTF_8)
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }

    private fun encodeBase64(value: ByteArray): String = Base64.getEncoder().encodeToString(value)

    private fun decodeBase64(value: String): ByteArray = Base64.getDecoder().decode(value)
}
