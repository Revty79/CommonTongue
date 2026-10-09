"""Create one local, test-only pinned pack; no network or binary Git writes."""
import hashlib
import json
import pathlib
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / '.local/pass7-artifacts'
ASSETS = ROOT / 'app/src/debug/assets/pass7'


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def main():
    plan = json.loads((ROOT / 'tools/device-trial/fixtures/trial-plan.json').read_text(encoding='utf8'))
    files = {
        'recognizer': ROOT / '.local/models/whisper/ggml-base-q5_1.bin',
        'translator': ROOT / '.local/quality/models/google--madlad400-3b-mt/model-q4k.gguf',
        'tokenizer': ROOT / '.local/quality/models/google--madlad400-3b-mt/tokenizer.json',
        'configuration': ROOT / '.local/quality/models/google--madlad400-3b-mt/config.json',
        'control_en': ROOT / '.local/device/fixtures/fixture-en01.wav',
        'control_es': ROOT / '.local/device/fixtures/fixture-es01.wav',
    }
    expected = {}
    for name, path in files.items():
        expected[name] = {'bytes': path.stat().st_size, 'sha256': digest(path)}
    # All rows are validated against accepted, committed locks, never inferred from a new model.
    locked = []
    for name in ('tools/offline-feasibility/artifacts.lock.json', 'tools/translation-quality/artifacts.lock.json'):
        locked += json.loads((ROOT / name).read_text())['artifacts']
    for name in ('recognizer', 'translator', 'tokenizer', 'configuration'):
        row = next(x for x in locked if x['path'] == files[name].relative_to(ROOT).as_posix())
        if expected[name] != {k: row[k] for k in ('bytes', 'sha256')}:
            raise RuntimeError('Accepted resource identity differs; stop adoption')
    for lang in ('en', 'es'):
        row = next(x for x in plan['audio_artifacts'] if x['source_id'] == lang + '01')
        if expected['control_' + lang] != {k: row[k] for k in ('bytes', 'sha256')}:
            raise RuntimeError('Accepted fixture identity differs')
    OUT.mkdir(parents=True, exist_ok=True)
    ASSETS.mkdir(parents=True, exist_ok=True)
    manifest = {'version': 'offline-core-en-es-v1', 'files': expected}
    archive = OUT / 'CommonTongue-Pass7-Test-Resources.zip'
    with zipfile.ZipFile(archive, 'w', compression=zipfile.ZIP_STORED, allowZip64=True) as package:
        package.writestr('manifest.json', json.dumps(manifest, sort_keys=True))
        for name, path in files.items(): package.write(path, name)
        package.write(ROOT / 'docs/LOCAL-AI-PROVENANCE.md', 'NOTICES.txt')
    download = {**manifest, 'archive_bytes': archive.stat().st_size, 'archive_sha256': digest(archive),
        'url': 'https://github.com/Revty79/CommonTongue/releases/download/pass7-adapter-check-v8/CommonTongue-Pass7-Test-Resources.zip'}
    with zipfile.ZipFile(archive) as package:
        download['metadata'] = {name: {'bytes': len(package.read(name)), 'sha256': hashlib.sha256(package.read(name)).hexdigest()}
                                for name in ('manifest.json', 'NOTICES.txt')}
    (ASSETS / 'resources.json').write_text(json.dumps(download, indent=2) + '\n', encoding='utf8')
    controls = []
    baseline = json.loads((ROOT / '.local/local-ai/asr-host-baseline.json').read_text(encoding='utf8'))
    for lang in ('en', 'es'):
        row = next(x for x in plan['text_controls'] if x['case_id'] == 'text-' + lang + '01')
        controls.append({'language': lang, 'source': row['source'], 'expected_translation': row['expected_pass4_translation'],
            'audio_token': 'control_' + lang, 'expected_asr': next(x['text'] for x in baseline if x['language'] == lang)})
    (ASSETS / 'controls.json').write_text(json.dumps(controls, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    print(json.dumps({'bytes': download['archive_bytes'], 'sha256': download['archive_sha256']}))


if __name__ == '__main__': main()
