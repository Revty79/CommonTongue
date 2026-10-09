# Pass 7 locked local inference provenance

This pass adopts the accepted Pass 5 configuration. Commercial adoption remains provisional, subject to pre-release legal review. It does not certify translation accuracy or give legal clearance.

| Component | Immutable identity | License evidence / status |
| --- | --- | --- |
| Multilingual Whisper Base Q5_1 | ggerganov/whisper.cpp model publication `5359861c739e955e79d9a303bcbc70fb988958b1`; 59,707,625 bytes; SHA-256 `422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898` | OpenAI Whisper MIT; converted/quantized third-party publication, provenance review remains required |
| whisper.cpp | v1.9.5, ggml-org/whisper.cpp `d1be6fde11ac6e0407606b4e42fe72d34add8037` | MIT; include upstream license notice, including ggml notices |
| MADLAD400-3B-MT Q4_K | google/madlad400-3b-mt `fa184c675da0b5c9e1c8694fccd4e12e2d422094`; 1,654,597,280 bytes; SHA-256 `ea6e5531a3e95213c7f0635988d119e078a655c09306e47851e15d4c0c3f9c37` | Model card declares Apache-2.0. Exact GGUF conversion/quantization provenance remains provisional; no new artifact substituted |
| MADLAD tokenizer | Same Google revision; 16,629,031 bytes; SHA-256 `a2799ccc696b752ba00c34f58726bfe253a04921ceb6cfc620400f560474790b` | Same model publication; exact tokenizer distribution rights included in legal review |
| MADLAD configuration | Same revision; 749 bytes; SHA-256 `cad399cab799b99409a6ec2d90d72552257c2bb752861261d2016691e0643e7c` | Same publication |
| Candle quantized T5 | 0.11.0, huggingface/candle `31f35b147389700ed2a178ee66a91c3cc25cc80d` | MIT OR Apache-2.0 |
| Rust tokenizer | tokenizers 0.22.2, exact Cargo.lock | Apache-2.0; native oniguruma dependencies retain their notices |
| Native build | Rust 1.91.0; NDK 27.1.12297006; Android API 26; arm64-v8a; CMake 3.22.1 | Toolchain and C++ runtime notices retained; 16 KiB ELF page alignment |

The locked `tools/local-ai/t5/Cargo.lock` carries the same 138 dependency versions as the accepted research runtime. `tools/device-trial/evidence/cargo-licenses.json` remains the accepted per-package license inventory. Packaged notices are copied from that accepted inventory, not vendor TTS voice assets. No OPUS, SentencePiece C++ wrapper, ONNX, neural TTS or cloud inference dependency enters this production runtime.

CPU execution uses four threads for both engines. Whisper transcribes explicitly selected English or Spanish; translation is disabled, greedy best_of=1, no context, temperature increment=0. MADLAD uses the unchanged tokenizer, `<2es>`/`<2en>` prefixes, no padding/truncation, maximum 512 input tokens, greedy seed 404, KV cache enabled and cleared per turn, maximum 128 decoder iterations. EOS determines complete versus partial output.

First-party and immutable publication references: [Whisper license](https://github.com/openai/whisper/blob/main/LICENSE), [whisper.cpp pinned license](https://github.com/ggml-org/whisper.cpp/blob/d1be6fde11ac6e0407606b4e42fe72d34add8037/LICENSE), [Google model card](https://huggingface.co/google/madlad400-3b-mt/blob/fa184c675da0b5c9e1c8694fccd4e12e2d422094/README.md), [Candle pinned licenses](https://github.com/huggingface/candle/tree/31f35b147389700ed2a178ee66a91c3cc25cc80d), and the prior Pass 4/5 provenance receipts. These are carried-forward evidence; this pass makes no broader redistribution claim.

The temporary debug resource pack redistributes only these locked research artifacts and two existing synthetic eSpeak fixture WAVs. Models and APKs are repository-associated artifacts, never Git blobs. eSpeak is not bundled as a runtime. System TTS remains the accepted Pass 6 adapter; installed vendor voices are invoked only, never copied/extracted/bundled. See the existing Pass 6 licensing notes for unresolved vendor terms.
