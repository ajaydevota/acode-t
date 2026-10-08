#!/usr/bin/env python3
"""Fuses upstream Termux (real, unmodified UI) with Acode inside ONE app.

Design
------
Base project  : Acode's Cordova project (so Acode's editor + plugin system keep working).
Termux        : upstream termux-app v0.118.0 is cloned next to it and its `app` + `termux-shared`
                sources/resources are merged into the Cordova app module. Termux's own UI
                (TermuxActivity, its layouts, styles, drawer, settings) is used unchanged, and it
                is the launcher. Nothing in Termux's UI is rewritten.
Added         : `eg <file>` (small editor) and `acode-t` (brings Acode to the front).

phases: pre | post      usage: python3 patch.py <acode-src-dir> pre|post
"""
import os
import re
import shutil
import sys
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
OVERLAY = os.path.join(HERE, "overlay", "app", "src", "main")

root = sys.argv[1]
phase = sys.argv[2] if len(sys.argv) > 2 else "post"
TERMUX = os.path.join(os.path.dirname(os.path.dirname(HERE)), "termux")

NEW_ID = "com.termux"
NEW_NAME = "Termux"
SKIP_OVERLAY = {"TermuxTerminalActivity.java"}   # Termux's real UI replaces it


def read(p, enc="utf-8"):
    with open(p, encoding=enc, errors="replace") as f:
        return f.read()


def write(p, s):
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w", encoding="utf-8") as f:
        f.write(s)


def fail(msg):
    raise SystemExit("PATCH ERROR: " + msg)


def copy_tree(src, dst, skip=()):
    n = 0
    for base, dirs, names in os.walk(src):
        dirs[:] = [d for d in dirs if d not in skip]
        rel = os.path.relpath(base, src)
        out = os.path.join(dst, rel) if rel != "." else dst
        os.makedirs(out, exist_ok=True)
        for name in names:
            if name in skip:
                continue
            shutil.copy2(os.path.join(base, name), os.path.join(out, name))
            n += 1
    return n


def merge_values(src_file, dst_file):
    """Append entries from src_file into dst_file, skipping names dst already defines."""
    if not os.path.exists(src_file):
        return 0
    if not os.path.exists(dst_file):
        shutil.copy2(src_file, dst_file)
        return 1
    try:
        src_root = ET.parse(src_file).getroot()
        dst_root = ET.parse(dst_file).getroot()
    except Exception as e:
        print("  values merge skipped (%s): %s" % (os.path.basename(src_file), e))
        return 0
    have = set()
    for el in dst_root:
        nm = el.get("name")
        if nm:
            have.add((el.tag, nm))
    added = 0
    for el in list(src_root):
        nm = el.get("name")
        if nm and (el.tag, nm) in have:
            continue
        dst_root.append(el)
        added += 1
    ET.ElementTree(dst_root).write(dst_file, encoding="utf-8", xml_declaration=True)
    return added


# ======================================================================= pre
if phase == "pre":
    # utils/config.js rewrites the widget id from ID_PAID on every build, so that constant is the
    # real switch for the installed package name. It must be com.termux because the Termux
    # bootstrap binaries have /data/data/com.termux/files/... compiled in.
    for rel in ("utils/config.js", "utils/setup.js"):
        fp = os.path.join(root, rel)
        if not os.path.exists(fp):
            print("skip (missing):", rel)
            continue
        t = read(fp)
        t2 = t.replace('ID_PAID = "com.foxdebug.acode"', 'ID_PAID = "com.termux"')
        t2 = t2.replace("ID_PAID = 'com.foxdebug.acode'", "ID_PAID = 'com.termux'")
        if t2 != t:
            write(fp, t2)
            print("patched ID_PAID in", rel)

    cfg = os.path.join(root, "config.xml")
    t = read(cfg)
    t = re.sub(r'(<widget[^>]*?\sid=")[^"]+(")', r"\1" + NEW_ID + r"\2", t, count=1)
    t = re.sub(r"(<name>)[^<]*(</name>)", r"\1" + NEW_NAME + r"\2", t, count=1)
    write(cfg, t)
    print("config.xml -> id", NEW_ID, "name", NEW_NAME)

    # Acode's browser plugin hook rewrites the R import to com.foxdebug.<last id segment>.
    up = os.path.join(root, "src", "plugins", "browser", "utils", "updatePackage.js")
    if os.path.exists(up):
        t = read(up)
        t2 = re.sub(
            r"const updated = data\.replace\([\s\S]*?\);",
            lambda m: 'const updated = data.replace(/import\\s+com\\.foxdebug\\.(acode|acodefree)\\.R;/, "import com.termux.R;");',
            t, count=1)
        if t2 != t:
            write(up, t2)
            print("patched updatePackage.js -> com.termux.R")
        else:
            print("WARNING: updatePackage.js replace() not matched")
    sys.exit(0)


