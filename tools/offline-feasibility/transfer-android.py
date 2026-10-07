"""Binary-safe local ADB transfer into the debuggable spike's private files."""

import argparse
import hashlib
import json
import pathlib
import subprocess
import uuid

ROOT = pathlib.Path(__file__).resolve().parents[2]
APP = "com.commontongue.spike.offline"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--adb", required=True)
    parser.add_argument("--serial", default="emulator-5554")
    parser.add_argument("--asr", choices=["tiny", "base"], default="tiny")
    parser.add_argument("--fixture", choices=["piper", "espeak"], default="piper")
    parser.add_argument("--collect", type=pathlib.Path)
    args = parser.parse_args()
    adb = [args.adb, "-s", args.serial]
    if args.collect:
        result = subprocess.run(adb + ["exec-out", "run-as", APP, "cat", f"files/results-{args.asr}.json"], check=True, capture_output=True)
        data = json.loads(result.stdout)
        args.collect.parent.mkdir(parents=True, exist_ok=True)
        args.collect.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        return
    transfers = []
    for direction in ("en-es", "es-en"):
        for filename in ("source.spm", "target.spm", "vocab.json", "config.json",
                         "onnx/encoder_model_quantized.onnx", "onnx/decoder_model_quantized.onnx"):
            transfers.append((ROOT / f".local/models/{direction}/{filename}", f"files/models/{direction}/{filename}"))
    filename = f"ggml-{args.asr}-q5_1.bin"
    transfers.append((ROOT / ".local/models/whisper" / filename, f"files/models/whisper/{filename}"))
    for language in ("en", "es"):
        filename = f"{language}01-clean.wav"
        transfers.append((ROOT / f".local/feasibility-audio-{args.fixture}" / filename, f"files/audio/{filename}"))
    records = []
    for source, relative in transfers:
        directory = str(pathlib.PurePosixPath(relative).parent)
        subprocess.run(adb + ["shell", "run-as", APP, "mkdir", "-p", directory], check=True, capture_output=True)
        # ADB push is binary-safe. The debug run-as domain can read shell staging files.
        temporary = f"/data/local/tmp/common-tongue-pass2-{uuid.uuid4().hex}"
        try:
            subprocess.run(adb + ["push", str(source), temporary], check=True, capture_output=True, timeout=120)
            subprocess.run(adb + ["shell", "chmod", "644", temporary], check=True, capture_output=True)
            subprocess.run(adb + ["shell", "run-as", APP, "cp", temporary, relative], check=True, capture_output=True)
        finally:
            subprocess.run(adb + ["shell", "rm", "-f", temporary], check=True, capture_output=True)
        remote = subprocess.run(adb + ["shell", "run-as", APP, "sha256sum", relative], check=True, capture_output=True, text=True).stdout.split()[0]
        with source.open("rb") as content:
            expected = hashlib.file_digest(content, "sha256").hexdigest()
        if remote != expected:
            raise RuntimeError(f"Android checksum mismatch: {relative}")
        records.append({"file": relative, "bytes": source.stat().st_size, "sha256": remote})
        print(f"Verified Android {relative}", flush=True)
    destination = ROOT / f".local/android-transfer-{args.asr}.json"
    destination.write_text(json.dumps(records, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
