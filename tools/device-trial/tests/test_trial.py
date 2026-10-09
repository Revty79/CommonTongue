import copy
import hashlib
import importlib.util
import json
import pathlib
import struct
import sys
import tempfile
import unittest
import wave
from unittest.mock import patch

HERE = pathlib.Path(__file__).resolve().parents[1]
ROOT = HERE.parents[1]
sys.path.insert(0, str(HERE))
import schemas
import device
from analyze import percentile, rating_summary

spec = importlib.util.spec_from_file_location("apk_audit", HERE / "verify-apk.py")
apk = importlib.util.module_from_spec(spec)
spec.loader.exec_module(apk)


class SchemaTests(unittest.TestCase):
    def test_profile_rejects_private_identifier(self):
        for key in ("serial", "android_id", "phone_number", "ssid", "location", "account_name", "username"):
            data = {"schema_version": 1, "device_label": "device-high-01", "physical_device": True, "result": "NOT_TESTED", key: "private"}
            with self.subTest(key=key), self.assertRaises(ValueError): schemas.profile(data)

    def test_profile_does_not_invent_execution_abi(self):
        data = {"schema_version": 1, "device_label": "device-old-01", "physical_device": True, "result": "NOT_TESTED", "actual_execution_abi": None}
        self.assertIsNone(schemas.profile(data)["actual_execution_abi"])

    def test_profile_rejects_nested_identifier(self):
        data = {"schema_version": 1, "device_label": "device-high-01", "physical_device": True, "result": "NOT_TESTED", "manufacturer": {"serial": "private"}}
        with self.assertRaises(ValueError): schemas.profile(data)

    def test_labels_are_anonymous(self):
        for label in ("Galaxy serial 123", "Johns-phone", "device-high-123456"):
            with self.subTest(label=label), self.assertRaises(ValueError): schemas.device_label(label)

    def test_metrics_reject_nan_and_negative_memory(self):
        for value in (float("nan"), -1, True):
            with self.subTest(value=value), self.assertRaises(ValueError): schemas.memory({"process_pss_kb": value})

    def test_metrics_reject_private_keys(self):
        with self.assertRaises(ValueError): schemas.memory({"process_pss_kb": 1024, "pid": 42})

    def test_offline_rejects_internet_permission(self):
        with self.assertRaises(ValueError): schemas.offline(dict.fromkeys(schemas.OFFLINE_FIELDS, True))

    def test_unobserved_radio_is_not_true(self):
        with self.assertRaises(ValueError): schemas.offline({"airplane_mode_on": "unknown", "wifi_setting_off": True, "mobile_data_setting_off": True, "internet_permission": False})

    def test_human_text_requires_explicit_export(self):
        data = {"direction": "en-es", "source_kind": "human_microphone", "recognized_text": "Hello", "translation": "Hola", "translator": "madlad", "asr_ms": 2, "translation_ms": 3}
        with self.assertRaises(ValueError): schemas.turn(data)
        self.assertEqual(schemas.turn(data, human_text=True), data)

    def test_network_voice_rejected(self):
        data = {"direction": "en-es", "source_kind": "text", "recognized_text": "Hello", "translation": "Hola", "translator": "madlad", "asr_ms": 0, "translation_ms": 3, "tts": {"requires_network": True}}
        with self.assertRaises(ValueError): schemas.turn(data)

    def test_failure_requires_separate_stage(self):
        data = {"device_label": "device-old-01", "direction": "en-es", "failure_stage": "ASR", "intended_meaning": "Pickup after work"}
        self.assertEqual(schemas.failure(data), data)
        with self.assertRaises(ValueError): schemas.failure({**data, "failure_stage": "MODEL_WRONG"})

    def test_correction_needs_permission(self):
        data = {"device_label": "device-high-01", "direction": "es-en", "failure_stage": "TRANSLATION", "intended_meaning": "cash", "human_correction": "cash"}
        with self.assertRaises(ValueError): schemas.failure(data)
        self.assertEqual(schemas.failure({**data, "correction_permission": True})["human_correction"], "cash")

    def test_human_rating_cannot_be_synthetic(self):
        row = self.rating()
        with self.assertRaises(ValueError): schemas.rating({**row, "source_kind": "synthetic"})

    @staticmethod
    def rating():
        return {"device_label": "device-high-01", "case_id": "en01", "direction": "en-es", "speaker_label": "speaker-es-01", "source_kind": "human_microphone", "meaning": "mostly_correct", "naturalness": "understandable_awkward", "latency": "noticeable", "fluent_spanish": True}

    def test_missing_direction_remains_zero_not_pass(self):
        result = rating_summary([self.rating()])
        self.assertEqual(result["en-es"]["meaning_percentages"], {"mostly_correct": 100.0})
        self.assertEqual(result["es-en"]["rated_human_utterances"], 0)
        self.assertEqual(result["es-en"]["meaning_percentages"], {})

    def test_duplicate_ratings_cannot_inflate_denominator(self):
        with self.assertRaises(ValueError): rating_summary([self.rating(), self.rating()])

    def test_percentile_handles_missing_and_single_observation(self):
        self.assertIsNone(percentile([], .95))
        self.assertEqual(percentile([25], .95), 25)

    def test_metric_unknown_export_rejected(self):
        with self.assertRaises(ValueError): device.sanitized_metric({"event": "device_accounts", "elapsed_ms": 2, "data": {"account": "private"}})

    def test_metric_diagnostics_redacted(self):
        data = device.sanitized_metric({"event": "error", "elapsed_ms": 2, "data": {"stage": "ASR", "error": "C:/Users/private/account"}})
        self.assertNotIn("private/account", json.dumps(data))

    def test_model_loading_diagnostics_accept_only_known_private_relative_paths(self):
        pack = {"installed_state_persists": True, "source": "installed_test_pack", "private_storage": True,
                "receipt_present": True, "complete_marker_matches": True,
                "required_files": [{"relative_path": "models/madlad/model-q4k.gguf", "present": True,
                                    "readable": True, "bytes": 12, "expected_bytes": 12, "matches_expected_size": True}]}
        item = {"event": "model_load_stage", "elapsed_ms": 1, "data": {"stage": "PACK_STATE", "message": "checking", "pack": pack}}
        self.assertTrue(device.sanitized_metric(item)["data"]["pack"]["installed_state_persists"])
        pack["required_files"][0]["relative_path"] = "/data/user/private/models/madlad/model-q4k.gguf"
        with self.assertRaises(ValueError): device.sanitized_metric(item)

    def test_loading_diagnostic_rejects_nested_identifiers(self):
        item = {"event": "model_load_stage", "elapsed_ms": 1, "data": {"stage": "NATIVE_RUNTIME", "native_build": "arm64-v8a; optimized; CPU only; GGML_NATIVE=OFF", "device_serial": "private"}}
        with self.assertRaises(ValueError): device.sanitized_metric(item)

    def test_native_failure_code_survives_without_raw_error_or_source_text(self):
        item = {"event": "error", "elapsed_ms": 1, "data": {"stage": "NATIVE_RUNTIME", "diagnostic_code": "NATIVE_LINK_FAILED", "error": "/data/user/private/path", "recognized_text": "private conversation"}}
        safe = device.sanitized_metric(item)
        self.assertEqual(safe["data"]["diagnostic_code"], "NATIVE_LINK_FAILED")
        self.assertNotIn("private", json.dumps(safe))

    def test_safe_native_tokens_survive_but_private_paths_and_unknown_libraries_do_not(self):
        item = {"event": "error", "elapsed_ms": 1, "data": {"stage": "NATIVE_RUNTIME", "diagnostic_code": "NATIVE_LINK_FAILED",
                "native_missing_library": "libtrial_t5.so", "native_missing_symbol": "trial_t5_load", "error": "/data/private/path"}}
        safe = device.sanitized_metric(item)["data"]
        self.assertEqual(safe["native_missing_library"], "libtrial_t5.so")
        self.assertEqual(safe["native_missing_symbol"], "trial_t5_load")
        for library, symbol in (("/data/private/libtrial_t5.so", "/private/symbol"), ("libprivate.so", "x" * 129)):
            item["data"].update(native_missing_library=library, native_missing_symbol=symbol)
            safe = device.sanitized_metric(item)["data"]
            self.assertNotIn("native_missing_library", safe)
            self.assertNotIn("native_missing_symbol", safe)


