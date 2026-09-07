#!/usr/bin/env python3
from pathlib import Path
p=Path(__file__).resolve().parents[1]/'src/main/java/LicenseRecoverGUI.java'
s=p.read_text(encoding='utf-8')
fixes={
'    static void run(ActionEvent e) {    static void run(ActionEvent e) {':'    static void run(ActionEvent e) {',
'    static void copyCode(ActionEvent e) {    static void copyCode(ActionEvent e) {':'    static void copyCode(ActionEvent e) {',
'     * 方式二: 生成离线授权码。    /**\n     * 方式二: 生成离线授权码。':'     * 方式二: 生成离线授权码。',
}
for old,new in fixes.items():
    if old not in s:
        raise SystemExit('expected cleanup anchor missing: '+old[:50])
    s=s.replace(old,new,1)
for bad in ['static void run(ActionEvent e) {    static void run', 'static void copyCode(ActionEvent e) {    static void copyCode']:
    if bad in s: raise SystemExit('duplicate signature remains')
p.write_text(s,encoding='utf-8',newline='\n')
print('Legacy GUI duplicate signatures cleaned.')
