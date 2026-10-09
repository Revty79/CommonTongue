"""Package a model-free tester installer around an audited research APK."""

import argparse
import hashlib
import json
import pathlib
import shutil
import zipfile

from device import locked_assets, write_json

ROOT = pathlib.Path(__file__).resolve().parents[2]
HERE = pathlib.Path(__file__).parent


def digest(path):
    with path.open("rb") as stream: return hashlib.file_digest(stream, "sha256").hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=pathlib.Path, required=True)
    parser.add_argument("--platform-tools-archive", type=pathlib.Path, required=True)
    parser.add_argument("--output", type=pathlib.Path, required=True)
    parser.add_argument("--stage-local-cache", action="store_true", help="Dev-only adjacent cache; excluded from ZIP")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    lock = json.loads((HERE / "setup-assets.lock.json").read_text(encoding="utf-8"))
    platform = lock["windows_platform_tools"]
    if args.platform_tools_archive.stat().st_size != platform["bytes"] or digest(args.platform_tools_archive) != platform["sha256"]:
        raise ValueError("Official Windows tool archive mismatch")
    package_root = args.output / "CommonTongue-Pass5-Test-Setup"
    package_root.mkdir(exist_ok=True)
    for name in ("Install Common Tongue Test.bat", "setup.ps1"):
        shutil.copyfile(HERE / "setup" / name, package_root / name)
    apk_name = "CommonTongue-Pass5-Research.apk"
    shutil.copyfile(args.apk, package_root / apk_name)
    model_assets = []
    installed = locked_assets("madlad", "base", True)
    installed.append(next(item for item in locked_assets("opus", "tiny", False) if "models/whisper/" in item["destination"]))
    for item in installed:
        relative = item["destination"].removeprefix("files/models/")
        display = "English/Spanish translator (large download)" if relative.endswith("model-q4k.gguf") else "speech recognition" if relative.startswith("whisper/") else "translation text support" if relative.startswith("madlad/") else "OPUS research comparison data"
        model_assets.append({"display_name": display, "cache_name": "models/" + relative,
                             "url": item["url"], "sha256": item["sha256"], "bytes": item["bytes"],
                             "destination": item["destination"], "workspace_path": item["path"], "revision": item["revision"]})
    tts = {**lock["research_tts"], "cache_name": "espeak-1.52.0-signed.apk", "display_name": "local eSpeak research voice"}
    platform_files = []
    with zipfile.ZipFile(args.platform_tools_archive) as archive:
        for name in ("adb.exe", "AdbWinApi.dll", "AdbWinUsbApi.dll"):
            data = archive.read("platform-tools/" + name)
            platform_files.append({"file": name, "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()})
        (package_root / "PLATFORM_TOOLS_NOTICE.txt").write_bytes(archive.read("platform-tools/NOTICE.txt"))
    fixture_root = HERE / "fixtures"
    fixtures = []
    for path in sorted(fixture_root.glob("*.wav")) + [fixture_root / "trial-plan.json"]:
        relative = "fixtures/" + path.name
        (package_root / "fixtures").mkdir(exist_ok=True)
        shutil.copyfile(path, package_root / relative)
        fixtures.append({"file": relative, "bytes": path.stat().st_size, "sha256": digest(path),
                         "destination": "files/trial-plan.json" if path.name == "trial-plan.json" else "files/audio/" + path.name})
    manifest = {"schema_version": 1, "research_only": True, "apk": {"file": apk_name, "bytes": args.apk.stat().st_size, "sha256": digest(args.apk)},
                "platform_tools": {**platform, "cache_name": "platform-tools-windows.zip", "display_name": "phone connection tools"},
                "adb_files": platform_files, "models": model_assets, "tts": tts, "fixtures": fixtures,
                "requirements": {"android_api": 26, "abi": "arm64-v8a", "translator": "MADLAD400-3B-MT Q4_K",
                                 "candle_revision": "31f35b147389700ed2a178ee66a91c3cc25cc80d", "asr": "Whisper Base Q5_1",
                                 "runtimes": {"candle": "0.11.0; CPU; four threads",
                                              "whisper_cpp": "v1.9.5; d1be6fde11ac6e0407606b4e42fe72d34add8037; CPU; four threads",
                                              "onnx_runtime_android": "1.30.0; OPUS control only",
                                              "sentencepiece": "0.2.2; e0cce7d37b065b5140349dbe12c6bcf6192fdd78",
                                              "tts": "Installed Android default engine; EN/ES voices must report requires_network=false. Optional separate eSpeak NG 1.52.0, 4870adfa25b1a32b4361592f1be8a40337c58d6c"},
                                 "no_model_weights_in_apk_or_zip": True, "model_assets_bytes": sum(row["bytes"] for row in model_assets)}}
    write_json(package_root / "setup-manifest.json", manifest)
    (package_root / "README.txt").write_text(
        "COMMON TONGUE - PASS 5 RESEARCH SETUP\n\n"
        "Extract the complete ZIP. Connect your authorized Android phone with a USB data cable.\n"
        "Double-click Install Common Tongue Test.bat and follow the plain-English prompts.\n"
        "Keep the Windows PC online during setup. About 2 GB of pinned research models are downloaded once and cached.\n"
        "The APK alone cannot translate: use this setup package to provision the models and local research voice.\n"
        "Allow at least 4.5 GB free phone storage during setup. ARM64 and Android 8+ are required; acceptable performance is not guaranteed.\n"
        "After setup: tap Start Testing, allow the microphone, then wait for Ready to Speak.\n"
        "Radios may be on or off. Airplane Mode ON with Wi-Fi/mobile data OFF is only the offline-proof test.\n"
        "Human recordings are transient. Do not upload recordings or private test text.\n"
        "This is a research build for authorized test devices, not a public production release.\n\n"
        "EXACT REQUIREMENTS\n"
        "The model filenames, publication revisions, sizes and SHA-256 are in setup-manifest.json.\n"
        "MADLAD Q4_K and tokenizer/config are unconverted locked Pass 4 artifacts.\n"
        "Candle 0.11.0, commit 31f35b147389700ed2a178ee66a91c3cc25cc80d, CPU, four threads.\n"
        "Whisper Base Q5_1, whisper.cpp v1.9.5 d1be6fde11ac6e0407606b4e42fe72d34add8037.\n"
        "Whisper Tiny Q5_1 is also provisioned as an optional ASR comparison.\n"
        "OPUS-MT int8 is a research control already provisioned by setup, using ONNX Runtime Android 1.30.0.\n"
        "OPUS tokenization uses SentencePiece 0.2.2 e0cce7d37b065b5140349dbe12c6bcf6192fdd78.\n"
        "Offline installed Android English/Spanish voices are checked in the app; Set Up Offline Voices opens phone-side setup.\n"
        "eSpeak NG 1.52.0 is installed as a separate optional GPL-3.0-or-later research TTS application; choose it in phone TTS settings if needed.\n"
        "Corresponding TTS source: https://github.com/espeak-ng/espeak-ng/tree/4870adfa25b1a32b4361592f1be8a40337c58d6c\n"
        "Platform-tools notices are retained; official SDK terms: https://developer.android.com/studio/terms\n"
        "No model/runtime has commercial production approval.\n", encoding="utf-8", newline="\n")
    if args.stage_local_cache:
        # Explicit local development convenience. Models never enter the ZIP.
        for asset in model_assets:
            source = ROOT / asset["workspace_path"]
            if digest(source) != asset["sha256"]: raise ValueError("Local staging asset mismatch")
            target = package_root / ".cache" / asset["cache_name"]
            target.parent.mkdir(parents=True, exist_ok=True)
            if not target.exists(): target.hardlink_to(source)
        source = ROOT / ".local/offline-tools/espeak-1.52.0-signed.apk"
        target = package_root / ".cache" / tts["cache_name"]
        if digest(source) != tts["sha256"]: raise ValueError("Local TTS cache mismatch")
        if not target.exists(): target.hardlink_to(source)
    zip_path = args.output / "CommonTongue-Pass5-Test-Setup.zip"
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for path in sorted(package_root.rglob("*")):
            if path.is_file() and ".cache" not in path.relative_to(package_root).parts:
                archive.write(path, package_root.name + "/" + str(path.relative_to(package_root)).replace("\\", "/"))
    shutil.copyfile(args.apk, args.output / apk_name)
    contract = json.loads((ROOT / "spikes/physical-trial/src/main/assets/test-pack-contract.json").read_text(encoding="utf-8"))
    write_json(args.output / "test-pack-details.json", contract)
    shutil.copyfile(ROOT / "docs/device/LOCAL_TEST_PACK_SETUP.md", args.output / "PHONE-SETUP.md")
    write_json(args.output / "build-artifacts.json", {"apk": manifest["apk"], "setup_zip": {"file": zip_path.name, "bytes": zip_path.stat().st_size, "sha256": digest(zip_path)},
                                                   "test_pack": {"file": contract["pack_file"], "bytes": contract["pack_bytes"], "sha256": contract["pack_sha256"], "pack_id": contract["manifest"]["pack_id"]},
                                                   "requirements": manifest["requirements"]})
    print("Prepared research APK and one-click setup ZIP; model files excluded.")


if __name__ == "__main__": main()
