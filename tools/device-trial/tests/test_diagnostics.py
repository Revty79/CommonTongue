"""Privacy and compatibility checks for crash-boundary/exit metadata."""
import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
import device
import schemas


class DiagnosticTests(unittest.TestCase):
    def checkpoint(self):
        return {"stage": "ASR_START", "mode": "FULL_PIPELINE", "source_kind": "human_microphone",
                "direction": "en-es", "turn_index": 1, "elapsed_ms": 1234,
                "sample_count": 31680, "sample_rate_hz": 16000, "channels": 1}

    def test_pipeline_boundary_contains_metadata_without_audio_text_or_identifiers(self):
        self.assertEqual(schemas.pipeline_checkpoint(self.checkpoint())["sample_count"], 31680)
        for key in {"pid", "run_id", "private_path", "recognized_text", "audio"}:
            with self.subTest(key=key), self.assertRaises(ValueError):
                schemas.pipeline_checkpoint({**self.checkpoint(), key: "private"})

    def test_corrupt_pcm_metadata_or_unknown_stage_is_rejected(self):
        for change in ({"stage": "PRIVATE_TEXT"}, {"sample_count": 999999}, {"sample_count": -1},
                       {"sample_rate_hz": 48000}, {"channels": 2}, {"elapsed_ms": float("nan")}):
            with self.subTest(change=change), self.assertRaises(ValueError):
                schemas.pipeline_checkpoint({**self.checkpoint(), **change})

    def test_android_exit_retains_observed_reason_signal_and_reporting_capability(self):
        data = {"availability": "AVAILABLE", "reason_code": 5, "reason": "CRASH_NATIVE", "status": 11,
                "signal": "SIGSEGV", "pss_kb": 2900000, "rss_kb": 3000000, "importance": 100,
                "age_ms": 100, "low_memory_kill_report_supported": True, "correlation": "MATCHED_WORKER",
                "process_checkpoint": self.checkpoint()}
        self.assertEqual(schemas.exit_diagnostic(data)["reason"], "CRASH_NATIVE")
        for key in {"pid", "uid", "process_name", "description", "trace", "timestamp", "device_serial"}:
            with self.subTest(key=key), self.assertRaises(ValueError): schemas.exit_diagnostic({**data, key: "private"})

    def test_signal_nine_remains_a_signal_and_is_not_rewritten_as_out_of_memory(self):
        data = {"reason_code": 2, "reason": "SIGNALED", "status": 9, "signal": "SIGKILL", "low_memory_kill_report_supported": False}
        self.assertEqual(schemas.exit_diagnostic(data)["reason"], "SIGNALED")

    def test_absent_exit_record_and_unassigned_history_do_not_claim_a_crash(self):
        self.assertEqual(schemas.exit_diagnostic({"availability": "NOT_YET_AVAILABLE"}), {"availability": "NOT_YET_AVAILABLE"})
        self.assertEqual(schemas.exit_diagnostic({"correlation": "UNASSIGNED_HISTORY"})["correlation"], "UNASSIGNED_HISTORY")

    def test_component_results_cannot_include_human_source_or_invent_success(self):
        self.assertEqual(schemas.component_check({"mode": "WHISPER_ONLY", "result": "WORKER_EXIT"})["result"], "WORKER_EXIT")
        for data in ({"mode": "WHISPER_ONLY", "result": "PASS", "recognized_text": "private"},
                     {"mode": "WHISPER_ONLY", "result": "PROBABLY_PASS"},
                     {"mode": "MADLAD_ONLY", "result": "PASS", "translation_ms": float("inf")}):
            with self.assertRaises(ValueError): schemas.component_check(data)

    def test_metrics_preserve_pipeline_and_exit_records_but_redact_raw_errors(self):
        result = device.sanitized_metric({"event": "pipeline_stage", "elapsed_ms": 100, "data": self.checkpoint()})
        self.assertEqual(result["data"]["stage"], "ASR_START")
        error = device.sanitized_metric({"event": "error", "elapsed_ms": 100, "data": {"stage": "ASR_START", "error": "/data/private/path"}})
        self.assertEqual(error["data"]["stage"], "ASR_START")
        self.assertNotIn("private/path", str(error))

    def test_only_required_component_readiness_is_accepted(self):
        memory = {"process_pss_kb": 123}
        common = {"stage": "READY", "start_memory": memory, "translation_loaded_memory": memory, "loaded_memory": memory}
        for mode, asr, translation in (("FULL_PIPELINE", True, True), ("WHISPER_ONLY", True, False), ("MADLAD_ONLY", False, True)):
            data = {**common, "mode": mode, "asr_model_loaded": asr, "translation_model_loaded": translation}
            device.sanitized_metric({"event": "models_loaded", "elapsed_ms": 1, "data": data})
            data["asr_model_loaded"] = not asr
            with self.assertRaises(ValueError): device.sanitized_metric({"event": "models_loaded", "elapsed_ms": 1, "data": data})

    def test_translation_substages_survive_metric_validation_without_ids_or_tokens(self):
        for stage in ("TOKENIZE_START", "TOKENIZE_COMPLETE", "ENCODER_START", "ENCODER_COMPLETE", "DECODER_START", "FIRST_TOKEN"):
            data = {**self.checkpoint(), "stage": stage}
            result = device.sanitized_metric({"event": "pipeline_stage", "elapsed_ms": 100, "data": data})
            self.assertEqual(result["data"]["stage"], stage)
            for key in ("handle", "pointer", "token", "input_ids"):
                with self.assertRaises(ValueError): schemas.pipeline_checkpoint({**data, key: 1})


if __name__ == "__main__": unittest.main()
