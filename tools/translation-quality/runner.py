"""Local text-only bake-off; never imported by production or model-free CI tests."""

import argparse
import ctypes
import gc
import hashlib
import json
import os
import platform
import queue
import subprocess
import sys
import threading
import time
from pathlib import Path

from prompts import translate_prompt, verification_prompt
from schema import validate_corpus
from scoring import signals

HERE = Path(__file__).parent
ROOT = HERE.parents[1]
LOCAL = ROOT / ".local/quality"
sys.path.insert(0, str(ROOT / "tools/offline-feasibility"))
from offline import install_tripwire, probe


class Resources:
    """10 ms parent+child RSS sampling; shared pages can be counted twice, not Android PSS."""
    def __enter__(self):
        import psutil
        self.psutil = psutil
        self.process = psutil.Process()
        self.peak = 0
        self.stop = threading.Event()
        self.cpu_start = {}
        self.cpu_last = {}
        self.started = time.perf_counter()
        self.sample()
        self.thread = threading.Thread(target=self.loop, daemon=True)
        self.thread.start()
        return self

    def sample(self):
        memory = 0
        for process in [self.process] + self.process.children(recursive=True):
            try:
                memory += process.memory_info().rss
                cpu = process.cpu_times()
                value = cpu.user + cpu.system
                self.cpu_start.setdefault(process.pid, value)
                self.cpu_last[process.pid] = value
            except self.psutil.Error: pass
        self.peak = max(self.peak, memory)

    def loop(self):
        while not self.stop.wait(.01): self.sample()

    def __exit__(self, *args):
        self.sample()
        self.stop.set()
        self.thread.join()
        seconds = time.perf_counter() - self.started
        cpu = sum(value - self.cpu_start[pid] for pid, value in self.cpu_last.items())
        self.result = {"peak_process_tree_rss_bytes": self.peak,
                       "steady_process_tree_rss_bytes": sum(p.memory_info().rss for p in [self.process] + self.process.children(recursive=True) if p.is_running()),
                       "approx_cpu_seconds": cpu, "approx_busy_cpu_cores": cpu / seconds if seconds else 0}


