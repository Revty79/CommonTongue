"""Scoped deterministic checks, labelled heuristic signals, explicit review scoring."""

import json
import random
import re
import statistics
import unicodedata
from collections import Counter

DIMENSIONS = {"meaning": 4, "omission": 3, "invention": 3, "terminology": 2,
              "context": 2, "critical_content": 4, "negation": 4, "idiom_slang": 2,
              "regional": 1, "fluency": 1}
SEVERITIES = {"NONE", "MINOR", "MAJOR", "CRITICAL", "UNREVIEWED"}


def integers(text):
    if any(c.isdigit() and c not in "0123456789" for c in text): return None
    if re.search(r"[0-9]\s+[0-9]", text): return None
    result = []
    for match in re.finditer(r"[+-]?[0-9]+(?:[.,:/-][0-9]+)*", text):
        value = match.group()
        before = text[match.start() - 1] if match.start() else ""
        after = text[match.end()] if match.end() < len(text) else ""
        if not re.fullmatch(r"[+-]?[0-9]{1,128}", value): return None
        if before and (before.isalnum() or before in "_+-.,:/"): return None
        if after and (after.isalnum() or after == "_"): return None
        result.append(str(int(value)))
    return Counter(result)


def signals(case, output):
    source, target = integers(case["source"]), integers(output)
    if source is None or target is None: state = "NEEDS_REVIEW"
    elif not source: state = "NOT_APPLICABLE" if not target else "NEEDS_REVIEW"
    elif not target: state = "NEEDS_REVIEW"
    elif source != target: state = "LITERAL_MISMATCH"
    else: state = "CHECKED_MATCH"
    heuristic = []
    if output.lstrip().startswith(("{", "[")) and not case["source"].lstrip().startswith(("{", "[")): heuristic.append("JSON_OR_LIST_WRAPPER")
    if re.match(r"\s*(translation|traducción|here is|here's|sure[,!]|certainly|of course)\b", output, re.I): heuristic.append("COMMENTARY_PREFIX")
    if re.search(r"</?think>|<\|im_|as an ai|como (?:una )?(?:ia|inteligencia artificial)", output, re.I): heuristic.append("META_OR_REASONING_OUTPUT")
    if len(output.split()) < max(1, len(case["source"].split()) * .35): heuristic.append("POSSIBLE_SHORT_OUTPUT_OMISSION")
    if len(output.split()) > len(case["source"].split()) * 3 + 8: heuristic.append("POSSIBLE_EXPLANATION_OR_INVENTION")
    normalized = unicodedata.normalize("NFC", output)
    for name in case["critical"].get("names", []):
        if unicodedata.normalize("NFC", name) not in normalized: heuristic.append("NAME_LITERAL_NEEDS_REVIEW:" + name)
    return {"integer_check": {"state": state, "scope": "Standalone ASCII integer literal value/multiplicity only; no semantic approval"},
            "heuristic_signals": heuristic, "semantic_status": "UNREVIEWED"}


def review_score(review):
    if review["severity"] not in SEVERITIES: raise ValueError("Invalid severity")
    if review["severity"] == "UNREVIEWED": return None
    numerator = denominator = 0
    for dimension, weight in DIMENSIONS.items():
        value = review["scores"].get(dimension, "UNREVIEWED")
        if value in {"NA", "UNREVIEWED"}: continue
        if type(value) is not int or value not in {0, 1, 2}: raise ValueError("Invalid dimension score")
        numerator += value * weight
        denominator += 2 * weight
    if denominator == 0: return None
    raw = 100 * numerator / denominator
    penalty = {"NONE": 0, "MINOR": 5, "MAJOR": 25, "CRITICAL": 100}[review["severity"]]
    return {"quality": raw, "risk_weighted": max(0, raw - penalty), "scored_weight": denominator}


def summarize(rows, reviews=()):
    keyed = {(r["run"], r["case"]): r for r in reviews}
    groups = {}
    for row in rows: groups.setdefault(row["run"], []).append(row)
    result = {}
    for name, items in groups.items():
        assessed = [keyed[(name, r["case"])] for r in items if (name, r["case"]) in keyed and keyed[(name, r["case"])]["severity"] != "UNREVIEWED"]
        severity = Counter(r["severity"] for r in assessed)
        scored = [review_score(r) for r in assessed]
        scored = [s for s in scored if s]
        latencies = sorted(r["ms"] for r in items)
        result[name] = {
            "outputs": len(items), "reviewed": len(assessed), "unreviewed": len(items)-len(assessed),
            "review_type": "model-assisted Codex assessment; not native/fluent-human review",
            "severity_counts": dict(severity),
            "critical_rate_reviewed": severity["CRITICAL"] / len(assessed) if assessed else None,
            "major_rate_reviewed": severity["MAJOR"] / len(assessed) if assessed else None,
            "omission_count_reviewed": sum(r["scores"].get("omission") in {0, 1} for r in assessed),
            "material_omission_count_reviewed": sum(r["scores"].get("omission") == 0 for r in assessed),
            "invention_count_reviewed": sum(r["scores"].get("invention") in {0, 1} for r in assessed),
            "material_invention_count_reviewed": sum(r["scores"].get("invention") == 0 for r in assessed),
            "mean_quality_reviewed": statistics.mean(s["quality"] for s in scored) if scored else None,
            "mean_risk_weighted_reviewed": statistics.mean(s["risk_weighted"] for s in scored) if scored else None,
            "median_ms": statistics.median(latencies), "p95_ms": latencies[min(len(latencies)-1, int(len(latencies)*.95))],
            "incomplete": sum(not r["completed"] for r in items),
            "integer_states": dict(Counter(r["signals"]["integer_check"]["state"] for r in items)),
            "heuristic_signal_counts": dict(Counter(s for r in items for s in r["signals"]["heuristic_signals"])),
        }
    return result


def blind_export(cases, rows, seed=404):
    by_case = {}
    for row in rows: by_case.setdefault(row["case"], []).append(row)
    public, private = [], []
    for case in cases:
        choices = sorted(by_case.get(case["id"], []), key=lambda r: r["run"])
        random.Random(f"{seed}:{case['id']}").shuffle(choices)
        public.append({"id": case["id"], "source": case["source"], "direction": case["direction"],
                       "context": case["context"], "semantic_facts": case["semantic_facts"],
                       "forbidden_meaning_changes": case["forbidden_meaning_changes"],
                       "translations": [{"label": chr(65+i), "text": r["text"]} for i, r in enumerate(choices)],
                       "review": {"reviewer": None, "qualification": None, "status": "UNREVIEWED"}})
        private.append({"id": case["id"], "key": {chr(65+i): r["run"] for i, r in enumerate(choices)}})
    return public, private
