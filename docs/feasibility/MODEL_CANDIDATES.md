# Pass 2 candidate and license records

These are engineering/license evidence, not legal advice or production approval. **A** = provisionally compatible with commercial redistribution subject to legal review; **B** = research only; **C** = blocked from further use until rights are resolved. Every downloaded model/config/card/source archive has its exact filename, immutable source revision, byte size, download URL, and SHA-256 in [artifacts.lock.json](../../tools/offline-feasibility/artifacts.lock.json). That lock is the authoritative file inventory; weights remain ignored under `.local/models/`.

Run JSON records the local lock file's byte hash. Git's line-ending normalization and the later addition of Piper records can change that JSON-file hash without changing existing model identities; individual binary-artifact hashes are authoritative.

## Speech recognition

Both tested candidates are **multilingual**, not `.en` models. English and Spanish are explicitly selected for transcription; Whisper's own speech-translation mode is disabled. No contextual LLM was downloaded or tested.

| Candidate | Files | Downloaded size | Model license | Status |
| --- | --- | --- | --- | --- |
| OpenAI Whisper Tiny, GGML Q5_1 conversion | `ggml-tiny-q5_1.bin` | 32,152,673 bytes (30.7 MiB) | MIT | A, provisional |
| OpenAI Whisper Base, GGML Q5_1 conversion | `ggml-base-q5_1.bin` | 59,707,625 bytes (56.9 MiB) | MIT | A, provisional |

