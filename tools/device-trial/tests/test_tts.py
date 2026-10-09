"""Safe TTS configuration and playback evidence; no voice/text/audio/paths from arbitrary errors."""
import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
import device
import schemas


class TtsTests(unittest.TestCase):
    def report(self):
        return {"schema_version": 1, "operation": "SYNTHESIZE_FILE", "language": "es", "stage": "LISTENER_ERROR",
                "outcome": "FAILED", "diagnostic_code": "TTS_ENGINE_ERROR", "error_type": "TtsFailure",
                "engine": "com.samsung.SMT", "voice": "es-es-x-local", "locale": "es-ES", "requires_network": False,
                "init_result": 0, "language_available_result": 2, "set_language_result": 2, "set_voice_result": 0,
                "speak_result": None, "synthesize_result": 0, "listener_error": True, "android_error_code": -5,
                "audio_playback_started": False, "audio_playback_basis": "NOT_OBSERVED", "elapsed_ms": 1234}

    def test_engine_error_and_return_codes_survive_redaction(self):
        result = device.sanitized_metric({"event": "tts_diagnostic", "elapsed_ms": 1, "data": self.report()})
        self.assertEqual(result["data"]["android_error_code"], -5)
        error = device.sanitized_metric({"event": "error", "elapsed_ms": 2, "data": {"stage": "TTS", "diagnostic_code": "TTS_WAV_UNSUPPORTED", "error_type": "IllegalStateException", "error": "/data/private/text"}})
        self.assertEqual(error["data"]["diagnostic_code"], "TTS_WAV_UNSUPPORTED")
        self.assertEqual(error["data"]["error_type"], "IllegalStateException")
        self.assertNotIn("private/text", str(error))

    def test_raw_paths_text_audio_and_utterance_ids_are_rejected(self):
        for key in ("utterance_id", "path", "text", "audio", "raw_exception", "device_id"):
            with self.subTest(key=key), self.assertRaises(ValueError): schemas.tts_diagnostic({**self.report(), key: "private"})
        for key in ("engine", "voice", "active_voice", "locale", "active_locale"):
            with self.subTest(key=key), self.assertRaises(ValueError): schemas.tts_diagnostic({**self.report(), key: "/data/private"})

    def test_false_success_nonfinite_values_and_unknown_codes_fail(self):
        for change in ({"stage": "PRIVATE"}, {"diagnostic_code": "RAW_ERROR"}, {"error_type": "/private"},
                       {"synthesis_ms": float("nan")}, {"speak_result": False}, {"audio_playback_started": "yes"},
                       {"audio_playback_started": True, "audio_playback_basis": "NOT_OBSERVED"}):
            with self.subTest(change=change), self.assertRaises(ValueError): schemas.tts_diagnostic({**self.report(), **change})

    def test_direct_engine_callback_is_distinct_from_measured_audiotrack_playback(self):
        for operation, basis in (("DIRECT_SPEAK", "ENGINE_ON_START"), ("SYNTHESIZE_FILE", "AUDIOTRACK_TIMESTAMP"), ("SYNTHESIZE_FILE", "AUDIOTRACK_HEAD")):
            result = schemas.tts_diagnostic({**self.report(), "operation": operation, "stage": "PLAYBACK_START", "outcome": "RUNNING", "audio_playback_started": True, "audio_playback_basis": basis})
            self.assertEqual(result["audio_playback_basis"], basis)


if __name__ == "__main__": unittest.main()
