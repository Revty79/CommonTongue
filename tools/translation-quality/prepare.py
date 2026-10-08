"""Explicit downloads/conversion only; the inference runner has no download path."""

import argparse
import hashlib
import json
import os
import urllib.request
from pathlib import Path

HERE = Path(__file__).parent
ROOT = HERE.parents[1]


def digest(path):
    with path.open("rb") as stream: return hashlib.file_digest(stream, "sha256").hexdigest()


def verify_file(path, record):
    if path.stat().st_size != record["bytes"] or digest(path) != record["sha256"]:
        raise ValueError("Artifact integrity failure: " + str(path))


def convert_m2m():
    os.environ.update(HF_HUB_OFFLINE="1", TRANSFORMERS_OFFLINE="1", HF_HUB_DISABLE_TELEMETRY="1")
    import time
    import ctranslate2
    import transformers
    import torch
    source = ROOT / ".local/quality/models/facebook--m2m100_418M"
    target = ROOT / ".local/quality/models/m2m100-ct2-int8"
    if not (source / "special_tokens_map.json").exists():
        raise FileNotFoundError("M2M100 needs its pinned special-token map; do not fabricate language IDs")
    started = time.perf_counter()
    ctranslate2.converters.TransformersConverter(str(source)).convert(str(target), quantization="int8")
    records = [{"path": f.relative_to(ROOT).as_posix(), "filename": f.name,
                "bytes": f.stat().st_size, "sha256": digest(f)} for f in sorted(target.iterdir()) if f.is_file()]
    receipt = {"runtime": "CTranslate2 " + ctranslate2.__version__, "transformers": transformers.__version__,
               "torch": torch.__version__, "quantization": "int8", "source_revision": "55c2e61bbf05dfb8d7abccdc3fae6fc8512fd636",
               "elapsed_seconds": time.perf_counter()-started, "artifacts": records}
    (ROOT / ".local/quality/conversion.json").write_text(json.dumps(receipt, indent=2)+"\n", encoding="utf-8")
    print(json.dumps(receipt), flush=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--download", action="store_true")
    parser.add_argument("--verify-local", action="store_true")
    parser.add_argument("--convert-m2m", action="store_true")
    args = parser.parse_args()
    if args.convert_m2m: return convert_m2m()
    if not args.download and not args.verify_local: parser.error("Choose explicit preparation operation")
    lock = json.loads((HERE / "artifacts.lock.json").read_text(encoding="utf-8"))
    for record in lock["artifacts"]:
        path = ROOT / record["path"]
        if not path.exists() and args.download and record.get("url"):
            path.parent.mkdir(parents=True, exist_ok=True)
            temporary = path.with_suffix(path.suffix + ".partial")
            urllib.request.urlretrieve(record["url"], temporary)
            verify_file(temporary, record)
            temporary.replace(path)
        if not path.exists():
            if args.download and not record.get("url"):
                print("LOCAL CONVERSION REQUIRED", record["path"], flush=True)
                continue
            raise FileNotFoundError("Prepare/convert locally before inference: " + str(path))
        verify_file(path, record)
        print("VERIFIED", record["path"], flush=True)


if __name__ == "__main__": main()
