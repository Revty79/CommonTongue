"""Controlled Windows native port for exercising production JVM adapters, not Android evidence."""
import base64
import ctypes
import json
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/offline-feasibility'))
from offline import install_tripwire
events = install_tripwire()
ctypes.windll.kernel32.SetErrorMode(0x0001 | 0x0002 | 0x8000)
native = ctypes.CDLL(str(ROOT / '.local/local-ai/t5-host/x86_64-pc-windows-msvc/release/trial_t5.dll'))
native.local_t5_privacy_init()
native.trial_t5_load.argtypes = [ctypes.c_char_p]; native.trial_t5_load.restype = ctypes.c_void_p
progress_type = ctypes.CFUNCTYPE(ctypes.c_int32, ctypes.c_uint32, ctypes.c_void_p)
native.trial_t5_run.argtypes = [ctypes.c_uint64, ctypes.c_char_p, progress_type, ctypes.c_void_p]; native.trial_t5_run.restype = ctypes.c_void_p
native.trial_t5_close.argtypes = [ctypes.c_uint64]
native.trial_t5_string_free.argtypes = [ctypes.c_void_p]


def reply(pointer):
    try: return json.loads(ctypes.string_at(pointer).decode('utf8'))
    finally: native.trial_t5_string_free(pointer)


def b64(value): return base64.b64encode(value.encode('utf8')).decode('ascii')
handle = 0
plan = json.loads((ROOT / 'app/src/debug/assets/pass7/controls.json').read_text(encoding='utf8'))
stages = []
@progress_type
def checkpoint(stage, _):
    stages.append(stage)
    return 1

for line in sys.stdin:
    try:
        parts = line.rstrip('\n').split('\t')
        if parts[0] == 'LOAD':
            result = reply(native.trial_t5_load(str(ROOT / '.local/quality/models/google--madlad400-3b-mt').encode()))
            if result.get('handle_kind') != 'opaque-id-v1' or not 1 <= result.get('handle', 0) <= 0xffffffff: raise RuntimeError()
            handle = result['handle']
            output = 'OK'
        elif parts[0] == 'CONTROL':
            row = plan[int(parts[1])]
            output = '\t'.join(b64(row[k]) for k in ('language', 'source', 'expected_translation', 'expected_asr'))
        elif parts[0] == 'ASR':
            language = parts[1]
            cli = ROOT / '.local/offline-build/whisper-desktop/bin/Release/whisper-cli.exe'
            result = subprocess.run([str(cli), '-m', str(ROOT / '.local/models/whisper/ggml-base-q5_1.bin'),
                '-f', str(ROOT / ('.local/device/fixtures/fixture-' + language + '01.wav')), '-l', language,
                '-t', '4', '-bo', '1', '-bs', '1', '-tpi', '0', '-ng', '-np', '-nt'],
                stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, check=True)
            output = 'OK\t' + b64(result.stdout.decode('utf8').strip()) + '\ttrue'
        elif parts[0] == 'MT':
            stages.clear()
            source = base64.b64decode(parts[2]).decode('utf8')
            result = reply(native.trial_t5_run(handle, json.dumps({'text': source, 'direction': parts[1]}, ensure_ascii=False).encode(), checkpoint, None))
            if 'error' in result or stages != [1, 2, 3, 4, 5, 6]: raise RuntimeError()
            output = 'OK\t' + b64(result['text']) + '\t' + str(result['completed']).lower()
        elif parts[0] == 'CLOSE':
            native.trial_t5_close(handle); handle = 0
            output = 'OK'
        else: raise RuntimeError()
        if events: raise RuntimeError()
        print(output, flush=True)
    except BaseException:
        print('FAILED', flush=True)  # Static failure only; no exception text/path/address.
if handle: native.trial_t5_close(handle)
