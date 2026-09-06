#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
APP_DETECTOR = ROOT / "src/main/java/AppDetector.java"
AUTO = ROOT / "src/main/java/LicenseRecoverModernGUIAutoRecovery.java"
TEST = ROOT / "src/test/java/RefactorSmokeTest.java"


def replace_once(text, old, new, desc):
    if old not in text:
        raise RuntimeError("Unable to patch %s; source anchor not found" % desc)
    return text.replace(old, new, 1)


app = APP_DETECTOR.read_text(encoding="utf-8")
if "classes/config.xml fallback" not in app:
    old = '        return readSoftVersionXml(new File(appRoot, "data" + File.separator + "config.xml"));'
    new = '''        String dataVersion = readSoftVersionXml(new File(appRoot, "data" + File.separator + "config.xml"));
        if (dataVersion != null) return dataVersion;
        // XMT01xx generation keeps SystemSoft in WEB-INF/classes/config.xml.
        String classesVersion = readSoftVersionXml(new File(appRoot, "WEB-INF" + File.separator
                + "classes" + File.separator + "config.xml")); // classes/config.xml fallback
        return classesVersion;'''
    app = replace_once(app, old, new, "AppDetector XMT classes config fallback")
APP_DETECTOR.write_text(app, encoding="utf-8", newline="\n")

auto = AUTO.read_text(encoding="utf-8")
if "XMT classes/config.xml fallback" not in auto:
    old = '        return readVersionFile(new File(root,"data"+File.separator+"config.xml"));'
    new = '''        String dataVersion=readVersionFile(new File(root,"data"+File.separator+"config.xml"));
        if(dataVersion!=null)return dataVersion;
        // XMT classes/config.xml fallback
        return readVersionFile(new File(root,"WEB-INF"+File.separator+"classes"+File.separator+"config.xml"));'''
    auto = replace_once(auto, old, new, "one-click XMT classes config fallback")
AUTO.write_text(auto, encoding="utf-8", newline="\n")

test = TEST.read_text(encoding="utf-8")
if "AppDetector reads XMT0107 classes config" not in test:
    anchor = '        check("XMT01".equals(LicenseRecover.productMainFor("XMT0107")), "XMT0107 maps to XMT01 ProName");'
    new = anchor + '''
        AppInfo xmtDetected = AppDetector.detect(xmtRoot.toFile());
        check("XMT0107".equals(xmtDetected.softVersionId), "AppDetector reads XMT0107 classes config");
        LicenseRecoverModernGUIAutoRecovery.Detection xmtOneClick =
                LicenseRecoverModernGUIAutoRecovery.detect(xmtRoot.toFile());
        check("XMT0107".equals(xmtOneClick.versionId), "one-click detector reads XMT0107 classes config");'''
    test = replace_once(test, anchor, new, "XMT detection regression")
TEST.write_text(test, encoding="utf-8", newline="\n")
print("Java detector fallback patched for XMT0107")
