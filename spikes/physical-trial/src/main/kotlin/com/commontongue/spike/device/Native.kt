package com.commontongue.spike.device

internal object Native {
    init {
        System.loadLibrary("device_trial")
    }

    external fun buildInfo(): String

    external fun asrCreate(path: String): Long

    external fun asrRun(handle: Long, audio: FloatArray, language: String): String

    external fun asrClose(handle: Long)

    external fun tokenizerCreate(path: String): Long

    external fun encode(handle: Long, text: String): Array<String>

    external fun decode(handle: Long, pieces: Array<String>): String

    external fun tokenizerClose(handle: Long)

    external fun t5Load(path: String): String

    external fun t5Run(handle: Long, request: String, progress: TranslationProgress): String

    external fun t5Close(handle: Long)
}
