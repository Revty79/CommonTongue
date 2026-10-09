package com.commontongue.spike.device

/** Exports only linker tokens, never the raw message, paths, or device identifiers. */
internal object NativeLinkDiagnostic {
    private val libraries =
        setOf(
            "libdevice_trial.so",
            "libtrial_t5.so",
            "libonnxruntime.so",
            "libonnxruntime4j_jni.so",
            "libandroid.so",
            "liblog.so",
            "libm.so",
            "libdl.so",
            "libc.so",
            "libc++_shared.so",
        )
    private val libraryPattern = Regex("library \"([^\"\\r\\n]{1,1024})\" not found")
    private val symbolPattern = Regex("cannot locate symbol \"([A-Za-z_][A-Za-z0-9_.$@]{0,127})\"")
    private val symbolToken = Regex("[A-Za-z_][A-Za-z0-9_.$@]{0,127}")

    fun fromError(error: Throwable): Map<String, String> {
        // Initializer failures may wrap the actual UnsatisfiedLinkError.
        return generateSequence(error) { it.cause }
            .take(8)
            .filterIsInstance<LinkageError>()
            .map { fromMessage(it.message) }
            .firstOrNull { it.isNotEmpty() } ?: emptyMap()
    }

    fun fromMessage(message: String?): Map<String, String> {
        if (message == null) return emptyMap()
        val library =
            libraryPattern
                .find(message)
                ?.groupValues
                ?.get(1)
                ?.substringAfterLast('/')
                ?.substringAfterLast('\\')
        val symbol = symbolPattern.find(message)?.groupValues?.get(1)
        return validate(library, symbol)
    }

    fun validate(library: String?, symbol: String?): Map<String, String> = buildMap {
        if (library in libraries) put("native_missing_library", library!!)
        if (symbol != null && symbolToken.matches(symbol)) put("native_missing_symbol", symbol)
    }
}
