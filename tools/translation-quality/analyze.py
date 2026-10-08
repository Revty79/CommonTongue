"""Recompute committed research summaries without models or third-party packages."""

import argparse
import json
from pathlib import Path

from schema import validate_corpus
from scoring import blind_export, summarize

HERE = Path(__file__).parent


def lines(path):
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--summary", type=Path)
    parser.add_argument("--blind-public", type=Path)
    parser.add_argument("--blind-private", type=Path)
    parser.add_argument("--seed", type=int, default=404)
    args = parser.parse_args()
    rows = [row for path in sorted((HERE / "evidence/outputs").glob("*.jsonl")) for row in lines(path)]
    reviews = lines(HERE / "evidence/reviews.jsonl")
    if args.summary:
        args.summary.write_text(json.dumps(summarize(rows, reviews), indent=2)+"\n", encoding="utf-8")
    if args.blind_public:
        if not args.blind_private: parser.error("A separate private key path is required")
        if args.blind_public.resolve() == args.blind_private.resolve(): parser.error("Public sheet and private key must be separate")
        cases = validate_corpus(json.loads((HERE / "corpus.json").read_text(encoding="utf-8")))
        main_runs = set(json.loads((HERE / "protocol.json").read_text())["main_runs"])
        main_rows = [r for r in rows if r["run"] in main_runs]
        public, private = blind_export(cases, main_rows, args.seed)
        args.blind_public.write_text(json.dumps(public, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")
        args.blind_private.write_text(json.dumps(private, indent=2)+"\n", encoding="utf-8")
    if not args.summary and not args.blind_public: parser.error("Choose a summary or blind export")


if __name__ == "__main__": main()
