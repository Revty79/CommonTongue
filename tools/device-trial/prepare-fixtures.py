"""Generate fixed synthetic WAV controls locally, never label them human speech."""

import argparse
import hashlib
import json
import pathlib
import sys
import wave

ROOT = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/offline-feasibility"))
from offline import install_tripwire, probe
from harness import synthesize


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--require-network-unavailable", action="store_true", required=True)
    args = parser.parse_args()
    import numpy as np
    observations = probe()
    if "AVAILABLE" in observations.values():
        raise RuntimeError("Network-unavailable preflight failed")
    events = install_tripwire()
    corpus = json.loads((ROOT / "tools/translation-quality/corpus.json").read_text(encoding="utf-8"))["cases"]
    selected = ["en01", "es01", "en04", "es04", "en05", "es05", "en08", "es08", "en11", "es11", "en12", "es12", "en02", "es02"]
    prior = {row["case"]: row for row in (json.loads(line) for line in (ROOT / "tools/translation-quality/evidence/outputs/madlad-q4k.jsonl").read_text(encoding="utf-8").splitlines())}
    directory = ROOT / ".local/device/fixtures"
    directory.mkdir(parents=True, exist_ok=True)
    cases, audio, text_controls = [], [], []
    for identifier in selected:
        case = next(row for row in corpus if row["id"] == identifier)
        raw = directory / f"raw-{identifier}.wav"
        identity = synthesize(case["source"], case["direction"][:2], raw, "espeak")
        with wave.open(str(raw)) as audio_in:
            if audio_in.getnchannels() != 1 or audio_in.getsampwidth() != 2: raise ValueError("Expected mono PCM16 fixture")
            rate = audio_in.getframerate()
            samples = np.frombuffer(audio_in.readframes(audio_in.getnframes()), dtype="<i2").astype(np.float32) / 32768
        # Preserve Pass 2's deterministic linear resampling method for controls.
        pcm = np.interp(np.arange(round(len(samples) * 16000 / rate)) * rate / 16000, np.arange(len(samples)), samples)
        filename = f"fixture-{identifier}.wav"
        output = directory / filename
        with wave.open(str(output), "wb") as audio_out:
            audio_out.setnchannels(1); audio_out.setsampwidth(2); audio_out.setframerate(16000)
            audio_out.writeframes((np.clip(pcm, -1, 1) * 32767).astype("<i2").tobytes())
        raw.unlink()
        digest = hashlib.sha256(output.read_bytes()).hexdigest()
        audio.append({"file": filename, "bytes": output.stat().st_size, "sha256": digest,
                      "source_id": identifier, "source": case["source"], "direction": case["direction"],
                      "provenance": "synthetic_eSpeak_NG_1.52.0_fixed_control", "sample_rate": 16000,
                      "duration_seconds": len(pcm) / 16000, "voice": identity["voice"]})
        cases.append({"case_id": "wav-" + identifier, "direction": case["direction"], "file": filename})
        cases.append({"case_id": "text-" + identifier, "direction": case["direction"], "text": case["source"]})
        text_controls.append({"case_id": "text-" + identifier, "source": case["source"], "direction": case["direction"],
                              "expected_pass4_translation": prior[identifier]["text"], "expectation": "deterministic_desktop_parity_not_gold"})
        print("Prepared synthetic control " + identifier, flush=True)
    if events: raise RuntimeError("Post-preflight socket attempt observed")
    plan = {"schema_version": 1, "cases": cases, "audio_artifacts": audio, "text_controls": text_controls,
            "offline_observations": observations, "post_tripwire_events": events}
    (directory / "trial-plan.json").write_text(json.dumps(plan, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")


if __name__ == "__main__": main()
