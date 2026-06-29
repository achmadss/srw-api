package util

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object AesUtil {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    fun keyFromEnv(base64Key: String): SecretKey {
        val keyBytes = Base64.getDecoder().decode(base64Key)
        require(keyBytes.size == 32) { "AES-256 key must be exactly 32 bytes" }
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encrypt(plaintext: String, key: SecretKey): String {
        val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + ciphertext)
    }

    fun decrypt(encryptedBase64: String, key: SecretKey): String {
        val combined = Base64.getDecoder().decode(encryptedBase64)
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    fun encryptInt(value: Int, key: SecretKey): String = encrypt(value.toString(), key)
    fun decryptInt(encryptedBase64: String, key: SecretKey): Int = decrypt(encryptedBase64, key).toInt()
}
