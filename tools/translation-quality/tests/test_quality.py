import copy
import json
import pathlib
import sys
import tempfile
import unittest
import hashlib

HERE = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(HERE))
from schema import validate_corpus, validate_lock, validate_candidates
from scoring import blind_export, integers, review_score, signals, summarize, DIMENSIONS
from prompts import translate_prompt, verification_prompt
from prepare import verify_file


class CorpusTests(unittest.TestCase):
    def setUp(self):
        self.data = json.loads((HERE / "corpus.json").read_text(encoding="utf-8"))

    def test_full_corpus_balanced_preserves_regressions(self):
        cases = validate_corpus(self.data)
        self.assertEqual(196, len(cases))
        self.assertEqual(98, sum(c["direction"] == "en-es" for c in cases))
        self.assertEqual(32, sum("pass2-regression" in c["tags"] for c in cases))

    def test_context_terms_and_regions(self):
        cases = validate_corpus(self.data)
        self.assertGreaterEqual(sum(bool(c["context"]) for c in cases), 25)
        self.assertEqual(28, sum(bool(c["terminology"]) for c in cases))
        self.assertTrue({"es-MX", "es-AR", "es-CL"} <= {c["source_region"] for c in cases})

    def test_required_risk_categories(self):
        categories = {c["category"] for c in self.data["cases"]}
        self.assertTrue({"negation", "prohibition", "conditional", "obedience", "regional", "slang", "idiom",
                         "names", "measurement", "numbers-money", "multi-clause", "medical-language"} <= categories)

    def test_duplicate_id_rejected(self):
        self.data["cases"][1]["id"] = self.data["cases"][0]["id"]
        with self.assertRaisesRegex(ValueError, "duplicate"): validate_corpus(self.data)

    def test_duplicate_source_rejected(self):
        self.data["cases"][1]["source"] = self.data["cases"][0]["source"]
        with self.assertRaisesRegex(ValueError, "Duplicate source"): validate_corpus(self.data)

    def test_missing_semantic_metadata_rejected(self):
        del self.data["cases"][0]["critical"]
        with self.assertRaisesRegex(ValueError, "Missing semantic"): validate_corpus(self.data)

    def test_empty_facts_rejected(self):
        self.data["cases"][0]["semantic_facts"] = []
        with self.assertRaises(ValueError): validate_corpus(self.data)

    def test_empty_forbidden_changes_rejected(self):
        self.data["cases"][0]["forbidden_meaning_changes"] = []
        with self.assertRaises(ValueError): validate_corpus(self.data)

    def test_invalid_direction_rejected(self):
        self.data["cases"][0]["direction"] = "es-es"
        with self.assertRaisesRegex(ValueError, "direction"): validate_corpus(self.data)

    def test_invalid_category_rejected(self):
        self.data["cases"][0]["category"] = "invented-category"
        with self.assertRaisesRegex(ValueError, "category"): validate_corpus(self.data)

    def test_regression_cannot_be_rewritten(self):
        self.data["cases"][0]["source"] += " Extra sentence."
        with self.assertRaisesRegex(ValueError, "Rewritten"): validate_corpus(self.data)

    def test_missing_context_subset_rejected(self):
        for c in self.data["cases"]: c["context"] = []
        with self.assertRaisesRegex(ValueError, "context subset"): validate_corpus(self.data)


