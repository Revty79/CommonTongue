package com.commontongue.local

import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Resource-layer transaction. Only exact flat, hash-pinned entries are accepted. */
object FlatResourceInstaller {
    suspend fun install(
        archive: File,
        root: File,
        expected: Map<String, ResourceIdentity>,
        progress: (Long) -> Unit = {},
    ) {
        require(
            expected.isNotEmpty() &&
                expected.keys.all {
                    it.matches(Regex("[a-zA-Z0-9_.-]{1,64}")) &&
                        it !in setOf(".", "..", "installed")
                }
        )
        val parent = root.parentFile ?: throw LocalFault(LocalFailure.WRONG_RESOURCES)
        val staging = File(parent, root.name + "-staging")
        val previous = File(parent, root.name + "-previous")
        try {
            staging.deleteRecursively()
            if (!staging.mkdirs()) throw LocalFault(LocalFailure.RESOURCE_LIMIT)
            val seen = mutableSetOf<String>()
            var total = 0L
            ZipInputStream(archive.inputStream().buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    currentCoroutineContext().ensureActive()
                    val identity =
                        expected[entry.name] ?: throw LocalFault(LocalFailure.WRONG_RESOURCES)
                    if (entry.isDirectory || !seen.add(entry.name))
                        throw LocalFault(LocalFailure.WRONG_RESOURCES)
                    var size = 0L
                    val hash = MessageDigest.getInstance("SHA-256")
                    File(staging, entry.name).outputStream().buffered().use { out ->
                        val bytes = ByteArray(1024 * 1024)
                        var count = zip.read(bytes)
                        while (count != -1) {
                            currentCoroutineContext().ensureActive()
                            size += count
                            if (size > identity.bytes)
                                throw LocalFault(LocalFailure.WRONG_RESOURCES)
                            out.write(bytes, 0, count)
                            hash.update(bytes, 0, count)
                            total += count
                            progress(total)
                            count = zip.read(bytes)
                        }
                    }
                    if (
                        size != identity.bytes ||
                            hash.digest().joinToString("") { "%02x".format(it) } != identity.sha256
                    )
                        throw LocalFault(LocalFailure.WRONG_RESOURCES)
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            if (seen != expected.keys) throw LocalFault(LocalFailure.WRONG_RESOURCES)
            // Reopen the copied files. Verification precedes both the marker and atomic directory
            // move.
            expected.forEach { (name, identity) ->
                ResourceIntegrity.verify(File(staging, name), identity)
            }
            File(staging, "installed").writeText(LockedCore.VERSION)
            previous.deleteRecursively()
            if (root.exists() && !root.renameTo(previous))
                throw LocalFault(LocalFailure.RESOURCE_LIMIT)
            if (!staging.renameTo(root)) {
                if (previous.exists()) previous.renameTo(root)
                throw LocalFault(LocalFailure.RESOURCE_LIMIT)
            }
            previous.deleteRecursively()
        } finally {
            withContext(NonCancellable) { staging.deleteRecursively() }
        }
    }
}
