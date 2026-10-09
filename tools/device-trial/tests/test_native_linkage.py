"""Model-free ELF regressions for the physical Android loader failure."""

import importlib.util
import pathlib
import struct
import sys
import unittest

HERE = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(HERE))
spec = importlib.util.spec_from_file_location("native_apk_audit", HERE / "verify-apk.py")
apk = importlib.util.module_from_spec(spec)
spec.loader.exec_module(apk)


def elf(needed=("libtrial_t5.so", "libc.so"), soname="libdevice_trial.so", symbols=None):
    """Small ELF64 with actual dynstr/dynamic/dynsym sections; no model files."""
    strings = bytearray(b"\0")
    def string(value):
        index = len(strings)
        strings.extend(value.encode() + b"\0")
        return index
    dynamic = [(1, string(name)) for name in needed]
    if soname is not None: dynamic.append((14, string(soname)))
    dynamic.append((0, 0))
    symbols = symbols if symbols is not None else [("Java_com_commontongue_spike_device_Native_buildInfo", 0x12, 0, 1)]
    symtab = bytes(24) + b"".join(struct.pack("<IBBHQQ", string(name), info, other, section, 0, 4)
                               for name, info, other, section in symbols)
    dyn = b"".join(struct.pack("<qQ", *row) for row in dynamic)
    offsets = (64, 64 + len(strings), 64 + len(strings) + len(dyn))
    sections_offset = offsets[2] + len(symtab)
    header = struct.pack("<16sHHIQQQIHHHHHH", b"\x7fELF\x02\x01" + bytes(10), 3, 183, 1, 0,
                         0, sections_offset, 0, 64, 56, 0, 64, 4, 0)
    sections = bytes(64)
    for kind, offset, size, link, entry_size in (
            (3, offsets[0], len(strings), 0, 0), (6, offsets[1], len(dyn), 1, 16),
            (11, offsets[2], len(symtab), 1, 24)):
        sections += struct.pack("<IIQQQQIIQQ", 0, kind, 0, 0, offset, size, link, 0, 8, entry_size)
    return header + strings + dyn + symtab + sections


def valid_libraries():
    return {
        "libdevice_trial.so": {"needed": ["libtrial_t5.so", "libc.so"], "soname": "libdevice_trial.so",
                               "exports": apk.REQUIRED_EXPORTS["libdevice_trial.so"].copy()},
        "libtrial_t5.so": {"needed": ["libdl.so", "libm.so", "libc.so"], "soname": "libtrial_t5.so",
                           "exports": apk.REQUIRED_EXPORTS["libtrial_t5.so"].copy()},
    }


class NativeLinkageTests(unittest.TestCase):
    def test_reads_real_dynamic_names_and_visible_defined_symbols(self):
        data = elf(symbols=[("visible", 0x12, 0, 1), ("weak", 0x22, 3, 1),
                            ("undefined", 0x12, 0, 0), ("hidden", 0x12, 2, 1), ("local", 0x02, 0, 1)])
        result = apk.elf_linkage(data)
        self.assertEqual(result, {"needed": ["libtrial_t5.so", "libc.so"], "soname": "libdevice_trial.so",
                                  "exports": {"visible", "weak"}})

    def test_accepts_packaged_rust_and_public_platform_dependency_closure(self):
        apk.verify_linkage(valid_libraries())

    def test_rejects_actual_build_host_dependency_pattern_without_echoing_path(self):
        for name in ("D:/private/account/native/arm64-v8a/libtrial_t5.so", "/home/private/native/libtrial_t5.so"):
            libraries = valid_libraries()
            libraries["libdevice_trial.so"].update(apk.elf_linkage(elf(needed=(name,))))
            with self.subTest(name=name), self.assertRaises(ValueError) as error:
                apk.verify_linkage(libraries)
            self.assertNotIn("private", str(error.exception))

    def test_rejects_unpackaged_transitive_cpp_runtime(self):
        libraries = valid_libraries()
        libraries["libtrial_t5.so"]["needed"].append("libc++_shared.so")
        with self.assertRaisesRegex(ValueError, "dependency"):
            apk.verify_linkage(libraries)
        libraries["libc++_shared.so"] = {"needed": ["libc.so"], "soname": "libc++_shared.so", "exports": set()}
        apk.verify_linkage(libraries)

    def test_rejects_missing_rust_soname_and_mismatched_soname(self):
        for soname in (None, "libwrong.so"):
            libraries = valid_libraries()
            libraries["libtrial_t5.so"]["soname"] = soname
            with self.subTest(soname=soname), self.assertRaisesRegex(ValueError, "SONAME"):
                apk.verify_linkage(libraries)

    def test_rejects_missing_jni_or_rust_export(self):
        for name, symbol in (("libdevice_trial.so", "Java_com_commontongue_spike_device_Native_buildInfo"),
                             ("libtrial_t5.so", "trial_t5_load")):
            libraries = valid_libraries()
            libraries[name]["exports"].remove(symbol)
            with self.subTest(name=name), self.assertRaisesRegex(ValueError, "symbol"):
                apk.verify_linkage(libraries)

    def test_rejects_missing_packaged_runtime(self):
        libraries = valid_libraries()
        del libraries["libtrial_t5.so"]
        with self.assertRaisesRegex(ValueError, "runtime missing"):
            apk.verify_linkage(libraries)

    def test_allows_third_party_library_without_soname_when_dependencies_resolve(self):
        libraries = valid_libraries()
        libraries["libonnxruntime4j_jni.so"] = {"needed": ["libonnxruntime.so", "libc.so"], "soname": None, "exports": set()}
        libraries["libonnxruntime.so"] = {"needed": ["libm.so", "libc.so"], "soname": "libonnxruntime.so", "exports": set()}
        apk.verify_linkage(libraries)

    def test_truncated_elf_and_invalid_string_offset_fail(self):
        data = elf()
        with self.assertRaises(ValueError): apk.elf_linkage(data[:-1])
        mutable = bytearray(data)
        header = apk.elf_header(data)
        dynamic_section = struct.unpack_from("<IIQQQQIIQQ", data, header[6] + 2 * 64)
        struct.pack_into("<Q", mutable, dynamic_section[4] + 8, 2**32)
        with self.assertRaises(ValueError): apk.elf_linkage(mutable)


if __name__ == "__main__": unittest.main()