class ScoringTests(unittest.TestCase):
    def review(self, severity="NONE", **changes):
        result = {"run": "one", "case": "x", "severity": severity,
                  "scores": {key: 2 for key in DIMENSIONS}}
        result["scores"].update(changes)
        return result

    def row(self, text="Salida"):
        return {"run": "one", "case": "x", "text": text, "ms": 20, "completed": True,
                "signals": signals({"source": "Source", "critical": {}}, text)}

    def test_perfect_review_scores_100(self):
        self.assertEqual(100, review_score(self.review())["quality"])

    def test_critical_failure_dominates_fluency(self):
        self.assertEqual(0, review_score(self.review("CRITICAL"))["risk_weighted"])
        self.assertGreater(review_score(self.review("MINOR"))["risk_weighted"], 0)

    def test_major_failure_penalty(self):
        self.assertEqual(75, review_score(self.review("MAJOR"))["risk_weighted"])

    def test_unreviewed_is_not_a_pass(self):
        self.assertIsNone(review_score(self.review("UNREVIEWED")))
        result = summarize([self.row()])["one"]
        self.assertIsNone(result["critical_rate_reviewed"])
        self.assertIsNone(result["mean_quality_reviewed"])

    def test_severity_rate_uses_reviewed_denominator(self):
        other = self.row(); other["case"] = "y"
        result = summarize([self.row(), other], [self.review("CRITICAL")])["one"]
        self.assertEqual(1, result["critical_rate_reviewed"])
        self.assertEqual(1, result["unreviewed"])

    def test_omission_and_invention_count_separately(self):
        result = summarize([self.row()], [self.review("CRITICAL", omission=0, invention=0)])["one"]
        self.assertEqual(1, result["omission_count_reviewed"])
        self.assertEqual(1, result["invention_count_reviewed"])

    def test_na_dimensions_do_not_reduce_score(self):
        self.assertEqual(100, review_score(self.review(context="NA"))["quality"])

    def test_invalid_scores_rejected(self):
        for value in [3, -1, True, "2"]:
            with self.assertRaises(ValueError): review_score(self.review(meaning=value))

    def test_integer_multiplicity_changes_detected(self):
        case = {"source": "12 here and 12 there", "critical": {}}
        self.assertEqual("LITERAL_MISMATCH", signals(case, "12 aquí y 13 allá")["integer_check"]["state"])

    def test_integer_match_is_scoped_only(self):
        result = signals({"source": "Do not use 12", "critical": {}}, "Use 12")
        self.assertEqual("CHECKED_MATCH", result["integer_check"]["state"])
        self.assertEqual("UNREVIEWED", result["semantic_status"])

    def test_arbitrary_precision_without_rounding(self):
        value = "1234567890" * 12
        other = value[:-1] + "1"
        self.assertNotEqual(integers(value), integers(other))

    def test_ambiguous_formats_require_review(self):
        for text in ["1,000", "1 000", "2.5", "15:30", "555-013-4826", "M12", "١٢"]:
            self.assertIsNone(integers(text), text)

    def test_written_out_number_requires_review(self):
        result = signals({"source": "Bring 12", "critical": {}}, "Trae doce")
        self.assertEqual("NEEDS_REVIEW", result["integer_check"]["state"])

    def test_no_numbers_not_vacuous_pass(self):
        self.assertEqual("NOT_APPLICABLE", signals({"source": "Hello", "critical": {}}, "Hola")["integer_check"]["state"])

    def test_wrapper_is_heuristic_not_confirmed_error(self):
        result = signals({"source": "Hello", "critical": {}}, '{"source":"Hola"}')
        self.assertIn("JSON_OR_LIST_WRAPPER", result["heuristic_signals"])
        self.assertEqual("UNREVIEWED", result["semantic_status"])


class BlindingAndPromptTests(unittest.TestCase):
    def setUp(self):
        self.case = json.loads((HERE / "corpus.json").read_text(encoding="utf-8"))["cases"][32]
        self.rows = [{"run": run, "case": self.case["id"], "text": text} for run, text in [("secret-model-1", "Uno"), ("secret-model-2", "Dos")]]

    def test_blinded_export_hides_identity_has_private_key(self):
        public, private = blind_export([self.case], self.rows)
        self.assertNotIn("secret-model", json.dumps(public))
        self.assertEqual({"secret-model-1", "secret-model-2"}, set(private[0]["key"].values()))
        self.assertEqual("UNREVIEWED", public[0]["review"]["status"])

    def test_blinding_reproducible(self):
        self.assertEqual(blind_export([self.case], self.rows, 42), blind_export([self.case], list(reversed(self.rows)), 42))

    def test_prompts_have_no_gold_semantic_facts(self):
        self.case["semantic_facts"] = ["SECRET_GOLD_FACT"]
        for template in ["concise", "structured"]:
            self.assertNotIn("SECRET_GOLD_FACT", translate_prompt(self.case, template))
        self.assertNotIn("SECRET_GOLD_FACT", verification_prompt(self.case, "Salida"))

    def test_context_ablation_removes_prior_turns(self):
        prompt = translate_prompt(self.case, context=False)
        self.assertNotIn("prior_turns", prompt)
        self.assertIn(self.case["source"], prompt)

    def test_hybrid_supplies_original_and_candidate(self):
        prompt = translate_prompt(self.case, candidate="DRAFT_TOKEN")
        self.assertIn("DRAFT_TOKEN", prompt)
        self.assertIn(self.case["source"], prompt)


