# Pass 9: locked-stack guidance feasibility

**Status: research gate failed; Pass 9 is NOT implemented, accepted or complete.** No Pass 9 tester is published. The existing [Pass 8 S25 tester](https://github.com/Revty79/CommonTongue/releases/tag/pass8-translator-v9) remains the working product. No Pass 10 work.

The user authorized Pass 9 on `8f37267f318839e696537a4e3ebe7b8fed9b1297`. Exact-commit [Actions 37885718318, attempt 2](https://github.com/Revty79/CommonTongue/actions/runs/37885718318) succeeded. The user confirmed real S25 microphone capture, recognition, translation, spoken output and normal product interaction; the initial quiet output was phone media volume. Extended Pass 8 checks, other devices and complete Pass 9 physical acceptance are not claimed.

## What was attempted

The first gate was whether a layer above raw MADLAD could safely guide translation with the locked resources. No product capability flags were changed. These are Windows production C ABI text controls, **not Android/S25 execution**, unit-test fakes or a new model.

First-party provenance research consulted the original [MADLAD release resources](https://github.com/google-research/google-research/tree/master/madlad_400) and [research paper](https://arxiv.org/abs/2309.04662). They describe released translation models; they do not establish safety of this application's context wrappers. Conclusions below come from the locked implementation and measured fixture outputs.

The experiment streamed and verified the existing MADLAD model, tokenizer and configuration SHA-256 hashes. It used the existing production host DLL, `trial_t5.dll`, SHA-256 `a66656e88badac63dd41211f9b887bde6b59e7d29940109b830beefbba1751fa`. Candle 0.11.0/revision `31f35b147389700ed2a178ee66a91c3cc25cc80d`, Rust 1.91.0, four threads, CPU, cache clearing, greedy seed 404, 512 input tokens and 128 output tokens are unchanged. The model bytes/hashes and all effective sources/raw outputs are in the [controlled record](../tools/conversation-guidance/evidence/locked-probes.json).

The 66 native calls comprised 23 unchanged current-utterance baselines and 43 experimental guidance calls. Context methods translated a source-language prior sentence with the current sentence, using a numbered layout, ordinary paragraph, numeric separator, text sentinel before the current sentence, or sentinel after the current sentence. The last method also used two different marker nonces. Terminology replaced authored source spans with recoverable sentinels and restored the user's exact paired target wording. All text is authored research data; none is captured user conversation. Twelve calls reuse Pass 4 authored cases `q005`, `q010` and `q013` in both directions.

| Method | Native calls | Complete native results | Structurally recoverable current results | Median native time (Windows) |
| --- | --- | --- | --- | --- |
| Basic current source | 23 | 23 | 23 basic outputs | 6.22 s |
| Numbered context | 3 | 0 | 0 | 71.94 s |
| Ordinary paragraph context | 2 | 0 | 0 | 60.54 s |
| Prior context + text sentinel + current | 13 | 13 | 7 | 28.33 s |
| Prior context + numeric separator + current | 2 | 2 | 1 | 17.35 s |
| Current + text sentinel + prior context | 17 | 17 | 7 | 18.03 s |
| Protected terminology spans | 6 | 6 | 6; meaning not certified | 11.96 s |

Only 15 of 37 contextual calls passed conservative structural recovery checks. Recovery requires the exact marker inventory/count, both nonblank segments and complete generation. Changed, repeated, invented or missing markers and missing segments decline recovery; numbered/paragraph layouts have no proven extraction boundary. This check is a research filter, not a production semantic verifier. Native completion means EOS was produced, not that the correct utterance was translated. Five calls hit the existing 128-token cap. The initial pilot retired already-failing layouts after 12 completed calls; the next interrupted in-flight call is excluded, not called a failure or a success.

## Real improvements and real failures

Context **can** change a useful sense. The river context changed `I left it by the bank.` from `Lo dejé en el banco.` to `Lo dejé en la orilla.` The Spanish sail control changed `Revisa la vela.` from `Check the candle.` to `Check the sail.` University context changed `Termino la carrera este año.` from `I finish the course this year.` to `I am finishing my degree this year.` These are fixture observations, not a fluent-human quality certification or evidence of general safe conversational support.

Counterexamples prevent adoption:

- Numbered/paragraph context repeatedly copied old source text, omitted the current utterance and exhausted the output limit.
- With river context, a clear paycheck-deposit sentence acquired repeated clauses. With car context, a sentence explicitly about an escaped cat acquired `I'm talking about the car.` in the current-output segment. No such clause was spoken in that fixture. The stricter structural filter declines these particular outputs, but does not provide a general semantic guard.
- The current-first layout omitted `Trae el gato.` and emitted prior-topic text plus an invented next marker. It also omitted the current candle request. Some other cases repeated the expected marker. Changing the nonce did not resolve the clear-sentence duplicate-marker failures.
- Successful boundary recovery still left `gato` as `cat` despite the vehicle-lifting topic, and left authored electrical `contacto` as `contact`. Context effects are inconsistent.
- Protected spans recovered `formwork`/`cimbra`, single/multiword phrases and repeated occurrences, but the Spanish imperative `Trae cables para pasar corriente...` became `It brings jumper cables...`. Every marker survived: exact-term presence did not detect the changed speech act.
- Blindly protecting `fair` in `That price is fair.` and restoring `feria` produced `Ese precio es feria.` A paired term alone cannot classify its sense. An optional meaning note was not interpreted, so no note/meaning support is claimed.

Structural rejection could support an explicitly limited fallback to the current-utterance baseline. It would preserve some controls, but does not make these methods a proven, useful, safe end-to-end context/terminology implementation. No strategy was selected for production. This is not proof that every possible algorithm using MADLAD must fail; it is a failed adoption gate for the methods actually measured.

## Architecture and missing capability

The production architecture is **unchanged**: UI -> `ConversationTurnCoordinator` -> raw `MadladTranslator` -> accepted native translation -> accepted offline TTS/playback. Existing `ConversationContext`, `ConversationTurn`, `ContextLimits`, `TerminologyHint` and `TerminologyStrength` remain the future integration contracts. `RecentTurn` is still presentation history; no context badge or glossary control claims functionality that was not adopted. Raw MADLAD still honestly reports context/terminology unsupported, and rejects required hints.

The smallest missing **capability**, rather than an approved asset, is source-bounded guidance: resolve a current ambiguous sense or selected term using prior context while keeping the current utterance separate from historical text and preserving its clauses/speech act. The raw text-composition methods did not supply that reliably. A provider-neutral planner above the unchanged translator remains the intended boundary. Strict target-term checking would still be needed; free-text meaning is not deterministically enforceable merely by finding a target word.

Two next research directions require user/Ember review: an explicit user clarification workflow with no new model, or a narrowly scoped offline contextual planner. The first changes the automatic-context acceptance behavior; the second may require an additional model/runtime and measured memory/latency. **No additional model has been selected or proven necessary, no resource budget is asserted, and the old Pass 4 LLMs are not silently adopted.** They previously had copying/meaning regressions. A new asset, runtime/decoding change or pack change requires approval before work. The existing pack must remain valid.

## Privacy, resources and regression

Only fixed fixture text/effective source/output is recorded in the research folder. The runner cannot consume a human transcript/export or microphone input. It loads already-cached locked files and does not download, provision, synthesize speech or alter model assets. It suppresses the native panic hook, records only finite native failure codes, uses the existing Python network tripwire and required OS network-restricted sandbox. Both connection probes returned WinError 10013 and no Python networking event occurred during inference. These observations do not turn a Python audit hook into a native network monitor.

No new model, library, package dependency, vendor asset or Android permission was added. Models still total approximately 1.67 GB; no APK/pack/signing update is needed for research-only changes. Measured timings above are sequential desktop controls with warm/cache/host effects, not a S25 latency prediction. Some matched context pairs are substantially slower than baseline; no production Android RAM/latency impact is claimed because no method is integrated.

All 732 accepted product/runtime/tooling files were compared to the Pass 8 baseline and remain unchanged, normalizing only checkout line endings for the comparison. The existing 285-file research freeze, 274-file local-AI freeze, local network/ownership boundary and accepted production voice guards pass. No microphone, worker, native-handle, decoding, voice, AudioTrack, resource installation, launcher, identity, signer or four-theme behavior changes. The S25 should continue using the existing signed Pass 8 update and installed `offline-core-en-es-v1`; do not uninstall, clear data or redownload resources.

Local Gradle `test lint spotlessCheck verifyCoreBoundaries :app:verifyFoundationManifest` passed. The unchanged JVM suites retain 209 unique passing test results, with no failure/error/skip: domain 10, translation 112, local AI 32, Android local AI 7, speech 37, app 11. Gradle reused unchanged test outputs where up to date. No new production tests or physical acceptance are claimed for the unimplemented guidance layer.

## Reproduction and remaining acceptance

Run `python -m unittest discover -s tools/conversation-guidance/tests -v` and `python tools/conversation-guidance/probe.py` for evidence-integrity/rejection checks without models. All twelve research tests passed, as did all 113 existing Python regression tests (6 feasibility, 42 quality, 65 device tooling). The research tests check provenance, unchanged baseline input, preserved authored cases, unsafe marker/segment rejection, partial generation, limitations and the distinction between recovery and meaning. They do not establish production feature support. Actionlint passed the amended workflow.

To rerun a specific controlled native call with the exact existing Windows host build, use `python tools/conversation-guidance/probe.py --run-model --probe-id p047`. The new replay CLI was executed once for p047 and reproduced its recorded text exactly with the locked four-thread runtime; this repeat is separate from the 66-call study. A full `--run-model` reruns all 66 authored calls and can take tens of minutes. It rejects changed model/DLL hashes and an unrestricted network sandbox. It never downloads another model. Outputs default to ignored `.local/pass9/replayed-probes.json`.

Production context memory, terminology lifecycle/UI, New Conversation reset, guidance cancellation/replacement/retry integration and their automated acceptance cases are **not implemented**. The intended later implementation must use the existing bounded 8-turn/8,000-character contract, preserve source-language-view provenance, exclude failed/partial/cancelled turns, keep content local and memory-only, and preserve Pass 8 behavior without guidance. No broad Pass 10 verifier or Pass 11 notes have been started.

The real-product S25 checklist is pending in full: ordinary EN/ES/replay regression, contextual ambiguity both directions, deliberate reset without redownload, paired terms both directions, unrelated language after guidance, airplane-mode proof and restart/storage lifecycle. No component/desktop tests substitute for that physical acceptance. Work stops at the requested real-model/approval gate for user + Ember review.
