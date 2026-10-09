"""Read-only anonymous inventory and explicit local deployment. No radio changes."""

import argparse
import hashlib
import json
import pathlib
import re
import subprocess
import uuid
import time

from schemas import device_label, profile, memory, offline, turn

ROOT = pathlib.Path(__file__).resolve().parents[2]
APP = "com.commontongue.spike.device"


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")


class Adb:
    def __init__(self, binary, index=None):
        self.binary = binary
        self.serial = None
        if index is not None:
            entries = self.devices()
            if not 0 <= index < len(entries) or entries[index][1] != "device":
                raise RuntimeError("Select an authorized endpoint from the anonymized list")
            self.serial = entries[index][0]  # Ephemeral routing only; never exported/logged.

    def run(self, *args, timeout=60):
        command = [self.binary] + (["-s", self.serial] if self.serial else []) + list(args)
        try:
            result = subprocess.run(command, capture_output=True, timeout=timeout)
        except (OSError, subprocess.TimeoutExpired) as error:
            raise RuntimeError("ADB unavailable or command timed out; no raw device diagnostics exported") from None
        if result.returncode:
            raise RuntimeError(f"ADB {args[0]} failed (exit {result.returncode}); inspect privately if needed")
        return result.stdout.decode("utf-8", errors="replace").strip()

    def devices(self):
        return [tuple(line.split()[:2]) for line in self.run("devices").splitlines()[1:]
                if re.fullmatch(r"\S+\s+(device|offline|unauthorized)", line.strip())]

    def shell(self, *args): return self.run("shell", *args)
    def optional(self, *args):
        try: return self.shell(*args)
        except RuntimeError: return ""
    def prop(self, name): return self.optional("getprop", name) or None


def number(text, pattern):
    match = re.search(pattern, text, re.MULTILINE)
    return int(match.group(1)) if match else None


def inventory(adb, label):
    device_label(label)
    mem = adb.shell("cat", "/proc/meminfo")
    battery = adb.optional("dumpsys", "battery")
    thermal = adb.optional("dumpsys", "thermalservice")
    storage = adb.shell("df", "-k", "/data").splitlines()[-1].split()
    free = int(storage[-3]) * 1024 if len(storage) >= 6 and storage[-3].isdigit() else None
    features = adb.optional("pm", "list", "features")
    accelerators = [line.removeprefix("feature:") for line in features.splitlines()
                    if re.fullmatch(r"feature:android\.hardware\.(?:vulkan\.(?:level|version|compute)|opengles\.aep|neuralnetworks)(?:=[0-9]+)?", line)]
    level = number(battery, r"^\s*level:\s*([0-9]+)")
    scale = number(battery, r"^\s*scale:\s*([0-9]+)")
    temperature = number(battery, r"^\s*temperature:\s*([0-9]+)")
    charging = number(battery, r"^\s*status:\s*([0-9]+)")
    status = number(thermal, r"^\s*(?:Thermal Status|mStatus|mThermalStatus):\s*([0-6])")
    screen = re.findall(r"(?:Physical|Override) size:\s*([0-9]+x[0-9]+)", adb.optional("wm", "size"))
    api = adb.prop("ro.build.version.sdk")
    cores = adb.optional("getconf", "_NPROCESSORS_ONLN")
    page = adb.optional("getconf", "PAGESIZE")
    data = {
        "schema_version": 1, "device_label": label,
        "manufacturer": adb.prop("ro.product.manufacturer"), "model": adb.prop("ro.product.model"),
        "android_version": adb.prop("ro.build.version.release"), "api": int(api) if api and api.isdigit() else None,
        "supported_abis": (adb.prop("ro.product.cpu.abilist") or "").split(","),
        "preferred_abi": adb.prop("ro.product.cpu.abi"), "actual_execution_abi": None,
        "total_ram_bytes": (number(mem, r"^MemTotal:\s*([0-9]+)") or 0) * 1024,
        "available_ram_bytes": (number(mem, r"^MemAvailable:\s*([0-9]+)") or 0) * 1024,
        "cpu_core_count": int(cores) if cores.isdigit() else None,
        "soc_manufacturer": adb.prop("ro.soc.manufacturer"), "soc_model": adb.prop("ro.soc.model"),
        "board_platform": adb.prop("ro.board.platform"), "free_storage_bytes": free,
        "battery_percent": level * 100 / scale if level is not None and scale else None,
        "battery_temperature_c": temperature / 10 if temperature is not None else None, "thermal_status": status,
        "power_source": "USB" if re.search(r"^\s*USB powered:\s*true", battery, re.M) else "AC" if re.search(r"^\s*AC powered:\s*true", battery, re.M) else "WIRELESS" if re.search(r"^\s*Wireless powered:\s*true", battery, re.M) else "NONE_OR_UNKNOWN",
        "battery_charging": charging == 2 if charging is not None else None,
        "screen_size": screen, "device_type": "tablet_or_phone_unconfirmed",
        "reported_acceleration_features": accelerators, "page_size_bytes": int(page) if page.isdigit() else None,
        "offline": {"airplane_mode_on": adb.optional("settings", "get", "global", "airplane_mode_on") == "1",
                    "wifi_setting_off": adb.optional("settings", "get", "global", "wifi_on") == "0",
                    "mobile_data_setting_off": "feature:android.hardware.telephony" not in features.splitlines() or adb.optional("settings", "get", "global", "mobile_data") == "0",
                    "internet_permission": False},
        "physical_device": not adb.serial.startswith("emulator-") and adb.prop("ro.kernel.qemu") != "1", "result": "NOT_TESTED",
    }
    profile(data)
    return data