Publisher/origin: [OpenAI Whisper](https://github.com/openai/whisper), with GGML conversion/quantized files published by [ggerganov/whisper.cpp](https://huggingface.co/ggerganov/whisper.cpp). Exact model-artifact repository revision: `5359861c739e955e79d9a303bcbc70fb988958b1`.

Runtime: [whisper.cpp](https://github.com/ggml-org/whisper.cpp), **MIT**, version **1.9.5**, source commit `d1be6fde11ac6e0407606b4e42fe72d34add8037`. Built CPU-only on Windows and Android. GGML is included with that pinned source tree. No CUDA, Vulkan, cloud SDK, model server, or runtime downloader is integrated.

Q5_1 is a mixed-precision GGML quantization, not a claim that every tensor uses five bits. MIT appears to permit commercial use, redistribution, and modification/quantization with copyright/license notices. The tested files are publisher conversions; original PyTorch weights were not downloaded or independently reconverted. Quantization provenance, retained notices, and reproducible conversion from canonical source weights need final review before shipping. This pass did not compare against unquantized Whisper or test Small/large models.

## Translation

| Candidate | Model publisher/source | Exact converted revision | Upstream evidence snapshot | Model license | Status |
| --- | --- | --- | --- | --- | --- |
| OPUS-MT EN→ES | Helsinki-NLP/opus-mt-en-es; ONNX conversion by Xenova | `4b002a4c7edd54a7ced58877258b87f7efd3f892` | `5bc4493d463cf000c1f0b50f8d56886a392ed4ab` | Apache-2.0 | A, provisional |
| OPUS-MT ES→EN | Helsinki-NLP/opus-mt-es-en; ONNX conversion by Xenova | `eadfd7c658a9d8929ac3b8e996b68a68e2c7d480` | `c96e2c5399ebfae4fc43d9669556b9afa74bb69d` | Apache-2.0 | A, provisional |
| Compact alternate: OPUS-MT EN→ROMANCE, forced Spanish tag | Helsinki-NLP/opus-mt-en-ROMANCE; ONNX conversion by Xenova | `9d2ba69ac80c8e8453c3d9a1e2323a0e7b8ca3cd` | `f8f3a28e8b6272d0ccc0290b832f699e154ae431` | Apache-2.0 | A, provisional |

Primary model-license evidence: [EN→ES model card](https://huggingface.co/Helsinki-NLP/opus-mt-en-es), [ES→EN model card](https://huggingface.co/Helsinki-NLP/opus-mt-es-en), and [EN→ROMANCE model card](https://huggingface.co/Helsinki-NLP/opus-mt-en-ROMANCE). This is the **model** license, not the separate MIT license of an OPUS software repository.

Each tested pair uses `onnx/encoder_model_quantized.onnx` (52,899,742 bytes) and `onnx/decoder_model_quantized.onnx` (59,842,102 bytes), totaling **112,741,844 bytes (107.5 MiB)** of ONNX weights. Tokenizer/config/vocabulary files bring a bilingual pack to approximately **110.7 MiB**. Original published PyTorch weight size is 312,087,523 bytes for each bilingual source model and 312,087,009 bytes for the Romance model; these are publisher file-size metadata, not locally downloaded originals.

Runtime: **ONNX Runtime 1.30.0 (MIT)** for desktop Python and Android Java/JNI. Tokenization: **SentencePiece 0.2.2 (Apache-2.0)**, source commit `e0cce7d37b065b5140349dbe12c6bcf6192fdd78`. Its native build fetches **Abseil 20260526.0 (Apache-2.0)**, verified checkout commit `5650e9cf76d3be4318d5fa3af38ee483ddfd5e4a`. The source tree also contains bundled protobuf/Unicode normalization dependencies with their own notices; preserve and audit those before redistribution.

Conversion process: Xenova publishes Optimum-compatible split Marian encoder/decoder ONNX exports and an accompanying `quantize_config.json`. We downloaded the publisher's already quantized artifacts and did not pretend to perform the conversion ourselves. Apache-2.0 appears to permit commercial redistribution and modification/quantization, with license/notice requirements. The converted repositories link to the upstream model but do not pin the original conversion's upstream commit or carry an explicit model-license field. The upstream evidence snapshots above establish current source/card identity, **not** the exact historical base commit used by the converter. That chain of provenance and retained notices must be resolved/rebuilt before any production adoption.

The isolated adapters use CPU execution, four threads, greedy decoding, no KV cache, max 256 input pieces/max 96 generated tokens, and fail when limits are exceeded. The alternate adds the `>>es<<` source tag. It was tested only EN→ES; ES→EN alternate coverage is **NOT RUN**. This compact alternate provides a bounded dedicated-model comparison; M2M100, large contextual LLMs, and extensive model sweeps were deferred.

ONNX Runtime's Android 1.30.0 AAR unexpectedly declares Internet/network-state permissions and a telemetry initializer. The spike removes all three and disables native telemetry with `setTelemetry(false)`. The final spike manifest guard requires zero requested permissions/providers. The AAR's minSdk is 24, so it does not force the application's minimum above 26.

## Speech synthesis and individual voices

| Engine / voice | Exact identity | Engine/model/voice terms | Status |
| --- | --- | --- | --- |
| eSpeak NG formant synthesis, desktop `en` and `es` | 1.52.0; source `4870adfa25b1a32b4361592f1be8a40337c58d6c` | Engine and checked voice/language rules: GPL-3.0-or-later; no neural model weights | B: research baseline |
| Separately installed eSpeak Android engine | Official `espeak-1.52.0-signed.apk`; SHA-256 in lock; four ABIs | GPL-3.0-or-later; independently distributed app; language/voice rules come from same pinned project | B: interim Android proof |
| Archived Piper engine | 2023.11.14-2; source `38917ffd8c0e219c6581d73e07b30ef1d572fce1`; official Windows ZIP hash in lock | Piper MIT; bundled phonemizer/eSpeak has GPL obligations; bundled ONNX Runtime 1.14 has MIT terms | B: archived research distribution |
| Piper English `en_US-ljspeech-medium` | Voice repository revision `c10ece1aade47bb51c153c893d14e5bf8e5b7117`; ONNX + JSON + MODEL_CARD | Card identifies public-domain LJ Speech data, trained from scratch; explicit independent model-weight grant/provenance needs review | B: research only |
| Piper Spanish `es_ES-carlfm-x_low` | Same voice repository revision; ONNX + JSON + MODEL_CARD | Card identifies public-domain carlfm speech data, trained from scratch; explicit independent model-weight grant/provenance needs review | B: research only |

Voice evidence is individual: [LJ Speech card](https://huggingface.co/rhasspy/piper-voices/blob/main/en/en_US/ljspeech/medium/MODEL_CARD), [CarlFM card](https://huggingface.co/rhasspy/piper-voices/blob/main/es/es_ES/carlfm/x_low/MODEL_CARD), [LJ Speech dataset](https://keithito.com/LJ-Speech-Dataset/), and [CarlFM speech dataset](https://github.com/carlfm01/my-speech-datasets). A dataset's public-domain declaration is **not** assumed to be an independent grant for every derived neural voice. The cards and configurations are pinned/downloaded with the exact tested weights.

English ONNX voice: **63,531,379 bytes (60.6 MiB)**. Spanish ONNX voice: **28,130,791 bytes (26.8 MiB)**. These are publisher ONNX float models; no additional voice quantization was performed. English voice is US English, one female speaker, 22,050 Hz; Spanish voice is Spain Spanish, one speaker, 16,000 Hz. No voice cloning or new speaker training was performed. Formant eSpeak has no independent learned speaker model and sounds robotic. The Piper-fixture run had lower Whisper WER, but decoding also changed; this is not a controlled voice-only comparison. No fluent/native listening approval is claimed.

GPL permits commercial activity subject to its conditions; its copyleft/source/notice obligations are a shipping-policy issue, not a download fee or paid service. We kept eSpeak in research and as a separately installed Android service. This does **not** approve bundling/linking GPL components into the production app. Voices reporting no network requirement were synthesized with Android networking disabled; exact selected voice names/locales are in Android JSON evidence. This proves local synthesis, not inclusion of those voices in Common Tongue's APK.

Other Piper cards inspected but **not tested/downloaded** include Lessac and voices fine-tuned from it. Lessac's dataset has separate linked terms; Sharvard's CC-BY dataset card also discloses Lessac fine-tuning. Those chains are **C / blocked for our shipping evaluation pending rights review**; no inference was run with them. No noncommercial-only model was adopted or tested.

## Before any shipping decision

Obtain legal review of model/conversion/voice provenance and notice obligations; reproduce chosen conversions from canonical source weights; audit all native/package notices; confirm telemetry exclusion and package permissions; and measure quality, memory, latency, battery and thermals on physical arm64 devices. Passing this spike is not a production-model approval.
