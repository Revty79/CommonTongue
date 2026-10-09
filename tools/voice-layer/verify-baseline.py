"""Fail if the accepted native/model research inputs have changed during Pass 6."""
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def verify():
    inventory = (ROOT / "tools/device-trial/evidence/s25-v7-acceptance.json").read_bytes()
    if hashlib.sha256(inventory).hexdigest() != "48cc1b0f1e14207a69ad28015765f8ad0442839ca4801c3c102ce4839b62c994":
        raise SystemExit("Accepted Pass 5 source inventory changed.")
    evidence = json.loads(inventory)
    protected = [
        row for row in evidence["preserved_runtime_sources"]
        if row["path"].startswith(("spikes/physical-trial/", "tools/device-trial/t5/"))
    ]
    if not protected:
        raise SystemExit("Accepted research baseline inventory is missing.")
    for row in protected:
        path = ROOT / row["path"]
        if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != row["sha256"]:
            raise SystemExit("Pass 5 runtime/model input changed: " + row["path"])
    print(f"Verified {len(protected)} unchanged Pass 5 research/model/native inputs.")
    return len(protected)


if __name__ == "__main__":
    verify()
