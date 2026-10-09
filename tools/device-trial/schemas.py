"""Strict allowlists for device research exports; never export raw ADB dumps."""

import math
import re
import xml.etree.ElementTree as ET

LABEL = re.compile(r"device-(?:high|old|tablet|test)-[0-9]{2}\Z")
STAGES = {"ASR", "TRANSLATION", "TTS", "PERFORMANCE", "RESOURCE", "UX"}
LOAD_STAGES = {"WORKER_BIND", "PACK_STATE", "PACK_HASHES", "NATIVE_RUNTIME", "TRANSLATION_MODEL", "ASR_MODEL", "READY"}
LOAD_CODES = {"PACK_STATE_FAILED", "INSTALLED_FILE_CHECK_FAILED", "NATIVE_LINK_FAILED", "NATIVE_PROBE_FAILED",
              "TRANSLATION_LOAD_FAILED", "ASR_LOAD_FAILED", "PIPELINE_FAILED", "WORKER_EXIT_OR_DISCONNECT",
              "WORKER_BIND_FAILED", "LOAD_INTERRUPTED"}
PIPELINE_STAGES = {"AUDIO_CAPTURE_COMPLETE", "AUDIO_DECODE_START", "AUDIO_DECODE_COMPLETE", "ASR_START", "ASR_COMPLETE",
                   "TRANSLATION_START", "TOKENIZE_START", "TOKENIZE_COMPLETE", "ENCODER_START", "ENCODER_COMPLETE",
                   "DECODER_START", "FIRST_TOKEN", "TRANSLATION_COMPLETE", "TTS_START", "TTS_COMPLETE"}
DIAGNOSTIC_MODES = {"WHISPER_ONLY", "MADLAD_ONLY", "FULL_PIPELINE"}
EXIT_AVAILABILITY = {"UNSUPPORTED", "MISSING_WORKER_IDENTITY", "NOT_YET_AVAILABLE", "AVAILABLE", "QUERY_FAILED"}
EXIT_REASONS = {"UNKNOWN", "EXIT_SELF", "SIGNALED", "LOW_MEMORY", "CRASH", "CRASH_NATIVE", "ANR", "INITIALIZATION_FAILURE",
                "PERMISSION_CHANGE", "EXCESSIVE_RESOURCE_USAGE", "USER_REQUESTED", "USER_STOPPED", "DEPENDENCY_DIED",
                "OTHER", "FREEZER", "PACKAGE_STATE_CHANGE", "PACKAGE_UPDATED", "UNKNOWN_REASON"}
NATIVE_LIBRARIES = {"libdevice_trial.so", "libtrial_t5.so", "libonnxruntime.so", "libonnxruntime4j_jni.so",
                    "libandroid.so", "liblog.so", "libm.so", "libdl.so", "libc.so", "libc++_shared.so"}
NATIVE_SYMBOL = re.compile(r"[A-Za-z_][A-Za-z0-9_.$@]{0,127}\Z")
MODEL_PATH = re.compile(r"models/(madlad/(model-q4k\.gguf|config\.json|tokenizer\.json)|whisper/ggml-(base|tiny)-q5_1\.bin|(en-es|es-en)/(config\.json|vocab\.json|source\.spm|target\.spm|onnx/(encoder|decoder)_model_quantized\.onnx))\Z")
RESULTS = {"RUNS_COMFORTABLY", "RUNS_WITH_CONCERNS", "TOO_SLOW", "MEMORY_LIMITED", "INCOMPATIBLE", "NOT_TESTED"}
PROFILE_FIELDS = {
    "schema_version", "device_label", "manufacturer", "model", "android_version", "api",
    "supported_abis", "preferred_abi", "actual_execution_abi", "total_ram_bytes", "available_ram_bytes",
    "cpu_core_count", "soc_manufacturer", "soc_model", "board_platform", "free_storage_bytes",
    "battery_percent", "battery_temperature_c", "thermal_status", "screen_size", "device_type",
    "reported_acceleration_features", "page_size_bytes", "offline", "physical_device", "result",
    "power_source", "battery_charging",
}
MEMORY_FIELDS = {
    "elapsed_ms", "process_pss_kb", "process_rss_kb", "process_hwm_rss_kb", "native_heap_bytes",
    "system_available_ram_bytes", "system_total_ram_bytes", "system_low_memory", "process_cpu_ms",
    "battery_percent", "battery_temperature_c", "thermal_status", "execution_abi",
    "power_source", "battery_charging",
}
OFFLINE_FIELDS = {"airplane_mode_on", "wifi_setting_off", "mobile_data_setting_off", "internet_permission"}
TTS_FIELDS = {"engine", "voice", "locale", "requires_network", "synthesis_ms", "audio_start_basis",
              "release_to_audio_ms", "play_request_to_audio_ms", "playback_duration_ms", "first_signal_offset_ms"}
