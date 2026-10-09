package com.commontongue.prototype.platform

import android.Manifest
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** SDK recorder integration on a dedicated emulator; no unattended physical-device capture. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 28)
class MicrophoneIntegrationTest {
    @Test
    fun androidRecorderWritesTheLockedPcmFormatAndDeletesAfterUse() {
        assumeTrue("Automatic recording is emulator-only", Build.MODEL.startsWith("sdk_gphone"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.RECORD_AUDIO,
        )
        ActivityScenario.launch(MainActivity::class.java).use {
            runBlocking {
                withTimeout(5000) {
                    val microphone = MicrophoneInput(context)
                    val released = CompletableDeferred<Unit>()
                    val started = CompletableDeferred<Unit>()
                    val captured = async { microphone.capture(released) { started.complete(Unit) } }
                    started.await()
                    delay(400)
                    released.complete(Unit)
                    val reference = captured.await()
                    try {
                        val file = microphone.resolve(reference)
                        val header =
                            ByteBuffer.wrap(file.readBytes().copyOf(44))
                                .order(ByteOrder.LITTLE_ENDIAN)
                        assertEquals(16000, header.getInt(24))
                        assertEquals(1, header.getShort(22).toInt())
                        assertEquals(16, header.getShort(34).toInt())
                        assertTrue(file.length() > 6444)
                        assertTrue(file.length() <= MicrophoneInput.MAXIMUM_BYTES + 44)
                        microphone.discard(reference)
                        assertFalse(file.exists())
                    } finally {
                        microphone.discard(reference)
                    }
                }
            }
        }
    }
}
