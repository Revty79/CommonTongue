"""Package the model-free debug voice check; do not publish or commit binary assets."""
import hashlib
import json
import shutil
import subprocess
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
APK = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
MANIFEST = ROOT / "app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml"
OUTPUT = ROOT / ".local/pass6-artifacts"
NAME = "CommonTongue-Pass6-Offline-Voice-Check.apk"
ANDROID = "{http://schemas.android.com/apk/res/android}"


def main():
    manifest = ET.parse(MANIFEST).getroot()
    permissions = [node.get(ANDROID + "name") for node in manifest.findall("uses-permission")]
    if any(name != "com.commontongue.prototype.debug.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" for name in permissions):
        raise SystemExit("Unexpected permission in voice-check APK.")
    if manifest.findall("uses-permission-sdk-23"):
        raise SystemExit("Unexpected conditional permission.")
    if not any(node.get(ANDROID + "name") == "android.intent.action.TTS_SERVICE" for node in manifest.findall("queries/intent/action")):
        raise SystemExit("TTS engine discovery query is missing.")
    with zipfile.ZipFile(APK) as archive:
        native = [name for name in archive.namelist() if name.startswith("lib/")]
        allowed_ui = {f"lib/{abi}/libandroidx.graphics.path.so" for abi in ("arm64-v8a", "armeabi-v7a", "x86", "x86_64")}
        if any(name not in allowed_ui for name in native) or any(name != "DebugProbesKt.bin" and name.lower().endswith((".gguf", ".onnx", ".safetensors", ".bin", ".wav")) for name in archive.namelist()):
            raise SystemExit("Unapproved native/model/audio asset bundled in voice check.")
    OUTPUT.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(APK, OUTPUT / NAME)
    digest = hashlib.sha256(APK.read_bytes()).hexdigest()
    revision = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    dirty = bool(subprocess.check_output(["git", "status", "--porcelain"], cwd=ROOT, text=True))
    details = {
        "schema_version": 1, "scope": "PASS_6_MODEL_FREE_DEBUG_PHYSICAL_CHECK_NOT_PRODUCTION_RELEASE",
        "apk": NAME, "bytes": APK.stat().st_size, "sha256": digest,
        "source_commit": revision, "working_tree_dirty_at_build_packaging": dirty,
        "application_id": manifest.get("package"),
        "version_code": manifest.get(ANDROID + "versionCode"), "version_name": manifest.get(ANDROID + "versionName"),
        "permissions": permissions, "tts_service_discovery": True,
        "synthesis_path": "SpeechSynthesizer -> installed offline Android TTS -> PCM16 WAV -> AudioTrack",
        "runtime_requirements": ["Android API 26 or newer", "Installed Android TTS engine", "Installed offline English and Spanish voices", "Foreground Common Tongue session and available audio focus"],
        "translation_models_required": False, "vendor_voice_assets_bundled": False,
        "inherited_androidx_ui_native_libraries": native,
        "existing_pass5_installation_or_model_pack_modified": False,
        "physical_acceptance": "PENDING_NEW_S25_RESULTS_NOT_INFERRED_FROM_PASS5",
    }
    (OUTPUT / "Voice-Check-Details.json").write_text(json.dumps(details, indent=2) + "\n", encoding="utf-8")
    (OUTPUT / "SHA256SUMS.txt").write_text(f"{digest}  {NAME}\n", encoding="utf-8")
    shutil.copyfile(ROOT / "tools/voice-layer/START-HERE.txt", OUTPUT / "START-HERE.txt")
    print(json.dumps({"apk": NAME, "bytes": details["bytes"], "sha256": digest}))


if __name__ == "__main__":
    main()