# ======================================================================= post
platform = os.path.join(root, "platforms", "android")
app = os.path.join(platform, "app")
if not os.path.isdir(app):
    fail("no platforms/android/app - did `npm run setup` run?")
if not os.path.isdir(TERMUX):
    fail("termux checkout not found at " + TERMUX)

gradle = os.path.join(app, "build.gradle")
manifest = os.path.join(app, "src", "main", "AndroidManifest.xml")
java_dir = os.path.join(app, "src", "main", "java")
res_dir = os.path.join(app, "src", "main", "res")

gsrc = read(gradle)

# ---- namespace: must stay the widget id (Acode's R import hook depends on it)
ns = None
cfg_path = os.path.join(root, "config.xml")
if os.path.exists(cfg_path):
    m = re.search(r'<widget[^>]*?\sid="([^"]+)"', read(cfg_path))
    if m:
        ns = m.group(1)
if not ns:
    m = re.search(r'namespace\s*=?\s*["\']([\w.]+)["\']', gsrc)
    ns = m.group(1) if m else NEW_ID
print("java namespace:", ns)

# ---- applicationId = com.termux
if "applicationId" in gsrc:
    gsrc = re.sub(r'applicationId\s+["\'][\w.]+["\']', 'applicationId "%s"' % NEW_ID, gsrc)
else:
    gsrc += '\nandroid.defaultConfig.applicationId "%s"\n' % NEW_ID
print("applicationId ->", NEW_ID)

# ---- jitpack + terminal-view (Termux's real terminal widget, prebuilt AAR)
b = os.path.join(platform, "build.gradle")
if os.path.exists(b):
    t = read(b)
    if "jitpack.io" not in t:
        t += ("\nallprojects {\n    repositories {\n"
              "        maven { url 'https://jitpack.io' }\n    }\n}\n")
        write(b, t)
        print("added jitpack allprojects")
if "termux-app:terminal-view" not in gsrc:
    gsrc += ("\n\ndependencies {\n"
             "    implementation 'com.termux.termux-app:terminal-view:0.118.0'\n"
             "}\n")
    print("added terminal-view dependency")

# ---- arm64-v8a (the bundled bootstrap is arm64)
if "abiFilters" not in gsrc:
    m = re.search(r"defaultConfig\s*\{", gsrc)
    if m:
        gsrc = gsrc[:m.end()] + '\n        ndk { abiFilters "arm64-v8a" }' + gsrc[m.end():]
        print("abiFilters arm64-v8a")
write(gradle, gsrc)

# ---- merge upstream Termux java (app + termux-shared) untouched
n1 = copy_tree(os.path.join(TERMUX, "app", "src", "main", "java"), java_dir)
n2 = copy_tree(os.path.join(TERMUX, "termux-shared", "src", "main", "java"), java_dir)
print("copied termux java:", n1, "+", n2)

# ---- merge upstream Termux resources (values are name-deduped; icons left alone)
tm_res = os.path.join(TERMUX, "app", "src", "main", "res")
sh_res = os.path.join(TERMUX, "termux-shared", "src", "main", "res")
for src_root in (tm_res, sh_res):
    if not os.path.isdir(src_root):
        continue
    for sub in sorted(os.listdir(src_root)):
        s = os.path.join(src_root, sub)
        if not os.path.isdir(s):
            continue
        if sub.startswith("mipmap"):
            print("  skipped", sub, "(keeps Acode launcher icon)")
            continue
        d = os.path.join(res_dir, sub)
        if sub == "values" or sub == "values-night":
            os.makedirs(d, exist_ok=True)
            for f in os.listdir(s):
                if f.endswith(".xml"):
                    a = merge_values(os.path.join(s, f), os.path.join(d, f))
                    print("  merged values/%s (+%d)" % (f, a))
        else:
            n = copy_tree(s, d)
            print("  copied res/%s (%d)" % (sub, n))

# ---- our small additions (EditorActivity, CodeSuggest, CodeHighlighter, RequestWatcher,
#      TermuxBootstrap) - TermuxTerminalActivity is dropped, Termux's real UI is used
src_java = os.path.join(OVERLAY, "java", "apppkg", "termux")
dst_java = os.path.join(java_dir, *ns.split("."), "termux")
os.makedirs(dst_java, exist_ok=True)
count = 0
for name in os.listdir(src_java):
    if not name.endswith(".java") or name in SKIP_OVERLAY:
        continue
    write(os.path.join(dst_java, name),
          read(os.path.join(src_java, name)).replace("__APP_PKG__", ns))
    count += 1
print("copied our java:", count, "->", dst_java)