def locked_assets(translator, asr, control):
    quality = json.loads((ROOT / "tools/translation-quality/artifacts.lock.json").read_text(encoding="utf-8"))["artifacts"]
    prior = json.loads((ROOT / "tools/offline-feasibility/artifacts.lock.json").read_text(encoding="utf-8"))["artifacts"]
    result = []
    if translator == "madlad":
        for filename in ("model-q4k.gguf", "config.json", "tokenizer.json"):
            item = next(row for row in quality if row["path"].endswith("/google--madlad400-3b-mt/" + filename))
            result.append({**item, "destination": "files/models/madlad/" + filename})
    if translator == "opus" or control:
        for direction in ("en-es", "es-en"):
            for filename in ("config.json", "source.spm", "target.spm", "vocab.json", "onnx/encoder_model_quantized.onnx", "onnx/decoder_model_quantized.onnx"):
                item = next(row for row in prior if row["path"] == f".local/models/{direction}/{filename}")
                result.append({**item, "destination": f"files/models/{direction}/{filename}"})
    item = next(row for row in prior if row["path"] == f".local/models/whisper/ggml-{asr}-q5_1.bin")
    result.append({**item, "destination": f"files/models/whisper/ggml-{asr}-q5_1.bin"})
    return result


def verify_local(item):
    source = (ROOT / item["path"]).resolve()
    if not source.is_relative_to(ROOT / ".local") or not source.is_file():
        raise ValueError("Prepared locked local artifact missing")
    with source.open("rb") as stream: actual = hashlib.file_digest(stream, "sha256").hexdigest()
    if actual != item["sha256"] or source.stat().st_size != item["bytes"]:
        raise ValueError("Locked local artifact checksum/size mismatch")
    return source


def private_transfer(adb, source, destination, expected=None):
    if not re.fullmatch(r"files/(?:models|audio|trial-plan\.json|provision\.json)(?:/[A-Za-z0-9_.-]+)*", destination) or ".." in destination:
        raise ValueError("Invalid app-private destination")
    with source.open("rb") as stream: digest = hashlib.file_digest(stream, "sha256").hexdigest()
    if expected and digest != expected: raise ValueError("Transfer source checksum mismatch")
    try:
        existing = adb.shell("run-as", APP, "sha256sum", destination).split()[0]
        if existing == digest: return {"file": destination, "bytes": source.stat().st_size, "sha256": digest, "verified_on_device": True}
    except RuntimeError: pass
    temporary = "/data/local/tmp/common-tongue-pass5-" + uuid.uuid4().hex
    partial = destination + ".partial"
    directory = str(pathlib.PurePosixPath(destination).parent)
    adb.shell("run-as", APP, "mkdir", "-p", directory)
    try:
        adb.run("push", str(source), temporary, timeout=900)
        adb.shell("chmod", "644", temporary)
        adb.shell("run-as", APP, "cp", temporary, partial)
        actual = adb.shell("run-as", APP, "sha256sum", partial).split()[0]
        if actual != digest: raise ValueError("Device checksum mismatch; final asset not replaced")
        adb.shell("run-as", APP, "mv", partial, destination)
    finally:
        adb.shell("rm", "-f", temporary)
        adb.shell("run-as", APP, "rm", "-f", partial)
    return {"file": destination, "bytes": source.stat().st_size, "sha256": digest, "verified_on_device": True}


