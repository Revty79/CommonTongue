# Pass 4 translation research

This isolated desktop study selects candidates for later phone research. The Android app and its core modules do not import this directory or any model runtime. The [report](../../docs/quality/PASS_4_QUALITY_BAKEOFF.md) explains the decision and limitations.

Normal checks need only Python 3.11+ and no models, credentials, network, or third-party Python packages:

```sh
python -m unittest discover -s tools/translation-quality/tests -v
python tools/translation-quality/analyze.py --summary summary.json
```

`corpus.json` contains sources and authored semantic expectations, not model-derived reference translations. `protocol.json` records the risk panel, template pilot, ablations, and balanced resource subset. `evidence/outputs/` contains compact actual outputs. The scoring rubric uses explicit model-assisted assessments; unreviewed outputs are never counted as passes. The assessment panel is deliberately difficult and its failure rates are not population estimates.

Models/toolchains belong exclusively under ignored `.local/`. `candidates.json` records licensing/provenance; `artifacts.lock.json` pins every downloaded artifact and locally converted M2M output. Check declared rights and all caveats before any redistribution. Nothing here approves a production model.

Explicit preparation, on a network-enabled development host:

```powershell
python -m venv .local/quality/env
.local/quality/env/Scripts/python.exe -m pip install -r tools/translation-quality/requirements.txt --extra-index-url https://download.pytorch.org/whl/cpu
.local/quality/env/Scripts/python.exe tools/translation-quality/prepare.py --download
.local/quality/env/Scripts/python.exe tools/translation-quality/prepare.py --convert-m2m
.local/quality/env/Scripts/python.exe tools/translation-quality/prepare.py --verify-local
```

Downloads are explicit, pinned and digest-checked. M2M conversion requires its upstream `special_tokens_map.json`; never synthesize missing language IDs. The source checkpoint is PyTorch float weights, the tested local artifact is CTranslate2 int8. Conversion receipts include library versions and output digests. A different converter/platform may produce different bytes: investigate rather than replacing the lock silently. The recorded Windows Python environment is in `evidence/environment.txt`. Wheels are platform-specific; PyTorch is from its official CPU wheel index.

Extract the locked llama.cpp source zip to `.local/quality/runtime-source/llama.cpp-8345f333951c661d166b00e6f9362e553768f292`. Build the small CPU stdio worker with CMake 3.22+, C++17 and an existing free compiler:

```powershell
cmake -S tools/translation-quality/native -B .local/quality/native-build -DLLAMA_SOURCE=D:/CommonTongue/.local/quality/runtime-source/llama.cpp-8345f333951c661d166b00e6f9362e553768f292
cmake --build .local/quality/native-build --config Release --target quality-worker quality-official-simple --parallel 4
```

The official MADLAD `model-q4k.gguf` is a **Candle tensor-only T5 archive**, not a llama.cpp model. Extract the locked Candle zip to `.local/quality/candle-runtime-source/candle-31f35b147389700ed2a178ee66a91c3cc25cc80d`, then build the T5 stdio helper:

```powershell
$env:CARGO_HOME='D:/CommonTongue/.local/quality/cargo'
$env:CARGO_TARGET_DIR='D:/CommonTongue/.local/quality/candle-build'
$env:RUSTFLAGS='-C target-cpu=x86-64-v3'
cargo build --release --locked --manifest-path tools/translation-quality/candle-worker/Cargo.toml
```

Rust 1.91.0/MSVC 14.44 were used. The CPU ISA/compiler flags are desktop settings, not an Android port. Cargo.lock pins registry checksums; the source archive separately pins Candle. No GPU, local HTTP server or download client is built into these workers. The upstream simple llama.cpp example reproduces a selected weak Qwen3.5 output byte for byte; the parity receipt is committed. This rules out that particular output being unique to our stdio wrapper, not every possible inference bug.

After preparation, run inference in a process sandbox that denies outbound sockets, including child processes. The runner refuses to run if either network canary succeeds. Python's audit tripwire is additional evidence, not a substitute for blocking native worker sockets. Do not escalate inference to a network-enabled process:

```powershell
.local/quality/env/Scripts/python.exe tools/translation-quality/runner.py --run opus-baseline --engine opus --architecture dedicated --offline
.local/quality/env/Scripts/python.exe tools/translation-quality/runner.py --run q2-direct --engine q2-q4 --architecture direct --ablations --offline
.local/quality/env/Scripts/python.exe tools/translation-quality/runner.py --run q2-hybrid --engine q2-q4 --architecture hybrid --ablations --offline
.local/quality/env/Scripts/python.exe tools/translation-quality/runner.py --run q2-verify-retry --engine q2-q4 --architecture verify-retry --offline
```

Other engines are `m2m`, `madlad`, `q080-q4`, `q080-q5`, and `q25-q4`. Dedicated/direct/hybrid runs default to 196 cases; verify/retry defaults to the fixed 68-case risk panel. Use `--case-ids` for a declared subset. `--ablations` adds 32 no-context and 28 no-terminology outputs for contextual engines. Two templates are `concise` and `structured`; ChatML follows each model's official template, with Qwen3.5's no-thinking prefix and no such prefix for Qwen2.5. All LLM inference is greedy, capped at 128 tokens; verification is capped at 96 and repair occurs at most once. Capped output remains incomplete, with no automatic cleanup or retry beyond that pipeline.

Research outputs go to `.local/quality/results/`. The compact committed evidence retains run configuration, raw text, measured resources, and any verification/draft/repair. Quality sweeps overlap and their latency is unsuitable for selecting a resource frontier. Separate resource replays run sequentially without other benchmark/build jobs. Cold load means newly constructed model/process with a warm filesystem cache, not a reboot or cleared OS cache. Sampled RSS is neither Android PSS nor guaranteed total allocation. `resource_study.py --offline` measures sequential OPUS unloading then LLM reloading; both runtime sets also coexist in the hybrid sweep/replay.

Generate a future blinded review sheet and keep its key separate:

```powershell
python tools/translation-quality/analyze.py --blind-public .local/quality/reviewer-sheet.json --blind-private .local/quality/reviewer-key.json --seed 404
```

The public export hides model/architecture names and performance. These files are preparation for a later qualified review, not evidence that such a study happened. Do not give reviewers the private key, committed assessments or model report until their ratings are frozen.
