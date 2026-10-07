"""Offline research CLI. Model-dependent commands are deliberately outside normal CI."""

import argparse
import gc
import hashlib
import json
import os
import platform
import re
import subprocess
import threading
import time
import wave
from pathlib import Path

from offline import install_tripwire, probe

ROOT = Path(__file__).resolve().parents[2]
TOOLS = Path(__file__).parent


class Meter:
    def __init__(self):
        import psutil
        self.psutil = psutil
        self.process = psutil.Process()
        self.peak = 0
        self.cpu = {}
        self.done = threading.Event()

    def sample(self):
        while not self.done.is_set():
            processes = [self.process] + self.process.children(recursive=True)
            memory = 0
            for process in processes:
                try:
                    info = process.memory_info()
                    memory += info.rss
                    cpu = process.cpu_times()
                    self.cpu[process.pid] = cpu.user + cpu.system
                except self.psutil.Error:
                    pass
            self.peak = max(self.peak, memory)
            self.done.wait(0.01)

    def __enter__(self):
        self.started = time.perf_counter()
        self.thread = threading.Thread(target=self.sample, daemon=True)
        self.thread.start()
        self.initial_cpu = self.process.cpu_times()
        return self

    def __exit__(self, *args):
        self.done.set()
        self.thread.join()
        elapsed = time.perf_counter() - self.started
        current = self.process.cpu_times()
        child_cpu = sum(value for pid, value in self.cpu.items() if pid != self.process.pid)
        cpu_seconds = current.user + current.system - self.initial_cpu.user - self.initial_cpu.system + child_cpu
        self.result = {"peak_process_tree_rss_bytes": self.peak,
                       "steady_parent_rss_bytes": self.process.memory_info().rss,
                       "approx_cpu_seconds": cpu_seconds,
                       "approx_busy_cpu_cores": cpu_seconds / elapsed if elapsed else 0,
                       "memory_method": "10 ms process-tree RSS sampling; shared pages may be counted twice; not device RAM requirements"}


def espeak_binary():
    binary = ROOT / ".local/offline-build/espeak-desktop/src/Release/espeak-ng.exe"
    if not binary.exists():
        raise FileNotFoundError("Run build-desktop.ps1 to compile eSpeak")
    return binary


