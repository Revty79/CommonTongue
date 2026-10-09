package com.commontongue.local

enum class CoreResourceRole {
    RECOGNIZER,
    TRANSLATOR,
    TOKENIZER,
    CONFIGURATION,
}

data class ResourceIdentity(val bytes: Long, val sha256: String)

/**
 * Immutable production identities; acquisition locations and filenames belong to the resource
 * layer.
 */
object LockedCore {
    const val VERSION = "offline-core-en-es-v1"
    const val WHISPER_RUNTIME = "d1be6fde11ac6e0407606b4e42fe72d34add8037"
    const val WHISPER_MODEL = "5359861c739e955e79d9a303bcbc70fb988958b1"
    const val MADLAD_MODEL = "fa184c675da0b5c9e1c8694fccd4e12e2d422094"
    const val CANDLE_RUNTIME = "31f35b147389700ed2a178ee66a91c3cc25cc80d"
    const val THREADS = 4
    val identities: Map<CoreResourceRole, ResourceIdentity> =
        mapOf(
            CoreResourceRole.RECOGNIZER to
                ResourceIdentity(
                    59707625,
                    "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898",
                ),
            CoreResourceRole.TRANSLATOR to
                ResourceIdentity(
                    1654597280,
                    "ea6e5531a3e95213c7f0635988d119e078a655c09306e47851e15d4c0c3f9c37",
                ),
            CoreResourceRole.TOKENIZER to
                ResourceIdentity(
                    16629031,
                    "a2799ccc696b752ba00c34f58726bfe253a04921ceb6cfc620400f560474790b",
                ),
            CoreResourceRole.CONFIGURATION to
                ResourceIdentity(
                    749,
                    "cad399cab799b99409a6ec2d90d72552257c2bb752861261d2016691e0643e7c",
                ),
        )
}

/** A resource provider must verify the physical bytes before issuing this snapshot. */
class ValidatedCoreResources(
    val storageToken: String,
    val version: String,
    identities: Map<CoreResourceRole, ResourceIdentity>,
) {
    val identities = identities.toMap()

    fun verifyIdentity() {
        if (
            !storageToken.matches(Regex("[a-zA-Z0-9_-]{1,96}")) ||
                version != LockedCore.VERSION ||
                identities != LockedCore.identities
        )
            throw LocalFault(LocalFailure.WRONG_RESOURCES)
    }
}

fun interface CoreResourceSource {
    /**
     * Validates content, not merely existence/filenames. Never downloads during an inference
     * request.
     */
    suspend fun validated(): ValidatedCoreResources
}
