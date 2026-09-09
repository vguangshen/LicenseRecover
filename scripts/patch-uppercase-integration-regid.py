from pathlib import Path

p = Path('.github/workflows/uppercase-host-windows-integration.yml')
text = p.read_text(encoding='utf-8')
old = '''                  public string getRegNo()\n                  {\n'''
new = '''                  // Exact recovered uppercase property used by LicenseRecover.NET.Modern AppReflection.GetRegNo().\n                  public string RegID\n                  {\n                      get\n                      {\n                          AssertRoot("get_RegID");\n                          return getRegNo();\n                      }\n                  }\n\n                  public string getRegNo()\n                  {\n'''
if text.count(old) != 1:
    raise SystemExit(f'expected one getRegNo fixture block, got {text.count(old)}')
text = text.replace(old, new, 1)
old_marker = "              '[FAKE_UPPER] NewRegistry BaseDirectory=',"
new_marker = "              '[FAKE_UPPER] get_RegID BaseDirectory=',"
if text.count(old_marker) != 1:
    raise SystemExit(f'expected one old marker, got {text.count(old_marker)}')
text = text.replace(old_marker, new_marker, 1)
p.write_text(text, encoding='utf-8')
print('uppercase fixture RegID property patched')