class MetadataTests(unittest.TestCase):
    def test_local_artifact_corruption_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory) / "fixture.bin"
            path.write_bytes(b"known-fixture")
            record = {"bytes": path.stat().st_size, "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
            verify_file(path, record)
            path.write_bytes(b"wrong-fixture")
            with self.assertRaisesRegex(ValueError, "integrity failure"):
                verify_file(path, record)

    def lock(self):
        revision = "a" * 40
        return {"artifacts": [{"path": ".local/model.bin", "bytes": 10, "sha256": "b"*64,
                               "revision": revision, "url": "https://huggingface.co/test/model/resolve/"+revision+"/model.bin"}]}

    def test_valid_lock(self):
        self.assertEqual({".local/model.bin"}, validate_lock(self.lock()))

    def test_bad_hash_rejected(self):
        lock = self.lock(); lock["artifacts"][0]["sha256"] = "bad"
        with self.assertRaises(ValueError): validate_lock(lock)

    def test_traversal_rejected(self):
        lock = self.lock(); lock["artifacts"][0]["path"] = "../model.bin"
        with self.assertRaises(ValueError): validate_lock(lock)

    def test_unpinned_artifact_url_rejected(self):
        lock = self.lock(); lock["artifacts"][0]["url"] = "https://huggingface.co/test/model/resolve/main/model.bin"
        with self.assertRaises(ValueError): validate_lock(lock)

    def test_candidate_missing_license_rejected(self):
        with self.assertRaisesRegex(ValueError, "Incomplete"):
            validate_candidates({"candidates": [{"id": "test"}]}, self.lock())

    def test_committed_candidate_and_lock_metadata(self):
        candidates = json.loads((HERE / "candidates.json").read_text(encoding="utf-8"))
        lock = json.loads((HERE / "artifacts.lock.json").read_text(encoding="utf-8"))
        self.assertGreaterEqual(len(validate_candidates(candidates, lock)), 3)


class EvidenceTests(unittest.TestCase):
    def rows(self, path):
        return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()]

    def test_main_runs_complete_and_review_panel_explicit(self):
        evidence = HERE / "evidence"
        panel = set(json.loads((HERE / "protocol.json").read_text())["review_panel_ids"])
        reviews = self.rows(evidence / "reviews.jsonl")
        corpus_ids = {c["id"] for c in validate_corpus(json.loads((HERE / "corpus.json").read_text(encoding="utf-8")))}
        for metadata_path in (evidence / "metadata").glob("*.json"):
            run = metadata_path.stem
            if run.startswith("pilot-") or run == "q080-q5-precision": continue
            rows = [r for r in self.rows(evidence / "outputs" / (run+".jsonl")) if r["run"] == run]
            expected = panel if run == "q2-verify-retry" else corpus_ids
            self.assertEqual(expected, {r["case"] for r in rows})
            self.assertEqual(len(expected), len(rows))
            self.assertEqual(panel, {r["case"] for r in reviews if r["run"] == run})

    def test_identical_pairs_cannot_claim_improvement(self):
        for row in self.rows(HERE / "evidence/ablations.jsonl"):
            if row["identical_text"]: self.assertEqual("UNCHANGED", row["change"])

    def test_committed_failure_counts_recompute(self):
        evidence = HERE / "evidence"
        rows = [r for path in (evidence / "outputs").glob("*.jsonl") for r in self.rows(path)]
        actual = summarize(rows, self.rows(evidence / "reviews.jsonl"))
        recorded = json.loads((evidence / "summary.json").read_text())
        for run in actual:
            for key in ["outputs", "reviewed", "unreviewed", "severity_counts", "omission_count_reviewed", "invention_count_reviewed"]:
                self.assertEqual(recorded[run][key], actual[run][key])


if __name__ == "__main__": unittest.main()