for sub in ("layout", "values"):
    s_dir = os.path.join(OVERLAY, "res", sub)
    if not os.path.isdir(s_dir):
        continue
    d_dir = os.path.join(res_dir, sub)
    os.makedirs(d_dir, exist_ok=True)
    for name in os.listdir(s_dir):
        if name == "activity_termux_terminal.xml":
            continue
        if sub == "values" and name.endswith(".xml"):
            merge_values(os.path.join(s_dir, name), os.path.join(d_dir, name))
        else:
            shutil.copy2(os.path.join(s_dir, name), os.path.join(d_dir, name))
print("copied our res")

# ---- bootstrap asset (Termux's installer is patched to read this instead of the native lib)
assets_termux = os.path.join(app, "src", "main", "assets", "termux")
os.makedirs(assets_termux, exist_ok=True)
if os.path.exists(os.path.join(HERE, "bootstrap-arm64.zip")):
    shutil.copy2(os.path.join(HERE, "bootstrap-arm64.zip"),
                 os.path.join(assets_termux, "bootstrap-arm64.zip"))
    print("copied bootstrap asset")
else:
    fail("bootstrap-arm64.zip missing next to patch.py")

# ---- Termux installer: read the bootstrap from assets (no native lib needed)
ti = os.path.join(java_dir, "com", "termux", "app", "TermuxInstaller.java")
if os.path.exists(ti):
    t = read(ti)
    if "TermuxBootstrap" not in t:
        t = t.replace("final byte[] zipBytes = loadZipBytes();",
                      "final byte[] zipBytes = TermuxBootstrap.loadZipBytes(activity);", 1)
        t = t.replace('Logger.logInfo(LOG_TAG, "Bootstrap packages installed successfully.");',
                      'Logger.logInfo(LOG_TAG, "Bootstrap packages installed successfully.");\n'
                      '                    TermuxBootstrap.installCommands(activity);', 1)
        t = t.replace("            } else {\n                whenDone.run();\n                return;\n            }",
                      "            } else {\n                TermuxBootstrap.installCommands(activity);\n"
                      "                whenDone.run();\n                return;\n            }", 1)
        t = t.replace("import com.termux.R;", "import com.termux.R;\nimport " + ns + ".termux.TermuxBootstrap;", 1)
        write(ti, t)
        print("patched TermuxInstaller -> asset bootstrap + command install")
    else:
        print("TermuxInstaller already patched")

# ---- manifest: add Termux's components, make TermuxActivity the launcher
tm_manifest = os.path.join(TERMUX, "app", "src", "main", "AndroidManifest.xml")
s = read(manifest)
tm = read(tm_manifest)

# permissions + uses-features from Termux
perms = re.findall(r"<uses-permission[^>]*/>", tm)
feats = re.findall(r"<uses-feature[^>]*/>", tm)
add_head = "".join(p for p in perms if p not in s) + "".join(f for f in feats if f not in s)

body = tm[tm.index("<application"):tm.rindex("</application>")]
body = re.sub(r"^<application[^>]*>", "", body, flags=re.S)
for k, v in {
    "${TERMUX_PACKAGE_NAME}": NEW_ID, "${TERMUX_APP_NAME}": NEW_NAME,
    "${TERMUX_API_APP_NAME}": "Termux:API", "${TERMUX_BOOT_APP_NAME}": "Termux:Boot",
    "${TERMUX_FLOAT_APP_NAME}": "Termux:Float", "${TERMUX_STYLING_APP_NAME}": "Termux:Styling",
    "${TERMUX_TASKER_APP_NAME}": "Termux:Tasker", "${TERMUX_WIDGET_APP_NAME}": "Termux:Widget",
}.items():
    body = body.replace(k, v)

if "TermuxActivity" not in s:
    # drop Acode's launcher activity-aliases so Termux owns the launcher role
    s = re.sub(r"<activity-alias[\s\S]*?</activity-alias>", "", s)
    s = s.replace("<application", '<application\n        android:name="com.termux.app.TermuxApplication"\n        android:theme="@style/Theme.Termux"', 1)
    idx = s.rindex("</application>")
    s = s[:idx] + body + "\n" + s[idx:]
    # put Termux's permissions on top
    s = s.replace("<application", add_head + "\n    <application", 1)
    print("manifest: Termux components merged, TermuxActivity is launcher")
else:
    print("manifest already merged")

# our editor activity (only if Termux's manifest did not already add it)
if "EditorActivity" not in s:
    editor = ('\n        <activity android:name="%s.termux.EditorActivity"\n'
              '            android:exported="true"\n'
              '            android:launchMode="singleTop"\n'
              '            android:theme="@style/Theme.Termux.Editor"\n'
              '            android:windowSoftInputMode="adjustResize" />\n' % ns)
    idx = s.rindex("</application>")
    s = s[:idx] + editor + s[idx:]
    print("manifest: EditorActivity added")

write(manifest, s)
print("PATCH OK (post)")
