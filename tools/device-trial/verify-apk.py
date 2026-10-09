"""Model-free permission, ARM64, native linkage and 16 KiB alignment audit."""

import argparse
import hashlib
import json
import pathlib
import re
import struct
import zipfile

from schemas import manifest_permissions

# Public Android NDK libraries actually used by the pinned API-26 CPU runtimes.
# A C++ shared runtime is deliberately not allowed here: it must be packaged.
PLATFORM_LIBRARIES = {"libandroid.so", "liblog.so", "libm.so", "libdl.so", "libc.so"}
JNI_METHODS = {"buildInfo", "asrCreate", "asrRun", "asrClose", "tokenizerCreate", "encode", "decode",
               "tokenizerClose", "t5Load", "t5Run", "t5Close"}
REQUIRED_EXPORTS = {
    "libdevice_trial.so": {"Java_com_commontongue_spike_device_Native_" + name for name in JNI_METHODS},
    "libtrial_t5.so": {"trial_t5_load", "trial_t5_run", "trial_t5_close", "trial_t5_string_free"},
}


def elf_header(data):
    if len(data) < 64 or data[:6] != b"\x7fELF\x02\x01":
        raise ValueError("Expected little-endian ELF64 library")
    header = struct.unpack_from("<16sHHIQQQIHHHHHH", data)
    if header[2] != 183: raise ValueError("Expected actual ARM64 native library")
    return header


def bounded(data, offset, size):
    if offset < 0 or size < 0 or offset + size > len(data): raise ValueError("Truncated ELF table")
    return data[offset:offset + size]


def elf_string(table, offset):
    end = table.find(b"\0", offset)
    if offset < 0 or offset >= len(table) or end < 0: raise ValueError("Invalid ELF string")
    return table[offset:end].decode("utf-8", errors="strict")


def elf_linkage(data):
    """Read real dynamic-table names and visible defined dynamic symbols, including stripped ELFs."""
    header = elf_header(data)
    offset, size, count = header[6], header[11], header[12]
    if size != 64 or not count: raise ValueError("Missing ELF section headers")
    bounded(data, offset, size * count)
    sections = [struct.unpack_from("<IIQQQQIIQQ", data, offset + index * size) for index in range(count)]
    needed, sonames, exports = [], [], set()
    dynamic_count = 0
    for entry in sections:
        if entry[1] not in (6, 11): continue  # SHT_DYNAMIC, SHT_DYNSYM
        table = bounded(data, entry[4], entry[5])
        if entry[6] >= count or sections[entry[6]][1] != 3: raise ValueError("Invalid ELF string table link")
        strings = sections[entry[6]]
        strings = bounded(data, strings[4], strings[5])
        expected_size = 16 if entry[1] == 6 else 24
        if entry[9] != expected_size or len(table) % expected_size: raise ValueError("Invalid ELF dynamic table")
        if entry[1] == 6:
            dynamic_count += 1
            terminated = False
            for pos in range(0, len(table), 16):
                tag, value = struct.unpack_from("<qQ", table, pos)
                if tag == 0:
                    terminated = True
                    break
                if tag == 1: needed.append(elf_string(strings, value))
                elif tag == 14: sonames.append(elf_string(strings, value))
            if not terminated: raise ValueError("Unterminated ELF dynamic table")
        else:
            for pos in range(0, len(table), 24):
                name, info, other, section, _, _ = struct.unpack_from("<IBBHQQ", table, pos)
                # STB_GLOBAL/WEAK, STV_DEFAULT/PROTECTED, not SHN_UNDEF.
                if section and info >> 4 in (1, 2) and other & 3 in (0, 3):
                    exports.add(elf_string(strings, name))
    if dynamic_count != 1 or len(sonames) > 1: raise ValueError("Invalid ELF dynamic metadata")
    return {"needed": needed, "soname": sonames[0] if sonames else None, "exports": exports}


