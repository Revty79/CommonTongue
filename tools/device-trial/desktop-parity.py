"""Exercise the port's C ABI on Windows, not a physical Android result."""

import argparse
import ctypes
import hashlib
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/offline-feasibility"))
from offline import install_tripwire, probe


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=pathlib.Path, required=True)
    parser.add_argument("--limit", type=int, help="Use the first N existing synthetic text controls")
    args = parser.parse_args()
    observations = probe()
    if "AVAILABLE" in observations.values(): raise RuntimeError("Use the OS network-restricted inference sandbox")
    events = install_tripwire()
    ctypes.windll.kernel32.SetErrorMode(0x0001 | 0x0002 | 0x8000)
    library = ROOT / ".local/device/t5-desktop/x86_64-pc-windows-msvc/release/trial_t5.dll"
    native = ctypes.CDLL(str(library))
    native.trial_t5_load.argtypes = [ctypes.c_char_p]; native.trial_t5_load.restype = ctypes.c_void_p
    progress_type = ctypes.CFUNCTYPE(ctypes.c_int32, ctypes.c_uint32, ctypes.c_void_p)
    native.trial_t5_run.argtypes = [ctypes.c_uint64, ctypes.c_char_p, progress_type, ctypes.c_void_p]; native.trial_t5_run.restype = ctypes.c_void_p
    native.trial_t5_close.argtypes = [ctypes.c_uint64]
    native.trial_t5_string_free.argtypes = [ctypes.c_void_p]
    def reply(pointer):
        try: return json.loads(ctypes.string_at(pointer).decode("utf-8"))
        finally: native.trial_t5_string_free(pointer)
    loaded = reply(native.trial_t5_load(str(ROOT / ".local/quality/models/google--madlad400-3b-mt").encode()))
    if "error" in loaded: raise RuntimeError(loaded["error"])
    if loaded.get("handle_kind") != "opaque-id-v1" or not 1 <= loaded["handle"] <= 0xffffffff:
        raise RuntimeError("Opaque model ID contract failed")
    plan = json.loads((ROOT / "tools/device-trial/fixtures/trial-plan.json").read_text(encoding="utf-8"))
    results = []
    stages = []
    @progress_type
    def progress(stage, context):
        stages.append(stage)
        return 1
    @progress_type
    def abort_checkpoint(stage, context): return 0
    sample = json.dumps({"text": plan["text_controls"][0]["source"], "direction": "en-es"}).encode()
    invalid_handles_rejected = True
    for invalid in (0, 0xb400007a12345678, 0x7fffffffffffffff, 0xffffffffffffffff):
        result = reply(native.trial_t5_run(invalid, sample, progress, None))
        invalid_handles_rejected &= "error" in result
    if not invalid_handles_rejected: raise RuntimeError("Invalid handle was not rejected")
    checkpoint_failure_aborts = "error" in reply(native.trial_t5_run(loaded["handle"], sample, abort_checkpoint, None))
    if not checkpoint_failure_aborts: raise RuntimeError("Failed checkpoint did not stop inference")
    try:
        controls = plan["text_controls"]
        if args.limit is not None:
            if not 1 <= args.limit <= len(controls): raise ValueError("Invalid control limit")
            controls = controls[:args.limit]
        for row in controls:
            stages.clear()
            output = reply(native.trial_t5_run(loaded["handle"], json.dumps({"text": row["source"], "direction": row["direction"]}, ensure_ascii=False).encode(), progress, None))
            if "error" in output: raise RuntimeError(output["error"])
            if stages != [1, 2, 3, 4, 5, 6]: raise RuntimeError("Translation checkpoints missing or out of order")
            results.append({"case_id": row["case_id"], "text": output["text"], "completed": output["completed"],
                            "matches_pass4": output["text"] == row["expected_pass4_translation"], "translation_checkpoints": stages.copy()})
            print(row["case_id"] + ": " + ("MATCH" if results[-1]["matches_pass4"] else "DIVERGED"), flush=True)
    finally: native.trial_t5_close(loaded["handle"])
    closed_handle_rejected = "error" in reply(native.trial_t5_run(loaded["handle"], sample, progress, None))
    if not closed_handle_rejected: raise RuntimeError("Closed model ID was not rejected")
    result = {"scope": "Windows_C_ABI_port_control_NOT_ANDROID_NOT_PHYSICAL", "runtime_revision": "31f35b147389700ed2a178ee66a91c3cc25cc80d",
              "rust_version": "1.91.0", "target_cpu": "default_portable_x86_64; Pass4 used x86-64-v3", "profile": "release_thin_lto",
              "library_sha256": hashlib.sha256(library.read_bytes()).hexdigest(), "offline_observations": observations,
              "post_tripwire_events": events, "results": results, "opaque_handle_kind": loaded["handle_kind"],
              "invalid_handles_rejected": invalid_handles_rejected, "closed_handle_rejected": closed_handle_rejected,
              "checkpoint_failure_aborts_inference": checkpoint_failure_aborts}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
    if events or not all(row["completed"] for row in results):
        raise RuntimeError("Incomplete inference or socket attempt; stop adoption")
    if not all(row["matches_pass4"] for row in results):
        print("DIVERGENCE: preserve both outputs and review before Android adoption; exact weights are unchanged.")


if __name__ == "__main__": main()
