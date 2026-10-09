"""Pass 9 controlled text probes, not a production guidance implementation.

Default verification uses only committed research fixtures/evidence. --run-model
explicitly exercises the existing Windows production C ABI without downloading
anything. It never reads app history, microphone recordings or user exports.
"""
import argparse
import collections
import ctypes
import hashlib
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
HERE = pathlib.Path(__file__).resolve().parent
BASELINE = '8f37267f318839e696537a4e3ebe7b8fed9b1297'
MARKERS = re.compile(r'\[CT(?:BOUNDARY|TERM)\d+\]')


def recover(row, result):
    """A structural recovery check is NOT a check of the resulting meaning."""
    if not result.get('completed') or 'error' in result:
        return {'status': 'INCOMPLETE_OR_NATIVE_FAILURE'}
    strategy = row['strategy']
    text = result['text']
    if strategy == 'baseline':
        return {'status': 'BASIC', 'text': text}
    if strategy in ('numbered', 'paragraph'):
        return {'status': 'NO_PROVEN_BOUNDARY'}
    if strategy == 'numeric':
        markers = ['76321984:']
        actual = markers * text.count(markers[0])
    else:
        markers = MARKERS.findall(row['effective_source'])
        actual = MARKERS.findall(text)
        # Changed/partial/translated marker families also make recovery unsafe.
        if text.count('CT') != len(actual):
            return {'status': 'MARKER_DAMAGED'}
    if not markers or collections.Counter(markers) != collections.Counter(actual):
        return {'status': 'MARKER_COUNT_MISMATCH'}
    if strategy == 'protected-term':
        restored = text
        for marker in set(markers):
            restored = restored.replace(marker, row['target_term'])
        if 'CT' in restored:
            return {'status': 'MARKER_DAMAGED'}
        return {'status': 'RECOVERED_NOT_MEANING_VERIFIED', 'text': restored}
    if len(markers) != 1:
        return {'status': 'MARKER_COUNT_MISMATCH'}
    before, after = text.split(markers[0])
    if not before.strip() or not after.strip():
        return {'status': 'SEGMENT_MISSING'}
    return {
        'status': 'RECOVERED_NOT_MEANING_VERIFIED',
        'text': (before if strategy == 'context-suffix' else after).strip(),
    }


def verify_record():
    controls = json.loads((HERE / 'controls.json').read_text(encoding='utf8'))
    record = json.loads((HERE / 'evidence/locked-probes.json').read_text(encoding='utf8'))
    rows = controls['probes']
    if len(rows) != len(record['results']):
        raise ValueError('Fixture/evidence count mismatch')
    if record['model_hashes'] != controls['model_hashes']:
        raise ValueError('Locked model identity mismatch')
    if record['production_ready'] or record['decision'] != 'STOP_FOR_GUIDANCE_REVIEW':
        raise ValueError('Failed research must not be represented as production support')
    for fixture, observation in zip(rows, record['results']):
        if observation['probe'] != fixture:
            raise ValueError('Fixture/evidence provenance mismatch')
        result = observation['result']
        if result.get('threads') != 4 or result.get('execution_abi') != 'desktop-control':
            raise ValueError('Runtime configuration mismatch')
        if not 0 <= result['tokens'] <= 128 or not 0 < result['prompt_tokens'] <= 512:
            raise ValueError('Accepted decoding limits changed')
        if observation['recovery'] != recover(fixture, result):
            raise ValueError('Recovery evidence mismatch')
        if not fixture['current'] or fixture['direction'] not in ('en-es', 'es-en'):
            raise ValueError('Invalid controlled fixture')
    if record['python_network_events']:
        raise ValueError('Inference attempted Python networking')
    return controls, record


