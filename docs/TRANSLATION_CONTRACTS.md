# Production translation contracts

Pass 3 adds engine-neutral Kotlin/JVM contracts in `:core:translation`, exporting values from `:core:domain`. No concrete inference adapter is connected to the app. Source is under `core/translation/src/main/kotlin/com/commontongue/translation`.

## Identity and request values

| Type | Semantics |
| --- | --- |
| `LanguageId` | Immutable canonical tag/base language/script/region. `parse` trims outer whitespace and uses strict Locale.Builder with a two/three-letter base restriction. ES-mx equals es-MX; es, es-MX and es-419 differ. `und`/private-use-only tags, display names, underscores and malformed tags are rejected. Syntax is not registry/engine validation. |
| `TranslationDirection` | Ordered source/target; same base language rejected even across regions. Reverse direction is explicit. Same-language locale rewriting is a separate task. |
| `RegionalPreference` | Desired target locale and natural/neutral style, independent of direction. es-419 is a macroregion, not a country. Must match target base language. |
| `DeviceQualityProfile` | LITE/STANDARD/ENHANCED preferences; no device benchmarking, model mapping or Lite viability claim. |
| `DomainContext` | Extensible lowercase identifier (`[a-z][a-z0-9._-]{0,63}`); only GENERAL predefined. No pack store/fixed specialty enum. |
| `AudioReference` / `VoiceId` | Opaque validated tokens (`[A-Za-z0-9_-]{1,128}`), resolved privately by adapters. No path/URL/Context/ByteBuffer/tensor/codec. Semantic boundaries can be implemented on another platform; this pass does not add KMP/iOS. |

