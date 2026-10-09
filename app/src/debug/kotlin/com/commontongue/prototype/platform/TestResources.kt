package com.commontongue.prototype.platform

import android.content.Context
import android.os.StatFs
import com.commontongue.local.CoreResourceRole
import com.commontongue.local.LocalFailure
import com.commontongue.local.LocalFault
import com.commontongue.local.ResourceIdentity
import com.commontongue.local.ResourceIntegrity
import com.commontongue.local.android.FileCoreResources
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal class SetupFault(val plain: String) : Exception(plain)

/** Debug acquisition only: one immutable pack, no user content, no inference dependency on HTTP. */
internal class TestResources(context: Context) {
    private val app = context.applicationContext
    private val root = File(app.filesDir, "local-core")
    private val lock =
        JSONObject(app.assets.open("pass7/resources.json").bufferedReader().use { it.readText() })
    private val roles = CoreResourceRole.entries.associateWith { it.name.lowercase() }
    val source = FileCoreResources("debug-core-v1", roles.mapValues { File(root, it.value) })

    fun fixture(token: String): File {
        if (token !in setOf("control_en", "control_es"))
            throw LocalFault(LocalFailure.INPUT_INVALID)
        return File(root, token)
    }

    fun installed() = File(root, "installed").isFile

    suspend fun install(progress: (String) -> Unit) =
        withContext(Dispatchers.IO) {
            if (installed()) {
                try {
                    source.validated()
                    verifyFixtures(root)
                    return@withContext
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    /* Retry a damaged installation transactionally. */
                }
            }
            val archiveBytes = lock.getLong("archive_bytes")
            if (
                StatFs(app.filesDir.absolutePath).availableBytes <
                    archiveBytes * 2 + 512L * 1024 * 1024
            )
                throw SetupFault("Not enough free space. Free about 4 GB and try again.")
            val archive = File(app.filesDir, "test-resources-download")
            val staging = File(app.filesDir, "local-core-staging")
            try {
                staging.deleteRecursively()
                staging.mkdirs()
                download(archive, progress)
                progress("Checking the download…")
                ResourceIntegrity.verify(
                    archive,
                    ResourceIdentity(archiveBytes, lock.getString("archive_sha256")),
                )
                val files = lock.getJSONObject("files")
                val metadata = lock.getJSONObject("metadata")
                val expected =
                    (files.keys().asSequence().toSet() + metadata.keys().asSequence().toSet())
                        .associateWith { name ->
                            val row =
                                if (files.has(name)) files.getJSONObject(name)
                                else metadata.getJSONObject(name)
                            ResourceIdentity(row.getLong("bytes"), row.getString("sha256"))
                        }
                com.commontongue.local.FlatResourceInstaller.install(archive, root, expected) {
                    copied ->
                    progress(
                        "Installing test resources: ${(copied * 100 / archiveBytes).coerceAtMost(100)}%"
                    )
                }
                progress("Installation complete — ready for checks.")
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (fault: SetupFault) {
                throw fault
            } catch (_: LocalFault) {
                throw SetupFault("The file was damaged during download. Please try again.")
            } catch (_: Exception) {
                throw SetupFault("Model download failed — try again.")
            } finally {
                withContext(NonCancellable) {
                    archive.delete()
                    staging.deleteRecursively()
                }
            }
        }

    private suspend fun verifyFixtures(directory: File) {
        for (name in listOf("control_en", "control_es")) {
            val row = lock.getJSONObject("files").getJSONObject(name)
            ResourceIntegrity.verify(
                File(directory, name),
                ResourceIdentity(row.getLong("bytes"), row.getString("sha256")),
            )
        }
    }

    private suspend fun download(output: File, progress: (String) -> Unit) {
        var location = URI(lock.getString("url"))
        repeat(8) {
            if (
                location.scheme != "https" ||
                    location.host !in
                        setOf(
                            "github.com",
                            "release-assets.githubusercontent.com",
                            "objects.githubusercontent.com",
                        )
            )
                throw SetupFault("Model download failed — try again.")
            val connection = location.toURL().openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 30_000
            connection.readTimeout = 30_000
            try {
                if (connection.responseCode in 300..399) {
                    location = location.resolve(connection.getHeaderField("Location"))
                } else {
                    if (connection.responseCode != 200)
                        throw SetupFault("Model download failed — try again.")
                    val maximum = lock.getLong("archive_bytes")
                    var downloaded = 0L
                    connection.inputStream.buffered().use { incoming ->
                        output.outputStream().buffered().use { out ->
                            val buffer = ByteArray(1024 * 1024)
                            var count = incoming.read(buffer)
                            while (count != -1) {
                                currentCoroutineContext().ensureActive()
                                downloaded += count
                                if (downloaded > maximum)
                                    throw SetupFault(
                                        "The file was damaged during download. Please try again."
                                    )
                                out.write(buffer, 0, count)
                                progress(
                                    "Downloading test resources — ${(downloaded * 100 / maximum).coerceAtMost(100)}%"
                                )
                                count = incoming.read(buffer)
                            }
                        }
                    }
                    return
                }
            } finally {
                connection.disconnect()
            }
        }
        throw SetupFault("Model download failed — try again.")
    }
}
