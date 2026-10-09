package com.commontongue.local.android

internal object Native {
    init {
        System.loadLibrary("common_tongue_ai")
    }

    external fun asrCreate(path: String): Long

    external fun asrRun(handle: Long, samples: FloatArray, language: String): String

    external fun asrClose(handle: Long)

    external fun t5Load(directory: String): String

    external fun t5Run(handle: Long, request: String, progress: NativeProgress): String

    external fun t5Close(handle: Long)
}

internal fun interface NativeProgress {
    fun onStage(stage: Int)
}
