package com.commontongue.translation

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class TranslateTextUseCaseTest {
    @Test
    fun integerOnlyChecksCannotCoverDecimalExpectations() = runTest {
        val verifier =
            FakeVerifier(
                answer = {
                    CapabilityResult.Success(
                        VerificationReport(
                            VerificationStatus.PASS,
                            checksPerformed = setOf(VerificationCheck.EXACT_INTEGER_LITERALS),
                        ),
                        ExecutionMode.OFFLINE,
                    )
                }
            )
        val request =
            TranslationRequest(
                "12.5",
                direction,
                criticalContent =
                    listOf(CriticalExpectation.NumberLiteral(NumericValue.parse("12.5"))),
            )
        assertTrue(
            TranslateTextUseCase(FakeTranslator(), verifier)(request)
                is TranslationOutcome.NeedsReview
        )
    }

    @Test
    fun failedSelectedVerificationDoesNotRetainTranslatorSuppliedPass() = runTest {
        val translator =
            FakeTranslator(
                answer = {
                    CapabilityResult.Success(
                        TranslatedText(
                            "output",
                            direction,
                            verification = VerificationState.Evaluated(passing()),
                        ),
                        ExecutionMode.OFFLINE,
                    )
                }
            )
        val verifier =
            FakeVerifier(
                answer = {
                    CapabilityResult.Failure(
                        CapabilityFailure(FailureCategory.ENGINE_NOT_AVAILABLE)
                    )
                }
            )
        val result =
            TranslateTextUseCase(translator, verifier)(request()) as TranslationOutcome.Failed
        assertSame(VerificationState.Unverified, result.candidate!!.verification)
    }

    private fun request() = TranslationRequest("source", direction)

    @Test
    fun fakeCapabilitiesOrchestrateAnEnrichedPass() = runTest {
        val translator = FakeTranslator()
        val verifier = FakeVerifier()
        val result =
            TranslateTextUseCase(translator, verifier)(request()) as TranslationOutcome.Complete
        assertEquals(1, translator.calls)
        assertEquals(1, verifier.calls)
        assertEquals("Salida de prueba", verifier.received!!.translatedText)
        assertEquals(
            VerificationStatus.PASS,
            (result.result.value.verification as VerificationState.Evaluated).report.status,
        )
        assertEquals(ExecutionMode.OFFLINE, result.result.executionMode)
    }

    @Test
    fun noContextIsForwardedWithoutInventingHistory() = runTest {
        val translator = FakeTranslator()
        val verifier = FakeVerifier()
        TranslateTextUseCase(translator, verifier)(request())
        assertSame(ConversationContext.None, translator.received!!.context)
        assertSame(ConversationContext.None, verifier.received!!.context)
    }

    @Test
    fun richRequestReachesTranslatorAndVerifier() = runTest {
        val context =
            ConversationContext.recent(
                listOf(ConversationTurn(ConversationSide.FIRST, "earlier", "antes", direction))
            )
        val content = listOf(CriticalExpectation.Prohibition("do not"))
        val request =
            TranslationRequest(
                "Do not start",
                direction,
                context,
                listOf(TerminologyHint("jack", targetMeaning = "vehicle lifting tool")),
                DomainContext.of("automotive"),
                RegionalPreference(com.commontongue.domain.LanguageId.parse("es-MX")),
                criticalContent = content,
            )
        val translator = FakeTranslator()
        val verifier = FakeVerifier()
        TranslateTextUseCase(translator, verifier)(request)
        assertSame(request, translator.received)
        assertSame(context, verifier.received!!.context)
        assertEquals(content, verifier.received!!.expectations)
        assertEquals("es-MX", translator.received!!.regionalPreference!!.locale.tag)
    }

    @Test
    fun verifierWarningsRemainVisible() = runTest {
        val warning = caution()
        val verifier =
            FakeVerifier(
                answer = {
                    CapabilityResult.Success(
                        VerificationReport(
                            VerificationStatus.PASS_WITH_WARNINGS,
                            listOf(warning),
                            setOf(VerificationCheck.SEMANTIC_CONTENT),
                        ),
                        ExecutionMode.OFFLINE,
                    )
                }
            )
        val result =
            TranslateTextUseCase(FakeTranslator(), verifier)(request())
                as TranslationOutcome.Complete
        assertTrue(warning in result.result.issues)
        assertEquals(
            VerificationStatus.PASS_WITH_WARNINGS,
            (result.result.value.verification as VerificationState.Evaluated).report.status,
        )
    }

    @Test
    fun verifierFailurePreservesCandidateAndFindings() = runTest {
        val issue =
            CapabilityIssue(
                IssueCode.NEGATION_MISMATCH,
                IssueSeverity.CRITICAL,
                IssueCertainty.OBSERVED,
            )
        val verifier =
            FakeVerifier(
                answer = {
                    CapabilityResult.Success(
                        VerificationReport(
                            VerificationStatus.FAIL,
                            listOf(issue),
                            setOf(VerificationCheck.SEMANTIC_CONTENT),
                        ),
                        ExecutionMode.OFFLINE,
                    )
                }
            )
        val result =
            TranslateTextUseCase(FakeTranslator(), verifier)(request()) as TranslationOutcome.Failed
        assertEquals(FailureCategory.VERIFICATION_FAILED, result.failure.category)
        assertEquals(TranslationStage.VERIFICATION, result.stage)
        assertEquals("Salida de prueba", result.candidate!!.text)
        assertTrue(issue in result.issues)
    }

    @Test
    fun needsReviewIsNotSilentlyPromotedToACompleteOutcome() = runTest {
        val verifier =
            FakeVerifier(
                answer = {
                    CapabilityResult.Success(
                        VerificationReport(VerificationStatus.NEEDS_REVIEW),
                        ExecutionMode.OFFLINE,
                    )
                }
            )
        assertTrue(
            TranslateTextUseCase(FakeTranslator(), verifier)(request())
                is TranslationOutcome.NeedsReview
        )
    }

    @Test
    fun unsupportedContextRegionalDomainAndPolicyOptionsAreReported() = runTest {
        val translator = FakeTranslator(CapabilityDescription(setOf(ExecutionMode.OFFLINE)))
        val request =
            TranslationRequest(
                "source",
                direction,
                ConversationContext.recent(
                    listOf(ConversationTurn(ConversationSide.FIRST, "earlier", "antes", direction))
                ),
                listOf(TerminologyHint("jack", targetTerm = "gato")),
                DomainContext.of("automotive"),
                RegionalPreference(com.commontongue.domain.LanguageId.parse("es-419")),
            )
        val result =
            TranslateTextUseCase(translator, FakeVerifier())(request) as TranslationOutcome.Complete
        assertEquals(
            setOf(
                CapabilityFeature.CONVERSATION_CONTEXT,
                CapabilityFeature.TERMINOLOGY_HINTS,
                CapabilityFeature.DOMAIN_CONTEXT,
                CapabilityFeature.REGIONAL_PREFERENCES,
            ),
            result.result.support.unsupportedFeatures,
        )
        assertEquals(
            TranslationRequirement.entries.toSet(),
            result.result.support.unsupportedPolicies,
        )
        assertTrue(result.result.issues.any { it.code == IssueCode.CONTEXT_UNAVAILABLE })
        assertTrue(result.result.issues.any { it.code == IssueCode.UNSUPPORTED_POLICY })
    }

    @Test
    fun strictUnsupportedHandlingRejectsBeforeInference() = runTest {
        val translator = FakeTranslator(CapabilityDescription(setOf(ExecutionMode.OFFLINE)))
        val verifier = FakeVerifier()
        val request =
            TranslationRequest(
                "source",
                direction,
                policy = TranslationPolicy(unsupportedHandling = UnsupportedHandling.REJECT),
            )
        val result =
            TranslateTextUseCase(translator, verifier)(request) as TranslationOutcome.Failed
        assertEquals(FailureCategory.UNSUPPORTED_CAPABILITY, result.failure.category)
        assertEquals(0, translator.calls)
        assertEquals(0, verifier.calls)
        assertTrue(result.support.hasUnsupported)
    }

    @Test
    fun adapterReportedUnsupportedFeaturesAreNotLost() = runTest {
        val translator =
            FakeTranslator(
                answer = {
                    CapabilityResult.Success(
                        TranslatedText("output", direction),
                        ExecutionMode.OFFLINE,
                        support = SupportReport(setOf(CapabilityFeature.CONVERSATION_CONTEXT)),
                    )
                }
            )
        val result =
            TranslateTextUseCase(translator, FakeVerifier())(request())
                as TranslationOutcome.Complete
        assertTrue(
            CapabilityFeature.CONVERSATION_CONTEXT in result.result.support.unsupportedFeatures
        )
    }

    @Test
    fun strictPolicyAlsoRejectsSupportWithdrawnAtRuntime() = runTest {
        val translator =
            FakeTranslator(
                answer = {
                    CapabilityResult.Success(
                        TranslatedText("output", direction),
                        ExecutionMode.OFFLINE,
                        support =
                            SupportReport(
                                unsupportedPolicies =
                                    setOf(TranslationRequirement.PRESERVE_NEGATION)
                            ),
                    )
                }
            )
        val verifier = FakeVerifier()
        val result =
            TranslateTextUseCase(translator, verifier)(
                TranslationRequest(
                    "source",
                    direction,
                    policy = TranslationPolicy(unsupportedHandling = UnsupportedHandling.REJECT),
                )
            )
                as TranslationOutcome.Failed
        assertEquals(FailureCategory.UNSUPPORTED_CAPABILITY, result.failure.category)
        assertEquals(0, verifier.calls)
    }

    @Test
    fun requiredTerminologyCannotUseAnUnsupportedEngine() = runTest {
        val translator =
            FakeTranslator(
                CapabilityDescription(
                    setOf(ExecutionMode.OFFLINE),
                    policies = TranslationRequirement.entries.toSet(),
                )
            )
        val request =
            TranslationRequest(
                "jack",
                direction,
                terminology =
                    listOf(
                        TerminologyHint(
                            "jack",
                            targetTerm = "gato",
                            strength = TerminologyStrength.REQUIRED,
                        )
                    ),
            )
        val result =
            TranslateTextUseCase(translator, FakeVerifier())(request) as TranslationOutcome.Failed
        assertEquals(FailureCategory.UNSUPPORTED_CAPABILITY, result.failure.category)
        assertEquals(0, translator.calls)
    }

    @Test
    fun requiredTerminologyBecomesAVerificationExpectation() = runTest {
        val hint =
            TerminologyHint("jack", targetTerm = "gato", strength = TerminologyStrength.REQUIRED)
        val verifier = FakeVerifier()
        TranslateTextUseCase(FakeTranslator(), verifier)(
            TranslationRequest("jack", direction, terminology = listOf(hint))
        )
        assertEquals(
            listOf(CriticalExpectation.Terminology(hint)),
            verifier.received!!.expectations,
        )
    }

    @Test
    fun numberOnlyPassCannotCoverRequestedNegation() = runTest {
        val verifier =
            FakeVerifier(
                answer = {
                    CapabilityResult.Success(
                        VerificationReport(
                            VerificationStatus.PASS,
                            checksPerformed = setOf(VerificationCheck.EXACT_INTEGER_LITERALS),
                        ),
                        ExecutionMode.OFFLINE,
                    )
                }
            )
        val result =
            TranslateTextUseCase(FakeTranslator(), verifier)(
                TranslationRequest(
                    "do not",
                    direction,
                    criticalContent = listOf(CriticalExpectation.Negation("do not")),
                )
            )
                as TranslationOutcome.NeedsReview
        assertTrue(result.result.issues.any { it.code == IssueCode.VERIFICATION_INCOMPLETE })
        assertEquals(
            setOf(CriticalContentKind.NEGATION),
            (result.result.value.verification as VerificationState.Evaluated).report.uncheckedKinds,
        )
    }

    @Test
    fun criticalTranslatorWarningsRequireReviewDespiteVerifierPass() = runTest {
        val translator =
            FakeTranslator(
                answer = {
                    CapabilityResult.Success(
                        TranslatedText("output", direction),
                        ExecutionMode.OFFLINE,
                        listOf(
                            CapabilityIssue(
                                IssueCode.NAME_UNCERTAINTY,
                                IssueSeverity.CRITICAL,
                                IssueCertainty.POSSIBLE,
                            )
                        ),
                    )
                }
            )
        assertTrue(
            TranslateTextUseCase(translator, FakeVerifier())(request())
                is TranslationOutcome.NeedsReview
        )
    }

    @Test
    fun invalidInputNeverReachesCapabilities() = runTest {
        val translator = FakeTranslator()
        val verifier = FakeVerifier()
        val result =
            TranslateTextUseCase(translator, verifier)(TranslationRequest(" ", direction))
                as TranslationOutcome.Failed
        assertEquals(FailureCategory.INPUT_INVALID, result.failure.category)
        assertEquals(0, translator.calls)
        assertEquals(0, verifier.calls)
    }

    @Test
    fun applicationInputLimitIsStructuredAndConfigurable() = runTest {
        val translator = FakeTranslator()
        val result =
            TranslateTextUseCase(translator, FakeVerifier(), maxInputCharacters = 3)(request())
                as TranslationOutcome.Failed
        assertEquals(FailureCategory.INPUT_TOO_LONG, result.failure.category)
        assertEquals(0, translator.calls)
    }

    @Test
    fun expectedTranslatorFailuresKeepTheirCategoriesAndSkipVerification() = runTest {
        for (category in FailureCategory.entries) {
            val verifier = FakeVerifier()
            val translator =
                FakeTranslator(answer = { CapabilityResult.Failure(CapabilityFailure(category)) })
            val result =
                TranslateTextUseCase(translator, verifier)(request()) as TranslationOutcome.Failed
            assertEquals(category, result.failure.category)
            assertEquals(0, verifier.calls)
        }
    }

    @Test
    fun verifierOperationalFailuresStayDistinctFromFailedMeaningChecks() = runTest {
        val verifier =
            FakeVerifier(
                answer = {
                    CapabilityResult.Failure(CapabilityFailure(FailureCategory.RESOURCE_LIMIT))
                }
            )
        val result =
            TranslateTextUseCase(FakeTranslator(), verifier)(request()) as TranslationOutcome.Failed
        assertEquals(FailureCategory.RESOURCE_LIMIT, result.failure.category)
        assertEquals(TranslationStage.VERIFICATION, result.stage)
        assertNotNull(result.candidate)
    }

    @Test
    fun partialEmptyAndWrongDirectionCandidatesAreNotCompleteTranslations() = runTest {
        for (candidate in
            listOf(
                TranslatedText("output", direction, CompletionStatus.PARTIAL),
                TranslatedText(" ", direction),
                TranslatedText("output", direction.reversed()),
            )) {
            val translator =
                FakeTranslator(
                    answer = { CapabilityResult.Success(candidate, ExecutionMode.OFFLINE) }
                )
            val verifier = FakeVerifier()
            assertEquals(
                FailureCategory.INFERENCE_FAILED,
                (TranslateTextUseCase(translator, verifier)(request()) as TranslationOutcome.Failed)
                    .failure
                    .category,
            )
            assertEquals(0, verifier.calls)
        }
    }

    @Test
    fun translatorSuppliedVerificationDoesNotBypassTheSelectedVerifier() = runTest {
        val translator =
            FakeTranslator(
                answer = {
                    CapabilityResult.Success(
                        TranslatedText(
                            "output",
                            direction,
                            verification = VerificationState.Evaluated(passing()),
                        ),
                        ExecutionMode.OFFLINE,
                    )
                }
            )
        val verifier =
            FakeVerifier(
                answer = {
                    CapabilityResult.Success(
                        VerificationReport(VerificationStatus.NEEDS_REVIEW),
                        ExecutionMode.OFFLINE,
                    )
                }
            )
        assertTrue(
            TranslateTextUseCase(translator, verifier)(request()) is TranslationOutcome.NeedsReview
        )
        assertEquals(1, verifier.calls)
    }

    @Test
    fun unexpectedProgrammingFaultsAreNotFlattenedIntoProductFailures() = runTest {
        val fault = IllegalStateException("fixture programming fault")
        val translator = FakeTranslator(answer = { throw fault })
        try {
            TranslateTextUseCase(translator, FakeVerifier())(request())
            fail("Fault must propagate")
        } catch (caught: IllegalStateException) {
            assertSame(fault, caught)
        }
    }

    @Test
    fun repeatedNumberExpectationsRetainMultiplicityInVerification() = runTest {
        val n = CriticalExpectation.NumberLiteral(NumericValue.parse("12"))
        val verifier = FakeVerifier()
        TranslateTextUseCase(FakeTranslator(), verifier)(
            TranslationRequest("12 and 12", direction, criticalContent = listOf(n, n))
        )
        assertEquals(listOf(n, n), verifier.received!!.expectations)
    }
}
