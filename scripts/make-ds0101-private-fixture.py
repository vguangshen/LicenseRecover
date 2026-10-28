#!/usr/bin/env python3
"""Make a private-only DS0101 DLL-loader test ZIP from an original site ZIP.

Contains original proprietary DLL/EXE files. NEVER upload the result to a
public GitHub repository, Actions artifact, release, or issue attachment.
Original XML, databases, passwords and registration history are NOT copied.
The generated site is not suitable for deployment or actual registration.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import zipfile
from pathlib import Path

MAX_FILES = 256
MAX_ASSEMBLY_BYTES = 40 * 1024 * 1024
NEEDED = {'ITMC.Web.dll', 'ITMC.Regedit.dll', 'itmcRegedit.dll'}
CONFIG = (b'<ROOT><reg><regType>3</regType><regName></regName>'
          b'<Service>http://127.0.0.1:9/disabled</Service></reg>'
          b'<SystemSoft><SoftVersionID>DS0101</SoftVersionID></SystemSoft></ROOT>\n')
REGISTER = b'<ROOT><reg><regType>1</regType></reg></ROOT>\n'
WEBCONFIG = (b'<configuration><system.web>'
             b'<compilation targetFramework="4.5"/>'
             b'<httpRuntime targetFramework="4.5"/>'
             b'</system.web></configuration>\n')


def build(source: Path, destination: Path):
    if destination.exists():
        raise ValueError('Refusing to replace a private test fixture')
    if source.resolve() == destination.resolve():
        raise ValueError('Input/output files must differ')
    with zipfile.ZipFile(source) as archive:
        candidates = [member for member in archive.infolist()
                      if re.fullmatch(r'DS0101/bin/[^/]+\.(?:dll|exe)',
                                      member.filename, re.I)]
        if len(candidates) > MAX_FILES:
            raise ValueError('Unexpectedly many managed/native binaries')
        if len({member.filename.lower() for member in candidates}) != len(candidates):
            raise ValueError('Duplicate assembly paths')
        if not NEEDED.issubset({i.filename.rsplit('/', 1)[1] for i in candidates}):
            raise ValueError('Missing DS0101 registration assemblies')
        version = archive.read('DS0101/config.xml')
        if not re.search(rb'<SoftVersionID\s*>\s*DS0101\s*</SoftVersionID\s*>', version, re.I):
            raise ValueError('Not a DS0101 website')
        if any(i.file_size > MAX_ASSEMBLY_BYTES for i in candidates):
            raise ValueError('Oversized assembly')
        metadata = {'purpose': 'PRIVATE Windows CLR diagnostic only',
                    'original_site_xml_copied': False,
                    'license_validated': False, 'assemblies': []}
        with zipfile.ZipFile(destination, 'x', compression=zipfile.ZIP_DEFLATED,
                             compresslevel=6) as output:
            for member in sorted(candidates, key=lambda i: i.filename.lower()):
                name = member.filename.rsplit('/', 1)[1]
                data = archive.read(member)
                output.writestr('DS0101/bin/' + name, data)
                metadata['assemblies'].append({
                    'name': name, 'size': len(data),
                    'sha256': hashlib.sha256(data).hexdigest()})
            output.writestr('DS0101/config.xml', CONFIG)
            output.writestr('DS0101/Register.xml', REGISTER)
            output.writestr('DS0101/web.config', WEBCONFIG)
            output.writestr('DS0101/PRIVATE_TEST_ONLY.txt',
                            'Contains proprietary binaries. PRIVATE TEST ONLY. '
                            'Do not publish or deploy.\n')
            output.writestr('DS0101/private-fixture-manifest.json',
                            json.dumps(metadata, indent=2) + '\n')
    return {'output': str(destination), 'count': len(candidates),
            'bytes': destination.stat().st_size, 'private': True}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('original_zip', type=Path)
    parser.add_argument('private_test_zip', type=Path)
    args = parser.parse_args()
    print(json.dumps(build(args.original_zip, args.private_test_zip)))


if __name__ == '__main__':
    main()
