#!/usr/bin/env python3
"""Inspect the real plugin ZIP without extracting it; fail closed on packaging regressions."""
import argparse
from collections import Counter
import hashlib
import io
import json
from pathlib import Path, PurePosixPath
import sys
import xml.etree.ElementTree as ET
import zipfile


def inspect(path: Path):
    errors, descriptors, licenses, notices = [], [], [], []
    jars = []
    classes = Counter()

    def scan(archive, prefix='', depth=0):
        names = archive.namelist()
        for name, count in Counter(names).items():
            if count > 1:
                errors.append(f'Duplicate archive entry: {prefix}{name}')
        for item in archive.infolist():
            if item.is_dir():
                continue
            name = item.filename
            parts = PurePosixPath(name).parts
            full = prefix + name
            if name.startswith('/') or '..' in parts or '\\' in name:
                errors.append(f'Unsafe archive path: {full}')
            lower = name.lower()
            if any(part.lower() in {'node_modules', 'storyformats', '.storyformats'} for part in parts) or lower.endswith(('.ts', '.tsx', '.vue', '.js', '.wasm', '.node', '.exe')):
                errors.append(f'Legacy/runtime asset is bundled: {full}')
            if any(PurePosixPath(lower).name.startswith(stem) for stem in ('kotlin-stdlib', 'kotlin-reflect', 'kotlinx-coroutines')):
                errors.append(f'IDE-provided runtime JAR is bundled: {full}')
            if lower.endswith('.class'):
                classes[name] += 1
                if name.startswith(('kotlin/', 'kotlinx/coroutines/')) or '/kotlin/' in name or '/kotlinx/coroutines/' in name:
                    errors.append(f'IDE-provided runtime class is bundled: {full}')
            if name == 'META-INF/plugin.xml':
                descriptors.append(archive.read(item))
            if name == 'META-INF/LICENSE':
                licenses.append(archive.read(item))
            if name == 'META-INF/NOTICE':
                notices.append(archive.read(item))
            if lower.endswith('.jar'):
                jars.append(full)
                if depth >= 2:
                    errors.append(f'Unexpected nested archive depth: {full}')
                else:
                    try:
                        with zipfile.ZipFile(io.BytesIO(archive.read(item))) as nested:
                            scan(nested, full + '!/', depth + 1)
                    except zipfile.BadZipFile:
                        errors.append(f'Invalid JAR: {full}')

    try:
        with zipfile.ZipFile(path) as archive:
            scan(archive)
    except (OSError, zipfile.BadZipFile) as error:
        errors.append(str(error))
    if len(descriptors) != 1:
        errors.append(f'Expected one native plugin descriptor, found {len(descriptors)}')
    else:
        try:
            root = ET.fromstring(descriptors[0])
            if root.findtext('id') != 'twee.twee3-webstorm':
                errors.append('Unexpected plugin ID')
            idea = root.find('idea-version')
            if idea is None or idea.get('since-build') != '253.33813.27' or idea.get('until-build') is not None:
                errors.append('Expected reviewed minimum 253.33813.27 and no upper bound')
            dependencies = {node.text for node in root.findall('depends')}
            if not {'com.intellij.modules.platform', 'com.intellij.modules.lang', 'JavaScript'} <= dependencies:
                errors.append('Required platform/lang/JavaScript dependencies missing')
        except ET.ParseError as error:
            errors.append(f'Invalid plugin descriptor: {error}')
    if not any(b'Copyright (c) 2020 Cyrus Firheir' in value and b'Permission is hereby granted' in value for value in licenses):
        errors.append('Original MIT license/attribution is missing')
    if not any(b'Cyrus Firheir' in value for value in notices):
        errors.append('NOTICE attribution is missing')
    for name, count in classes.items():
        if count > 1 and name != 'module-info.class' and not name.endswith('/module-info.class'):
            errors.append(f'Duplicate class across bundled JARs: {name}')
    if not any(name.startswith('twee/') and name.endswith('.class') for name in classes):
        errors.append('Native Twee classes missing')
    return {'archive': str(path), 'sha256': hashlib.sha256(path.read_bytes()).hexdigest() if path.is_file() else None,
            'status': 'failed' if errors else 'passed', 'jars': jars, 'errors': errors}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('archive', type=Path, help='ZIP file or directory containing plugin ZIPs')
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    paths = sorted(args.archive.glob('*.zip')) if args.archive.is_dir() else [args.archive]
    reports = [inspect(path) for path in paths]
    result = {'status': 'passed' if reports and all(r['status'] == 'passed' for r in reports) else 'failed',
              'archives': reports, 'errors': [] if reports else ['No plugin ZIPs found']}
    text = json.dumps(result, indent=2) + '\n'
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(text, encoding='utf-8')
    print(text, end='')
    return 0 if result['status'] == 'passed' else 1


if __name__ == '__main__':
    sys.exit(main())
