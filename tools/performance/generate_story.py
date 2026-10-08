#!/usr/bin/env python3
"""Deterministic native-IDE workload, not an IDE performance measurement."""
import argparse
import hashlib
import json
from pathlib import Path


def generate(destination: Path, files: int = 200, passages: int = 50):
    if files < 1 or passages < 2:
        raise ValueError('Use at least one file and two passages per file')
    # Never replace an existing project or a prior measurement's inputs.
    destination.mkdir(parents=True, exist_ok=False)
    total = files * passages
    contents = {
        'story/meta.tw': ':: StoryTitle\nPerformance fixture\n\n:: StoryData\n'
        '{"ifid":"6877A006-1534-4C8C-8142-B15EFC73A770","format":"Harlowe",'
        '"format-version":"3.3.9","start":"P000000"}\n\n'
        ':: Boot [startup]\n(set: $visits to 0)\n'
        '(set: $greet to (macro: [(output-data: "Hello")]))\n',
        'other-story/duplicate.tw': ':: P000000\nWrong story; must not be returned.\n',
        'story/dist/stale.tw': ':: P000000\nGenerated output; must be excluded.\n',
        'story/storyformats/format.js': '/* excluded installation fixture */\n',
        'story/site.js': 'window.tweePerformanceFixture = true;\n',
        'story/site.css': 'tw-story { border-top: 1px solid purple; }\n',
    }
    for file_index in range(files):
        sections = []
        for local in range(passages):
            number = file_index * passages + local
            # A ring plus a long-distance edge provides cycles and cross-file destinations.
            next_number = (number + 1) % total
            far = (number + passages) % total
            sections.append(f':: P{number:06d} [group{file_index % 10} benchmark]\n'
                            f'(set: $visits to $visits + 1)(print: $visits)\n'
                            f'($greet:)\n[[Next->P{next_number:06d}]] [[Across->P{far:06d}]]\n')
        contents[f'story/chapter-{file_index:04d}.twee'] = '\n'.join(sections)
    hashes = {}
    for relative, text in sorted(contents.items()):
        path = destination / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        data = text.encode('utf-8')
        path.write_bytes(data)
        hashes[relative] = hashlib.sha256(data).hexdigest()
    manifest = {'schema': 1, 'generator': 'tools/performance/generate_story.py',
                'chapter_files': files, 'passages_per_file': passages,
                'selected_passages': total + 3, 'literal_links': total * 2,
                'selected_scope': 'story', 'other_scope': 'other-story',
                'output_directory': 'story/dist', 'sha256': hashes}
    (destination / 'manifest.json').write_text(json.dumps(manifest, indent=2, sort_keys=True) + '\n', encoding='utf-8')
    return manifest


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('destination', type=Path)
    parser.add_argument('--files', type=int, default=200)
    parser.add_argument('--passages', type=int, default=50)
    args = parser.parse_args()
    manifest = generate(args.destination, args.files, args.passages)
    print(json.dumps({key: value for key, value in manifest.items() if key != 'sha256'}, sort_keys=True))