TTS_CODES = {"TTS_NOT_READY", "TTS_OFFLINE_VOICE_MISSING", "TTS_LANGUAGE_UNAVAILABLE", "TTS_SET_VOICE_FAILED",
             "TTS_ACTIVE_VOICE_UNVERIFIED", "TTS_LISTENER_FAILED", "TTS_ENQUEUE_FAILED", "TTS_ENGINE_ERROR",
             "TTS_ENGINE_STOPPED", "TTS_SYNTHESIS_TIMEOUT", "TTS_WAV_INVALID", "TTS_WAV_UNSUPPORTED", "TTS_WAV_SILENT",
             "TTS_PLAYBACK_INIT_FAILED", "TTS_PLAYBACK_START_FAILED", "TTS_PLAYBACK_INCOMPLETE", "TTS_INTEGRATION_EXCEPTION"}
TTS_ERROR_TYPES = {"TtsFailure", "Exception", "IllegalStateException", "IllegalArgumentException", "IOException",
                   "SecurityException", "NullPointerException", "UnsupportedOperationException"}
TTS_STAGES = {"ENGINE_INIT", "VOICE_SELECT", "LANGUAGE_CONFIG", "VOICE_CONFIG", "LISTENER_CONFIG", "ENQUEUE",
              "LISTENER_START", "SYNTHESIS_FORMAT", "LISTENER_DONE", "LISTENER_ERROR", "LISTENER_STOP",
              "WAV_READ", "WAV_FORMAT", "PLAYBACK_INIT", "PLAYBACK_REQUEST", "PLAYBACK_START", "COMPLETE", "CANCELLED"}
TURN_FIELDS = {"case_id", "direction", "source_kind", "recognized_text", "translation", "translator", "asr",
               "asr_ms", "translation_ms", "speech_seconds", "worker_pipeline_ms", "worker_cpu_ms",
               "worker_memory", "offline", "tts", "ui_memory"}
TURN_FIELDS |= {"resident_turn_index", "first_after_model_load", "mode"}


def exact_keys(data, allowed, required=()):
    if not isinstance(data, dict) or set(data) - allowed or not set(required) <= set(data):
        raise ValueError("Unexpected or missing research fields")


def finite_tree(value):
    if isinstance(value, float) and not math.isfinite(value):
        raise ValueError("Non-finite metric")
    if isinstance(value, dict):
        for item in value.values(): finite_tree(item)
    elif isinstance(value, list):
        for item in value: finite_tree(item)


