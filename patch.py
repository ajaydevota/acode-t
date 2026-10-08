#!/usr/bin/env python3
"""Fuses the Termux terminal + our simple editor into the Acode Cordova project.

phase "pre"  : run before `npm run setup`  -> rename the app to com.termux / Termux
phase "post" : run after  `npm run setup`  -> add our Java + res + manifest + gradle changes

Usage:  python3 patch.py <acode-src-dir> pre|post
"""
import os
import re
import shutil
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
OVERLAY = os.path.join(HERE, "overlay", "app", "src", "main")

root = sys.argv[1]
phase = sys.argv[2] if len(sys.argv) > 2 else "post"

NEW_ID = "com.termux"
NEW_NAME = "Termux"


def read(p):
    with open(p, encoding="utf-8") as f:
        return f.read()


def write(p, s):
    with open(p, "w", encoding="utf-8") as f:
        f.write(s)


def fail(msg):
    raise SystemExit("PATCH ERROR: " + msg)


# --------------------------------------------------------------------- pre
if phase == "pre":
    for rel in ("utils/setup.js", "utils/config.js"):
        p = os.path.join(root, rel)
        if not os.path.exists(p):
            print("skip (missing):", rel)
            continue
        s = read(p)
        s2 = s.replace('ID_PAID = "com.foxdebug.acode"', 'ID_PAID = "com.termux"')
        s2 = s2.replace("ID_PAID = 'com.foxdebug.acode'", "ID_PAID = 'com.termux'")
        if s2 != s:
            write(p, s2)
            print("patched", rel)
        else:
            print("no ID_PAID match in", rel)

    cfg = os.path.join(root, "config.xml")
    s = read(cfg)
    s2 = re.sub(r'(<widget[^>]*\sid=")[^"]+(")', r"\1" + NEW_ID + r"\2", s, count=1)
    s2 = re.sub(r"(<name>)[^<]*(</name>)", r"\1" + NEW_NAME + r"\2", s2, count=1)
    if s2 == s:
        fail("config.xml unchanged (widget id / name not matched)")
    write(cfg, s2)
    print("patched config.xml ->", NEW_ID, NEW_NAME)
    sys.exit(0)


# --------------------------------------------------------------------- post
platform = os.path.join(root, "platforms", "android")
app = os.path.join(platform, "app")
if not os.path.isdir(app):
    fail("no platforms/android/app - did `npm run setup` run?")

gradle = os.path.join(app, "build.gradle")
manifest = os.path.join(app, "src", "main", "AndroidManifest.xml")
if not os.path.exists(gradle):
    fail("missing " + gradle)

gsrc = read(gradle)

# ---- work out the java namespace (where the generated R class lives)
ns = None
m = re.search(r'namespace\s+["\']([\w.]+)["\']', gsrc)
if m:
    ns = m.group(1)
else:
    if os.path.exists(manifest):
        m = re.search(r'package="([\w.]+)"', read(manifest))
        if m:
            ns = m.group(1)
if not ns:
    ns = NEW_ID
print("java namespace:", ns)

# ---- applicationId -> com.termux
if 'applicationId' in gsrc:
    gsrc = re.sub(r'applicationId\s+["\'][\w.]+["\']', 'applicationId "%s"' % NEW_ID, gsrc)
    print("set applicationId =", NEW_ID)

# ---- jitpack repo (root build.gradle)
for rel in ("build.gradle",):
    p = os.path.join(platform, rel)
    if not os.path.exists(p):
        continue
    s = read(p)
    if "jitpack.io" not in s:
        s = s.replace("repositories {", "repositories {\n        maven { url 'https://jitpack.io' }", 1)
        write(p, s)
        print("added jitpack to platforms/android/" + rel)

# ---- dependencies: terminal-view (prebuilt AAR with the native PTY lib)
if "termux-app:terminal-view" not in gsrc:
    gsrc += ("\n\ndependencies {\n"
             "    implementation 'com.termux.termux-app:terminal-view:0.118.0'\n"
             "}\n")
    print("appended terminal-view dependency block")

# ---- arm64-v8a only
if "abiFilters" not in gsrc:
    m = re.search(r"defaultConfig\s*\{", gsrc)
    if m:
        gsrc = (gsrc[:m.end()]
                + '\n        ndk { abiFilters "arm64-v8a" }'
                + gsrc[m.end():])
        print("added abiFilters arm64-v8a")

write(gradle, gsrc)

# ---- our java sources
java_dir = os.path.join(app, "src", "main", "java", *ns.split("."), "termux")
os.makedirs(java_dir, exist_ok=True)
src_java = os.path.join(OVERLAY, "java", "apppkg", "termux")
count = 0
for name in os.listdir(src_java):
    if not name.endswith(".java"):
        continue
    body = read(os.path.join(src_java, name)).replace("__APP_PKG__", ns)
    write(os.path.join(java_dir, name), body)
    count += 1
print("copied", count, "java files ->", java_dir)

# ---- our resources
for sub in ("layout", "values"):
    s_dir = os.path.join(OVERLAY, "res", sub)
    if not os.path.isdir(s_dir):
        continue
    d_dir = os.path.join(app, "src", "main", "res", sub)
    os.makedirs(d_dir, exist_ok=True)
    for name in os.listdir(s_dir):
        shutil.copy2(os.path.join(s_dir, name), os.path.join(d_dir, name))
    print("copied res/" + sub)

# ---- bootstrap asset
assets_termux = os.path.join(app, "src", "main", "assets", "termux")
os.makedirs(assets_termux, exist_ok=True)
src_zip = os.path.join(HERE, "bootstrap-arm64.zip")
if os.path.exists(src_zip):
    shutil.copy2(src_zip, os.path.join(assets_termux, "bootstrap-arm64.zip"))
    print("copied bootstrap asset")
else:
    fail("bootstrap-arm64.zip not found next to patch.py")

# ---- manifest: our activities + make the terminal the launcher
s = read(manifest)
terminal = ns + ".termux.TermuxTerminalActivity"
editor = ns + ".termux.EditorActivity"

# strip the LAUNCHER filter from the generated MainActivity (Acode)
def strip_launcher(match):
    block = match.group(0)
    if "android.intent.category.LAUNCHER" in block:
        return re.sub(r"\s*<intent-filter>.*?</intent-filter>", "", block, count=1,
                      flags=re.S)
    return block

s = re.sub(r"<activity\b[^>]*android:name=\"[^\"]*MainActivity\"[^>]*>.*?</activity>",
           strip_launcher, s, count=1, flags=re.S)

our_activities = """
        <activity
            android:name="%s"
            android:exported="true"
            android:theme="@style/Theme.Termux.Editor"
            android:configChanges="orientation|screenSize|keyboardHidden"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <activity
            android:name="%s"
            android:exported="true"
            android:theme="@style/Theme.Termux.Editor"
            android:configChanges="orientation|screenSize|keyboardHidden"
            android:windowSoftInputMode="adjustResize" />
""" % (terminal, editor)

if "TermuxTerminalActivity" not in s:
    idx = s.rfind("</application>")
    if idx == -1:
        fail("no </application> in AndroidManifest.xml")
    s = s[:idx] + our_activities + s[idx:]
    print("added our activities + launcher")
else:
    print("manifest already patched")

write(manifest, s)

print("PATCH OK (post)")
