#!/usr/bin/env python3
"""Refresh shipped compatibility JARs without changing bundled build inputs.

The old JARs still supply third-party dependencies and resources. Their own
classes must come from the same compilation as the runtime overlay, including
when users invoke a compatibility JAR directly with ``java -jar``.
"""

from __future__ import annotations

import argparse
import hashlib
import io
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo


FIXED_TIME = (1980, 1, 1, 0, 0, 0)
CORE_ENTRY = "LicenseRecover.jar"
NATIVE_HELPER_ENTRY = "LicenseRecover.NET/LicenseRecover.NET.exe"
CLASS_MANIFEST = "META-INF/LicenseRecover-classes.sha256"


def class_family(name: str) -> str:
    return name[:-6].split("$", 1)[0]


def compiled_classes(directory: Path) -> dict[str, bytes]:
    classes = {
        path.relative_to(directory).as_posix(): path.read_bytes()
        for path in sorted(directory.rglob("*.class"))
        if path.is_file()
    }
    for required in ("LicenseRecover.class", "LicenseRecoverGUI.class",
                     "NetRemover.class", "SafeNetRemoverCLI.class"):
        if required not in classes:
            raise ValueError(f"Missing current compatibility class: {required}")
    if any(not data.startswith(b"\xca\xfe\xba\xbe") for data in classes.values()):
        raise ValueError("Compiled class directory contains an invalid class file")
    return classes


def refreshed_jar(source: Path, classes: dict[str, bytes],
                  replacements: dict[str, bytes] | None = None) -> bytes:
    families = {class_family(name) for name in classes}
    entries: dict[str, bytes] = {}
    with ZipFile(source) as archive:
        for entry in archive.infolist():
            name = entry.filename
            if name in entries:
                raise ValueError(f"Duplicate entry in {source.name}: {name}")
            # Remove the full old class family, including anonymous/inner
            # classes that no longer exist in the current GUI or core source.
            if name.endswith(".class") and class_family(name) in families:
                continue
            entries[name] = archive.read(entry)
    if "META-INF/MANIFEST.MF" not in entries:
        raise ValueError(f"Missing manifest in {source.name}")
    for name, data in (replacements or {}).items():
        if name not in entries:
            raise ValueError(f"Missing embedded compatibility resource: {name}")
        entries[name] = data
    entries.update(classes)
    entries[CLASS_MANIFEST] = "".join(
        f"{hashlib.sha256(data).hexdigest()}  {name}\n"
        for name, data in sorted(classes.items())
    ).encode("ascii")

    buffer = io.BytesIO()
    with ZipFile(buffer, "w", compression=ZIP_DEFLATED, compresslevel=9) as archive:
        for name in sorted(entries, key=lambda value: (value != "META-INF/MANIFEST.MF", value)):
            info = ZipInfo(name, FIXED_TIME)
            info.compress_type = ZIP_DEFLATED
            info.external_attr = (0o40755 if name.endswith("/") else 0o100644) << 16
            archive.writestr(info, entries[name])
    return buffer.getvalue()


def package(core_source: Path, gui_source: Path, classes_dir: Path,
            native_helper: Path, output_dir: Path) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    core_target = output_dir / CORE_ENTRY
    gui_target = output_dir / "LicenseRecoverGUI.jar"
    inputs = {core_source.resolve(), gui_source.resolve(), native_helper.resolve()}
    if core_target.resolve() in inputs or gui_target.resolve() in inputs:
        raise ValueError("Compatibility JAR outputs must not overwrite bundled inputs")
    classes = compiled_classes(classes_dir)
    core = refreshed_jar(core_source, classes)
    gui = refreshed_jar(gui_source, classes, {
        CORE_ENTRY: core,
        NATIVE_HELPER_ENTRY: native_helper.read_bytes(),
    })
    core_target.write_bytes(core)
    gui_target.write_bytes(gui)
    print(f"Refreshed compatibility JARs with {len(classes)} current classes: {output_dir}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--core-source", required=True, type=Path)
    parser.add_argument("--gui-source", required=True, type=Path)
    parser.add_argument("--classes-dir", required=True, type=Path)
    parser.add_argument("--native-helper", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    args = parser.parse_args()
    package(args.core_source, args.gui_source, args.classes_dir,
            args.native_helper, args.output_dir)


if __name__ == "__main__":
    main()
