package com.commontongue.spike.device

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

/** Small synthetic archives exercise the actual importer, never research weights. */
class PackStoreTest {
    @Test
    fun appendedArchiveBytesAreRejected() = withStore { store, _, bytes ->
        assertTrue(fails { install(store, bytes + ByteArray(2 * 1024 * 1024)) }.contains("damaged"))
        assertFalse(store.isInstalled())
    }

    private val content = "synthetic payload".toByteArray()
    private val manifest = "fixed test version\n".toByteArray()
    private val path = "models/madlad/model-q4k.gguf"

    private fun sha(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun archive(
        entries: List<Pair<String, ByteArray>> =
            listOf("pack-manifest.json" to manifest, "payload/$path" to content)
    ): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            for ((name, data) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(data)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun contract(bytes: ByteArray) =
        PackContract(
            "unit-test",
            bytes.size.toLong(),
            sha(bytes),
            sha(manifest),
            listOf(PackFile(path, content.size.toLong(), sha(content))),
        )

    private fun withStore(test: (PackStore, File, ByteArray) -> Unit) {
        val root = Files.createTempDirectory("common-tongue-pack-test").toFile()
        val bytes = archive()
        try {
            test(PackStore(root, contract(bytes)), root, bytes)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun install(store: PackStore, bytes: ByteArray) =
        store.install(ByteArrayInputStream(bytes), { Long.MAX_VALUE }, { false }, {})

    private fun fails(action: () -> Unit): String {
        try {
            action()
            fail("Import must fail")
        } catch (error: PackProblem) {
            return error.message!!
        }
        return "unreachable"
    }

    @Test
    fun validStreamInstallsAndSurvivesWithoutSourceArchive() = withStore { store, root, bytes ->
        install(store, bytes)
        assertTrue(store.isInstalled())
        assertArrayEquals(content, File(store.root(), path).readBytes())
        store.verifyInstalled()
        store.recover()
        assertTrue(store.isInstalled())
        assertFalse(File(root, "incoming").exists())
    }

    @Test
    fun freshStoreAfterRestartResolvesTheSamePrivateMadladFile() = withStore { store, root, bytes ->
        install(store, bytes)
        val restarted = PackStore(root, contract(bytes))
        restarted.recover()
        assertTrue(restarted.isInstalled())
        val paths = ModelPaths(restarted.root())
        assertArrayEquals(content, File(paths.madlad, "model-q4k.gguf").readBytes())
        assertEquals(
            File(root, "installed/models/whisper/ggml-base-q5_1.bin"),
            paths.whisper("base"),
        )
        assertEquals(
            File(root, "installed/models/whisper/ggml-tiny-q5_1.bin"),
            paths.whisper("tiny"),
        )
        restarted.verifyInstalled()
    }

    @Test
    fun insufficientSpaceDoesNotConsumeArchiveOrReplaceInstallation() =
        withStore { store, _, bytes ->
            install(store, bytes)
            val input = ByteArrayInputStream(bytes)
            assertEquals(
                "Not enough free space.",
                fails { store.install(input, { 0 }, { false }, {}) },
            )
            assertEquals(bytes.size, input.available())
            assertTrue(store.isInstalled())
        }

    @Test
    fun corruptedPayloadNeverBecomesReady() = withStore { store, root, _ ->
        val bad =
            archive(
                listOf(
                    "pack-manifest.json" to manifest,
                    "payload/$path" to "changed payload!!".toByteArray(),
                )
            )
        assertTrue(fails { install(store, bad) }.contains("damaged"))
        assertFalse(store.isInstalled())
        assertFalse(File(root, "incoming").exists())
    }

    @Test
    fun missingFileIsIncompleteAndKeepsWorkingInstallation() = withStore { store, _, bytes ->
        install(store, bytes)
        assertEquals(
            "This test pack is incomplete.",
            fails { install(store, archive(listOf("pack-manifest.json" to manifest))) },
        )
        assertTrue(store.isInstalled())
        store.verifyInstalled()
    }

    @Test
    fun pathTraversalIsRejectedBeforeWriting() = withStore { store, root, _ ->
        val bad =
            archive(listOf("pack-manifest.json" to manifest, "payload/../../escape" to content))
        assertEquals("This test pack is incomplete.", fails { install(store, bad) })
        assertFalse(File(root.parentFile, "escape").exists())
        assertFalse(store.isInstalled())
    }

    @Test
    fun wrongManifestCannotChangePinnedFiles() = withStore { store, _, _ ->
        val bad =
            archive(
                listOf(
                    "pack-manifest.json" to "other version".toByteArray(),
                    "payload/$path" to content,
                )
            )
        assertTrue(fails { install(store, bad) }.contains("does not match"))
        assertFalse(store.isInstalled())
    }

    @Test
    fun truncatedCentralDirectoryIsNotAccepted() = withStore { store, _, bytes ->
        assertTrue(fails { install(store, bytes.copyOf(bytes.size - 10)) }.contains("damaged"))
        assertFalse(store.isInstalled())
    }

    @Test
    fun cancelledReplacementRetainsWorkingInstallation() = withStore { store, root, bytes ->
        install(store, bytes)
        val cancelled = AtomicBoolean(false)
        assertTrue(
            fails {
                    store.install(
                        ByteArrayInputStream(bytes),
                        { Long.MAX_VALUE },
                        { cancelled.get() },
                        { cancelled.set(true) },
                    )
                }
                .contains("interrupted")
        )
        assertTrue(store.isInstalled())
        assertFalse(File(root, "incoming").exists())
    }

    @Test
    fun restartRestoresPreviousCommitAndRemovesPartialStage() = withStore { store, root, bytes ->
        install(store, bytes)
        assertTrue(store.root().renameTo(File(root, "previous")))
        File(root, "incoming").mkdirs()
        File(root, "incoming/partial").writeText("interrupted")
        assertFalse(store.isInstalled())
        store.recover()
        assertTrue(store.isInstalled())
        assertFalse(File(root, "incoming").exists())
    }

    @Test
    fun installedFileDamageIsFoundBeforeModelLoading() = withStore { store, _, bytes ->
        install(store, bytes)
        File(store.root(), path).writeBytes(ByteArray(content.size))
        assertTrue(fails { store.verifyInstalled() }.contains("damaged"))
    }

    @Test
    fun oversizedEntryStopsAtItsPinnedSize() = withStore { store, _, _ ->
        val bad =
            archive(
                listOf(
                    "pack-manifest.json" to manifest,
                    "payload/$path" to ByteArray(2 * 1024 * 1024),
                )
            )
        assertTrue(fails { install(store, bad) }.contains("damaged"))
        assertFalse(store.isInstalled())
    }

    @Test
    fun duplicateEntryCannotReplaceVerifiedData() = withStore { _, root, _ ->
        val entries =
            archive(
                listOf(
                    "pack-manifest.json" to manifest,
                    "payload/$path" to content,
                    "payload/${path.replace("gguf", "xxxx")}" to content,
                )
            )
        val patched =
            String(entries, Charsets.ISO_8859_1)
                .replace("model-q4k.xxxx", "model-q4k.gguf")
                .toByteArray(Charsets.ISO_8859_1)
        val store = PackStore(root, contract(patched))
        assertEquals("This test pack is incomplete.", fails { install(store, patched) })
        assertFalse(store.isInstalled())
    }
}
