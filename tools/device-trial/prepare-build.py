"""Explicit preparation for native APK builds: pinned sources/tools, no models."""

import argparse
import hashlib
import json
import pathlib
import re
import shutil
import urllib.request
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
HERE = pathlib.Path(__file__).parent


def fetch(asset, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    if not path.exists():
        partial = path.with_suffix(path.suffix + ".partial")
        with urllib.request.urlopen(asset["url"], timeout=120) as response, partial.open("wb") as output:
            shutil.copyfileobj(response, output, 1024 * 1024)
        if hashlib.sha256(partial.read_bytes()).hexdigest() != asset["sha256"]: raise ValueError("Preparation download checksum mismatch")
        partial.replace(path)
    if path.stat().st_size != asset["bytes"] or hashlib.sha256(path.read_bytes()).hexdigest() != asset["sha256"]:
        raise ValueError("Preparation asset size/checksum mismatch")
    return path


def extract(archive, destination):
    destination.mkdir(parents=True, exist_ok=True)
    root = destination.resolve()
    with zipfile.ZipFile(archive) as source:
        for info in source.infolist():
            # GitHub archives have exactly one root directory. Strip only it.
            parts = pathlib.PurePosixPath(info.filename).parts[1:]
            if not parts: continue
            target = root.joinpath(*parts).resolve()
            if not target.is_relative_to(root): raise ValueError("Source archive path escape")
            if info.is_dir(): target.mkdir(parents=True, exist_ok=True)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                with source.open(info) as input_file, target.open("wb") as output: shutil.copyfileobj(input_file, output)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--platform-tools-only", action="store_true")
    args = parser.parse_args()
    setup = json.loads((HERE / "setup-assets.lock.json").read_text(encoding="utf-8"))
    fetch(setup["windows_platform_tools"], ROOT / ".local/device/setup-assets/platform-tools-windows.zip")
    if args.platform_tools_only: return
    old = json.loads((ROOT / "tools/offline-feasibility/artifacts.lock.json").read_text(encoding="utf-8"))["artifacts"]
    quality = json.loads((ROOT / "tools/translation-quality/artifacts.lock.json").read_text(encoding="utf-8"))["artifacts"]
    for name in ("whisper", "sentencepiece"):
        item = next(row for row in old if row["path"] == f".local/offline-sources/{name}.zip")
        extract(fetch(item, ROOT / item["path"]), ROOT / f".local/device/sources/{name}")
    candle = next(row for row in quality if row["path"] == ".local/quality/candle-runtime-source.zip")
    extract(fetch(candle, ROOT / candle["path"]), ROOT / ".local/quality/candle-runtime-source/candle-31f35b147389700ed2a178ee66a91c3cc25cc80d")
    extract(fetch(setup["abseil_source"], ROOT / ".local/device/setup-assets/abseil.zip"), ROOT / ".local/device/sources/sentencepiece/third_party/abseil-cpp")
    # Legacy FetchContent_Populate(full arguments) ignores the disconnect flags.
    # Patch acquisition only in this pass's separate cache; no tokenizer code or
    # Pass 2 source/cache is altered. Both archives were verified above.
    cmake = ROOT / ".local/device/sources/sentencepiece/CMakeLists.txt"
    source = cmake.read_text(encoding="utf-8")
    source, count = re.subn(r"  FetchContent_Populate\(abseil-cpp\s+GIT_REPOSITORY.*?GIT_TAG 20260526\.0\)",
                          '  if (NOT EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/third_party/abseil-cpp/CMakeLists.txt")\n'
                          '    message(FATAL_ERROR "Prepare the locked Abseil source before building")\n  endif()', source, count=1, flags=re.S)
    if count != 1: raise ValueError("Unexpected SentencePiece acquisition stanza; stop rather than guess")
    cmake.write_text(source, encoding="utf-8", newline="\n")
    print("Verified and prepared pinned native sources and Windows connection tools; no model downloads.")


if __name__ == "__main__": main()
