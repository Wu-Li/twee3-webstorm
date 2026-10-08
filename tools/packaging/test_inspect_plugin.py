import io
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile
from inspect_plugin import inspect

DESCRIPTOR = b'''<idea-plugin><id>twee.twee3-webstorm</id>
<idea-version since-build="253.33813.27"/>
<depends>com.intellij.modules.platform</depends><depends>com.intellij.modules.lang</depends>
<depends>JavaScript</depends></idea-plugin>'''


class PackageInspectionTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.path = Path(self.temp.name) / 'plugin.zip'
        self.entries = {'META-INF/plugin.xml': DESCRIPTOR,
                        'META-INF/LICENSE': b'Copyright (c) 2020 Cyrus Firheir\nPermission is hereby granted',
                        'META-INF/NOTICE': b'Inherited work by Cyrus Firheir',
                        'twee/Example.class': b'synthetic test class'}

    def tearDown(self):
        self.temp.cleanup()

    def package(self, extra=None, second=None):
        jar = io.BytesIO()
        with zipfile.ZipFile(jar, 'w') as archive:
            for name, value in self.entries.items():
                archive.writestr(name, value)
        with zipfile.ZipFile(self.path, 'w') as archive:
            archive.writestr('twee/lib/native.jar', jar.getvalue())
            for name, value in (extra or {}).items():
                archive.writestr(name, value)
            if second:
                other = io.BytesIO()
                with zipfile.ZipFile(other, 'w') as nested:
                    nested.writestr(second, b'duplicate')
                archive.writestr('twee/lib/other.jar', other.getvalue())
        return inspect(self.path)

    def test_minimal_native_package(self):
        report = self.package()
        self.assertEqual('passed', report['status'])
        self.assertEqual(64, len(report['sha256']))

    def test_shaded_kotlin_and_coroutines(self):
        for name in ['kotlin/Unit.class', 'kotlinx/coroutines/Job.class']:
            self.entries[name] = b'runtime'
            self.assertEqual('failed', self.package()['status'])
            del self.entries[name]

    def test_duplicate_classes_across_jars(self):
        report = self.package(second='twee/Example.class')
        self.assertTrue(any('Duplicate class' in error for error in report['errors']))

    def test_legacy_assets_and_runtime_jars(self):
        for name in ['twee/dist/main.js', 'twee/node_modules/package.json', 'twee/storyformats/harlowe/format.js', 'twee/lib/kotlin-stdlib.jar']:
            self.assertEqual('failed', self.package({name: b'legacy'})['status'])

    def test_missing_notice_and_wrong_minimum(self):
        del self.entries['META-INF/LICENSE']
        self.entries['META-INF/plugin.xml'] = DESCRIPTOR.replace(b'253.33813.27', b'241')
        errors = self.package()['errors']
        self.assertTrue(any('license' in value for value in errors))
        self.assertTrue(any('minimum' in value for value in errors))

    def test_invalid_archive_and_unsafe_path(self):
        self.path.write_bytes(b'not a ZIP')
        self.assertEqual('failed', inspect(self.path)['status'])
        self.assertEqual('failed', self.package({'../outside.txt': b'no extraction'})['status'])
        self.assertFalse((Path(self.temp.name).parent / 'outside.txt').exists())

    def test_empty_distribution_fails_and_records_report(self):
        report = Path(self.temp.name) / 'report.json'
        result = subprocess.run([sys.executable, str(Path(__file__).with_name('inspect_plugin.py')),
                                 self.temp.name, '--report', str(report)], capture_output=True, text=True)
        self.assertEqual(1, result.returncode)
        self.assertIn('No plugin ZIPs found', report.read_text())


if __name__ == '__main__':
    unittest.main()
