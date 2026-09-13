from pathlib import Path

path = Path("README.md")
text = path.read_text(encoding="utf-8")
old = "当前稳定版本：**v1.2.45**"
new = "当前稳定版本：**v1.2.46**"
count = text.count(old)
if count != 1:
    raise SystemExit(f"expected one stable-version marker, got {count}")
path.write_text(text.replace(old, new, 1), encoding="utf-8")
