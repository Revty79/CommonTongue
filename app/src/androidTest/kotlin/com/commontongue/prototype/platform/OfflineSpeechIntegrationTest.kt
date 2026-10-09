package com.commontongue.prototype.platform

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.commontongue.domain.LanguageId
import com.commontongue.speech.android.AndroidSpeechSynthesizer
import com.commontongue.speech.android.SpeechDiagnostic
import com.commontongue.speech.android.SpeechStage
import com.commontongue.translation.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opt-in device integration: requires installed offline EN/ES; a skip is not physical acceptance.
 */
@RunWith(AndroidJUnit4::class)
class OfflineSpeechIntegrationTest {
    @Test
    fun productionContractSynthesizesAndReplaysEnglishAndSpanish() {
        ActivityScenario.launch(MainActivity::class.java).use {
            runBlocking {
                withContext(Dispatchers.Main) {
                    val context = InstrumentationRegistry.getInstrumentation().targetContext
                    val events = mutableListOf<SpeechDiagnostic>()
                    val speech = AndroidSpeechSynthesizer(context) { events.add(it) }
                    try {
                        speech.setForeground(true)
                        val catalog = speech.installedVoices()
                        assumeTrue(
                            "Install an offline TTS engine",
                            catalog is CapabilityResult.Success,
                        )
                        val voices = (catalog as CapabilityResult.Success).value
                        assumeTrue(
                            "Install offline EN and ES voices",
                            listOf("en", "es").all { language ->
                                voices.any {
                                    it.locale.baseLanguage == language &&
                                        it.installed &&
                                        !it.requiresNetwork
                                }
                            },
                        )
                        withTimeout(180000) {
                            val contract: SpeechSynthesizer = speech
                            for ((language, text) in
                                listOf(
                                    "en" to "Offline English voice check.",
                                    "es-419" to "Prueba de voz en español sin conexión.",
                                )) {
                                val result =
                                    contract.synthesize(
                                        SynthesisRequest(text, LanguageId.parse(language))
                                    )
                                assertTrue(result is CapabilityResult.Success)
                                val audio = (result as CapabilityResult.Success).value.audio
                                assertEquals(ExecutionMode.OFFLINE, result.executionMode)
                                assertTrue(speech.play(audio) is CapabilityResult.Success)
                                assertTrue(speech.play(audio) is CapabilityResult.Success)
                            }
                        }
                        assertTrue(
                            events.any {
                                it.stage == SpeechStage.PLAYBACK_START && (it.frames ?: 0) > 0
                            }
                        )
                        assertTrue(
                            events
                                .filter { it.stage == SpeechStage.VOICE_SELECTED }
                                .all { it.requiresNetwork == false }
                        )
                        val missing =
                            speech.synthesize(SynthesisRequest("fixed", LanguageId.parse("zz")))
                        assertTrue(missing is CapabilityResult.Failure)
                    } finally {
                        speech.close()
                    }
                }
            }
        }
    }
}
