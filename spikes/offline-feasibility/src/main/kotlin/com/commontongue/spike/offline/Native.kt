package com.commontongue.spike.offline

internal object Native {
    init {
        System.loadLibrary("feasibility")
    }

    external fun buildInfo(): String

    external fun asrCreate(path: String): Long

    external fun asrRun(handle: Long, audio: FloatArray, language: String): String

    external fun asrClose(handle: Long)

    external fun tokenizerCreate(path: String): Long

    external fun encode(handle: Long, text: String): Array<String>

    external fun decode(handle: Long, pieces: Array<String>): String

    external fun tokenizerClose(handle: Long)
}
