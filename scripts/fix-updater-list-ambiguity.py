#!/usr/bin/env python3
from pathlib import Path
p = Path('src/main/java/LicenseRecoverModernGUILauncher.java')
s = p.read_text(encoding='utf-8')
old = s
s = s.replace('        List<NetworkRoute> routes = networkRoutes();', '        java.util.List<NetworkRoute> routes = networkRoutes();')
s = s.replace('    private static List<NetworkRoute> networkRoutes() {\n        List<NetworkRoute> routes = new ArrayList<NetworkRoute>();', '    private static java.util.List<NetworkRoute> networkRoutes() {\n        java.util.List<NetworkRoute> routes = new ArrayList<NetworkRoute>();')
if s == old:
    raise SystemExit('no updater List ambiguity replacements made')
p.write_text(s, encoding='utf-8')
print('fixed java.awt.List/java.util.List ambiguity')
