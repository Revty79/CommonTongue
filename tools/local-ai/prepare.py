"""Prepare hash-locked runtime sources only. Never acquire inference models."""
import hashlib
import json
import pathlib
import shutil
import urllib.request
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
SOURCES = ROOT / '.local/local-ai/sources'


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def main():
    locks = [json.loads((ROOT / name).read_text())['artifacts'] for name in (
        'tools/offline-feasibility/artifacts.lock.json',
        'tools/translation-quality/artifacts.lock.json')]
    for name, cache in [('whisper', '.local/offline-sources/whisper.zip'),
                        ('candle', '.local/quality/candle-runtime-source.zip')]:
        row = next(r for rows in locks for r in rows if r['path'] == cache)
        archive = ROOT / cache
        if not archive.is_file():
            archive.parent.mkdir(parents=True, exist_ok=True)
            with urllib.request.urlopen(row['url'], timeout=120) as incoming, archive.open('wb') as out:
                shutil.copyfileobj(incoming, out)
        if archive.stat().st_size != row['bytes'] or digest(archive) != row['sha256']:
            raise RuntimeError('Pinned source archive integrity check failed')
        destination = SOURCES / name
        if destination.exists():
            # A cache is not evidence of identity. Recheck its source bytes against
            # the locked archive before a build can silently reuse changed code.
            with zipfile.ZipFile(archive) as package:
                for entry in package.infolist():
                    if entry.is_dir(): continue
                    relative = pathlib.PurePosixPath(entry.filename).parts[1:]
                    path = destination.joinpath(*relative)
                    if not path.is_file() or hashlib.sha256(path.read_bytes()).digest() != hashlib.sha256(package.read(entry)).digest():
                        raise RuntimeError('Prepared runtime source differs from its immutable archive')
            print(name + ': cached source identity verified')
            continue
        staging = SOURCES / (name + '-staging')
        staging.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(archive) as package:
            for entry in package.infolist():
                path = pathlib.PurePosixPath(entry.filename)
                if path.is_absolute() or '..' in path.parts or (entry.external_attr >> 16) & 0o170000 == 0o120000:
                    raise RuntimeError('Unsafe source archive entry')
            package.extractall(staging)
        roots = list(staging.iterdir())
        if len(roots) != 1 or not roots[0].is_dir():
            raise RuntimeError('Unexpected runtime source layout')
        roots[0].rename(destination)
        staging.rmdir()
        print(name + ': pinned sources prepared')


if __name__ == '__main__':
    main()
