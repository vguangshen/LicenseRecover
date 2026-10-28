"""Pure stdlib unit tests for offline FOAP/VBPD restoration input guards."""
import struct
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))
from itmc_foap_unpack_stdlib import rc4, method_length, restore
from ds0101_unpack_site import _target_members, recover_site
from inspect_ds01xx_registration_chain import call_edges
from inspect_ds01xx_registration_modes import mode_order_from_instructions


class ProtectedILTests(unittest.TestCase):
    def test_rc4_known_vector(self):
        plaintext = b'Plaintext'
        ciphertext = bytes.fromhex('BBF316E8D940AF0AD3')
        self.assertEqual(rc4(plaintext, b'Key'), ciphertext)
        self.assertEqual(rc4(ciphertext, b'Key'), plaintext)

    def test_tiny_method(self):
        self.assertEqual(method_length(b'\x06\x2a'), 2)
        with self.assertRaises(ValueError):
            method_length(b'\x0a\x2a')

    def test_fat_method_with_small_exception_section(self):
        header = struct.pack('<HHII', 0x300B, 8, 1, 0)
        body = header + b'\x2a\0\0\0' + b'\x01\x10\0\0' + b'\0' * 12
        self.assertEqual(method_length(body), len(body))
        with self.assertRaises(ValueError):
            method_length(body[:-1])

    def test_truncated_or_invalid_method(self):
        for body in [b'', b'\xff', b'\x03', b'\x0b']:
            with self.subTest(body=body), self.assertRaises(ValueError):
                method_length(body)

    def test_restore_does_not_overwrite_source(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp) / 'sample.dll'
            p.write_bytes(b'MZ' + b'\0' * 128)
            with self.assertRaises(ValueError):
                restore(p, p, None)
            self.assertEqual(p.read_bytes(), b'MZ' + b'\0' * 128)


class RegistrationCallTraceTests(unittest.TestCase):
    def test_tiny_cil_method_call_reference(self):
        # Tiny CIL: call (MethodDef token 0x06000002), ret.
        code = b'\x28\x02\x00\x00\x06\x2a'
        image = b'\x00' + bytes([(len(code) << 2) | 2]) + code

        class FakePE:
            b = image
            def rva_to_offset(self, rva):
                return rva

        class FakeMetadata:
            methods = {1: 1}
            def target_name(self, token):
                return 'Demo.RegeditMain.CheckReInfo' if token == 0x06000002 else 'unexpected'

        edges = call_edges(FakePE(), FakeMetadata(), 1)
        self.assertEqual([edge['target'] for edge in edges],
                         ['Demo.RegeditMain.CheckReInfo'])

    def test_float_operand_is_not_a_false_method_call(self):
        # ldc.r4 floating point value contains opcode 0x28, but it is data.
        code = b'\x22\x28\x00\x00\x00\x2a'
        image = b'\x00' + bytes([(len(code) << 2) | 2]) + code

        class FakePE:
            b = image
            def rva_to_offset(self, rva):
                return rva

        class FakeMetadata:
            methods = {1: 1}
            def target_name(self, token):
                return 'phantom call'

        self.assertEqual(call_edges(FakePE(), FakeMetadata(), 1), [])

    def test_registration_mode_three_priority_is_scoped_to_jump_target(self):
        # A synthetic CIL mode selector with an early unrelated CheckDog call.
        # It must trace the call sequence FROM the branch target only.
        inst = [
            (0,5,0x28,0x06000003,4),
            (5,10,0x72,0x70000001,4),
            (10,15,0x28,0x0a000001,4),
            (15,17,0x2d,0x17,1),   # brtrue.s 17+23 => 40
            (17,18,0x2a,None,0),
            (40,45,0x28,0x06000001,4),
            (45,50,0x28,0x06000002,4),
            (50,55,0x28,0x06000003,4),
        ]

        class FakeMetadata:
            def target_name(self, token):
                return {
                    0x06000001: 'ITMC.Regedit.RegeditMain.CheckNet',
                    0x06000002: 'ITMC.Regedit.RegeditMain.CheckLocalReg',
                    0x06000003: 'ITMC.Regedit.RegeditMain.CheckHASPDog',
                }.get(token, 'String.op_Equality')

        with patch('inspect_ds01xx_registration_modes.user_string',
                   side_effect=lambda meta, tok: '3' if tok == 0x70000001 else None):
            self.assertEqual(mode_order_from_instructions(inst, FakeMetadata(), '3'),
                             ['CheckNet', 'CheckLocalReg', 'CheckHASPDog'])
            self.assertEqual(mode_order_from_instructions(inst, FakeMetadata(), '1'), [])

    def test_token_bytes_in_ldstr_are_not_false_method_calls(self):
        # The operand to ldstr contains a 0x28 byte, but must not become a
        # phantom call in the static registration-chain report.
        code = b'\x72\x28\x00\x00\x70\x2a'
        image = b'\x00' + bytes([(len(code) << 2) | 2]) + code

        class FakePE:
            b = image
            def rva_to_offset(self, rva):
                return rva

        class FakeMetadata:
            methods = {1: 1}
            def target_name(self, token):
                return 'phantom'

        self.assertEqual(call_edges(FakePE(), FakeMetadata(), 1), [])


