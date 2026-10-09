package com.commontongue.local.android

import com.commontongue.local.LocalFailure
import com.commontongue.local.LocalFault
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object PcmInput {
    fun read(file: File): FloatArray {
        if (!file.isFile || file.length() !in 44L..(16000L * 2 * 31)) invalid()
        val bytes = file.readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        fun tag(at: Int) = String(bytes, at, 4, Charsets.US_ASCII)
        if (tag(0) != "RIFF" || tag(8) != "WAVE") invalid()
        var position = 12
        var format = false
        var audio: FloatArray? = null
        while (position + 8 <= bytes.size) {
            val length = buffer.getInt(position + 4)
            val begin = position + 8
            if (length < 0 || length > bytes.size - begin) invalid()
            when (tag(position)) {
                "fmt " -> {
                    if (
                        length < 16 ||
                            buffer.getShort(begin).toInt() != 1 ||
                            buffer.getShort(begin + 2).toInt() != 1 ||
                            buffer.getInt(begin + 4) != 16000 ||
                            buffer.getShort(begin + 14).toInt() != 16
                    )
                        invalid()
                    format = true
                }
                "data" -> {
                    if (audio != null || length == 0 || length % 2 != 0) invalid()
                    audio = FloatArray(length / 2) { buffer.getShort(begin + it * 2) / 32768f }
                }
            }
            position = begin + length + (length % 2)
        }
        if (!format) invalid()
        return audio ?: invalid()
    }

    private fun invalid(): Nothing = throw LocalFault(LocalFailure.INPUT_INVALID)
}