class ProvenanceTests(unittest.TestCase):
    def test_exact_selected_model_and_control_assets(self):
        rows = device.locked_assets("madlad", "base", True)
        self.assertEqual(len(rows), 16)
        model = next(row for row in rows if row["destination"].endswith("model-q4k.gguf"))
        self.assertEqual(model["revision"], "fa184c675da0b5c9e1c8694fccd4e12e2d422094")
        self.assertEqual(model["sha256"], "ea6e5531a3e95213c7f0635988d119e078a655c09306e47851e15d4c0c3f9c37")

    def test_local_import_contract_contains_exact_locked_models(self):
        contract = json.loads((ROOT / "spikes/physical-trial/src/main/assets/test-pack-contract.json").read_text(encoding="utf-8"))
        rows = {row["path"]: row for row in contract["manifest"]["files"]}
        models = device.locked_assets("madlad", "base", True) + [row for row in device.locked_assets("opus", "tiny", False) if "models/whisper/" in row["destination"]]
        for model in models:
            item = rows[model["destination"].removeprefix("files/")]
            self.assertEqual((item["sha256"], item["bytes"], item["revision"]), (model["sha256"], model["bytes"], model["revision"]))
        self.assertLess(contract["pack_bytes"], 2 * 1024**3)

    def test_local_import_contract_manifest_hash_and_fixture_provenance(self):
        contract = json.loads((ROOT / "spikes/physical-trial/src/main/assets/test-pack-contract.json").read_text(encoding="utf-8"))
        encoded = (json.dumps(contract["manifest"], sort_keys=True, ensure_ascii=True, separators=(",", ":")) + "\n").encode()
        self.assertEqual(hashlib.sha256(encoded).hexdigest(), contract["manifest_sha256"])
        plan = json.loads((HERE / "fixtures/trial-plan.json").read_text(encoding="utf-8"))
        rows = {row["path"]: row for row in contract["manifest"]["files"]}
        for fixture in plan["audio_artifacts"]:
            self.assertEqual(rows["audio/" + fixture["file"]]["sha256"], fixture["sha256"])
        self.assertEqual(contract["manifest"]["minimum_apk_version"], 2)

    def test_control_only_does_not_transfer_madlad(self):
        self.assertFalse(any("madlad" in row["destination"] for row in device.locked_assets("opus", "tiny", False)))

    def test_fixed_audio_is_synthetic_and_checksum_matches(self):
        plan = json.loads((HERE / "fixtures/trial-plan.json").read_text(encoding="utf-8"))
        self.assertEqual(len(plan["audio_artifacts"]), 14)
        for row in plan["audio_artifacts"]:
            path = HERE / "fixtures" / row["file"]
            self.assertTrue(row["provenance"].startswith("synthetic_"))
            self.assertEqual(hashlib.sha256(path.read_bytes()).hexdigest(), row["sha256"])
            with wave.open(str(path)) as wav:
                self.assertEqual((wav.getframerate(), wav.getnchannels(), wav.getsampwidth()), (16000, 1, 2))

    def test_desktop_expectations_are_not_linguistic_golds(self):
        plan = json.loads((HERE / "fixtures/trial-plan.json").read_text(encoding="utf-8"))
        self.assertEqual(len(plan["text_controls"]), 14)
        self.assertTrue(all(row["expectation"] == "deterministic_desktop_parity_not_gold" for row in plan["text_controls"]))

    def test_controls_preserve_original_unicode_sources_and_outputs(self):
        corpus = {row["id"]: row for row in json.loads((ROOT / "tools/translation-quality/corpus.json").read_text(encoding="utf-8"))["cases"]}
        prior = {row["case"]: row for row in (json.loads(line) for line in (ROOT / "tools/translation-quality/evidence/outputs/madlad-q4k.jsonl").read_text(encoding="utf-8").splitlines())}
        plan = json.loads((HERE / "fixtures/trial-plan.json").read_text(encoding="utf-8"))
        for row in plan["text_controls"]:
            identifier = row["case_id"].removeprefix("text-")
            with self.subTest(case=identifier):
                self.assertEqual(row["source"], corpus[identifier]["source"])
                self.assertEqual(row["expected_pass4_translation"], prior[identifier]["text"])
                self.assertEqual(next(case for case in plan["cases"] if case["case_id"] == row["case_id"])["text"], row["source"])
        for row in plan["audio_artifacts"]:
            self.assertEqual(row["source"], corpus[row["source_id"]]["source"])

    def test_human_script_and_readable_instructions_preserve_original_unicode(self):
        corpus = {row["id"]: row for row in json.loads((ROOT / "tools/translation-quality/corpus.json").read_text(encoding="utf-8"))["cases"]}
        rows = json.loads((HERE / "human-script.json").read_text(encoding="utf-8"))["cases"]
        instructions = (ROOT / "docs/device/HUMAN_TRIAL.md").read_text(encoding="utf-8")
        for row in rows:
            with self.subTest(case=row["case_id"]):
                self.assertEqual(row["source"], corpus[row["case_id"]]["source"])
                self.assertEqual(row["intended_meaning"], corpus[row["case_id"]]["semantic_facts"])
                self.assertIn("| " + row["case_id"] + " | " + row["source"] + " |", instructions)

    def test_human_script_balanced_no_medical_instructions(self):
        rows = json.loads((HERE / "human-script.json").read_text(encoding="utf-8"))["cases"]
        self.assertEqual(sum(row["direction"] == "en-es" for row in rows), 20)
        self.assertEqual(sum(row["direction"] == "es-en" for row in rows), 20)
        self.assertFalse(any(row["category"] == "medical-language" for row in rows))

    def test_transfer_refuses_path_escape_before_adb(self):
        with self.assertRaises(ValueError): device.private_transfer(None, pathlib.Path("unused"), "files/models/../../private")

    def test_transfer_refuses_outside_private_research_paths(self):
        with self.assertRaises(ValueError): device.private_transfer(None, pathlib.Path("unused"), "/sdcard/other-app")

    def test_corrupt_local_artifact_refused(self):
        (ROOT / ".local").mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=ROOT / ".local") as directory:
            path = pathlib.Path(directory) / "model"
            path.write_bytes(b"corrupt")
            with self.assertRaises(ValueError): device.verify_local({"path": str(path), "bytes": 7, "sha256": "0" * 64})

    def test_adb_error_does_not_export_serial(self):
        adb = device.Adb("unused")
        adb.serial = "PRIVATE_DEVICE_SERIAL"
        with patch("subprocess.run", side_effect=__import__("subprocess").TimeoutExpired("PRIVATE_DEVICE_SERIAL", 1)):
            with self.assertRaises(RuntimeError) as error: adb.run("shell", "getprop")
        self.assertNotIn("PRIVATE_DEVICE_SERIAL", str(error.exception))

    def test_corrupt_device_transfer_never_replaces_final_file(self):
        class FakeAdb:
            calls = []
            def shell(self, *args):
                self.calls.append(args)
                if "sha256sum" in args and not args[-1].endswith(".partial"): raise RuntimeError("missing")
                if "sha256sum" in args: return "0" * 64 + "  file"
                return ""
            def run(self, *args, **kwargs): self.calls.append(args); return ""
        adb = FakeAdb()
        with tempfile.TemporaryDirectory() as directory:
            source = pathlib.Path(directory) / "fixture"; source.write_bytes(b"fixture")
            with self.assertRaises(ValueError): device.private_transfer(adb, source, "files/audio/fixture-test.wav")
        self.assertFalse(any("mv" in call for call in adb.calls))
        self.assertTrue(any("rm" in call and call[-1].endswith(".partial") for call in adb.calls))
        self.assertTrue(any("rm" in call and call[-1].startswith("/data/local/tmp/common-tongue-pass5-") for call in adb.calls))

    def test_verified_existing_file_skips_transfer(self):
        class FakeAdb:
            def shell(self, *args): return hashlib.sha256(b"fixture").hexdigest() + "  file"
            def run(self, *args, **kwargs): raise AssertionError("Should not push a verified file")
        with tempfile.TemporaryDirectory() as directory:
            source = pathlib.Path(directory) / "fixture"; source.write_bytes(b"fixture")
            result = device.private_transfer(FakeAdb(), source, "files/audio/fixture-test.wav")
        self.assertTrue(result["verified_on_device"])


