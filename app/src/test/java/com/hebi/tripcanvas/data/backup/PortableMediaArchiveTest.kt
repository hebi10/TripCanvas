package com.hebi.tripcanvas.data.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortableMediaArchiveTest {
    @Test fun generatedPathsAreBoundedAndDoNotUseSourceNames() {
        assertEquals("media/000.jpg", PortableMediaArchive.mediaPath(0, "IMAGE/JPEG", true))
        assertEquals("media/099.pdf", PortableMediaArchive.mediaPath(99, "application/pdf", false))
        assertEquals("content://com.hebi.tripcanvas.archive/photos/007", PortableMediaArchive.photoPlaceholder(7))
        assertTrue(runCatching { PortableMediaArchive.mediaPath(-1, "image/png", true) }.isFailure)
        assertTrue(runCatching { PortableMediaArchive.mediaPath(100, "image/png", true) }.isFailure)
        assertTrue(runCatching { PortableMediaArchive.mediaPath(0, "application/pdf", true) }.isFailure)
        assertTrue(runCatching { PortableMediaArchive.mediaPath(0, "image/../../bad", false) }.isFailure)
        assertTrue(runCatching { PortableMediaArchive.mediaPath(0, "image/svg+xml", false) }.isFailure)
    }

    @Test fun originalBytesAndDigestArePreservedAndLimitsAreEnforced() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        val out = ByteArrayOutputStream()
        val result = PortableMediaArchive.copyMedia(ByteArrayInputStream(bytes), out, bytes.size)
        assertEquals(bytes.size, result.bytes)
        assertEquals(PortableMediaArchive.digest(ByteArrayInputStream(bytes)).second, result.sha256)
        assertArrayEquals(bytes, out.toByteArray())
        val blocked = ByteArrayOutputStream()
        assertTrue(runCatching {
            PortableMediaArchive.copyMedia(ByteArrayInputStream(bytes), blocked, bytes.size - 1)
        }.isFailure)
        assertEquals(0, blocked.size())
        assertTrue(runCatching {
            PortableMediaArchive.copyMedia(ByteArrayInputStream(ByteArray(0)), ByteArrayOutputStream(), 1)
        }.isFailure)
    }

    @Test fun authenticatedArchiveRejectsWrongPasswordTamperingAndTruncation() {
        val password = "fixture-archive-passphrase".toCharArray()
        val wrong = "fixture-wrong-passphrase".toCharArray()
        val original = "QA original fixture".toByteArray(Charsets.UTF_8)
        try {
            fun create(): ByteArray = ByteArrayOutputStream().also { output ->
                PortableMediaArchive.writeEncryptedZip(output, password) { zip ->
                    zip.putNextEntry(ZipEntry("media/000.jpg"))
                    zip.write(original)
                    zip.closeEntry()
                }
            }.toByteArray()

            val first = create()
            val second = create()
            assertFalse(first.contentEquals(second))
            assertFalse(String(first, Charsets.ISO_8859_1).contains("QA original fixture"))
            val plaintext = PortableMediaArchive.decryptVerified(first, password)
            try {
                ZipInputStream(ByteArrayInputStream(plaintext)).use { zip ->
                    assertEquals("media/000.jpg", zip.nextEntry.name)
                    assertArrayEquals(original, zip.readBytes())
                    assertEquals(null, zip.nextEntry)
                }
            } finally {
                plaintext.fill(0)
            }
            assertTrue(runCatching { PortableMediaArchive.decryptVerified(first, wrong) }.isFailure)
            val tampered = first.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
            assertTrue(runCatching { PortableMediaArchive.decryptVerified(tampered, password) }.isFailure)
            val header = first.copyOf().also { it[10] = (it[10].toInt() xor 1).toByte() }
            assertTrue(runCatching { PortableMediaArchive.decryptVerified(header, password) }.isFailure)
            assertTrue(runCatching { PortableMediaArchive.decryptVerified(first.copyOf(12), password) }.isFailure)
        } finally {
            password.fill('\u0000')
            wrong.fill('\u0000')
        }
    }
}