def tts_diagnostic(data):
    booleans = {"engine_initialized", "engine_ready", "listener_start", "listener_done", "listener_error", "listener_stop",
                "audio_playback_started", "requires_network", "active_requires_network", "active_voice_matches"}
    integers = {"schema_version", "elapsed_ms", "init_result", "language_available_result", "set_language_result", "set_voice_result",
                "listener_registration_result", "speak_result", "synthesize_result", "android_error_code", "audio_callback_bytes",
                "audio_callback_chunks", "engine_sample_rate_hz", "engine_audio_format", "engine_channels", "wav_bytes",
                "wav_encoding", "wav_sample_rate_hz", "wav_channels", "wav_bits_per_sample", "audio_track_state", "audio_track_write_result"}
    strings = {"operation", "language", "engine", "voice", "locale", "active_voice", "active_locale", "audio_playback_basis",
               "stage", "outcome", "diagnostic_code", "error_type"}
    exact_keys(data, booleans | integers | strings | {"synthesis_ms"}, {"schema_version", "operation", "stage", "outcome"})
    if data["schema_version"] != 1 or type(data["schema_version"]) is not int: raise ValueError("Unknown speech diagnostic version")
    enums = {"operation": {"NONE", "DIRECT_SPEAK", "SYNTHESIZE_FILE"}, "language": {"NONE", "en", "es"},
             "stage": TTS_STAGES, "outcome": {"INITIALIZED", "RUNNING", "PASS", "FAILED", "CANCELLED"},
             "diagnostic_code": TTS_CODES, "error_type": TTS_ERROR_TYPES,
             "audio_playback_basis": {"NOT_OBSERVED", "ENGINE_ON_START", "AUDIOTRACK_TIMESTAMP", "AUDIOTRACK_HEAD"}}
    for key, values in enums.items():
        if key in data and data[key] not in values: raise ValueError("Unknown speech diagnostic value")
    for key in booleans & data.keys():
        if data[key] is None and key == "active_requires_network": continue
        if type(data[key]) is not bool: raise ValueError("Invalid speech observation")
    nullable = {"init_result", "speak_result", "synthesize_result", "android_error_code"}
    signed = nullable | {"language_available_result", "set_language_result", "set_voice_result", "listener_registration_result", "audio_track_write_result", "engine_sample_rate_hz", "engine_audio_format", "engine_channels", "wav_sample_rate_hz"}
    for key in integers & data.keys():
        if data[key] is None and key in nullable: continue
        if type(data[key]) is not int or (key not in signed and data[key] < 0) or abs(data[key]) > 2**63 - 1:
            raise ValueError("Invalid speech metadata")
    for key in {"engine", "voice", "active_voice"} & data.keys():
        if not isinstance(data[key], str) or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.#-]{0,159}", data[key]): raise ValueError("Unsafe speech configuration name")
    for key in {"locale", "active_locale"} & data.keys():
        if not isinstance(data[key], str) or not re.fullmatch(r"(?:REDACTED|(?:en|es)(?:-[A-Za-z0-9]{1,8})*)", data[key]): raise ValueError("Unsafe speech locale")
    if "synthesis_ms" in data and (type(data["synthesis_ms"]) not in (int, float) or data["synthesis_ms"] < 0): raise ValueError("Invalid synthesis timing")
    if data.get("outcome") == "FAILED" and data.get("diagnostic_code") not in TTS_CODES: raise ValueError("Missing speech failure classification")
    if data.get("audio_playback_started") is True and data.get("audio_playback_basis") in {None, "NOT_OBSERVED"}: raise ValueError("Missing playback evidence basis")
    finite_tree(data)
    return data


def pipeline_checkpoint(data):
    fields = {"stage", "mode", "source_kind", "direction", "turn_index", "elapsed_ms", "sample_count", "sample_rate_hz", "channels"}
    exact_keys(data, fields, fields - {"sample_count", "sample_rate_hz", "channels"})
    if data["stage"] not in PIPELINE_STAGES or data["mode"] not in DIAGNOSTIC_MODES:
        raise ValueError("Unexpected pipeline boundary")
    if data["source_kind"] not in {"human_microphone", "prerecorded_fixture", "text"} or data["direction"] not in {"en-es", "es-en"}:
        raise ValueError("Unexpected diagnostic source")
    for key in {"turn_index", "elapsed_ms", "sample_count"} & data.keys():
        if type(data[key]) is not int or data[key] < 0: raise ValueError("Invalid pipeline metadata")
    if not 1 <= data["turn_index"] <= 1000000: raise ValueError("Invalid turn index")
    if "sample_count" in data and (data["sample_count"] > 496000 or data.get("sample_rate_hz") != 16000 or data.get("channels") != 1):
        raise ValueError("Invalid PCM metadata")
    return data


def exit_diagnostic(data):
    fields = {"availability", "mode", "last_pipeline_stage", "lookup_attempt", "disconnect_elapsed_ms", "reason_code", "reason",
              "status", "importance", "pss_kb", "rss_kb", "age_ms", "low_memory_kill_report_supported", "signal", "correlation", "process_checkpoint",
              "worker_alive_at_disconnect", "cleanup_requested_after_disconnect"}
    exact_keys(data, fields)
    if "availability" in data and data["availability"] not in EXIT_AVAILABILITY: raise ValueError("Unknown exit lookup state")
    if "mode" in data and data["mode"] not in DIAGNOSTIC_MODES: raise ValueError("Unknown component mode")
    if "last_pipeline_stage" in data and data["last_pipeline_stage"] not in PIPELINE_STAGES | {"NOT_STARTED"}: raise ValueError("Unknown exit boundary")
    if "reason" in data and data["reason"] not in EXIT_REASONS: raise ValueError("Unexpected exit reason")
    if "correlation" in data and data["correlation"] not in {"MATCHED_WORKER", "UNASSIGNED_HISTORY"}: raise ValueError("Unknown exit correlation")
    if "signal" in data and data["signal"] not in {"SIGILL", "SIGABRT", "SIGBUS", "SIGKILL", "SIGSEGV", "SIGTERM"}: raise ValueError("Unknown signal")
    if "low_memory_kill_report_supported" in data and type(data["low_memory_kill_report_supported"]) is not bool: raise ValueError("Invalid platform observation")
    if "worker_alive_at_disconnect" in data and data["worker_alive_at_disconnect"] is not None and type(data["worker_alive_at_disconnect"]) is not bool: raise ValueError("Invalid worker observation")
    if "cleanup_requested_after_disconnect" in data and type(data["cleanup_requested_after_disconnect"]) is not bool: raise ValueError("Invalid cleanup observation")
    for key in {"reason_code", "status", "importance", "pss_kb", "rss_kb", "age_ms", "lookup_attempt", "disconnect_elapsed_ms"} & data.keys():
        if type(data[key]) is not int or data[key] < 0: raise ValueError("Invalid exit metric")
    if "process_checkpoint" in data: pipeline_checkpoint(data["process_checkpoint"])
    return data