Full BCP-47 permits additional forms; extending the bounded base-language rule requires an explicit decision. Unregistered but well-formed two/three-letter codes are permitted without claiming support. The underlying behavior follows [BCP-47 syntax](https://www.rfc-editor.org/rfc/rfc5646.html) and [JDK 17's strict builder](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Locale.Builder.html).

`TranslationRequest(sourceText, direction)` is lightweight, with optional context, terminology, domain, region, policy, critical expectations, execution requirement and profile. Defaults are no context/hints, GENERAL, no region, all preservation requirements, unsupported-option reporting, offline required and Standard preference. Blank text/mismatched regional language become structured validation failures. A configurable application limit defaults to 16,000 characters; adapters may report smaller input limits.

Each `ConversationTurn` holds side, source/translated text and direction. Context defaults to None; `recent` accepts a chronological snapshot bounded by explicit limits (default 8 turns/8,000 combined characters). Overflow is rejected. Callers may deliberately select a window before construction. No permanent storage is implied.

`TerminologyHint` requires a source phrase and target term and/or meaning, with optional domain/explanation and PREFERRED/REQUIRED strength. Required hints reject unsupported terminology engines and are supplied to verification. This is guidance plus review architecture, not guaranteed lexical substitution. No production glossary is included. Collections are copied/unmodifiable; invalid value-object configuration throws IllegalArgumentException, while normal capability/user-input failures are structured.

## Capability contracts

Every capability exposes `CapabilityDescription` and a suspend operation. Implementations must enforce `executionFailure` or equivalent before network-capable work, honor locks, report unsupported requests, state actual execution honestly and propagate cancellation.

| Contract | Request | Success payload |
| --- | --- | --- |
| `SpeechRecognizer.recognize` | Audio reference, locked language or explicitly permitted automatic selection, optional expected/candidate/vocabulary hints, execution/profile | Text, language actually used, complete/partial status, legitimate confidence or unavailable |
| `Translator.translate` | Rich TranslationRequest | Translated text, used direction, completion, ambiguity alternatives, confidence, verification state |
| `SpeechSynthesizer.synthesize` | Text/language locale, voice token, finite positive rate, future speaker-preservation preference, execution/profile | Generated audio reference, actual language/voice, completion |
| `LanguageDetector.detect` | Text/audio plus locked/automatic selection | Known language with optional evidence, or Unknown. Lock does not authorize detection. |
| `CriticalContentAnalyzer.identify` | Text/language/context/domain/terminology/execution | Critical expectations plus unchecked kinds; no NLP implementation exists. |
| `TranslationVerifier.verify` | Source/target text/direction, critical expectations/context/execution | Status, findings, checks performed and unchecked content kinds |
| `TranslationNotesAnalyzer.analyze` | Same source/translation verification input | Immutable sarcasm/idiom/slang/regional/alternate/technical/uncertainty notes with explicit certainty |

`CapabilityResult.Success<T>` wraps the payload with actual execution, issues and runtime support report; Failure wraps a categorized reason and issues. `TranslationResult` is `CapabilityResult<TranslatedText>`, not a String. The text use case rejects partial/blank/wrong-direction candidates. Used direction must equal the canonical requested direction; soft regional preference is separately supported/reported.

`ConfidenceEvidence.Unavailable` is default. A Reported normalized score must be finite in 0..1, provide a genuine underlying basis and calibrated/uncalibrated status. It is not automatically a probability or correctness guarantee. Core never invents confidence from fluency/latency/model branding. Possible sarcasm (`kind=SARCASM`, `certainty=POSSIBLE`) differs from an observed-evidence claim. No actual notes/sarcasm detector exists; fakes are fixtures only. Timing telemetry is deliberately omitted from semantic payloads; future operational profiling belongs outside domain meaning.

## Policy, support and warning semantics

TranslationPolicy requests meaning over literal wording; preservation of tone/slang/idioms/profanity/uncertainty/names/numbers/units/negation; no invented information; honoring terminology; and natural target language. These are engine-neutral requirements, not prompt strings or guaranteed model behavior.

Descriptions explicitly declare nonempty offline/online modes, features and supported policies. Features cover detection/context/terminology/domain/regional guidance/verification/streaming/notes/voice/rate/speaker preservation. No implementation brand is used in domain decisions. Unsupported features/policies are separate sets; basic requests do not falsely request absent context/hints/domain/region.

REPORT keeps limitations visible as observed capability limitations, not proven translation errors. REJECT prevents work when known unsupported and still applies when support is withdrawn at runtime. Required terminology rejects unsupported terminology even with REPORT. Supported policy flags are not evidence that every output satisfies them.

Issues separate severity (INFO/CAUTION/CRITICAL) from certainty (POSSIBLE/OBSERVED), with codes for ambiguity/idiom/slang/sarcasm/terminology/number/unit/negation/name/ASR/support/context/verification. A lexical observation need not prove a semantic error. Critical issues route the application outcome to review even if a narrower verifier check passes.

## Text use case, failure and cancellation

The use case validates, preflights translator and verifier, audits support, translates, validates execution/completion/languages, independently verifies, and returns Complete/NeedsReview/Failed. Failed verification preserves candidate/findings. Translator-supplied verification is cleared before checking. No notes/detection/audio pipeline/context persistence/fallback is automatically invoked.

PASS/PASS_WITH_WARNINGS remains scoped. Integer-only coverage cannot certify decimal values or requested semantic expectations. Missing coverage produces review; check FAIL becomes VERIFICATION_FAILED. Operational verifier failure retains its own category and the verification stage. See [quality architecture](QUALITY_ARCHITECTURE.md).

Failure categories: ENGINE_NOT_AVAILABLE, LANGUAGE_NOT_SUPPORTED, MODEL_NOT_INSTALLED, INPUT_INVALID, INPUT_TOO_LONG, RESOURCE_LIMIT, OFFLINE_REQUIREMENT_NOT_MET, UNSUPPORTED_CAPABILITY, INFERENCE_FAILED, VERIFICATION_FAILED, CANCELLED. Expected results contain no runtime-specific exception/tensor. Unexpected programming faults propagate. External engine cancellation may be a CANCELLED result; coroutine cancellation must propagate as cancellation.

OFFLINE_REQUIRED is default. Both stages are preflighted before either receives text, forbidden/undeclared actual modes are rejected, and no fallback exists. ONLINE_ALLOWED is explicit permission for the selected implementation, not routing or mandatory network use. Overall successful execution is ONLINE if either stage ran online. No online adapter exists. Metadata checks cannot sandbox a dishonest implementation; future adapters still require offline/privacy audits.

Calls inherit the caller's coroutine context. ensureActive runs at entry and after stages; CancellationException is never flattened into product failure. Tests cancel translators/verifiers and verify cleanup, including a translator swallowing cancellation. Future blocking/native adapters need cooperative interruption/resource cleanup: suspend alone cannot interrupt arbitrary native work. See [Kotlin cancellation guidance](https://kotlinlang.org/docs/cancellation-and-timeouts.html).