def sanitized_metric(item):
    if not isinstance(item, dict) or set(item) != {"event", "elapsed_ms", "data"}: raise ValueError("Unexpected metric envelope")
    event, data = item["event"], item["data"]
    if event in {"worker_sample", "ui_sample", "after_unload_ui"}: memory(data)
    elif event == "models_loaded":
        allowed = {"translator", "asr", "translation_load_ms", "asr_load_ms", "start_memory", "translation_loaded_memory", "loaded_memory", "native_build", "threads", "stage", "asr_model_loaded", "translation_model_loaded", "native_runtime_verified", "mode"}
        if set(data) - allowed: raise ValueError("Unexpected load metric")
        for key in ("start_memory", "translation_loaded_memory", "loaded_memory"): memory(data[key])
        if "stage" in data and data["stage"] != "READY": raise ValueError("Invalid ready stage")
        mode = data.get("mode", "FULL_PIPELINE")
        if mode not in __import__("schemas").DIAGNOSTIC_MODES: raise ValueError("Unknown ready configuration")
        if "asr_model_loaded" in data and data["asr_model_loaded"] is not (mode != "MADLAD_ONLY"): raise ValueError("ASR readiness not established")
        if "translation_model_loaded" in data and data["translation_model_loaded"] is not (mode != "WHISPER_ONLY"): raise ValueError("Translation readiness not established")
    elif event == "pipeline_stage": __import__("schemas").pipeline_checkpoint(data)
    elif event == "worker_exit_info": __import__("schemas").exit_diagnostic(data)
    elif event == "component_check": __import__("schemas").component_check(data)
    elif event == "tts_diagnostic": __import__("schemas").tts_diagnostic(data)
    elif event == "pack_state_after_restart":
        __import__("schemas").pack_diagnostic(data)
    elif event == "model_load_stage":
        allowed = {"stage", "message", "pack", "pack_hashes_verified", "native_runtime_verified", "native_build", "translation_model_loaded", "translation_load_ms"}
        if set(data) - allowed or data.get("stage") not in __import__("schemas").LOAD_STAGES:
            raise ValueError("Unexpected model loading diagnostic")
        if "pack" in data: __import__("schemas").pack_diagnostic(data["pack"])
        for key in {"pack_hashes_verified", "native_runtime_verified", "translation_model_loaded"}:
            if key in data and type(data[key]) is not bool: raise ValueError("Invalid model loading observation")
        if "native_build" in data and not re.fullmatch(r"arm64-v8a; (optimized|unoptimized); CPU only; GGML_NATIVE=OFF", data["native_build"]):
            raise ValueError("Unexpected native runtime diagnostic")
        # A diagnostic message is UI text; the finite stage code is sufficient for exports.
        item = {**item, "data": {key: value for key, value in data.items() if key != "message"}}
    elif event == "turn":
        if set(data) - {"source_kind", "direction", "asr_ms", "translation_ms", "speech_seconds", "tts", "resident_turn_index", "first_after_model_load"}: raise ValueError("Unexpected turn metric")
        if set(data.get("tts", {})) - __import__("schemas").TTS_FIELDS: raise ValueError("Unexpected TTS metric")
    elif event == "error":
        # Raw diagnostic strings can contain paths or source fragments.
        safe = {"stage": data.get("stage", "UX") if data.get("stage", "UX") in (__import__("schemas").STAGES | __import__("schemas").LOAD_STAGES | __import__("schemas").PIPELINE_STAGES) else "UX", "diagnostic": "redacted; inspect only in local storage"}
        if data.get("diagnostic_code") in (__import__("schemas").LOAD_CODES | __import__("schemas").TTS_CODES): safe["diagnostic_code"] = data["diagnostic_code"]
        if safe["stage"] == "TTS" and data.get("error_type") in __import__("schemas").TTS_ERROR_TYPES: safe["error_type"] = data["error_type"]
        if safe["stage"] == "NATIVE_RUNTIME" and safe.get("diagnostic_code") == "NATIVE_LINK_FAILED":
            library, symbol = data.get("native_missing_library"), data.get("native_missing_symbol")
            if isinstance(library, str) and library in __import__("schemas").NATIVE_LIBRARIES:
                safe["native_missing_library"] = library
            if isinstance(symbol, str) and __import__("schemas").NATIVE_SYMBOL.fullmatch(symbol):
                safe["native_missing_symbol"] = symbol
        item = {**item, "data": safe}
    elif event in {"activity_created", "capture_release", "cancel_or_unload", "worker_disconnected", "worker_terminated"}:
        allowed = {"launch_to_ui_ms", "speech_seconds", "reason", "ui_memory", "method", "memory_before", "last_load_stage", "last_pipeline_stage"}
        if set(data) - allowed: raise ValueError("Unexpected lifecycle metric")
        if "last_load_stage" in data and data["last_load_stage"] not in (__import__("schemas").LOAD_STAGES | __import__("schemas").PIPELINE_STAGES): raise ValueError("Unknown last load stage")
        if "last_pipeline_stage" in data and data["last_pipeline_stage"] not in (__import__("schemas").PIPELINE_STAGES | {"NOT_STARTED"}): raise ValueError("Unknown last pipeline stage")
        for key in ("ui_memory", "memory_before"):
            if key in data: memory(data[key])
    else: raise ValueError("Unknown metric event")
    __import__("schemas").finite_tree(item)
    return item


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["list", "inventory", "install", "provision", "fixtures", "collect", "sample", "launch"])
    parser.add_argument("--adb", required=True)
    parser.add_argument("--device-index", type=int)
    parser.add_argument("--label", default="device-high-01")
    parser.add_argument("--translator", choices=["madlad", "opus"], default="madlad")
    parser.add_argument("--asr", choices=["base", "tiny"], default="base")
    parser.add_argument("--with-opus-control", action="store_true")
    parser.add_argument("--output", type=pathlib.Path)
    parser.add_argument("--include-human-text", action="store_true")
    parser.add_argument("--reviewed-for-personal-data", action="store_true")
    parser.add_argument("--seconds", type=int, default=60)
    args = parser.parse_args()
    if args.command != "list" and args.device_index is None: parser.error("Select --device-index explicitly; serials are never displayed")
    adb = Adb(args.adb, args.device_index)
    device_label(args.label)
    output = args.output or ROOT / ".local/device" / args.label
    if args.command == "list":
        for index, (serial, state) in enumerate(adb.devices()):
            print(json.dumps({"device_index": index, "state": state, "physical_endpoint": not serial.startswith("emulator-")}, sort_keys=True))
        return
    current = inventory(adb, args.label)
    if not current["physical_device"]: raise ValueError("Physical trial tooling refuses emulator evidence")
    write_json(output / "profile.json", current)
    if args.command == "inventory":
        print(json.dumps(current, indent=2)); return
    if "arm64-v8a" not in current["supported_abis"] or (current["api"] or 0) < 26:
        raise ValueError("Research APK requires ARM64 and Android API 26+; record INCOMPATIBLE")
    if args.command == "install":
        apk = ROOT / "spikes/physical-trial/build/outputs/apk/debug/physical-trial-debug.apk"
        if not apk.is_file():
            apks = list((ROOT / "spikes/physical-trial/build/outputs/apk/debug").glob("*.apk"))
            if len(apks) != 1: raise ValueError("Build exactly one debug research APK first")
            apk = apks[0]
        adb.run("install", "-r", str(apk), timeout=120)
        print("Research APK installed; production application and unrelated phone data untouched.")
    elif args.command == "launch":
        raw = adb.shell("am", "start", "-W", "-n", APP + "/.TrialActivity")
        timing = {name: number(raw, rf"^{name}:\s*([0-9]+)") for name in ("ThisTime", "TotalTime", "WaitTime")}
        write_json(output / "launch.json", {"device_label": args.label, "adb_launch_ms": timing})
        print("Research app launched; anonymous launch timings recorded.")
    elif args.command == "sample":
        if not 1 <= args.seconds <= 1200: raise ValueError("Use a bounded 1-1200-second observation session")
        samples = []
        start = time.monotonic()
        while time.monotonic() - start < args.seconds:
            data = {"elapsed_seconds": time.monotonic() - start}
            for label, package in (("research_app", APP), ("external_tts", "com.reecedunn.espeak")):
                raw = adb.optional("dumpsys", "meminfo", package)
                pss = [int(value) for value in re.findall(r"TOTAL PSS:\s*([0-9]+)", raw)]
                rss = [int(value) for value in re.findall(r"TOTAL RSS:\s*([0-9]+)", raw)]
                data[label] = {"observed_process_count": len(pss), "total_pss_kb": sum(pss) if pss else None, "total_rss_kb": sum(rss) if rss else None}
            snapshot = inventory(adb, args.label)
            for key in ("available_ram_bytes", "battery_percent", "battery_temperature_c", "thermal_status", "power_source", "battery_charging"):
                data[key] = snapshot[key]
            samples.append(data)
            print("Anonymous memory/thermal sample recorded", flush=True)
            time.sleep(min(5, max(0, args.seconds - (time.monotonic() - start))))
        raw = adb.optional("dumpsys", "activity", "exit-info", APP)
        reasons = [int(value) for value in re.findall(r"reason=([0-9]+)", raw)]
        write_json(output / "resource-session.json", {"device_label": args.label, "samples": samples,
                   "research_app_exit_reason_codes": reasons, "exit_reason_limit": "Review platform reason codes and session timing; explicit cancellation is not automatically an OOM"})
        print("Session complete. Raw process identifiers/system dumps were not exported.")
    elif args.command == "provision":
        items = locked_assets(args.translator, args.asr, args.with_opus_control)
        sources = [(item, verify_local(item)) for item in items]
        required = sum(item["bytes"] for item in items) + max(item["bytes"] for item in items) + 512 * 1024**2
        if not current["free_storage_bytes"] or current["free_storage_bytes"] < required:
            raise ValueError("Insufficient staging/storage margin; free space manually before provisioning")
        records = []
        for item, source in sources:
            records.append(private_transfer(adb, source, item["destination"], item["sha256"]))
            print("Checksum verified: " + item["destination"], flush=True)
        receipt = {"schema_version": 1, "device_label": args.label, "verified_on_device": True,
                   "translator": args.translator, "asr": args.asr, "files": records,
                   "active_asset_bytes": sum(record["bytes"] for record in records)}
        path = output / "provision.json"; write_json(path, receipt)
        private_transfer(adb, path, "files/provision.json")
    elif args.command == "fixtures":
        plan = ROOT / "tools/device-trial/fixtures/trial-plan.json"
        data = json.loads(plan.read_text(encoding="utf-8"))
        for item in data["cases"]:
            if "file" in item:
                record = next(row for row in data["audio_artifacts"] if row["file"] == item["file"])
                private_transfer(adb, plan.parent / item["file"], "files/audio/" + item["file"], record["sha256"])
        private_transfer(adb, plan, "files/trial-plan.json")
        print("Fixed authored WAV/text checks transferred. Run them from the app after enabling offline mode.")
    elif args.command == "collect":
        raw = adb.run("exec-out", "run-as", APP, "cat", "files/metrics/session.jsonl")
        metrics = [sanitized_metric(json.loads(line)) for line in raw.splitlines() if line]
        if any(row["event"] == "worker_sample" and row["data"].get("execution_abi") == "arm64-v8a" for row in metrics):
            current["actual_execution_abi"] = "arm64-v8a"
            write_json(output / "profile.json", current)
        write_json(output / "metrics.json", {"device_label": args.label, "events": metrics})
        name = f"fixtures-{args.translator}-{args.asr}.json"
        try:
            result = json.loads(adb.run("exec-out", "run-as", APP, "cat", "files/results/" + name))
            if set(result) != {"cases", "offline"}: raise ValueError("Unexpected fixture envelope")
            for row in result["cases"]: turn(row)
            offline(result["offline"])
            write_json(output / name, {"device_label": args.label, **result})
        except RuntimeError: print("No completed fixed-fixture result yet; metrics collected.")
        if args.include_human_text:
            if not args.reviewed_for_personal_data: raise ValueError("Human text export requires explicit review acknowledgement")
            names = adb.shell("run-as", APP, "ls", "files/results").splitlines()
            rows = []
            for name in names:
                if re.fullmatch(r"saved-[a-f0-9]{32}\.json", name):
                    row = json.loads(adb.run("exec-out", "run-as", APP, "cat", "files/results/" + name))
                    rows.append(turn(row, human_text=True))
            write_json(output / "human-text-review-required.json", {"device_label": args.label, "cases": rows})
        print("Anonymous allowlisted evidence collected locally; inspect before committing. No recordings exported.")


if __name__ == "__main__":
    try: main()
    except (ValueError, RuntimeError, StopIteration, FileNotFoundError) as error:
        raise SystemExit(str(error) if not isinstance(error, FileNotFoundError) else "Required local research asset missing") from None
