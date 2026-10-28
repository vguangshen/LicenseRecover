#!/usr/bin/env python3
"""Offline, read-only DS01xx assembly-restoration batch driver.

Accepts the original ASP.NET site directory or its ZIP archive and restores
three protected .NET assemblies into a *separate* analysis directory.
Never executes site code, touches source files, or modifies license checks.
"""
from __future__ import annotations

import argparse
import json
import re
import shutil
import tempfile
import zipfile
from pathlib import Path
from itmc_foap_unpack_stdlib import restore
from inspect_ds01xx_registration_chain import inspect_web_assembly

ASSEMBLIES = ('ITMC.Web.dll', 'ITMC.Regedit.dll', 'itmcRegedit.dll')
MAX_DLL_SIZE = 32 * 1024 * 1024


def _target_members(z: zipfile.ZipFile):
    result = {}
    for name in ASSEMBLIES:
        suffix = '/bin/' + name.lower()
        matches = [item for item in z.infolist()
                   if not item.is_dir()
                   and not item.filename.replace('\\', '/').lower().startswith('__macosx/')
                   and '/.svn/' not in item.filename.replace('\\', '/').lower()
                   and ('/' + item.filename.replace('\\', '/').lower().lstrip('/')).endswith(suffix)]
        if len(matches) != 1:
            raise ValueError('Expected exactly one bin/%s in ZIP; found %s' % (name, len(matches)))
        if matches[0].file_size > MAX_DLL_SIZE:
            raise ValueError('DLL unexpectedly large: ' + name)
        result[name] = matches[0]
    return result


def _read_version_from_zip(z: zipfile.ZipFile, members):
    prefix = members['ITMC.Web.dll'].filename.replace('\\', '/').rsplit('/bin/', 1)[0]
    candidate = prefix + '/config.xml'
    try:
        info = z.getinfo(candidate)
        if info.file_size > 1024 * 1024:
            return None
        data = z.read(info)
    except KeyError:
        return None
    return _extract_version(data)


def _extract_version(data):
    # Never publish config values or authorisation fields in the manifest.
    try:
        decoded = data.decode('utf-8-sig')
    except UnicodeDecodeError:
        return None
    match = re.search(r'<SoftVersionID\s*>\s*([A-Za-z0-9_-]{1,32})\s*</SoftVersionID\s*>', decoded, re.I)
    return match.group(1) if match else None


def _site_inputs(source: Path, scratch: Path):
    version = None
    if source.is_file() and source.suffix.lower() == '.zip':
        with zipfile.ZipFile(source) as z:
            members = _target_members(z)
            version = _read_version_from_zip(z, members)
            for name, info in members.items():
                # Extraction uses hard-coded basenames and never the ZIP member path.
                with z.open(info) as inp, (scratch / name).open('wb') as out:
                    shutil.copyfileobj(inp, out, 1024 * 1024)
    elif source.is_dir():
        bin_dir = source if source.name.lower() == 'bin' else source / 'bin'
        site_root = bin_dir.parent
        for name in ASSEMBLIES:
            matches = [p for p in bin_dir.iterdir()
                       if p.is_file() and p.name.lower() == name.lower()]
            if len(matches) != 1:
                raise ValueError('Expected exactly one %s in %s' % (name, bin_dir))
            if matches[0].stat().st_size > MAX_DLL_SIZE:
                raise ValueError('DLL unexpectedly large: ' + name)
            shutil.copyfile(matches[0], scratch / name)
        config = site_root / 'config.xml'
        if config.is_file() and config.stat().st_size <= 1024 * 1024:
            version = _extract_version(config.read_bytes())
    else:
        raise ValueError('Expected a site directory or ZIP file')
    return version


def recover_site(source: Path, output: Path):
    if output.exists() and any(output.iterdir()):
        raise ValueError('Output directory must be new or empty')
    if not source.exists():
        raise ValueError('Input not found: ' + str(source))
    if source.is_dir() and (source.resolve() == output.resolve()
                            or source.resolve() in output.resolve().parents):
        raise ValueError('Output must not be inside the input site')
    with tempfile.TemporaryDirectory(prefix='ds01xx-foap-') as temp:
        scratch = Path(temp)
        version = _site_inputs(source, scratch)
        # Validate ALL inputs before creating any output.
        if version and not re.fullmatch(r'DS01\d{2}', version, re.I):
            raise ValueError('Not a DS01xx site: ' + version)
        output.mkdir(parents=True, exist_ok=True)
        summaries = []
        for name in ASSEMBLIES:
            target = output / (name[:-4] + '.unpacked.dll')
            report = output / (name[:-4] + '.report.json')
            summaries.append(restore(scratch / name, target, report))
        try:
            chain = inspect_web_assembly(output / 'ITMC.Web.unpacked.dll')
        except ValueError as ex:
            # Other DS01xx builds may not expose the identical names or metadata.
            # In that case, preserve the recovered DLLs and explicitly mark
            # the hybrid registration chain as unverified, not absent.
            chain = {'static_dual_registration_chain_evidenced': False,
                     'inspection_error': str(ex)[:240],
                     'runtime_licensing_tested': False}
        manifest = {
            'source': source.name,
            'registration_chain': chain,
            'soft_version_id': version,
            'restored_methods_total': sum(x['restored_method_count'] for x in summaries),
            'assemblies': summaries,
            'registration_changed': False,
            'runtime_tested': False,
            'note': 'For offline source inspection only; not a license patch or IIS deployment bundle.',
        }
        (output / 'manifest.json').write_text(
            json.dumps(manifest, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
        return manifest


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('source', type=Path, help='Original DS01xx site folder or .zip')
    p.add_argument('output', type=Path, help='New separate output folder')
    args = p.parse_args()
    print(json.dumps(recover_site(args.source, args.output),
                     indent=2, ensure_ascii=False))


if __name__ == '__main__':
    main()
