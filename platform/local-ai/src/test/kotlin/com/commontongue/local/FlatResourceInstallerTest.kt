package com.commontongue.local

import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class FlatResourceInstallerTest {
    private fun identity(bytes: ByteArray) =
        ResourceIdentity(
            bytes.size.toLong(),
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
                "%02x".format(it)
            },
        )

    private suspend fun trial(
        rows: Map<String, ByteArray>,
        expected: Map<String, ResourceIdentity>,
        interrupt: Boolean = false,
        success: Boolean = false,
    ) {
        val folder = kotlin.io.path.createTempDirectory().toFile()
        try {
            val archive = File(folder, "pack")
            ZipOutputStream(archive.outputStream()).use { zip ->
                rows.forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
            val root = File(folder, "owned").apply { mkdirs() }
            File(root, "original").writeText("preserved")
            try {
                FlatResourceInstaller.install(archive, root, expected) {
                    if (interrupt) throw CancellationException()
                }
                assertTrue("Damaged import accepted", success)
                assertTrue(File(root, "installed").isFile)
                assertFalse(File(root, "original").exists())
                expected.forEach { (name, id) -> ResourceIntegrity.verify(File(root, name), id) }
            } catch (error: Exception) {
                if (success) throw error
                assertTrue(error is LocalFault || error is CancellationException)
                assertEquals("preserved", File(root, "original").readText())
                assertFalse(File(root, "installed").exists())
            }
            assertFalse(File(folder, "owned-staging").exists())
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun completeVerifiedImportCommitsAndSurvivesReopen() = runTest {
        val b = byteArrayOf(1, 2)
        trial(mapOf("model" to b), mapOf("model" to identity(b)), success = true)
    }

    @Test
    fun corruptEntryPreservesPreviousInstallation() = runTest {
        trial(mapOf("model" to byteArrayOf(2, 1)), mapOf("model" to identity(byteArrayOf(1, 2))))
    }

    @Test
    fun missingEntryCannotMarkReady() = runTest {
        trial(emptyMap(), mapOf("model" to identity(byteArrayOf(1))))
    }

    @Test
    fun oversizedEntryIsBounded() = runTest {
        trial(mapOf("model" to byteArrayOf(1, 2, 3)), mapOf("model" to identity(byteArrayOf(1, 2))))
    }

    @Test
    fun unknownEntryIsRejected() = runTest {
        trial(mapOf("other" to byteArrayOf(1)), mapOf("model" to identity(byteArrayOf(1))))
    }

    @Test
    fun traversalEntryCannotEscapeStaging() = runTest {
        trial(mapOf("../escaped" to byteArrayOf(1)), mapOf("model" to identity(byteArrayOf(1))))
    }

    @Test
    fun interruptedImportHasNoHalfReadyState() = runTest {
        val b = byteArrayOf(1, 2)
        trial(mapOf("model" to b), mapOf("model" to identity(b)), interrupt = true)
    }
}
