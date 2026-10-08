"""Explicit offline CPU unload/reload study; never part of CI or the application."""

import argparse
import gc
import json
import os
import time
from pathlib import Path

from runner import LOCAL, ROOT, Resources, Worker
from prompts import translate_prompt
from offline import install_tripwire, probe


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--engine", choices=["q2-q4", "q25-q4"], default="q2-q4")
    parser.add_argument("--offline", action="store_true", required=True)
    args = parser.parse_args()
    observations = probe()
    if any(v == "AVAILABLE" for v in observations.values()): raise RuntimeError("Native offline sandbox required")
    events = install_tripwire()
    os.environ.update(HF_HUB_OFFLINE="1", TRANSFORMERS_OFFLINE="1", HF_HUB_DISABLE_TELEMETRY="1")
    from translation import Marian
    cases = {c["id"]: c for c in json.loads((Path(__file__).parent / "corpus.json").read_text(encoding="utf-8"))["cases"]}
    family = "qwen2.5" if args.engine == "q25-q4" else "qwen3.5"
    model = LOCAL / ("models/Qwen--Qwen2.5-1.5B-Instruct-GGUF/qwen2.5-1.5b-instruct-q4_k_m.gguf" if family == "qwen2.5" else "models/bartowski--Qwen_Qwen3.5-2B-GGUF/Qwen_Qwen3.5-2B-Q4_K_M.gguf")
    rows = []
    # Same two directions repeated three times. Reload is deliberately paid on each utterance.
    for trial in range(3):
        for key in ["en15", "es11"]:
            case = cases[key]
            start = time.perf_counter()
            with Resources() as meter:
                mt = Marian(ROOT / ".local/models" / case["direction"], 4)
                draft = mt.translate(case["source"], max_tokens=128)
            mt_resources = meter.result
            mt_load = mt.load_ms
            unload_start = time.perf_counter()
            del mt
            gc.collect()
            unload_ms = (time.perf_counter()-unload_start)*1000
            with Resources() as meter:
                worker = Worker(model, 4, "sequential-"+args.engine)
                ready = worker.ready
                result = worker.generate(translate_prompt(case, "concise", candidate=draft["text"], family=family))
            llm_resources = meter.result
            worker.close()
            del worker
            gc.collect()
            rows.append({"trial": trial, "case": key, "mt_load_ms": mt_load, "mt_inference_ms": draft["ms"],
                         "mt_unload_gc_ms": unload_ms, "mt_resources": mt_resources, "llm_load": ready,
                         "llm_resources": llm_resources, "llm_inference_ms": result["ms"], "text": result["text"].strip(),
                         "total_including_reload_shutdown_ms": (time.perf_counter()-start)*1000})
    if events: raise RuntimeError("Inference attempted network access")
    receipt = {"engine": args.engine, "threads": 4, "study": "Uncontended sequential one-direction OPUS then LLM; three process-model reloads per direction; OS file cache warm",
               "limitations": "RSS is sampled process-tree working set, not Android PSS; gc timing is not a guarantee of OS memory reclamation; no ASR/TTS co-residency measured",
               "network_canaries": observations, "socket_attempts_after_tripwire": events, "rows": rows}
    (LOCAL / ("sequential-"+args.engine+".json")).write_text(json.dumps(receipt, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")


if __name__ == "__main__": main()