class Worker:
    def __init__(self, model, threads, label, binary=None):
        binary = binary or LOCAL / "native-build/Release/quality-worker.exe"
        self.log = (LOCAL / f"{label}-native.log").open("w", encoding="utf-8")
        self.process = subprocess.Popen([str(binary), str(model), str(threads)],
                                        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=self.log,
                                        text=True, encoding="utf-8", creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        self.responses = queue.Queue()
        threading.Thread(target=self.read, daemon=True).start()
        self.ready = self.receive()
        if not self.ready.get("ready"): raise RuntimeError("Worker failed to load")

    def read(self):
        for line in self.process.stdout: self.responses.put(line)
        self.responses.put(None)

    def receive(self):
        try: line = self.responses.get(timeout=180)
        except queue.Empty:
            self.process.kill()
            raise TimeoutError("Native worker response exceeded limit")
        if line is None: raise RuntimeError(f"Native worker exited: {self.process.poll()}")
        result = json.loads(line)
        if "error" in result: raise RuntimeError(result["error"])
        return result

    def generate(self, prompt, limit=128):
        self.process.stdin.write(json.dumps({"prompt": prompt, "max_tokens": limit}) + "\n")
        self.process.stdin.flush()
        return self.receive()

    def close(self):
        if self.process.poll() is None:
            self.process.stdin.write('{"shutdown":true}\n')
            self.process.stdin.flush()
            self.process.wait(timeout=30)
        self.log.close()


class M2M:
    def __init__(self, threads):
        import ctranslate2
        from transformers import M2M100Tokenizer
        start = time.perf_counter()
        self.translator = ctranslate2.Translator(str(LOCAL / "models/m2m100-ct2-int8"),
                                                device="cpu", compute_type="int8", inter_threads=1, intra_threads=threads)
        self.tokenizer = M2M100Tokenizer.from_pretrained(str(LOCAL / "models/facebook--m2m100_418M"), local_files_only=True)
        self.load_ms = (time.perf_counter() - start) * 1000

    def translate(self, text, direction):
        source, target = direction.split("-")
        self.tokenizer.src_lang = source
        tokens = self.tokenizer.convert_ids_to_tokens(self.tokenizer.encode(text))
        start = time.perf_counter()
        output = self.translator.translate_batch([tokens], target_prefix=[[f"__{target}__"]],
                                                beam_size=5, max_decoding_length=128)[0]
        ids = self.tokenizer.convert_tokens_to_ids(output.hypotheses[0])
        return {"text": self.tokenizer.decode(ids, skip_special_tokens=True),
                "ms": (time.perf_counter() - start)*1000, "tokens": len(ids), "completed": len(ids)<128}


def main():
    if os.name == "nt": ctypes.windll.kernel32.SetErrorMode(0x0001|0x0002|0x8000)
    parser = argparse.ArgumentParser()
    parser.add_argument("--run", required=True)
    parser.add_argument("--engine", choices=["opus", "m2m", "madlad", "q080-q4", "q080-q5", "q2-q4", "q25-q4"], required=True)
    parser.add_argument("--architecture", choices=["dedicated", "direct", "hybrid", "verify-retry"], required=True)
    parser.add_argument("--template", choices=["concise", "structured"], default="concise")
    parser.add_argument("--case-ids", help="Comma-separated subset; defaults to 68-case risk panel for verify-retry, full corpus otherwise")
    parser.add_argument("--ablations", action="store_true")
    parser.add_argument("--threads", type=int, default=4)
    parser.add_argument("--offline", action="store_true", required=True)
    args = parser.parse_args()
    family = "qwen2.5" if args.engine == "q25-q4" else "qwen3.5"
    if args.architecture in {"hybrid", "verify-retry"} and not args.engine.startswith("q"): parser.error("Hybrid requires a local contextual engine")
    if args.architecture == "dedicated" and args.engine.startswith("q"): parser.error("Dedicated MT requires MT engine")
    observations = probe()
    if any(v == "AVAILABLE" for v in observations.values()): parser.error("OS network block absent; refusing native inference proof")
    events = install_tripwire()
    os.environ.update(HF_HUB_OFFLINE="1", TRANSFORMERS_OFFLINE="1", HF_HUB_DISABLE_TELEMETRY="1", TOKENIZERS_PARALLELISM="false")
    corpus_path = HERE / "corpus.json"
    cases = validate_corpus(json.loads(corpus_path.read_text(encoding="utf-8")))
    if not args.case_ids and args.architecture == "verify-retry":
        args.case_ids = ",".join(json.loads((HERE / "protocol.json").read_text())["review_panel_ids"])
    if args.case_ids:
        requested = set(args.case_ids.split(","))
        if not requested <= {c["id"] for c in cases}: parser.error("Unknown subset ID")
        cases = [c for c in cases if c["id"] in requested]
    output = LOCAL / "results" / args.run
    output.mkdir(parents=True, exist_ok=True)
    metadata = {"run": args.run, "engine": args.engine, "architecture": args.architecture,
                "template": args.template, "threads": args.threads, "python": platform.python_version(),
                "platform": platform.platform(), "corpus_sha256": hashlib.sha256(corpus_path.read_bytes()).hexdigest(),
                "prompts_sha256": hashlib.sha256((HERE / "prompts.py").read_bytes()).hexdigest(),
                "network_canaries": observations, "native_network_block": "Restricted OS process sandbox; CPU worker has no networking or downloader",
                "decoding": "Greedy LLM, max 128 tokens; recurrent/KV state reset for every request; M2M beam 5; historical OPUS greedy no KV cache",
                "loads": [], "runner_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                "performance_note": "Quality sweep may overlap free-tool preparation; use the separate uncontended finalist resource replay for selection."}
    metadata["case_ids"] = [case["id"] for case in cases]
    opus, worker, m2m = {}, None, None
    try:
        if args.engine == "opus" or args.architecture in {"hybrid", "verify-retry"}:
            from translation import Marian
            for direction in ["en-es", "es-en"]:
                with Resources() as meter: opus[direction] = Marian(ROOT / ".local/models" / direction, args.threads)
                metadata["loads"].append({"engine": "opus-"+direction, "load_ms": opus[direction].load_ms, **meter.result})
        if args.engine == "m2m":
            with Resources() as meter: m2m = M2M(args.threads)
            metadata["loads"].append({"engine": args.engine, "load_ms": m2m.load_ms, **meter.result})
        if args.engine.startswith("q"):
            small = args.engine.startswith("q080")
            quant = "Q5_K_M" if args.engine.endswith("q5") else "Q4_K_M"
            size = "0.8B" if small else "2B"
            model = LOCAL / f"models/bartowski--Qwen_Qwen3.5-{size}-GGUF/Qwen_Qwen3.5-{size}-{quant}.gguf"
            if args.engine == "q25-q4":
                model = LOCAL / "models/Qwen--Qwen2.5-1.5B-Instruct-GGUF/qwen2.5-1.5b-instruct-q4_k_m.gguf"
            with Resources() as meter: worker = Worker(model, args.threads, args.run)
            metadata["loads"].append({"engine": args.engine, **worker.ready, **meter.result})
        if args.engine == "madlad":
            with Resources() as meter:
                worker = Worker(LOCAL / "models/google--madlad400-3b-mt", args.threads, args.run,
                                LOCAL / "candle-build/release/common-tongue-quality-t5-worker.exe")
            metadata["loads"].append({"engine": args.engine, **worker.ready, **meter.result})
        with (output / "outputs.jsonl").open("w", encoding="utf-8") as destination:
            for index, case in enumerate(cases, 1):
                variants = [(True, True, "")]
                if args.ablations and worker and args.engine.startswith("q"):
                    if case["context"]: variants.append((False, True, "-no-context"))
                    if case["terminology"]: variants.append((True, False, "-no-terms"))
                for context, terminology, suffix in variants:
                    details = {}
                    with Resources() as meter:
                        start = time.perf_counter()
                        if args.engine == "opus": result = opus[case["direction"]].translate(case["source"], max_tokens=128)
                        elif args.engine == "m2m": result = m2m.translate(case["source"], case["direction"])
                        elif args.engine == "madlad": result = worker.generate("<2" + case["direction"].split("-")[1] + "> " + case["source"])
                        elif args.architecture == "direct": result = worker.generate(translate_prompt(case, args.template, context, terminology, family=family))
                        else:
                            draft = opus[case["direction"]].translate(case["source"], max_tokens=128)
                            details["draft"] = draft
                            if args.architecture == "hybrid": result = worker.generate(translate_prompt(case, args.template, context, terminology, draft["text"], family=family))
                            else:
                                verification = worker.generate(verification_prompt(case, draft["text"], family), limit=96)
                                verdict = verification["text"].strip()
                                details["verification"] = verification
                                literal_failure = signals(case, draft["text"])["integer_check"]["state"] == "LITERAL_MISMATCH"
                                retry = verdict.startswith("FAIL") or literal_failure
                                details["retry_count"] = int(retry)
                                result = worker.generate(translate_prompt(case, args.template, context, terminology, draft["text"], verdict, family)) if retry else draft
                        elapsed = (time.perf_counter() - start)*1000
                    row = {"run": args.run + suffix, "case": case["id"], "direction": case["direction"],
                           "text": result["text"].strip(), "completed": result["completed"], "ms": elapsed,
                           "context_enabled": context, "terminology_enabled": terminology,
                           "inference": result, "resources": meter.result, "details": details}
                    row["signals"] = signals(case, row["text"])
                    destination.write(json.dumps(row, ensure_ascii=False)+"\n"); destination.flush()
                    print(f"{args.run+suffix} {index}/{len(cases)} {case['id']} {elapsed:.0f} ms", flush=True)
        metadata["socket_attempts_after_tripwire"] = events
        if events: raise RuntimeError("Offline tripwire triggered")
        (output / "metadata.json").write_text(json.dumps(metadata, indent=2)+"\n", encoding="utf-8")
    finally:
        if worker: worker.close()
        del m2m, opus
        gc.collect()


if __name__ == "__main__": main()