class SiteArchiveTests(unittest.TestCase):
    def make_zip(self, base, version='DS0101', duplicated=False):
        path = base / 'input.zip'
        with zipfile.ZipFile(path, 'w') as z:
            z.writestr('DS0101/config.xml',
                       '<ROOT><SystemSoft><SoftVersionID>%s</SoftVersionID></SystemSoft></ROOT>' % version)
            for name in ('ITMC.Web.dll', 'ITMC.Regedit.dll', 'itmcRegedit.dll'):
                z.writestr('DS0101/bin/' + name, b'MZ' + b'\0' * 128)
            if duplicated:
                z.writestr('copy/bin/ITMC.Web.dll', b'MZ')
            z.writestr('DS0101/.svn/pristine/x/bin/ITMC.Web.dll', b'MZ')
            z.writestr('DS0101/bin/../../outside.dll', b'OTHER')
        return path

    def test_archive_members_ignore_svn_and_unrelated_files(self):
        with tempfile.TemporaryDirectory() as tmp:
            zpath = self.make_zip(Path(tmp))
            with zipfile.ZipFile(zpath) as z:
                found = _target_members(z)
            self.assertEqual(set(found), {'ITMC.Web.dll', 'ITMC.Regedit.dll', 'itmcRegedit.dll'})

    def test_reject_duplicate_assembly(self):
        with tempfile.TemporaryDirectory() as tmp:
            zpath = self.make_zip(Path(tmp), duplicated=True)
            with zipfile.ZipFile(zpath) as z, self.assertRaises(ValueError):
                _target_members(z)

    def test_reject_wrong_product_before_writing_output(self):
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            zpath = self.make_zip(base, version='YX030201')
            out = base / 'out'
            with self.assertRaisesRegex(ValueError, 'Not a DS01xx site'):
                recover_site(zpath, out)
            self.assertFalse(out.exists())

    def test_refuse_nonempty_output(self):
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            zpath = self.make_zip(base)
            out = base / 'out'
            out.mkdir()
            (out / 'original.txt').write_text('do not overwrite')
            with self.assertRaisesRegex(ValueError, 'new or empty'):
                recover_site(zpath, out)
            self.assertEqual((out / 'original.txt').read_text(), 'do not overwrite')

    def test_failure_during_second_dll_leaves_no_partial_output(self):
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            zpath = self.make_zip(base)
            output = base / 'analysis'
            attempts = []

            def fake_restore(source, target, report):
                attempts.append(source.name)
                if len(attempts) == 2:
                    raise ValueError('synthetic invalid second assembly')
                target.write_bytes(b'partial IL output')
                report.write_text('{}')
                return {'restored_method_count': 1}

            with patch('ds0101_unpack_site.restore', side_effect=fake_restore):
                with self.assertRaisesRegex(ValueError, 'invalid second assembly'):
                    recover_site(zpath, output)
            self.assertEqual(len(attempts), 2)
            self.assertFalse(output.exists(),
                             'an incomplete analysis bundle must never be published')
            self.assertFalse(any(p.name.startswith('.ds01xx-stage-') for p in base.iterdir()),
                             'temporary partial results must be cleaned up')

    def test_refuse_output_inside_site(self):
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp) / 'DS0101'
            base.mkdir()
            with self.assertRaisesRegex(ValueError, 'must not be inside'):
                recover_site(base, base / 'diagnostics')


if __name__ == '__main__':
    unittest.main()
