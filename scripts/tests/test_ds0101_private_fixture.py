"""No vendor binaries: verify the private test bundle strips site secrets."""
import hashlib
import importlib.util
import json
import tempfile
import unittest
import zipfile
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / 'make-ds0101-private-fixture.py'
spec = importlib.util.spec_from_file_location('private_ds0101_fixture', SCRIPT)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class PrivateFixtureTests(unittest.TestCase):
    def test_only_assemblies_and_sanitized_configs_are_exported(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            original = root / 'original.zip'
            target = root / 'private.zip'
            with zipfile.ZipFile(original, 'w') as z:
                for name in ('ITMC.Web.dll', 'ITMC.Regedit.dll', 'itmcRegedit.dll',
                             'Other.Dependency.dll'):
                    z.writestr('DS0101/bin/' + name, ('STUB_' + name).encode())
                z.writestr('DS0101/config.xml',
                           '<ROOT><SoftVersionID>DS0101</SoftVersionID>'
                           '<regName>DO_NOT_PUBLISH_SECRET</regName></ROOT>')
                z.writestr('DS0101/Register.xml',
                           '<ROOT><history>DO_NOT_PUBLISH_SECRET</history></ROOT>')
                z.writestr('DS0101/web.config',
                           '<connectionStrings password="DO_NOT_PUBLISH_SECRET"/>')
                z.writestr('DS0101/notRegFile.xml',
                           '<ROOT>DO_NOT_PUBLISH_SECRET</ROOT>')
                z.writestr('DS0101/local.sqlite', b'DO_NOT_PUBLISH_SECRET')

            result = module.build(original, target)
            self.assertEqual(result['count'], 4)
            raw = target.read_bytes()
            self.assertNotIn(b'DO_NOT_PUBLISH_SECRET', raw)
            with zipfile.ZipFile(target) as z:
                names = set(z.namelist())
                self.assertEqual(len(names), 9)
                self.assertNotIn('DS0101/notRegFile.xml', names)
                self.assertNotIn('DS0101/local.sqlite', names)
                self.assertEqual(z.read('DS0101/config.xml'), module.CONFIG)
                self.assertEqual(z.read('DS0101/Register.xml'), module.REGISTER)
                self.assertEqual(z.read('DS0101/web.config'), module.WEBCONFIG)
                manifest = json.loads(z.read('DS0101/private-fixture-manifest.json'))
                self.assertFalse(manifest['original_site_xml_copied'])
                self.assertFalse(manifest['license_validated'])
                self.assertEqual(len(manifest['assemblies']), 4)
            with self.assertRaises(ValueError):
                module.build(original, target)

    def test_missing_registration_dll_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            original = Path(tmp) / 'bad.zip'
            with zipfile.ZipFile(original, 'w') as z:
                z.writestr('DS0101/config.xml', '<SoftVersionID>DS0101</SoftVersionID>')
                z.writestr('DS0101/bin/ITMC.Web.dll', 'FAKE')
            with self.assertRaisesRegex(ValueError, 'Missing DS0101 registration'):
                module.build(original, Path(tmp) / 'private.zip')


if __name__ == '__main__':
    unittest.main()
