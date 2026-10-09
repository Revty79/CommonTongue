"""Build one deterministic local-import test pack; never add weights to Git."""

import argparse
import hashlib
import json
import pathlib
import shutil
import zipfile

from device import locked_assets, verify_local, write_json

ROOT = pathlib.Path(__file__).resolve().parents[2]
HERE = pathlib.Path(__file__).parent
CONTRACT = ROOT / "spikes/physical-trial/src/main/assets/test-pack-contract.json"
PACK_NAME = "CommonTongue-Pass5-Test-Pack.zip"


def sha(path):
    with path.open("rb") as stream: return hashlib.file_digest(stream, "sha256").hexdigest()


def payloads():
    models = locked_assets("madlad", "base", True)
    models.append(next(row for row in locked_assets("opus", "tiny", False) if "models/whisper/" in row["destination"]))
    rows = [(row["destination"].removeprefix("files/"), ROOT / row["path"], row["bytes"], row["sha256"], row.get("revision")) for row in models]
    fixtures = json.loads((HERE / "fixtures/trial-plan.json").read_text(encoding="utf-8"))
    rows += [("audio/" + row["file"], HERE / "fixtures" / row["file"], row["bytes"], row["sha256"], "fixed_synthetic_control") for row in fixtures["audio_artifacts"]]
    plan = HERE / "fixtures/trial-plan.json"
    rows.append(("trial-plan.json", plan, plan.stat().st_size, sha(plan), "fixed_synthetic_control"))
    for path in sorted((HERE / "pack-notices").glob("*")):
        if path.is_file(): rows.append(("notices/" + path.name, path, path.stat().st_size, sha(path), "research_provenance_notice"))
    return sorted(rows, key=lambda row: row[0])


def manifest_for(rows):
    return {"schema_version": 1, "pack_id": "common-tongue-pass5-v1", "app_package": "com.commontongue.spike.device", "minimum_apk_version": 2,
            "research_only": True, "total_payload_bytes": sum(row[2] for row in rows),
            "files": [{"path": row[0], "bytes": row[2], "sha256": row[3], "revision": row[4]} for row in rows]}


def manifest_bytes(manifest):
    return (json.dumps(manifest, sort_keys=True, ensure_ascii=True, separators=(",", ":")) + "\n").encode("utf-8")


def zip_info(name, size):
    info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
    info.compress_type = zipfile.ZIP_STORED
    info.create_system = 3
    info.external_attr = 0o100644 << 16
    info.file_size = size
    return info


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=pathlib.Path, default=ROOT / ".local/device/artifacts")
    parser.add_argument("--verify-contract", action="store_true", help="Model-free metadata check; no weights read")
    args = parser.parse_args()
    rows = payloads()
    manifest = manifest_for(rows)
    encoded = manifest_bytes(manifest)
    if args.verify_contract:
        contract = json.loads(CONTRACT.read_text(encoding="utf-8"))
        if contract["manifest"] != manifest or contract["manifest_sha256"] != hashlib.sha256(encoded).hexdigest():
            raise ValueError("The APK contract differs from the locked assets/fixtures; rebuild the test pack explicitly")
        print("APK test-pack contract matches pinned research metadata; no model files read.")
        return
    args.output.mkdir(parents=True, exist_ok=True)
    path = args.output / PACK_NAME
    partial = path.with_suffix(path.suffix + ".partial")
    try:
        with zipfile.ZipFile(partial, "w", compression=zipfile.ZIP_STORED, allowZip64=False) as archive:
            archive.writestr(zip_info("pack-manifest.json", len(encoded)), encoded)
            for relative, source, size, expected, revision in rows:
                if source.stat().st_size != size or sha(source) != expected: raise ValueError("Locked pack source checksum mismatch: " + relative)
                with source.open("rb") as stream, archive.open(zip_info("payload/" + relative, size), "w") as output:
                    shutil.copyfileobj(stream, output, 1024 * 1024)
                print("Packed verified research asset: " + relative, flush=True)
        if partial.stat().st_size >= 2 * 1024**3: raise ValueError("Pack exceeds the single GitHub asset limit; stop and split explicitly")
        partial.replace(path)
    finally:
        partial.unlink(missing_ok=True)
    contract = {"schema_version": 1, "pack_file": PACK_NAME, "pack_bytes": path.stat().st_size, "pack_sha256": sha(path),
                "manifest_sha256": hashlib.sha256(encoded).hexdigest(), "manifest": manifest}
    write_json(CONTRACT, contract)
    write_json(args.output / "test-pack-details.json", contract)
    print("One complete test pack prepared, below 2 GiB; APK contract generated without embedding weights.")


if __name__ == "__main__": main()
