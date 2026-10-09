package com.commontongue.spike.device

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal fun readPcm(file: File): FloatArray {
    check(file.length() in 44..(16000 * 2 * 31).toLong()) { "WAV file exceeds research limit" }
    val data = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
    check(
        String(data.array(), 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(data.array(), 8, 4, Charsets.US_ASCII) == "WAVE"
    )
    data.position(12)
    var valid = false
    while (data.remaining() >= 8) {
        val name = ByteArray(4).also { data.get(it) }.toString(Charsets.US_ASCII)
        val size = data.int
        check(size >= 0 && size <= data.remaining())
        val end = data.position() + size
        if (name == "fmt ") {
            check(size >= 16)
            val format = data.short.toInt()
            val channels = data.short.toInt()
            val rate = data.int
            data.int
            data.short
            check(format == 1 && channels == 1 && rate == 16000 && data.short.toInt() == 16) {
                "Expected mono 16 kHz PCM16"
            }
            valid = true
        } else if (name == "data") {
            check(valid && size % 2 == 0)
            return FloatArray(size / 2) { data.short / 32768f }
        }
        check(end + size % 2 <= data.limit())
        data.position(end + size % 2)
    }
    error("Missing WAV PCM data")
}
