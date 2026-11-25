package com.hebi.tripcanvas.data.backup

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Portable v1 envelope with fixed KDF parameters and authenticated header. */
object EncryptedTripBackup {
    private val MAGIC = "TCBACKUP".toByteArray(Charsets.US_ASCII)
    private const val VERSION: Byte = 1
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val TAG_BYTES = 16
    private const val HEADER_BYTES = 8 + 1 + SALT_BYTES + NONCE_BYTES
    private const val ITERATIONS = 600_000
    const val MIN_PASSWORD_LENGTH = 12
    const val MAX_PASSWORD_LENGTH = 128
    const val MAX_BYTES = TripBackupCodec.MAX_BYTES + HEADER_BYTES + TAG_BYTES

    fun isEncrypted(bytes: ByteArray): Boolean =
        bytes.size >= MAGIC.size && MAGIC.indices.all { bytes[it] == MAGIC[it] }

    fun validPassword(password: CharArray): Boolean =
        password.size in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH &&
            password.any { !it.isWhitespace() }

    fun encrypt(plaintext: ByteArray, password: CharArray): ByteArray {
        require(plaintext.size in 1..TripBackupCodec.MAX_BYTES) { "백업 용량이 제한을 초과합니다." }
        require(validPassword(password)) { "백업 비밀번호 길이를 확인해 주세요." }
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val header = MAGIC + byteArrayOf(VERSION) + salt + nonce
        val key = derive(password, salt)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BYTES * 8, nonce))
            cipher.updateAAD(header)
            return header + cipher.doFinal(plaintext)
        } finally {
            key.fill(0)
        }
    }

    fun decrypt(envelope: ByteArray, password: CharArray): ByteArray {
        require(envelope.size in (HEADER_BYTES + TAG_BYTES + 1)..MAX_BYTES &&
            isEncrypted(envelope) && envelope[MAGIC.size] == VERSION
        ) { "암호화 백업 파일 형식 또는 용량이 올바르지 않습니다." }
        require(validPassword(password)) { "백업 비밀번호 길이를 확인해 주세요." }
        val saltStart = MAGIC.size + 1
        val salt = envelope.copyOfRange(saltStart, saltStart + SALT_BYTES)
        val nonce = envelope.copyOfRange(saltStart + SALT_BYTES, HEADER_BYTES)
        val key = derive(password, salt)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BYTES * 8, nonce))
            cipher.updateAAD(envelope, 0, HEADER_BYTES)
            return cipher.doFinal(envelope, HEADER_BYTES, envelope.size - HEADER_BYTES)
        } catch (_: GeneralSecurityException) {
            throw IllegalArgumentException("비밀번호가 올바르지 않거나 암호화 백업이 손상되었습니다.")
        } finally {
            key.fill(0)
        }
    }

    private fun derive(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
