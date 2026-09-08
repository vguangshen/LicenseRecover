#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
p = root / 'src/main/java/LicenseRecoverModernGUIAutoRecovery.java'
s = p.read_text(encoding='utf-8')
start = s.index('    private static String jsonStringField(String json, String name) {')
end = s.index('    private static String normalizeProductCsv(String raw) {', start)
new = '''    private static String jsonStringField(String json, String name) {\n        if (json == null || name == null) return null;\n        String marker = "\\\"" + name + "\\\"";\n        int key = json.indexOf(marker);\n        if (key < 0) return null;\n        int colon = json.indexOf(':', key + marker.length());\n        if (colon < 0) return null;\n        int begin = json.indexOf('\\\"', colon + 1);\n        if (begin < 0) return null;\n        int end = json.indexOf('\\\"', begin + 1);\n        if (end < 0) return null;\n        return json.substring(begin + 1, end).trim();\n    }\n\n'''
p.write_text(s[:start] + new + s[end:], encoding='utf-8')
print('fixed jsonStringField parser')
