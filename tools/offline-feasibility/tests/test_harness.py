import hashlib
import json
import pathlib
import subprocess
import sys
import tempfile
import unittest

TOOLS = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))
from harness import word_error_rate
from prepare import download


class HarnessTests(unittest.TestCase):
    def test_corpus_contains_both_directions_and_required_meaning(self):
        corpus = json.loads((TOOLS / "corpus.json").read_text(encoding="utf-8"))
        self.assertGreaterEqual(len(corpus), 30)
        self.assertEqual(len({case["id"] for case in corpus}), len(corpus))
        for direction in ("en-es", "es-en"):
            self.assertGreaterEqual(sum(case["direction"] == direction for case in corpus), 15)
        for case in corpus:
            self.assertTrue(case["source"].strip())
            self.assertTrue(case["meaning"].strip())
            self.assertIn(case["direction"], ("en-es", "es-en"))

    def test_word_error_rate_ignores_case_and_punctuation(self):
        self.assertEqual(word_error_rate("No, don't start!", "no don't start"), 0)

    def test_word_error_rate_detects_a_lost_negation(self):
        self.assertEqual(word_error_rate("do not start", "do start"), 1 / 3)

    def test_download_rejects_a_corrupt_cached_artifact(self):
        with tempfile.TemporaryDirectory() as directory:
            artifact = pathlib.Path(directory) / "artifact"
            artifact.write_bytes(b"corrupt")
            with self.assertRaisesRegex(RuntimeError, "Checksum mismatch"):
                download({"path": str(artifact), "url": "https://example.invalid/never-requested",
                          "sha256": hashlib.sha256(b"expected").hexdigest()})

    def test_lock_pins_every_download_and_keeps_assets_out_of_git(self):
        lock = json.loads((TOOLS / "artifacts.lock.json").read_text(encoding="utf-8"))
        for artifact in lock["artifacts"]:
            self.assertRegex(artifact["sha256"], r"^[0-9a-f]{64}$")
            self.assertRegex(artifact["revision"], r"^[0-9a-f]{40}$")
            self.assertTrue(artifact["path"].startswith(".local/"))
            self.assertGreater(artifact["bytes"], 0)
            self.assertTrue(artifact["url"].startswith("https://"))

    def test_tripwire_refuses_network_connections(self):
        code = """from offline import install_tripwire
import socket
events = install_tripwire()
try:
    socket.create_connection(('127.0.0.1', 9), timeout=1)
except RuntimeError as error:
    assert 'Offline execution forbids' in str(error)
    assert events
else:
    raise AssertionError('Network request escaped the tripwire')
"""
        completed = subprocess.run([sys.executable, "-c", code], cwd=TOOLS, capture_output=True, text=True)
        self.assertEqual(completed.returncode, 0, completed.stderr)


if __name__ == "__main__":
    unittest.main()
