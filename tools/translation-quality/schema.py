"""Model-free benchmark/metadata validation. No inference imports."""

import hashlib
import json
import re
from collections import Counter
from pathlib import Path

HERE = Path(__file__).parent
ROOT = HERE.parents[1]
CATEGORIES = {
    "conversation", "slang", "idiom", "negation", "numbers-money", "date-time",
    "measurement", "automotive", "construction", "ranch", "agriculture", "incomplete",
    "ambiguous", "difficult", "regional-slang", "context", "ambiguity", "names",
    "request", "question", "command", "sarcasm", "humor", "profanity", "politeness",
    "directness", "regional", "code-switching", "false-friend", "business", "address",
    "phone", "quantity", "prohibition", "conditional", "multi-clause", "hospitality",
    "travel", "emergency", "medical-language", "obedience", "warning", "ordinary",
}


def validate_corpus(data, require_full=True):
    if data.get("schema_version") != 1:
        raise ValueError("Unsupported corpus schema")
    cases = data["cases"]
    if require_full and not 150 <= len(cases) <= 250:
        raise ValueError("Corpus outside research target")
    ids, sources = set(), set()
    for case in cases:
        required = {"id", "source", "direction", "category", "tags", "semantic_facts",
                    "forbidden_meaning_changes", "important_terms", "critical", "context",
                    "terminology", "domain", "source_region", "target_region", "provenance"}
        if not required <= case.keys(): raise ValueError("Missing semantic metadata")
        if not re.fullmatch(r"[a-z0-9-]+", case["id"]) or case["id"] in ids:
            raise ValueError("Invalid/duplicate case ID")
        ids.add(case["id"])
        if case["direction"] not in {"en-es", "es-en"}: raise ValueError("Invalid direction")
        if case["category"] not in CATEGORIES: raise ValueError("Invalid category")
        if not isinstance(case["source"], str) or not case["source"].strip(): raise ValueError("Empty source")
        identity = (case["direction"], case["source"])
        if identity in sources: raise ValueError("Duplicate source in direction")
        sources.add(identity)
        for field in ["semantic_facts", "forbidden_meaning_changes"]:
            if not case[field] or not all(isinstance(x, str) and x.strip() for x in case[field]):
                raise ValueError("Missing required semantic facts/forbidden changes")
        if not isinstance(case["critical"], dict): raise ValueError("Invalid critical metadata")
        if not isinstance(case["important_terms"], list): raise ValueError("Invalid important terms")
        for turn in case["context"]:
            if not {"speaker", "source", "translation", "direction"} <= turn.keys(): raise ValueError("Invalid context")
            if turn["direction"] not in {"en-es", "es-en"}: raise ValueError("Invalid context direction")
        for term in case["terminology"]:
            if not {"source_term", "target_term", "meaning", "domain", "strength"} <= term.keys(): raise ValueError("Invalid terminology")
        if not case["domain"] or not case["provenance"]: raise ValueError("Missing provenance/domain")
    if require_full:
        counts = Counter(c["direction"] for c in cases)
        if min(counts.get("en-es", 0), counts.get("es-en", 0)) < len(cases) * .4: raise ValueError("Unbalanced directions")
        if sum(bool(c["context"]) for c in cases) < 25: raise ValueError("Missing context subset")
        original_path = ROOT / "tools/offline-feasibility/corpus.json"
        if data["baseline_sha256"] != hashlib.sha256(original_path.read_bytes()).hexdigest(): raise ValueError("Pass 2 corpus changed")
        originals = json.loads(original_path.read_text(encoding="utf-8"))
        for original in originals:
            case = next((c for c in cases if c["id"] == original["id"]), None)
            if not case or case["provenance"].get("original") != original: raise ValueError("Lost Pass 2 regression")
            if any(case[field] != original[field] for field in ["id", "source", "direction", "category"]): raise ValueError("Rewritten regression")
    return cases


def validate_lock(lock):
    paths = set()
    for artifact in lock["artifacts"]:
        if not re.fullmatch(r"[0-9a-f]{64}", artifact["sha256"]): raise ValueError("Invalid artifact checksum")
        path = Path(artifact["path"])
        if path.is_absolute() or ".." in path.parts or artifact["path"] in paths: raise ValueError("Unsafe/duplicate artifact path")
        paths.add(artifact["path"])
        if not artifact["bytes"] > 0: raise ValueError("Invalid artifact size")
        if artifact.get("url") and not artifact["url"].startswith("https://"): raise ValueError("Invalid artifact URL")
        if artifact.get("url") and artifact.get("revision") and artifact["revision"] not in artifact["url"]:
            raise ValueError("URL must be pinned to its recorded revision")
    return paths


def validate_candidates(data, lock):
    paths = validate_lock(lock)
    ids = set()
    required = {"id", "publisher", "repositories", "source_provenance", "model_license",
                "tokenizer_license", "runtime_license", "conversion_provenance", "classification",
                "commercial_use", "redistribution", "derivative_obligations", "ambiguities",
                "quantization", "runtime", "artifacts"}
    for candidate in data["candidates"]:
        if not required <= candidate.keys(): raise ValueError("Incomplete candidate metadata")
        if candidate["id"] in ids: raise ValueError("Duplicate candidate")
        ids.add(candidate["id"])
        if candidate["classification"] not in {"A", "B", "C"}: raise ValueError("Invalid license classification")
        for repo in candidate["repositories"]:
            if not repo["repository"] or not re.fullmatch(r"[0-9a-f]{40}", repo["revision"]): raise ValueError("Unpinned repository")
        if not candidate["artifacts"] or any(a not in paths for a in candidate["artifacts"]): raise ValueError("Unlocked candidate artifact")
        for field in required - {"ambiguities", "artifacts", "repositories"}:
            if not candidate[field]: raise ValueError("Empty candidate metadata")
    return ids