def synthesize(text, language, output, engine="espeak"):
    started = time.perf_counter()
    if engine == "piper":
        basename = "en_US-ljspeech-medium" if language == "en" else "es_ES-carlfm-x_low"
        subprocess.run([str(ROOT / ".local/offline-tools/piper/piper.exe"),
                        "--model", str(ROOT / f".local/models/piper-{language}/{basename}.onnx"),
                        "--output_file", str(output)], input=text + "\n", encoding="utf-8",
                       check=True, capture_output=True, timeout=60,
                       creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        identity = {"engine": "Piper 2023.11.14-2", "voice": basename,
                    "quality_limit": "Synthetic fixture; no native/fluent listening review; research-only voice rights"}
    else:
        subprocess.run([str(espeak_binary()), f"--path={ROOT / '.local/offline-build/espeak-desktop'}",
                        "-v", language, "-s", "150", "-w", str(output), text], check=True,
                       capture_output=True, timeout=30, creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        identity = {"engine": "eSpeak NG 1.52.0", "voice": language,
                    "quality_limit": "Formant/rule-based voice; robotic, not a neural shipping voice"}
    with wave.open(str(output)) as audio:
        duration = audio.getnframes() / audio.getframerate()
    return {**identity, "ms_including_engine_model_load": (time.perf_counter() - started) * 1000,
            "audio_seconds": duration, "path": str(output), "bytes": output.stat().st_size,
            "cold_load_included": True}


def asr(audio, language, model, threads):
    binary = ROOT / ".local/offline-build/whisper-desktop/bin/Release/whisper-cli.exe"
    weights = ROOT / f".local/models/whisper/ggml-{model}-q5_1.bin"
    output = audio.with_name(audio.stem + f"-{model}-asr")
    started = time.perf_counter()
    process = subprocess.run([str(binary), "-m", str(weights), "-f", str(audio), "-l", language,
                              "-t", str(threads), "-otxt", "-of", str(output), "-nt", "-bs", "1", "-bo", "1", "-nf", "-ng"],
                             check=True, capture_output=True, text=True, encoding="utf-8", timeout=300)
    text = Path(str(output) + ".txt").read_text(encoding="utf-8").strip()
    load = re.search(r"load time\s*=\s*([\d.]+) ms", process.stderr)
    full = re.search(r"total time\s*=\s*([\d.]+) ms", process.stderr)
    Path(str(output) + ".log").write_text(process.stderr, encoding="utf-8")
    return {"model": model, "quantization": "Q5_1", "language": language, "transcript": text,
            "wall_ms_including_load": (time.perf_counter() - started) * 1000,
            "reported_load_ms": float(load[1]) if load else None,
            "reported_total_ms": float(full[1]) if full else None,
            "threads": threads, "translation_mode": False,
            "decoding": "greedy; beam/best-of 1; no temperature fallback; CPU only"}


def normalized_words(text):
    return re.findall(r"\w+", text.casefold())


def word_error_rate(reference, hypothesis):
    expected, actual = normalized_words(reference), normalized_words(hypothesis)
    row = list(range(len(actual) + 1))
    for index, word in enumerate(expected, 1):
        new = [index]
        for column, candidate in enumerate(actual, 1):
            new.append(min(row[column] + 1, new[-1] + 1, row[column - 1] + (word != candidate)))
        row = new
    return row[-1] / max(1, len(expected))


def generate_audio(destination, engine):
    import numpy as np
    destination.mkdir(parents=True, exist_ok=True)
    corpus = json.loads((TOOLS / "corpus.json").read_text(encoding="utf-8"))
    samples = []
    for case in corpus:
        if case["id"] not in ("en01", "en04", "en12", "es01", "es04", "es12"):
            continue
        raw = destination / f"{case['id']}-raw.wav"
        language = case["direction"][:2]
        synthesis = synthesize(case["source"], language, raw, engine)
        with wave.open(str(raw)) as audio:
            rate = audio.getframerate()
            pcm = np.frombuffer(audio.readframes(audio.getnframes()), dtype="<i2").astype(np.float32) / 32768
        converted = np.interp(np.arange(round(len(pcm) * 16000 / rate)) * rate / 16000, np.arange(len(pcm)), pcm)
        variants = [("clean", converted)]
        if case["id"] in ("en04", "es04"):
            rng = np.random.default_rng(20261007)
            noise = rng.normal(0, np.sqrt(np.mean(converted ** 2) / 10), len(converted))
            variants.append(("noise10db", converted + noise))
        for condition, signal in variants:
            path = destination / f"{case['id']}-{condition}.wav"
            with wave.open(str(path), "wb") as output:
                output.setnchannels(1)
                output.setsampwidth(2)
                output.setframerate(16000)
                output.writeframes((np.clip(signal, -1, 1) * 32767).astype("<i2").tobytes())
            samples.append({"id": f"{case['id']}-{condition}", "direction": case["direction"],
                            "reference": case["source"], "category": case["category"],
                            "path": str(path.relative_to(ROOT)), "seconds": len(signal) / 16000,
                            "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                            "provenance": f"Locally generated {synthesis['engine']} {synthesis['voice']}; no human recordings",
                            "condition": condition})
    (destination / "samples.json").write_text(json.dumps(samples, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return samples


def pipeline(audio, direction, model, translator, output, threads, tts_engine):
    started = time.perf_counter()
    with Meter() as meter:
        recognition = asr(audio, direction[:2], model, threads)
        translation = translator.translate(recognition["transcript"])
        if not translation["completed"]:
            raise RuntimeError("Translation exceeded decoding limit")
        speech = synthesize(translation["text"], direction[-2:], output, tts_engine)
    return {"direction": direction, "asr": recognition, "translation": translation, "tts": speech,
            "total_ms": (time.perf_counter() - started) * 1000, "memory_cpu": meter.result,
            "translator_load_ms_excluded_from_total": translator.load_ms}


def main():
    if os.name == "nt":
        import ctypes
        ctypes.windll.kernel32.SetErrorMode(0x0001 | 0x0002 | 0x8000)
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["generate-audio", "benchmark", "pipeline"])
    parser.add_argument("--offline", action="store_true", required=True)
    parser.add_argument("--require-network-unavailable", action="store_true")
    parser.add_argument("--threads", type=int, default=4)
    parser.add_argument("--wav", type=Path)
    parser.add_argument("--direction", choices=["en-es", "es-en"], default="en-es")
    parser.add_argument("--asr", choices=["tiny", "base"], default="tiny")
    parser.add_argument("--translator", choices=["baseline", "romance"], default="baseline")
    parser.add_argument("--tts", choices=["espeak", "piper"], default="espeak")
    parser.add_argument("--output", type=Path, default=ROOT / ".local/feasibility-results")
    args = parser.parse_args()
    if not 1 <= args.threads <= 12:
        parser.error("threads must be between 1 and 12")
    observations = probe()
    if args.require_network_unavailable and any(value == "AVAILABLE" for value in observations.values()):
        parser.error("OS network block is absent; native subprocess offline proof refused")
    events = install_tripwire()
    os.environ.update(HF_HUB_OFFLINE="1", TRANSFORMERS_OFFLINE="1", HF_HUB_DISABLE_TELEMETRY="1")
    from translation import Marian
    args.output.mkdir(parents=True, exist_ok=True)
    result = {"platform": platform.platform(), "python": platform.python_version(), "threads": args.threads,
              "network": {"canary_before_tripwire": observations, "python_tripwire": True,
                          "native_process_network_block": "externally enforced; see canaries and run procedure"},
              "artifact_lock_sha256": hashlib.sha256((TOOLS / "artifacts.lock.json").read_bytes()).hexdigest()}
    if args.command == "generate-audio":
        result["samples"] = generate_audio(ROOT / f".local/feasibility-audio-{args.tts}", args.tts)
    elif args.command == "pipeline":
        if not args.wav:
            parser.error("pipeline requires --wav")
        if args.translator == "romance" and args.direction != "en-es":
            parser.error("Romance alternate only supports English source in this comparison")
        group = "en-romance" if args.translator == "romance" else args.direction
        translator = Marian(ROOT / ".local/models" / group, args.threads, ">>es<<" if group == "en-romance" else "")
        result["pipeline"] = pipeline(args.wav.resolve(), args.direction, args.asr, translator, args.output / "speech.wav", args.threads, args.tts)
    else:
        corpus = json.loads((TOOLS / "corpus.json").read_text(encoding="utf-8"))
        samples = json.loads((ROOT / f".local/feasibility-audio-{args.tts}/samples.json").read_text(encoding="utf-8"))
        result.update(text_cases=[], audio_cases=[], loads=[])
        for group in ("en-es", "es-en", "en-romance"):
            with Meter() as meter:
                translator = Marian(ROOT / ".local/models" / group, args.threads, ">>es<<" if group == "en-romance" else "")
            result["loads"].append({"model": group, "ms": translator.load_ms, **meter.result})
            direction = "en-es" if group == "en-romance" else group
            for case in corpus:
                if case["direction"] != direction:
                    continue
                with Meter() as meter:
                    translation = translator.translate(case["source"])
                result["text_cases"].append({**case, "candidate": group, "translation": translation, "memory_cpu": meter.result})
                print(f"{group} {case['id']}: {translation['text']}", flush=True)
            if group != "en-romance":
                for model in ("tiny", "base"):
                    for sample in samples:
                        if sample["direction"] != direction:
                            continue
                        audio_result = pipeline(ROOT / sample["path"], direction, model, translator,
                                                args.output / f"{sample['id']}-{model}-speech.wav", args.threads, args.tts)
                        audio_result.update(sample=sample, asr_wer=word_error_rate(sample["reference"], audio_result["asr"]["transcript"]),
                                            reference_translation=translator.translate(sample["reference"]))
                        result["audio_cases"].append(audio_result)
                        print(f"{model} {sample['id']}: {audio_result['asr']['transcript']}", flush=True)
                        (args.output / "benchmark.partial.json").write_text(json.dumps(result, indent=2, ensure_ascii=False), encoding="utf-8")
            del translator
            gc.collect()
    result["network"]["inference_socket_attempts"] = list(events)
    if events:
        raise RuntimeError(f"Offline tripwire was triggered: {events}")
    path = args.output / f"{args.command}.json"
    path.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(path, flush=True)


if __name__ == "__main__":
    main()
