package com.commontongue.local.android

import com.commontongue.local.CoreResourceRole
import com.commontongue.local.CoreResourceSource
import com.commontongue.local.LocalFailure
import com.commontongue.local.LocalFault
import com.commontongue.local.LockedCore
import com.commontongue.local.ResourceIntegrity
import com.commontongue.local.ValidatedCoreResources
import com.commontongue.translation.AudioReference
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Swappable resource layer: adapters do not know acquisition source or on-disk layout. */
interface AndroidResourceBindings : CoreResourceSource {
    fun resolve(snapshot: ValidatedCoreResources): Map<CoreResourceRole, File>
}

fun interface LocalAudioSource {
    /** The input controller owns these local files; inference does not acquire audio. */
    fun resolve(reference: AudioReference): File
}

class FileCoreResources(
    private val token: String,
    private val files: Map<CoreResourceRole, File>,
) : AndroidResourceBindings {
    override suspend fun validated(): ValidatedCoreResources =
        withContext(Dispatchers.IO) {
            if (files.keys != LockedCore.identities.keys)
                throw LocalFault(LocalFailure.MISSING_RESOURCES)
            for ((role, identity) in LockedCore.identities) ResourceIntegrity.verify(
                files.getValue(role),
                identity,
            )
            ValidatedCoreResources(token, LockedCore.VERSION, LockedCore.identities)
        }

    override fun resolve(snapshot: ValidatedCoreResources): Map<CoreResourceRole, File> {
        snapshot.verifyIdentity()
        if (snapshot.storageToken != token) throw LocalFault(LocalFailure.WRONG_RESOURCES)
        return files.toMap()
    }
}
