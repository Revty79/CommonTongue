package com.commontongue.local

import com.commontongue.domain.LanguageId
import com.commontongue.domain.TranslationDirection
import com.commontongue.translation.*
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocalAdaptersTest {
    private val en = LanguageId.parse("en")
    private val es = LanguageId.parse("es")
    private val direction = TranslationDirection(en, es)
    private val snapshot =
        ValidatedCoreResources("owned", LockedCore.VERSION, LockedCore.identities)

    private fun recognition(language: LanguageId = en) =
        RecognitionRequest(AudioReference.of("audio"), LanguageSelection.Locked(language))

    private fun translation(text: String = "fixed", pair: TranslationDirection = direction) =
        TranslationRequest(text, pair)

    private class Fake : LocalRuntime {
        override var isOffline = true
        var loads = 0
        var releases = 0
        var recognitions = 0
        var translations = 0
        var load: suspend () -> Unit = {}
        var recognize: suspend (AudioReference, LanguageId) -> NativeRecognition = { _, l ->
            NativeRecognition(" transcript ", l, true)
        }
        var translate: suspend (String, TranslationDirection) -> NativeTranslation = { _, d ->
            NativeTranslation("output", d, true)
        }
        var cleanup: suspend () -> Unit = {}

        override suspend fun load(resources: ValidatedCoreResources) {
            loads++
            load()
        }

        override suspend fun recognize(
            audio: AudioReference,
            language: LanguageId,
        ): NativeRecognition {
            recognitions++
            return recognize.invoke(audio, language)
        }

        override suspend fun translate(
            text: String,
            direction: TranslationDirection,
        ): NativeTranslation {
            translations++
            return translate.invoke(text, direction)
        }

        override suspend fun release() {
            releases++
            cleanup()
        }
    }

    private fun session(
        runtime: Fake = Fake(),
        source: CoreResourceSource = CoreResourceSource { snapshot },
        sink: (LocalDiagnostic) -> Unit = {},
    ) = LocalAiSession(source, runtime, sink)

    private fun category(result: CapabilityResult<*>) =
        (result as CapabilityResult.Failure).failure.category

    @Test
    fun lockedLanguagesAndOpaqueAudioAreMapped() = runTest {
        val runtime = Fake()
        val recognizer = WhisperSpeechRecognizer(session(runtime))
        for (language in listOf(en, es, LanguageId.parse("en-US"), LanguageId.parse("es-419"))) {
            runtime.recognize = { a, l ->
                assertEquals("audio", a.token)
                assertEquals(language.baseLanguage, l.tag)
                NativeRecognition(" stable ", l, true)
            }
            val result = recognizer.recognize(recognition(language)) as CapabilityResult.Success
            assertEquals("stable", result.value.text)
            assertEquals(language.baseLanguage, result.value.languageUsed.tag)
            assertEquals(CompletionStatus.COMPLETE, result.value.completion)
            assertEquals(ExecutionMode.OFFLINE, result.executionMode)
            assertEquals(ConfidenceEvidence.Unavailable, result.value.confidence)
        }
        assertEquals(1, runtime.loads)
    }

    @Test
    fun automaticAndVocabularyAreRejectedBeforeLoad() = runTest {
        val runtime = Fake()
        val recognizer = WhisperSpeechRecognizer(session(runtime))
        assertEquals(
            FailureCategory.UNSUPPORTED_CAPABILITY,
            category(
                recognizer.recognize(
                    RecognitionRequest(AudioReference.of("a"), LanguageSelection.Automatic())
                )
            ),
        )
        assertEquals(
            FailureCategory.UNSUPPORTED_CAPABILITY,
            category(
                recognizer.recognize(
                    RecognitionRequest(
                        AudioReference.of("a"),
                        LanguageSelection.Locked(en),
                        listOf("hint"),
                    )
                )
            ),
        )
        assertEquals(0, runtime.loads)
    }

    @Test
    fun unsupportedLanguageFailsBeforeLoad() = runTest {
        val runtime = Fake()
        val session = session(runtime)
        assertEquals(
            FailureCategory.LANGUAGE_NOT_SUPPORTED,
            category(
                WhisperSpeechRecognizer(session).recognize(recognition(LanguageId.parse("fr")))
            ),
        )
        assertEquals(
            FailureCategory.LANGUAGE_NOT_SUPPORTED,
            category(
                MadladTranslator(session)
                    .translate(translation(pair = TranslationDirection(en, LanguageId.parse("fr"))))
            ),
        )
        assertEquals(0, runtime.loads)
    }

    @Test
    fun directionAndSourceArePassedUnchanged() = runTest {
        val runtime = Fake()
        val translator = MadladTranslator(session(runtime))
        for (pair in listOf(direction, direction.reversed())) {
            runtime.translate = { text, d ->
                assertEquals("fixed", text)
                assertEquals(pair, d)
                NativeTranslation("result", d, true)
            }
            val result = translator.translate(translation(pair = pair)) as CapabilityResult.Success
            assertEquals(pair, result.value.directionUsed)
            assertEquals(ExecutionMode.OFFLINE, result.executionMode)
            assertEquals(VerificationState.Unverified, result.value.verification)
            assertTrue(result.value.ambiguities.isEmpty())
            assertEquals(TranslationRequirement.entries.toSet(), result.support.unsupportedPolicies)
        }
        assertEquals(1, runtime.loads)
    }

    @Test
    fun partialResultsAreNotReportedAsComplete() = runTest {
        val runtime = Fake()
        val session = session(runtime)
        runtime.translate = { _, d -> NativeTranslation("partial", d, false) }
        runtime.recognize = { _, l -> NativeRecognition("partial", l, false) }
        assertEquals(
            CompletionStatus.PARTIAL,
            (MadladTranslator(session).translate(translation()) as CapabilityResult.Success)
                .value
                .completion,
        )
        assertEquals(
            CompletionStatus.PARTIAL,
            (WhisperSpeechRecognizer(session).recognize(recognition()) as CapabilityResult.Success)
                .value
                .completion,
        )
    }

    @Test
    fun incorrectNativeDirectionAndLanguageAreRejected() = runTest {
        val runtime = Fake()
        val session = session(runtime)
        runtime.translate = { _, _ -> NativeTranslation("out", direction.reversed(), true) }
        runtime.recognize = { _, _ -> NativeRecognition("out", es, true) }
        assertEquals(
            FailureCategory.INFERENCE_FAILED,
            category(MadladTranslator(session).translate(translation())),
        )
        assertEquals(
            FailureCategory.INFERENCE_FAILED,
            category(WhisperSpeechRecognizer(session).recognize(recognition())),
        )
        assertEquals(2, runtime.releases)
    }

    @Test
    fun emptyNativeResultsAreRejected() = runTest {
        val runtime = Fake()
        val session = session(runtime)
        runtime.translate = { _, d -> NativeTranslation(" ", d, true) }
        runtime.recognize = { _, l -> NativeRecognition(" ", l, true) }
        assertEquals(
            FailureCategory.INFERENCE_FAILED,
            category(MadladTranslator(session).translate(translation())),
        )
        assertEquals(
            FailureCategory.INFERENCE_FAILED,
            category(WhisperSpeechRecognizer(session).recognize(recognition())),
        )
    }

    @Test
    fun unsupportedFeaturesAreReportedWithoutApplyingHints() = runTest {
        val runtime = Fake()
        val translator = MadladTranslator(session(runtime))
        val context =
            ConversationContext.recent(
                listOf(ConversationTurn(ConversationSide.FIRST, "private", "private", direction))
            )
        runtime.translate = { text, d ->
            assertEquals("fixed", text)
            NativeTranslation("out", d, true)
        }
        val result =
            translator.translate(
                TranslationRequest(
                    "fixed",
                    direction,
                    context,
                    listOf(TerminologyHint("private", "private")),
                    DomainContext.of("medical"),
                    RegionalPreference(LanguageId.parse("es-MX")),
                )
            ) as CapabilityResult.Success
        assertEquals(
            setOf(
                CapabilityFeature.CONVERSATION_CONTEXT,
                CapabilityFeature.TERMINOLOGY_HINTS,
                CapabilityFeature.DOMAIN_CONTEXT,
                CapabilityFeature.REGIONAL_PREFERENCES,
            ),
            result.support.unsupportedFeatures,
        )
        assertFalse(result.issues.joinToString().contains("private"))
    }

    @Test
    fun rejectPolicyAndRequiredTerminologyNeverCallNative() = runTest {
        val runtime = Fake()
        val translator = MadladTranslator(session(runtime))
        assertEquals(
            FailureCategory.UNSUPPORTED_CAPABILITY,
            category(
                translator.translate(
                    TranslationRequest(
                        "fixed",
                        direction,
                        policy =
                            TranslationPolicy(unsupportedHandling = UnsupportedHandling.REJECT),
                    )
                )
            ),
        )
        assertEquals(
            FailureCategory.UNSUPPORTED_CAPABILITY,
            category(
                translator.translate(
                    TranslationRequest(
                        "fixed",
                        direction,
                        terminology =
                            listOf(
                                TerminologyHint("x", "y", strength = TerminologyStrength.REQUIRED)
                            ),
                    )
                )
            ),
        )
        assertEquals(0, runtime.loads)
    }

    @Test
    fun invalidAndOversizedSourceNeverLoadsModels() = runTest {
        val runtime = Fake()
        val translator = MadladTranslator(session(runtime))
        assertEquals(
            FailureCategory.INPUT_INVALID,
            category(translator.translate(translation(" "))),
        )
        assertEquals(
            FailureCategory.INPUT_TOO_LONG,
            category(translator.translate(translation("a".repeat(16001)))),
        )
        assertEquals(
            FailureCategory.INPUT_INVALID,
            category(
                translator.translate(
                    TranslationRequest(
                        "fixed",
                        direction,
                        regionalPreference = RegionalPreference(en),
                    )
                )
            ),
        )
        assertEquals(0, runtime.loads)
    }

    @Test
    fun offlinePreflightRejectsUntrustedBackendWithoutAcquisition() = runTest {
        val runtime = Fake().apply { isOffline = false }
        var acquisitions = 0
        val session =
            session(
                runtime,
                CoreResourceSource {
                    acquisitions++
                    snapshot
                },
            )
        assertEquals(
            FailureCategory.OFFLINE_REQUIREMENT_NOT_MET,
            category(MadladTranslator(session).translate(translation())),
        )
        assertEquals(0, acquisitions)
        assertEquals(0, runtime.loads)
    }

    @Test
    fun missingResourcesAreStructuredWithoutFallback() = runTest {
        val runtime = Fake()
        val session =
            session(
                runtime,
                CoreResourceSource { throw LocalFault(LocalFailure.MISSING_RESOURCES) },
            )
        assertEquals(
            FailureCategory.MODEL_NOT_INSTALLED,
            category(WhisperSpeechRecognizer(session).recognize(recognition())),
        )
        assertEquals(0, runtime.loads)
    }

    @Test
    fun wrongResourceVersionHashAndStorageIdentityAreRejected() = runTest {
        for (wrong in
            listOf(
                ValidatedCoreResources("owned", "wrong", LockedCore.identities),
                ValidatedCoreResources("../private", LockedCore.VERSION, LockedCore.identities),
                ValidatedCoreResources(
                    "owned",
                    LockedCore.VERSION,
                    LockedCore.identities - CoreResourceRole.TOKENIZER,
                ),
            )) {
            val runtime = Fake()
            val session = session(runtime, CoreResourceSource { wrong })
            assertEquals(
                FailureCategory.MODEL_NOT_INSTALLED,
                category(MadladTranslator(session).translate(translation())),
            )
            assertEquals(0, runtime.loads)
        }
    }

    @Test
    fun initializationFailureAllowsCleanRetry() = runTest {
        val runtime =
            Fake().apply { load = { if (loads == 1) throw LocalFault(LocalFailure.LOAD_FAILED) } }
        val translator = MadladTranslator(session(runtime))
        assertEquals(
            FailureCategory.ENGINE_NOT_AVAILABLE,
            category(translator.translate(translation())),
        )
        assertTrue(translator.translate(translation()) is CapabilityResult.Success)
        assertEquals(2, runtime.loads)
        assertEquals(1, runtime.releases)
    }

    @Test
    fun nativeExceptionsNeverExposeMessagesAndReloadOnRetry() = runTest {
        val runtime = Fake()
        val events = mutableListOf<LocalDiagnostic>()
        val translator = MadladTranslator(session(runtime, sink = events::add))
        runtime.translate = { _, _ ->
            throw UnsatisfiedLinkError("private text /private/path 0xff")
        }
        val result = translator.translate(translation())
        assertEquals(FailureCategory.INFERENCE_FAILED, category(result))
        assertFalse(result.toString().contains("private"))
        assertFalse(events.toString().contains("private"))
        runtime.translate = { _, d -> NativeTranslation("out", d, true) }
        assertTrue(translator.translate(translation()) is CapabilityResult.Success)
        assertEquals(2, runtime.loads)
    }

    @Test
    fun throwingDiagnosticSinkCannotBreakInference() = runTest {
        val translator = MadladTranslator(session(sink = { throw AssertionError("private") }))
        assertTrue(translator.translate(translation()) is CapabilityResult.Success)
    }

    @Test
    fun cancelWhisperReclaimsBeforeRetry() = runTest {
        val runtime = Fake()
        val session = session(runtime)
        val recognizer = WhisperSpeechRecognizer(session)
        runtime.recognize = { _, _ -> awaitCancellation() }
        val request = async { recognizer.recognize(recognition()) }
        runCurrent()
        request.cancelAndJoin()
        assertTrue(request.isCancelled)
        assertEquals(1, runtime.releases)
        runtime.recognize = { _, l -> NativeRecognition("retry", l, true) }
        assertTrue(recognizer.recognize(recognition()) is CapabilityResult.Success)
        assertEquals(2, runtime.loads)
    }

    @Test
    fun cancelTranslationReclaimsBeforeRetry() = runTest {
        val runtime = Fake()
        val session = session(runtime)
        val translator = MadladTranslator(session)
        runtime.translate = { _, _ -> awaitCancellation() }
        val request = async { translator.translate(translation()) }
        runCurrent()
        session.cancel()
        assertTrue(request.isCancelled)
        runtime.translate = { _, d -> NativeTranslation("retry", d, true) }
        assertTrue(translator.translate(translation()) is CapabilityResult.Success)
        assertEquals(2, runtime.loads)
    }

    @Test
    fun replacementRejectsStaleSuccessfulCompletion() = runTest {
        val runtime = Fake()
        val session = session(runtime)
        val translator = MadladTranslator(session)
        runtime.translate = { text, d ->
            if (text == "old") withContext(NonCancellable) { delay(100) }
            NativeTranslation(text, d, true)
        }
        val first = async { translator.translate(translation("old")) }
        runCurrent()
        val second = async { translator.translate(translation("new")) }
        advanceUntilIdle()
        assertTrue(first.isCancelled)
        assertEquals("new", (second.await() as CapabilityResult.Success).value.text)
        assertEquals(2, runtime.loads)
    }

    @Test
    fun closeWhileActiveReclaimsAndForbidsReuse() = runTest {
        val runtime = Fake()
        val session = session(runtime)
        val translator = MadladTranslator(session)
        runtime.translate = { _, _ -> awaitCancellation() }
        val first = async { translator.translate(translation()) }
        runCurrent()
        session.close()
        assertTrue(first.isCancelled)
        assertEquals(
            FailureCategory.ENGINE_NOT_AVAILABLE,
            category(translator.translate(translation())),
        )
        assertEquals(1, runtime.loads)
    }

    @Test
    fun backgroundReclaimsAndForegroundReloads() = runTest {
        val runtime = Fake()
        val session = session(runtime)
        val translator = MadladTranslator(session)
        assertTrue(translator.translate(translation()) is CapabilityResult.Success)
        session.setForeground(false)
        assertEquals(FailureCategory.CANCELLED, category(translator.translate(translation())))
        session.setForeground(true)
        assertTrue(translator.translate(translation()) is CapabilityResult.Success)
        assertEquals(2, runtime.loads)
    }

    @Test
    fun repeatedOperationsShareResidencyAndExplicitReleaseReloads() = runTest {
        val runtime = Fake()
        val session = session(runtime)
        repeat(5) {
            assertTrue(
                WhisperSpeechRecognizer(session).recognize(recognition())
                    is CapabilityResult.Success
            )
            assertTrue(
                MadladTranslator(session).translate(translation()) is CapabilityResult.Success
            )
        }
        assertEquals(1, runtime.loads)
        session.release()
        session.prepare()
        assertEquals(2, runtime.loads)
    }

    @Test
    fun cleanupFailureNeverAllowsUnconfirmedOwnerReuse() = runTest {
        val runtime = Fake()
        val session = session(runtime)
        val translator = MadladTranslator(session)
        runtime.translate = { _, _ -> throw LocalFault(LocalFailure.WORKER_EXIT) }
        runtime.cleanup = { throw IllegalStateException("private") }
        assertEquals(
            FailureCategory.INFERENCE_FAILED,
            category(translator.translate(translation())),
        )
        assertEquals(
            FailureCategory.ENGINE_NOT_AVAILABLE,
            category(translator.translate(translation())),
        )
        assertEquals(1, runtime.loads)
    }

    @Test
    fun alreadyCancelledCallerDoesNoWork() = runTest {
        val runtime = Fake()
        val translator = MadladTranslator(session(runtime))
        val request = launch(start = CoroutineStart.LAZY) { translator.translate(translation()) }
        request.cancel()
        request.join()
        assertEquals(0, runtime.loads)
    }

    @Test
    fun resourceIntegrityDetectsSameLengthCorruptionAndMissingFile() = runTest {
        val directory = kotlin.io.path.createTempDirectory().toFile()
        try {
            val file = File(directory, "content").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val identity =
                ResourceIdentity(
                    3,
                    MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") {
                        "%02x".format(it)
                    },
                )
            ResourceIntegrity.verify(file, identity)
            file.writeBytes(byteArrayOf(3, 2, 1))
            try {
                ResourceIntegrity.verify(file, identity)
                fail("Damaged bytes accepted")
            } catch (fault: LocalFault) {
                assertEquals(LocalFailure.WRONG_RESOURCES, fault.reason)
            }
            file.delete()
            try {
                ResourceIntegrity.verify(file, identity)
                fail("Absent bytes accepted")
            } catch (fault: LocalFault) {
                assertEquals(LocalFailure.MISSING_RESOURCES, fault.reason)
            }
        } finally {
            directory.deleteRecursively()
        }
    }
}
