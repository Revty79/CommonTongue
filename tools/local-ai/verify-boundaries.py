"""Fail closed on networking/provider leaks, altered pins, or changed accepted TTS sources."""
import hashlib
import pathlib
import re
import subprocess
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[2]
BASELINE = '3a2f3357f7ef3567d754943f6822fd06bdb1966f'


def main():
    for folder in ('platform/local-ai/src/main', 'platform/android-local-ai/src/main'):
        for path in (ROOT / folder).rglob('*'):
            if path.suffix not in ('.kt', '.cpp', '.xml'): continue
            text = path.read_text(encoding='utf8')
            if re.search(r'java\.net|okhttp|retrofit|INTERNET|openConnection|Socket\(', text) or (path.suffix != '.xml' and re.search(r'https?://', text)):
                raise RuntimeError('Network capability entered local inference')
    ui = '\n'.join(p.read_text(encoding='utf8') for p in (ROOT / 'app/src').rglob('*.kt'))
    if re.search(r'WhisperSpeechRecognizer|MadladTranslator|System\.loadLibrary|Native\.asr|Native\.t5', ui):
        raise RuntimeError('UI constructs or calls a provider implementation')
    for name in ('src/lib.rs', 'src/handles.rs', 'Cargo.lock'):
        if (ROOT / 'tools/local-ai/t5' / name).read_bytes() != (ROOT / 'tools/device-trial/t5' / name).read_bytes():
            raise RuntimeError('Accepted T5 inference/ownership/dependency source changed')
    paths = subprocess.check_output(['git', 'ls-tree', '-r', '--name-only', BASELINE, 'platform/android-speech'], cwd=ROOT, text=True).splitlines()
    for name in paths:
        accepted = subprocess.check_output(['git', 'show', BASELINE + ':' + name], cwd=ROOT)
        actual = (ROOT / name).read_bytes().replace(b'\r\n', b'\n')
        if hashlib.sha256(accepted).digest() != hashlib.sha256(actual).digest():
            raise RuntimeError('Accepted Pass 6 production speech source changed: ' + name)
    for folder in ('app/src/main', 'platform/android-local-ai/src/main', 'platform/local-ai/src/main'):
        for file in (ROOT / folder).rglob('AndroidManifest.xml'):
            manifest = ET.parse(file).getroot()
            if manifest.findall('uses-permission') or manifest.findall('uses-permission-sdk-23'):
                raise RuntimeError('A production source manifest gained permissions')
    print('Local inference network boundary, neutral UI wiring, locked T5 source and accepted TTS verified.')


if __name__ == '__main__': main()