def run_model(controls, selected, output):
    if sys.platform != 'win32':
        raise RuntimeError('Use the existing Windows native control build')
    sys.path.insert(0, str(ROOT / 'tools/offline-feasibility'))
    from offline import install_tripwire, probe
    probes = probe()
    if 'AVAILABLE' in probes.values():
        raise RuntimeError('Use the OS network-restricted inference sandbox')
    events = install_tripwire()
    model = ROOT / '.local/quality/models/google--madlad400-3b-mt'
    for name, digest in controls['model_hashes'].items():
        with (model / name).open('rb') as stream:
            if hashlib.file_digest(stream, 'sha256').hexdigest() != digest:
                raise RuntimeError('Model integrity failed; no download or modification attempted')
    library = ROOT / '.local/local-ai/t5-host/x86_64-pc-windows-msvc/release/trial_t5.dll'
    if hashlib.sha256(library.read_bytes()).hexdigest() != controls['library_sha256']:
        raise RuntimeError('This record requires the exact tested host runtime build')
    ctypes.windll.kernel32.SetErrorMode(0x0001 | 0x0002 | 0x8000)
    native = ctypes.CDLL(str(library))
    native.local_t5_privacy_init()
    native.trial_t5_load.argtypes = [ctypes.c_char_p]
    native.trial_t5_load.restype = ctypes.c_void_p
    progress_type = ctypes.CFUNCTYPE(ctypes.c_int32, ctypes.c_uint32, ctypes.c_void_p)
    native.trial_t5_run.argtypes = [ctypes.c_uint64, ctypes.c_char_p, progress_type, ctypes.c_void_p]
    native.trial_t5_run.restype = ctypes.c_void_p
    native.trial_t5_close.argtypes = [ctypes.c_uint64]
    native.trial_t5_string_free.argtypes = [ctypes.c_void_p]

    def reply(pointer):
        try:
            return json.loads(ctypes.string_at(pointer).decode('utf8'))
        finally:
            native.trial_t5_string_free(pointer)

    @progress_type
    def checkpoint(stage, context):
        return 1

    loaded = reply(native.trial_t5_load(str(model).encode()))
    if loaded.get('handle_kind') != 'opaque-id-v1' or not 1 <= loaded.get('handle', 0) <= 0xffffffff:
        raise RuntimeError('Native model load failed')
    observations = []
    try:
        for fixture in selected:
            request = {'text': fixture['effective_source'], 'direction': fixture['direction']}
            result = reply(native.trial_t5_run(
                loaded['handle'], json.dumps(request, ensure_ascii=False).encode(), checkpoint, None))
            if 'error' in result:
                result = {'error': 'NATIVE_FAILURE'}  # Never persist native exception/path details.
            observations.append({'probe': fixture, 'result': result, 'recovery': recover(fixture, result)})
            print(fixture['probe_id'] + ': ' + observations[-1]['recovery']['status'], flush=True)
    finally:
        native.trial_t5_close(loaded['handle'])
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps({
            'scope': 'CONTROLLED_FIXTURES_WINDOWS_C_ABI_NOT_S25_NOT_PRODUCTION',
            'results': observations, 'python_network_events': events,
            'network_probes': {key: 'AVAILABLE' if value == 'AVAILABLE' else 'BLOCKED' for key, value in probes.items()},
        }, indent=2, ensure_ascii=False) + '\n', encoding='utf8', newline='\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run-model', action='store_true')
    parser.add_argument('--probe-id', action='append')
    parser.add_argument('--output', type=pathlib.Path, default=ROOT / '.local/pass9/replayed-probes.json')
    args = parser.parse_args()
    controls, record = verify_record()
    if args.run_model:
        selected = [row for row in controls['probes'] if not args.probe_id or row['probe_id'] in args.probe_id]
        if not selected or (args.probe_id and len(selected) != len(set(args.probe_id))):
            raise ValueError('Unknown controlled probe ID')
        run_model(controls, selected, args.output)
    else:
        print(f"{len(record['results'])} locked fixture observations verified; production guidance gate NOT PASSED.")


if __name__ == '__main__':
    main()