def component_check(data):
    exact_keys(data, {"mode", "result", "asr_ms", "translation_ms"}, {"mode", "result"})
    if data["mode"] not in DIAGNOSTIC_MODES or data["result"] not in {"PASS", "FAILED", "WORKER_EXIT"}: raise ValueError("Invalid component result")
    for key in {"asr_ms", "translation_ms"} & data.keys():
        if isinstance(data[key], bool) or not isinstance(data[key], (int, float)) or not math.isfinite(data[key]) or data[key] < 0: raise ValueError("Invalid component timing")
    return data


def pack_diagnostic(data):
    fields = {"installed_state_persists", "source", "private_storage", "receipt_present", "complete_marker_matches", "required_files"}
    exact_keys(data, fields, fields)
    if data["source"] not in {"installed_test_pack", "developer_provisioning", "missing"}:
        raise ValueError("Unknown private model source")
    for key in fields - {"source", "required_files"}:
        if type(data[key]) is not bool: raise ValueError("Invalid pack observation")
    files = data["required_files"]
    if not isinstance(files, list) or not 1 <= len(files) <= 13: raise ValueError("Invalid required model list")
    paths = set()
    for row in files:
        row_fields = {"relative_path", "present", "readable", "bytes", "expected_bytes", "matches_expected_size"}
        exact_keys(row, row_fields, row_fields)
        path = row["relative_path"]
        if not isinstance(path, str) or not MODEL_PATH.fullmatch(path) or path in paths:
            raise ValueError("Only known relative model filenames may be exported")
        paths.add(path)
        for key in {"present", "readable", "matches_expected_size"}:
            if type(row[key]) is not bool: raise ValueError("Invalid model presence observation")
        for key in {"bytes", "expected_bytes"}:
            if row[key] is not None and (type(row[key]) is not int or not 0 <= row[key] <= 2**32):
                raise ValueError("Invalid observed model size")
    return data


def device_label(value):
    if not isinstance(value, str) or not LABEL.fullmatch(value):
        raise ValueError("Use an anonymous device-high/old/tablet/test-01 label")
    return value


def profile(data):
    exact_keys(data, PROFILE_FIELDS, {"schema_version", "device_label", "physical_device", "result"})
    device_label(data["device_label"])
    if data["schema_version"] != 1 or data["result"] not in RESULTS:
        raise ValueError("Invalid device profile classification")
    if type(data["physical_device"]) is not bool:
        raise ValueError("Physical-device state must be observed")
    if data.get("actual_execution_abi") not in (None, "arm64-v8a"):
        raise ValueError("Execution ABI must be observed in the research worker")
    for key in {"manufacturer", "model", "android_version", "preferred_abi", "soc_manufacturer", "soc_model", "board_platform", "device_type"}:
        value = data.get(key)
        if value is not None and (not isinstance(value, str) or len(value) > 128):
            raise ValueError("Invalid hardware property")
    for key in {"supported_abis", "screen_size", "reported_acceleration_features"}:
        value = data.get(key, [])
        if not isinstance(value, list) or not all(isinstance(item, str) and len(item) <= 128 for item in value):
            raise ValueError("Invalid hardware capability list")
    if "offline" in data: offline(data["offline"])
    finite_tree(data)
    return data


