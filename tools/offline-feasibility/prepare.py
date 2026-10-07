"""Explicit online preparation; inference never invokes this script."""

import argparse
import hashlib
import json
import pathlib
import urllib.request
import zipfile
from concurrent.futures import ThreadPoolExecutor

ROOT = pathlib.Path(__file__).resolve().parents[2]
LOCAL = ROOT / ".local"
LOCK = pathlib.Path(__file__).with_name("artifacts.lock.json")


def fetch(url):
    with urllib.request.urlopen(url, timeout=120) as response:
        return response.read()


def download(item):
    destination = ROOT / item["path"]
    destination.parent.mkdir(parents=True, exist_ok=True)
    if not destination.exists():
        temporary = destination.with_suffix(destination.suffix + ".partial")
        with urllib.request.urlopen(item["url"], timeout=120) as response, temporary.open("wb") as output:
            while chunk := response.read(1024 * 1024):
                output.write(chunk)
        temporary.replace(destination)
    with destination.open("rb") as source:
        digest = hashlib.file_digest(source, "sha256").hexdigest()
    if item.get("sha256") and item["sha256"] != digest:
        raise RuntimeError(f"Checksum mismatch: {destination}")
    item.update(sha256=digest, bytes=destination.stat().st_size)
    print(f"Verified {item['path']}: {item['bytes']} bytes", flush=True)
    return item


