package com.commontongue.spike.device

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

internal data class PackFile(val path: String, val bytes: Long, val sha256: String)

internal data class PackContract(
    val id: String,
    val bytes: Long,
    val sha256: String,
    val manifestSha256: String,
    val files: List<PackFile>,
)

internal class PackProblem(message: String) : IOException(message)

/** Streaming import with pinned allowlist, disk verification and a directory commit. */
internal class PackStore(private val directory: File, private val contract: PackContract) {
    companion object {
        private val importLock = Any()
    }

    private val installed = File(directory, "installed")
    private val incoming = File(directory, "incoming")
    private val previous = File(directory, "previous")
    private val reserve = 512L * 1024 * 1024
    private val damaged = "The file was damaged during download. Please download it again."

    init {
        require(contract.files.map { it.path }.toSet().size == contract.files.size)
        require(
            contract.files.all {
                it.bytes >= 0 &&
                    it.sha256.matches(Regex("[0-9a-f]{64}")) &&
                    it.path.matches(Regex("[A-Za-z0-9_.-]+(/[A-Za-z0-9_.-]+)*")) &&
                    it.path.split('/').none { part -> part == "." || part == ".." }
            }
        )
    }

    fun recover(): Unit =
        synchronized(importLock) {
            directory.mkdirs()
            if (!installed.exists() && previous.exists() && !previous.renameTo(installed))
                throw PackProblem("Installation was interrupted. Please try again.")
            incoming.deleteRecursively()
            if (isInstalled()) previous.deleteRecursively()
            Unit
        }

    fun isInstalled(): Boolean = complete(installed)

    fun root(): File = installed.also {
        if (!isInstalled()) throw PackProblem("Install the Common Tongue test pack first.")
    }

    private fun complete(root: File): Boolean =
        try {
            File(root, "install.complete").readText() == contract.sha256 &&
                File(root, "provision.json").isFile &&
                contract.files.all {
                    File(root, it.path).let { file -> file.isFile && file.length() == it.bytes }
                }
        } catch (_: IOException) {
            false
        }

    fun install(
        input: InputStream,
        freeSpace: () -> Long,
        cancelled: () -> Boolean,
        progress: (Int) -> Unit,
    ): Unit =
        synchronized(importLock) {
            recover()
            val total = contract.files.sumOf { it.bytes }
            if (freeSpace() < total + reserve) throw PackProblem("Not enough free space.")
            if (!incoming.mkdirs())
                throw PackProblem("Installation could not start. Please try again.")
            val archiveDigest = MessageDigest.getInstance("SHA-256")
            val counted = CountingInput(input, contract.bytes)
            val digested = DigestInputStream(counted, archiveDigest)
            var copied = 0L
            val buffer = ByteArray(1024 * 1024)
            fun checkCancelled() {
                if (cancelled() || Thread.currentThread().isInterrupted)
                    throw PackProblem(
                        "Installation was interrupted. Tap Install Test Pack to try again."
                    )
            }
            try {
                ZipInputStream(digested).use { archive ->
                    val first =
                        archive.nextEntry ?: throw PackProblem("This test pack is incomplete.")
                    if (first.name != "pack-manifest.json" || first.isDirectory)
                        throw PackProblem("This test pack is incomplete.")
                    val manifestOutput = ByteArrayOutputStream()
                    while (true) {
                        checkCancelled()
                        val size = archive.read(buffer, 0, 8192)
                        if (size < 0) break
                        if (manifestOutput.size() + size > 128 * 1024)
                            throw PackProblem("This test pack is incomplete.")
                        manifestOutput.write(buffer, 0, size)
                    }
                    if (hash(manifestOutput.toByteArray()) != contract.manifestSha256)
                        throw PackProblem(
                            "This test pack does not match this app. Download the matching APK and test pack."
                        )
                    archive.closeEntry()
                    val expected = contract.files.associateBy { "payload/" + it.path }
                    val seen = mutableSetOf<String>()
                    while (true) {
                        checkCancelled()
                        val entry = archive.nextEntry ?: break
                        val file =
                            expected[entry.name]
                                ?: throw PackProblem("This test pack is incomplete.")
                        if (entry.isDirectory || !seen.add(entry.name))
                            throw PackProblem("This test pack is incomplete.")
                        val destination = File(incoming, file.path)
                        check(
                            destination.canonicalPath.startsWith(
                                incoming.canonicalPath + File.separator
                            )
                        )
                        destination.parentFile!!.mkdirs()
                        var written = 0L
                        FileOutputStream(destination).use { output ->
                            while (true) {
                                checkCancelled()
                                val size = archive.read(buffer)
                                if (size < 0) break
                                written += size
                                if (written > file.bytes) throw PackProblem(damaged)
                                output.write(buffer, 0, size)
                                copied += size
                                progress(((copied * 100 / total).toInt()).coerceAtMost(99))
                            }
                            output.fd.sync()
                        }
                        if (written != file.bytes || hash(destination) != file.sha256)
                            throw PackProblem(damaged)
                        archive.closeEntry()
                    }
                    if (seen != expected.keys) throw PackProblem("This test pack is incomplete.")
                    // ZipInputStream may stop before the central directory. Hash every
                    // source byte too, so a truncated trailer is never accepted.
                    while (digested.read(buffer) >= 0) checkCancelled()
                    if (
                        counted.bytes != contract.bytes ||
                            hex(archiveDigest.digest()) != contract.sha256
                    )
                        throw PackProblem(damaged)
                }
                checkCancelled()
                durable(
                    File(incoming, "provision.json"),
                    "{\"schema_version\":1,\"verified_on_device\":true,\"pack_sha256\":\"${contract.sha256}\",\"active_asset_bytes\":$total}",
                )
                durable(File(incoming, "install.complete"), contract.sha256)
                previous.deleteRecursively()
                if (installed.exists() && !installed.renameTo(previous))
                    throw PackProblem("Installation was interrupted. Please try again.")
                if (!incoming.renameTo(installed)) {
                    previous.renameTo(installed)
                    throw PackProblem("Installation was interrupted. Please try again.")
                }
                previous.deleteRecursively()
                progress(100)
            } catch (error: Exception) {
                incoming.deleteRecursively()
                if (error is PackProblem) throw error
                if (freeSpace() < reserve) throw PackProblem("Not enough free space.")
                throw PackProblem(damaged)
            }
        }

    fun verifyInstalled(cancelled: () -> Boolean = { false }) {
        val root = root()
        for (item in contract.files) {
            if (cancelled())
                throw PackProblem("Installation check was interrupted. Please try again.")
            if (hash(File(root, item.path)) != item.sha256) throw PackProblem(damaged)
        }
    }

    private fun durable(file: File, text: String) =
        FileOutputStream(file).use { output ->
            output.write(text.toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }

    private fun hash(file: File): String =
        file.inputStream().use { stream ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val size = stream.read(buffer)
                if (size < 0) break
                digest.update(buffer, 0, size)
            }
            hex(digest.digest())
        }

    private fun hash(bytes: ByteArray): String =
        hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private class CountingInput(stream: InputStream, private val limit: Long) :
        FilterInputStream(stream) {
        var bytes = 0L

        private fun checkLimit() {
            if (bytes > limit)
                throw PackProblem("The file was damaged during download. Please download it again.")
        }

        override fun read(): Int =
            super.read().also {
                if (it >= 0) bytes++
                checkLimit()
            }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            `in`.read(buffer, offset, length).also {
                if (it > 0) bytes += it
                checkLimit()
            }
    }
}