def offline(data):
    exact_keys(data, OFFLINE_FIELDS, OFFLINE_FIELDS)
    if any(type(value) is not bool for value in data.values()) or data["internet_permission"]:
        raise ValueError("Invalid offline observations")
    return data


def memory(data):
    exact_keys(data, MEMORY_FIELDS)
    finite_tree(data)
    for key, value in data.items():
        if key.endswith(("_bytes", "_kb", "_ms")) and value is not None and (isinstance(value, bool) or not isinstance(value, (int, float)) or value < 0):
            raise ValueError("Invalid memory/time metric")
    return data


def turn(data, human_text=False):
    exact_keys(data, TURN_FIELDS, {"direction", "source_kind", "recognized_text", "translation", "translator", "asr_ms", "translation_ms"})
    if data["direction"] not in ("en-es", "es-en") or data["translator"] not in ("madlad", "opus"):
        raise ValueError("Unsupported trial configuration")
    if data["source_kind"] not in ("human_microphone", "text", "prerecorded_fixture"):
        raise ValueError("Unsupported source provenance")
    if data["source_kind"] == "human_microphone" and not human_text:
        raise ValueError("Human text needs explicit privacy-reviewed export")
    if "worker_memory" in data: memory(data["worker_memory"])
    if "ui_memory" in data: memory(data["ui_memory"])
    if "offline" in data: offline(data["offline"])
    if "tts" in data:
        exact_keys(data["tts"], TTS_FIELDS)
        if data["tts"].get("requires_network") is not False:
            raise ValueError("Offline TTS voice not established")
    for key in {"case_id", "recognized_text", "translation", "asr"}:
        if key in data and (not isinstance(data[key], str) or len(data[key]) > 10000):
            raise ValueError("Invalid text field")
    for key in {"asr_ms", "translation_ms", "speech_seconds", "worker_pipeline_ms", "worker_cpu_ms"}:
        if key in data and (isinstance(data[key], bool) or not isinstance(data[key], (int, float)) or data[key] < 0):
            raise ValueError("Invalid timing")
    finite_tree(data)
    return data


def rating(data):
    fields = {"device_label", "case_id", "direction", "speaker_label", "meaning", "naturalness", "latency",
              "notes", "corrected_text", "correction_permission", "fluent_spanish", "source_kind"}
    exact_keys(data, fields, {"device_label", "case_id", "direction", "speaker_label", "meaning", "naturalness", "latency", "source_kind"})
    device_label(data["device_label"])
    if data["direction"] not in ("en-es", "es-en") or not re.fullmatch(r"speaker-(en|es)-[0-9]{2}", data["speaker_label"]):
        raise ValueError("Invalid anonymous speaker/direction")
    if data["source_kind"] != "human_microphone":
        raise ValueError("Synthetic audio is not a human trial")
    if data["meaning"] not in {"correct", "mostly_correct", "wrong"} or data["naturalness"] not in {"natural", "understandable_awkward", "poor"} or data["latency"] not in {"comfortable", "noticeable", "disruptive"}:
        raise ValueError("Invalid participant rating")
    if data.get("corrected_text") and data.get("correction_permission") is not True:
        raise ValueError("Human correction requires permission")
    return data


def failure(data):
    fields = {"device_label", "case_id", "direction", "source_text", "context", "observed_asr", "translation",
              "intended_meaning", "human_correction", "correction_permission", "failure_stage", "notes"}
    exact_keys(data, fields, {"device_label", "direction", "failure_stage", "intended_meaning"})
    device_label(data["device_label"])
    if data["failure_stage"] not in STAGES or data["direction"] not in ("en-es", "es-en"):
        raise ValueError("Invalid failure stage/direction")
    if data.get("human_correction") and data.get("correction_permission") is not True:
        raise ValueError("Human correction requires permission")
    return data


def manifest_permissions(path):
    root = ET.parse(path).getroot()
    android = "{http://schemas.android.com/apk/res/android}"
    permissions = [node.attrib.get(android + "name") for node in root if node.tag in {"uses-permission", "uses-permission-sdk-23"}]
    if permissions != ["android.permission.RECORD_AUDIO"]:
        raise ValueError("Merged research manifest must request RECORD_AUDIO only")
    application = root.find("application")
    if application is None or application.findall("provider"):
        raise ValueError("No research telemetry providers allowed")
    services = application.findall("service")
    if len(services) != 1 or services[0].get(android + "exported") != "false" or services[0].get(android + "process") != ":inference":
        raise ValueError("Require private separate inference process")
    return permissions