def resolve():
    artifacts = []
    metadata = {}
    model_repos = {
        "en-es": "Xenova/opus-mt-en-es",
        "es-en": "Xenova/opus-mt-es-en",
        "en-romance": "Xenova/opus-mt-en-ROMANCE",
    }
    files = ["README.md", "config.json", "generation_config.json", "quantize_config.json",
             "source.spm", "target.spm", "vocab.json", "tokenizer_config.json",
             "onnx/encoder_model_quantized.onnx", "onnx/decoder_model_quantized.onnx"]
    for name, repo in model_repos.items():
        info = json.loads(fetch(f"https://huggingface.co/api/models/{repo}?blobs=true"))
        revision = info["sha"]
        source_repo = info["cardData"]["base_model"]
        source = json.loads(fetch(f"https://huggingface.co/api/models/{source_repo}?blobs=true"))
        metadata[name] = {"repo": repo, "revision": revision, "source_repo": source_repo,
                          "source_revision": source["sha"], "source_license": source["cardData"].get("license"),
                          "source_weight_bytes": {i["rfilename"]: i.get("size") for i in source["siblings"] if i["rfilename"] in ("pytorch_model.bin", "model.safetensors")}}
        for filename in files:
            blob = next(i for i in info["siblings"] if i["rfilename"] == filename)
            artifacts.append({"group": name, "revision": revision,
                              "url": f"https://huggingface.co/{repo}/resolve/{revision}/{filename}",
                              "path": f".local/models/{name}/{filename}",
                              "sha256": blob.get("lfs", {}).get("sha256")})
        for filename in ("README.md",):
            artifacts.append({"group": name, "revision": source["sha"],
                              "url": f"https://huggingface.co/{source_repo}/resolve/{source['sha']}/{filename}",
                              "path": f".local/models/{name}/source-{filename}"})
    whisper_repo = "ggerganov/whisper.cpp"
    info = json.loads(fetch(f"https://huggingface.co/api/models/{whisper_repo}?blobs=true"))
    metadata["whisper-models"] = {"repo": whisper_repo, "revision": info["sha"], "license": "MIT"}
    for filename in ("ggml-tiny-q5_1.bin", "ggml-base-q5_1.bin", "README.md"):
        blob = next(i for i in info["siblings"] if i["rfilename"] == filename)
        artifacts.append({"group": "whisper", "revision": info["sha"],
                          "url": f"https://huggingface.co/{whisper_repo}/resolve/{info['sha']}/{filename}",
                          "path": f".local/models/whisper/{filename}", "sha256": blob.get("lfs", {}).get("sha256")})
    sources = [("whisper", "ggml-org/whisper.cpp", "v1.9.5"),
               ("sentencepiece", "google/sentencepiece", "v0.2.2"),
               ("espeak", "espeak-ng/espeak-ng", "1.52.0")]
    for name, repo, tag in sources:
        commit = json.loads(fetch(f"https://api.github.com/repos/{repo}/commits/{tag}"))["sha"]
        metadata[name + "-runtime"] = {"repo": repo, "version": tag, "revision": commit}
        artifacts.append({"group": name + "-source", "revision": commit,
                          "url": f"https://codeload.github.com/{repo}/zip/{commit}",
                          "path": f".local/offline-sources/{name}.zip"})
    commit = json.loads(fetch("https://api.github.com/repos/espeak-ng/espeak-ng/commits/1.52.0"))["sha"]
    metadata["espeak-runtime"] = {"repo": "espeak-ng/espeak-ng", "version": "1.52.0", "revision": commit, "license": "GPL-3.0-or-later"}
    for filename in ("espeak-ng.msi", "espeak-1.52.0-signed.apk"):
        artifacts.append({"group": "espeak", "revision": commit,
                          "url": f"https://github.com/espeak-ng/espeak-ng/releases/download/1.52.0/{filename}",
                          "path": f".local/offline-tools/{filename}"})
    for filename in ("COPYING", "docs/voices.md", "espeak-ng-data/lang/gmw/en", "espeak-ng-data/lang/roa/es"):
        artifacts.append({"group": "espeak-license", "revision": commit,
                          "url": f"https://raw.githubusercontent.com/espeak-ng/espeak-ng/{commit}/{filename}",
                          "path": f".local/offline-tools/espeak-provenance/{filename}"})
    voice_repo = "rhasspy/piper-voices"
    voice_revision = json.loads(fetch(f"https://huggingface.co/api/models/{voice_repo}"))["sha"]
    metadata["piper-voices"] = {"repo": voice_repo, "revision": voice_revision,
                               "classification": "B: research only; dataset public domain, explicit weight grant needs review"}
    for language, directory, basename in (
            ("en", "en/en_US/ljspeech/medium", "en_US-ljspeech-medium"),
            ("es", "es/es_ES/carlfm/x_low", "es_ES-carlfm-x_low")):
        for filename in (basename + ".onnx", basename + ".onnx.json", "MODEL_CARD"):
            artifacts.append({"group": f"piper-{language}", "revision": voice_revision,
                              "url": f"https://huggingface.co/{voice_repo}/resolve/{voice_revision}/{directory}/{filename}",
                              "path": f".local/models/piper-{language}/{filename}"})
    metadata["piper-runtime"] = {"repo": "rhasspy/piper", "version": "2023.11.14-2",
                               "revision": json.loads(fetch("https://api.github.com/repos/rhasspy/piper/commits/2023.11.14-2"))["sha"],
                               "license": "MIT; bundled eSpeak NG GPL-3.0-or-later; archived research distribution"}
    artifacts.append({"group": "piper-runtime", "revision": metadata["piper-runtime"]["revision"],
                      "url": "https://github.com/rhasspy/piper/releases/download/2023.11.14-2/piper_windows_amd64.zip",
                      "path": ".local/offline-tools/piper_windows_amd64.zip"})
    return {"metadata": metadata, "artifacts": artifacts}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--resolve", action="store_true", help="Maintainer-only refresh; changes immutable lock")
    args = parser.parse_args()
    lock = resolve() if args.resolve else json.loads(LOCK.read_text(encoding="utf-8"))
    with ThreadPoolExecutor(max_workers=4) as pool:
        lock["artifacts"] = list(pool.map(download, lock["artifacts"]))
    LOCK.write_text(json.dumps(lock, indent=2) + "\n", encoding="utf-8")
    for name in ("whisper", "sentencepiece", "espeak"):
        target = LOCAL / "offline-sources" / name
        if not target.exists():
            with zipfile.ZipFile(LOCAL / "offline-sources" / f"{name}.zip") as archive:
                for member in archive.infolist():
                    relative = pathlib.PurePosixPath(member.filename).parts[1:]
                    if not relative:
                        continue
                    destination = target.joinpath(*relative).resolve()
                    if not destination.is_relative_to(target.resolve()):
                        raise RuntimeError("Archive member escapes source directory")
                    if member.is_dir():
                        destination.mkdir(parents=True, exist_ok=True)
                    else:
                        destination.parent.mkdir(parents=True, exist_ok=True)
                        destination.write_bytes(archive.read(member))
    piper = LOCAL / "offline-tools" / "piper"
    if not piper.exists():
        with zipfile.ZipFile(LOCAL / "offline-tools/piper_windows_amd64.zip") as archive:
            for member in archive.infolist():
                destination = (LOCAL / "offline-tools" / member.filename).resolve()
                if not destination.is_relative_to((LOCAL / "offline-tools").resolve()):
                    raise RuntimeError("Archive member escapes tool directory")
            archive.extractall(LOCAL / "offline-tools")


if __name__ == "__main__":
    main()
