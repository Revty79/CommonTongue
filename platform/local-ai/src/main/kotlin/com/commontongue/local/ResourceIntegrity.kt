package com.commontongue.local

import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Resource-layer helper. Paths never enter a capability result or diagnostic. */
object ResourceIntegrity {
    suspend fun verify(file: File, expected: ResourceIdentity) {
        if (!file.isFile || !file.canRead()) throw LocalFault(LocalFailure.MISSING_RESOURCES)
        if (file.length() != expected.bytes) throw LocalFault(LocalFailure.WRONG_RESOURCES)
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { stream ->
            val bytes = ByteArray(1024 * 1024)
            var count = stream.read(bytes)
            while (count != -1) {
                currentCoroutineContext().ensureActive()
                hash.update(bytes, 0, count)
                count = stream.read(bytes)
            }
        }
        val actual = hash.digest().joinToString("") { "%02x".format(it) }
        if (actual != expected.sha256) throw LocalFault(LocalFailure.WRONG_RESOURCES)
    }
}