def verify_linkage(libraries):
    """Fail closed on build-host paths, absent dependencies and missing JNI/C ABI exports."""
    if not REQUIRED_EXPORTS.keys() <= libraries.keys(): raise ValueError("Research native runtime missing")
    for name, metadata in libraries.items():
        for dependency in metadata["needed"] + ([metadata["soname"]] if metadata["soname"] else []):
            if not re.fullmatch(r"lib[A-Za-z0-9_.+-]+\.so", dependency):
                # Do not echo the build-host path into public reports/logs.
                raise ValueError("Native linkage contains a path or invalid library name")
        if metadata["soname"] not in (None, name): raise ValueError("Native SONAME does not match packaged filename")
        missing = set(metadata["needed"]) - libraries.keys() - PLATFORM_LIBRARIES
        if missing: raise ValueError("Native dependency is neither packaged nor a permitted Android platform library")
        if not REQUIRED_EXPORTS.get(name, set()) <= metadata["exports"]:
            raise ValueError("Required JNI or Rust C ABI symbol is not exported")
    if libraries["libtrial_t5.so"]["soname"] != "libtrial_t5.so":
        raise ValueError("Rust runtime must have SONAME libtrial_t5.so")


def elf_alignments(data):
    header = elf_header(data)
    offset, size, count = header[5], header[9], header[10]
    if size != 56: raise ValueError("Invalid ELF program header")
    bounded(data, offset, size * count)
    result = []
    for index in range(count):
        entry = struct.unpack_from("<IIQQQQQQ", data, offset + index * size)
        if entry[0] == 1:
            alignment = entry[7]
            if alignment < 16384 or entry[2] % alignment != entry[3] % alignment:
                raise ValueError("Native PT_LOAD segments lack 16 KiB alignment")
            result.append(alignment)
    if not result: raise ValueError("No ELF load segments")
    return result


def audit(apk, manifest):
    manifest_permissions(manifest)
    records = []
    libraries = {}
    with zipfile.ZipFile(apk) as archive, apk.open("rb") as raw:
        for info in archive.infolist():
            if info.filename.endswith((".gguf", ".onnx", ".safetensors", ".spm", ".bin")):
                raise ValueError("Research model weights/tokenizers must not be packaged")
            if info.filename.endswith(".so"):
                if not info.filename.startswith("lib/arm64-v8a/"): raise ValueError("Unexpected research ABI")
                data = archive.read(info)
                alignments = elf_alignments(data)
                name = pathlib.PurePosixPath(info.filename).name
                if info.filename != "lib/arm64-v8a/" + name or name in libraries:
                    raise ValueError("Invalid or duplicate native library entry")
                libraries[name] = elf_linkage(data)
                raw.seek(info.header_offset)
                header = struct.unpack("<IHHHHHIIIHH", raw.read(30))
                data_offset = info.header_offset + 30 + header[-2] + header[-1]
                if info.compress_type == zipfile.ZIP_STORED and data_offset % 16384:
                    raise ValueError("Uncompressed native library ZIP entry lacks 16 KiB alignment")
                records.append({"file": info.filename, "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest(),
                                "elf_load_alignments": alignments, "zip_compressed": info.compress_type != zipfile.ZIP_STORED})
    verify_linkage(libraries)
    for row in records:
        name = pathlib.PurePosixPath(row["file"]).name
        row.update({"needed": libraries[name]["needed"], "soname": libraries[name]["soname"],
                    "required_exports_verified": sorted(REQUIRED_EXPORTS.get(name, set()))})
    return {"schema_version": 2, "apk_bytes": apk.stat().st_size, "apk_sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
            "permissions": ["android.permission.RECORD_AUDIO"], "packaged_model_weights": False,
            "abi": "arm64-v8a", "dependency_closure_verified": True, "jni_and_rust_exports_verified": True,
            "native_libraries": records}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=pathlib.Path, required=True)
    parser.add_argument("--merged-manifest", type=pathlib.Path, required=True)
    parser.add_argument("--output", type=pathlib.Path, required=True)
    args = parser.parse_args()
    result = audit(args.apk, args.merged_manifest)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(f"Verified RECORD_AUDIO only, model-free ARM64 APK; {len(result['native_libraries'])} native libraries aligned; dependency closure and JNI/Rust exports verified")


if __name__ == "__main__": main()
