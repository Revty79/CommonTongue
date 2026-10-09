"""Audit and package the debug adapter check without model blobs or voice assets."""
import hashlib
import json
import os
import pathlib
import shutil
import subprocess
import xml.etree.ElementTree as ET
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / '.local/pass7-artifacts'
APK = ROOT / 'app/build/outputs/apk/debug/app-debug.apk'
A = '{http://schemas.android.com/apk/res/android}'


def main():
    manifest = ET.parse(ROOT / 'app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml').getroot()
    allowed = {'android.permission.INTERNET', 'com.commontongue.prototype.debug.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'}
    permissions = {x.get(A + 'name') for x in manifest.findall('uses-permission')}
    if permissions != allowed or manifest.findall('uses-permission-sdk-23'): raise RuntimeError('Unapproved debug permissions')
    if len([x for x in manifest.findall('.//category') if x.get(A + 'name') == 'android.intent.category.LAUNCHER']) != 1:
        raise RuntimeError('More than one launcher')
    required = {'libcommon_tongue_ai.so', 'libtrial_t5.so'}
    sdk = pathlib.Path(os.environ.get('ANDROID_HOME', str(pathlib.Path.home() / 'AppData/Local/Android/Sdk')))
    host = 'windows-x86_64' if os.name == 'nt' else 'linux-x86_64'
    readelf = sdk / ('ndk/27.1.12297006/toolchains/llvm/prebuilt/' + host + '/bin/llvm-readelf' + ('.exe' if os.name == 'nt' else ''))
    audit = ROOT / '.local/local-ai/apk-audit'
    audit.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(APK) as package:
        natives = [x for x in package.namelist() if x.startswith('lib/') and x.endswith('.so')]
        names = {pathlib.PurePosixPath(x).name for x in natives}
        if not required <= names or any(not x.startswith('lib/arm64-v8a/') for x in natives): raise RuntimeError('Missing or incorrect ABI runtime')
        if names - required - {'libandroidx.graphics.path.so', 'libc++_shared.so'}: raise RuntimeError('Unexpected native runtime')
        if any(x != 'DebugProbesKt.bin' and x.lower().endswith(('.gguf', '.bin', '.onnx', '.wav', '.safetensors')) for x in package.namelist()): raise RuntimeError('Model/audio blob bundled in APK')
        dependencies = {}
        for name in natives:
            file = audit / pathlib.PurePosixPath(name).name
            file.write_bytes(package.read(name))
            text = subprocess.check_output([str(readelf), '--dynamic', str(file)], text=True)
            import re
            needs = re.findall(r'\(NEEDED\).*?\[(.*?)\]', text)
            system = {'libc.so', 'libm.so', 'libdl.so', 'liblog.so', 'libandroid.so'}
            if set(needs) - names - system: raise RuntimeError('Unpackaged transitive native dependency')
            dependencies[file.name] = needs
            if file.name == 'libtrial_t5.so' and not re.search(r'\(SONAME\).*?\[libtrial_t5.so\]', text): raise RuntimeError('Nonportable Rust SONAME')
    OUT.mkdir(parents=True, exist_ok=True)
    name = 'CommonTongue-Pass7-Adapter-Check.apk'
    shutil.copyfile(APK, OUT / name)
    sha = hashlib.sha256(APK.read_bytes()).hexdigest()
    details = {'scope': 'PASS7_DEBUG_COMPONENT_CHECK_NOT_PRODUCTION_RELEASE', 'source_commit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
        'working_tree_dirty': bool(subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT)),
        'apk': name, 'bytes': APK.stat().st_size, 'sha256': sha, 'application_id': manifest.get('package'),
        'version_name': manifest.get(A + 'versionName'), 'permissions': sorted(permissions), 'native_dependencies': dependencies,
        'physical_acceptance': 'PENDING_S25_PRODUCTION_ADAPTER_EXPORT', 'model_files_bundled': False,
        'download': 'Tap Set up test resources once in the single Common Tongue launcher. About 1.73 GB download and 4 GB free storage.',
        'update_signing': 'The tester download is locally signed with the existing persistent debug key. Ordinary CI uses its own debug key and is not an update for that installation.'}
    (OUT / 'Adapter-Check-Details.json').write_text(json.dumps(details, indent=2) + '\n', encoding='utf8')
    (OUT / 'SHA256SUMS.txt').write_text(f'{sha}  {name}\n', encoding='utf8')
    print(json.dumps({'apk': name, 'sha256': sha, 'bytes': APK.stat().st_size}))


if __name__ == '__main__': main()
