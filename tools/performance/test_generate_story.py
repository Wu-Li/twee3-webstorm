import hashlib
import re
import tempfile
import unittest
from pathlib import Path
from generate_story import generate


class WorkloadTest(unittest.TestCase):
    def test_reproducible_graph_and_manifest(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            first = generate(root / 'one', 3, 4)
            second = generate(root / 'two', 3, 4)
            self.assertEqual(first, second)
            sources = list((root / 'one/story').glob('*.tw')) + list((root / 'one/story').glob('*.twee'))
            text = '\n'.join(path.read_text() for path in sources)
            names = re.findall(r'^:: ([^\[\n]+)', text, re.M)
            self.assertEqual(first['selected_passages'], len(names))
            targets = re.findall(r'->(P\d+)\]\]', text)
            self.assertEqual(first['literal_links'], len(targets))
            self.assertTrue(set(targets).issubset({name.strip() for name in names}))
            for relative, digest in first['sha256'].items():
                self.assertEqual(digest, hashlib.sha256((root / 'one' / relative).read_bytes()).hexdigest())
            self.assertTrue((root / 'one/other-story/duplicate.tw').exists())
            self.assertTrue((root / 'one/story/dist/stale.tw').exists())

    def test_existing_directory_is_untouched(self):
        with tempfile.TemporaryDirectory() as temp:
            marker = Path(temp) / 'keep.txt'
            marker.write_text('unrelated work')
            with self.assertRaises(FileExistsError):
                generate(Path(temp), 1, 2)
            self.assertEqual('unrelated work', marker.read_text())
            self.assertEqual([marker], list(Path(temp).iterdir()))

    def test_invalid_sizes_create_nothing(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'new'
            with self.assertRaises(ValueError):
                generate(path, 0, 2)
            self.assertFalse(path.exists())


if __name__ == '__main__':
    unittest.main()
