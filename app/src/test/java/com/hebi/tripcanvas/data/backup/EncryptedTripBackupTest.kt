package com.hebi.tripcanvas.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptedTripBackupTest {
    private val fixture = """{"format":"tripcanvas-structured-backup","note":"예약 자료"}""".toByteArray(Charsets.UTF_8)

    @Test fun randomizedAuthenticatedEnvelopeRoundTrip() {
        val password = "fixture-한글-passphrase".toCharArray()
        try {
            val first = EncryptedTripBackup.encrypt(fixture, password)
            val second = EncryptedTripBackup.encrypt(fixture, password)
            assertTrue(EncryptedTripBackup.isEncrypted(first))
            assertFalse(first.contentEquals(second))
            assertFalse(String(first, Charsets.ISO_8859_1).contains("tripcanvas-structured-backup"))
            assertArrayEquals(fixture, EncryptedTripBackup.decrypt(first, password))
            assertArrayEquals(fixture, EncryptedTripBackup.decrypt(second, password))
        } finally {
            password.fill('\u0000')
        }
    }

    @Test fun wrongPasswordAndModifiedCiphertextAreRejected() {
        val password = "fixture-original".toCharArray()
        val wrong = "fixture-different".toCharArray()
        try {
            val encrypted = EncryptedTripBackup.encrypt(fixture, password)
            assertTrue(runCatching { EncryptedTripBackup.decrypt(encrypted, wrong) }.isFailure)
            val tampered = encrypted.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
            assertTrue(runCatching { EncryptedTripBackup.decrypt(tampered, password) }.isFailure)
            val header = encrypted.copyOf().also { it[9] = (it[9].toInt() xor 1).toByte() }
            assertTrue(runCatching { EncryptedTripBackup.decrypt(header, password) }.isFailure)
            val unsupported = encrypted.copyOf().also { it[8] = 2 }
            assertTrue(runCatching { EncryptedTripBackup.decrypt(unsupported, password) }.isFailure)
            assertTrue(runCatching { EncryptedTripBackup.decrypt(encrypted.copyOf(12), password) }.isFailure)
        } finally {
            password.fill('\u0000')
            wrong.fill('\u0000')
        }
    }

    @Test fun rejectsInvalidLengthsBeforeEncryptionOrKeyDerivation() {
        val password = "fixture-original".toCharArray()
        try {
            assertFalse(EncryptedTripBackup.isEncrypted(fixture))
            assertTrue(runCatching { EncryptedTripBackup.encrypt(fixture, "short".toCharArray()) }.isFailure)
            assertTrue(runCatching { EncryptedTripBackup.encrypt(fixture, CharArray(129) { 'x' }) }.isFailure)
            assertTrue(runCatching { EncryptedTripBackup.encrypt(ByteArray(TripBackupCodec.MAX_BYTES + 1), password) }.isFailure)
            assertTrue(runCatching { EncryptedTripBackup.decrypt(ByteArray(EncryptedTripBackup.MAX_BYTES + 1), password) }.isFailure)
        } finally {
            password.fill('\u0000')
        }
    }
}
