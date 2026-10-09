"""Pass 8 integrates the accepted stack; fail on inference, model-lock or voice drift."""
import pathlib
import re
import subprocess

ROOT = pathlib.Path(__file__).resolve().parents[2]
BASELINE = 'd22b8fa513b79da29857e620affc6485993b4742'


def main():
    paths = subprocess.check_output([
        'git', 'ls-tree', '-r', '--name-only', BASELINE,
        'platform/local-ai', 'platform/android-local-ai',
        'app/src/debug/assets/pass7', 'tools/local-ai/t5',
    ], cwd=ROOT, text=True).splitlines()
    for name in paths:
        expected = subprocess.check_output(['git', 'show', BASELINE + ':' + name], cwd=ROOT)
        actual = (ROOT / name).read_bytes().replace(b'\r\n', b'\n')
        if actual != expected.replace(b'\r\n', b'\n'):
            raise RuntimeError('Pass 8 changed accepted inference or pack identity: ' + name)
    for file in (ROOT / 'app/src/main/kotlin/com/commontongue/prototype/ui').rglob('*.kt'):
        if re.search(r'android\.media|java\.io|java\.net|System\.loadLibrary|LocalCapabilities|AndroidSpeechSynthesizer', file.read_text(encoding='utf8')):
            raise RuntimeError('Product UI gained platform inference, audio or storage details')
    print(f'Pass 8 preserved {len(paths)} accepted local-AI, native, dependency and pack inputs; neutral UI verified.')


if __name__ == '__main__':
    main()
