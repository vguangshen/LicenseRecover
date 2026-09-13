from pathlib import Path
p = Path('src/test/java/RefactorSmokeTest.java')
text = p.read_text(encoding='utf-8')
old = '''            writeZipEntry(zout, "VERSION.txt", "1.1.1\\n");
            writeZipEntry(zout, "runtime.txt", "new\\n");
            writeZipEntry(zout, "nested/new.txt", "created\\n");
'''
new = '''            writeZipEntry(zout, "VERSION.txt", "1.1.1\\n");
            writeZipEntry(zout, "runtime.txt", "new\\n");
            writeZipEntry(zout, "nested/new.txt", "created\\n");
            writeZipEntry(zout, "LicenseRecoverRuntime.jar", "runtime-current\\n");
            writeZipEntry(zout, "LicenseRecoverOverlay.jar", "runtime-current\\n");
            writeZipEntry(zout, "LicenseRecover.jar", "core-current\\n");
            writeZipEntry(zout, "LicenseRecoverGUI.jar", "gui-current\\n");
            writeZipEntry(zout, "LicenseRecoverGUI.exe", "launcher-current\\n");
'''
if text.count(old) != 1:
    raise SystemExit(f'updater fixture match count={text.count(old)}')
text = text.replace(old, new, 1)
old2 = '''        check(Files.isRegularFile(updateInstall.resolve("nested/new.txt")),
                "updater adds new runtime files");
'''
new2 = old2 + '''        check(Files.isRegularFile(updateInstall.resolve("LicenseRecoverRuntime.jar"))
                        && Arrays.equals(Files.readAllBytes(updateInstall.resolve("LicenseRecoverRuntime.jar")),
                                Files.readAllBytes(updateInstall.resolve("LicenseRecoverOverlay.jar"))),
                "updater installs the transition-safe runtime jar before advancing version metadata");
'''
if text.count(old2) != 1:
    raise SystemExit(f'updater assertion match count={text.count(old2)}')
text = text.replace(old2, new2, 1)
p.write_text(text, encoding='utf-8')
print('patched updater smoke fixture')
