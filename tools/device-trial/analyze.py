"""Summarize observed trials without promoting missing evidence to PASS."""

import argparse
import collections
import json
import pathlib
import statistics

from schemas import rating, turn


def percentile(values, fraction):
    if not values: return None
    values = sorted(values)
    index = (len(values) - 1) * fraction
    low = int(index)
    return values[low] + (values[min(low + 1, len(values) - 1)] - values[low]) * (index - low)


def rating_summary(rows):
    for row in rows: rating(row)
    # Reject duplicate ratings so reruns cannot inflate the scripted denominator.
    identities = [(r["device_label"], r["case_id"], r["direction"]) for r in rows]
    if len(set(identities)) != len(identities): raise ValueError("Duplicate participant rating")
    result = {}
    for direction in ("en-es", "es-en"):
        selected = [r for r in rows if r["direction"] == direction]
        counts = collections.Counter(r["meaning"] for r in selected)
        result[direction] = {"rated_human_utterances": len(selected), "meaning_counts": dict(counts),
            "meaning_percentages": {key: count * 100 / len(selected) for key, count in counts.items()},
            "fluent_spanish_reviewer_present": any(r.get("fluent_spanish") is True for r in selected)}
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--device-results", type=pathlib.Path)
    parser.add_argument("--ratings", type=pathlib.Path)
    parser.add_argument("--output", type=pathlib.Path, required=True)
    args = parser.parse_args()
    result = {"schema_version": 1, "physical_evidence": "NOT_TESTED", "human_trial": "PARTIAL",
              "limitation": "Observed sample only; neither population accuracy nor device tier approval"}
    if args.device_results:
        data = json.loads(args.device_results.read_text(encoding="utf-8"))
        rows = data["cases"]
        for row in rows: turn(row, human_text=True)
        result["device_label"] = data["device_label"]
        result["observed_turns"] = len(rows)
        result["directions"] = {}
        for direction in ("en-es", "es-en"):
            selected = [row for row in rows if row["direction"] == direction]
            times = [row["tts"]["release_to_audio_ms"] for row in selected if row.get("tts", {}).get("release_to_audio_ms") is not None]
            result["directions"][direction] = {"observed_turns": len(selected), "release_to_audio_observations": len(times),
                                              "release_to_audio_median_ms": statistics.median(times) if times else None,
                                              "release_to_audio_p95_ms": percentile(times, .95)}
        result["physical_evidence"] = "OBSERVED_REQUIRES_DEVICE_AND_OFFLINE_REVIEW"
    if args.ratings:
        rows = [json.loads(line) for line in args.ratings.read_text(encoding="utf-8").splitlines() if line.strip()]
        result["human_ratings"] = rating_summary(rows)
        # Script ratings alone do not prove a live conversation, physical offline
        # state, participant fluency, or all Pass 5 acceptance criteria.
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8", newline="\n")


if __name__ == "__main__": main()