class ManifestTests(unittest.TestCase):
    def merged(self):
        return '<manifest xmlns:android="http://schemas.android.com/apk/res/android"><uses-permission android:name="android.permission.RECORD_AUDIO"/><application><service android:exported="false" android:process=":inference"/></application></manifest>'

    def check(self, source):
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory) / "AndroidManifest.xml"; path.write_text(source)
            return schemas.manifest_permissions(path)

    def test_permission_guard_accepts_audio_only(self): self.assertEqual(self.check(self.merged()), ["android.permission.RECORD_AUDIO"])
    def test_permission_guard_rejects_transitive_internet(self):
        with self.assertRaises(ValueError): self.check(self.merged().replace("<application>", '<uses-permission android:name="android.permission.INTERNET"/><application>'))
    def test_permission_guard_rejects_sdk_23_permissions(self):
        with self.assertRaises(ValueError): self.check(self.merged().replace("<application>", '<uses-permission-sdk-23 android:name="android.permission.ACCESS_NETWORK_STATE"/><application>'))
    def test_permission_guard_rejects_provider(self):
        with self.assertRaises(ValueError): self.check(self.merged().replace("<application>", '<application><provider android:name="Telemetry"/>'))
    def test_permission_guard_requires_private_worker(self):
        with self.assertRaises(ValueError): self.check(self.merged().replace('exported="false"', 'exported="true"'))

    def test_elf_alignment(self):
        def image(alignment):
            header = struct.pack("<16sHHIQQQIHHHHHH", b"\x7fELF\x02\x01" + b"\0" * 10, 3, 183, 1, 0, 64, 0, 0, 64, 56, 1, 0, 0, 0)
            return header + struct.pack("<IIQQQQQQ", 1, 5, 0, 0, 0, 0, 0, alignment)
        self.assertEqual(apk.elf_alignments(image(16384)), [16384])
        with self.assertRaises(ValueError): apk.elf_alignments(image(4096))


if __name__ == "__main__": unittest.main()
