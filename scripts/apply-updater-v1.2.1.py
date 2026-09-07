#!/usr/bin/env python3
import base64
import hashlib
import io
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ROOT / "scripts"
WORKFLOW = ROOT / ".github" / "workflows" / "apply-updater-v1.2.1.yml"

EXPECTED = {
    "src/main/java/LicenseRecoverModernGUILauncher.java": "1d0443f23120a7b5120dde582a77e1e3437629d23baef71aede0643064a88ab9",
    "run_gui.bat": "163e08f6f0c2e8dd05efe5bf4ef72780247faef0a976011fe6413ab02864c48a",
    "scripts/package-native-launcher.ps1": "1cf49be238c523df3cb54c8e08fad32f31d9b62f5fab8213888e7ee74b73807b",
    "README.md": "cf08eb3ec9c212cba092eba1b4bbc0b881340d411b78cee7d504f36df10cc50f",
    "README.txt": "a4a6e7d843bbe0ee2221200003deedcbb36ad22e1328d4fb81a048410610cd67",
    "CHANGELOG.md": "0b58bcf5356acd82921a1317d439158e528476e015c0dee43618bf7ae4d73916",
    "release-notes/v1.2.1.md": "d65f41b327ece78f1c0d6918a82cb9627724843df463900b06484f35e8cd12f8",
    "VERSION.txt": "6cf4e084b47f33c9b02ef79279d157833868f8f70514169a768be353ee328fea",
}


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> None:
    parts = sorted(SCRIPTS.glob(".v121-payload-*"))
    if len(parts) != 5:
        raise SystemExit(f"expected 5 payload chunks, found {len(parts)}")
    encoded = "".join(p.read_text(encoding="ascii") for p in parts)
    payload = base64.b64decode(encoded, validate=True)
    with zipfile.ZipFile(io.BytesIO(payload), "r") as zf:
        names = set(zf.namelist())
        if names != set(EXPECTED):
            raise SystemExit(f"payload file set mismatch: {sorted(names)}")
        for info in zf.infolist():
            target = (ROOT / info.filename).resolve()
            if ROOT.resolve() not in target.parents:
                raise SystemExit(f"unsafe payload path: {info.filename}")
        zf.extractall(ROOT)

    for rel, expected in EXPECTED.items():
        actual = sha256(ROOT / rel)
        if actual != expected:
            raise SystemExit(f"sha256 mismatch for {rel}: {actual}")

    for part in parts:
        part.unlink()
    Path(__file__).unlink()
    if WORKFLOW.exists():
        WORKFLOW.unlink()

    print("v1.2.1 sources applied and verified")


if __name__ == "__main__":
    main()
