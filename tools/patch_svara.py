#!/usr/bin/env python3
"""Suntik modul Svara ke hasil bongkar apktool.
  patch_svara.py smali    <src_apktool>
  patch_svara.py manifest <src_apktool> <ext_merged_manifest.xml>
  patch_svara.py dex      <unsigned.apk> <ext_release.apk>
Setiap titik suntik DIPERIKSA (harus ditemukan tepat sekali); kalau tidak, build berhenti dengan pesan jelas."""
import os, re, sys, zipfile, shutil
import xml.etree.ElementTree as ET

ANDROID = "http://schemas.android.com/apk/res/android"
TOOLS = "http://schemas.android.com/tools"
A = "{%s}" % ANDROID


def die(m):
    sys.exit("GAGAL: " + m)


def rd(p):
    return open(p, encoding="utf-8").read()


def wr(p, s):
    open(p, "w", encoding="utf-8").write(s)


def find_smali(src, rel):
    for d in sorted(os.listdir(src)):
        p = os.path.join(src, d, rel)
        if d.startswith("smali") and os.path.isfile(p):
            return p
    die("file smali tidak ketemu: " + rel)


def once(s, needle, what):
    n = s.count(needle)
    if n != 1:
        die("titik suntik '%s' ditemukan %d kali (harus 1). Versi APK asli berbeda dari yang diharapkan." % (what, n))


def safe_call(tag, insn, reg):
    """Panggilan ke modul dibungkus try/catch Throwable: kegagalan modul tidak boleh menjatuhkan aplikasi."""
    return ("\n    :s_%s\n    %s\n    :e_%s\n    .catch Ljava/lang/Throwable; {:s_%s .. :e_%s} :c_%s\n"
            "    goto :ok_%s\n    :c_%s\n    move-exception %s\n    :ok_%s\n") % (tag, insn, tag, tag, tag, tag, tag, tag, reg, tag)


def smali(src):
    # 1) App.<clinit> -> Hook.early (pencatat crash) dan App.onCreate -> Hook.init, keduanya dibungkus try/catch
    p = find_smali(src, "com/metrolist/music/App.smali")
    s = rd(p)
    cl = ".method static constructor <clinit>()V\n    .locals 1\n"
    once(s, cl, "App.<clinit>")
    s = s.replace(cl, cl + safe_call("c1", "invoke-static {}, Lcom/svara/ext/Hook;->early()V", "v0"))
    needle = "    invoke-super {p0}, Lcom/metrolist/music/Hilt_App;->onCreate()V\n"
    once(s, needle, "App.onCreate")
    s = s.replace(needle, needle + safe_call("c2", "invoke-static {p0}, Lcom/svara/ext/Hook;->init(Landroid/app/Application;)V", "v0"))
    wr(p, s)

    # 2) klik baris menu "Kamu": route khusus dialihkan ke Hook.route
    p = find_smali(src, "com/metrolist/music/ui/screens/YouScreenKt$$ExternalSyntheticLambda7.smali")
    s = rd(p)
    head = ".method public final invoke()Ljava/lang/Object;\n    .locals 3\n"
    once(s, head, "YouScreen lambda7.invoke")
    hook = (head +
            "\n    iget-object v2, p0, Lcom/metrolist/music/ui/screens/YouScreenKt$$ExternalSyntheticLambda7;->f$1:Ljava/lang/String;\n"
            "    :s_r\n"
            "    invoke-static {v2}, Lcom/svara/ext/Hook;->route(Ljava/lang/String;)Z\n"
            "    move-result v2\n"
            "    :e_r\n"
            "    .catch Ljava/lang/Throwable; {:s_r .. :e_r} :c_r\n"
            "    goto :ok_r\n"
            "    :c_r\n"
            "    move-exception v2\n"
            "    const/4 v2, 0x0\n"
            "    :ok_r\n"
            "    if-eqz v2, :svara_go\n"
            "    sget-object v2, Lkotlin/Unit;->INSTANCE:Lkotlin/Unit;\n"
            "    return-object v2\n"
            "    :svara_go\n")
    wr(p, s.replace(head, hook))

    # 3) tambah baris "Akun / Masuk dengan Google" + "Notifikasi" di grup pertama layar Kamu.
    #    Register v8,v9,v12,v13,v14 dipastikan ditulis ulang sebelum dibaca setelah titik ini (v10,v11 hidup: jangan dipakai).
    p = find_smali(src, "com/metrolist/music/ui/screens/YouScreenKt.smali")
    s = rd(p)
    needle = "    filled-new-array {v4, v6}, [Lcom/metrolist/music/ui/component/Material3SettingsItem;\n"
    once(s, needle, "YouLayout grup pertama")
    rowsig = "Lcom/metrolist/music/ui/screens/YouScreenKt;->YouLayout$row(Lkotlin/jvm/functions/Function1;ILjava/lang/String;Ljava/lang/String;Landroidx/compose/runtime/Composer;)Lcom/metrolist/music/ui/component/Material3SettingsItem;"
    row = ("    const-string v9, \"Akun\"\n" +
           safe_call("t", "invoke-static {}, Lcom/svara/ext/Hook;->accountTitle()Ljava/lang/String;\n    move-result-object v9", "v12") +
           "    const v8, 0x7f070106\n"
           "    const-string v12, \"svara_account\"\n"
           "    invoke-static {v0, v8, v9, v12, v7}, " + rowsig + "\n"
           "    move-result-object v13\n"
           "    const v8, 0x7f070103\n"
           "    const-string v9, \"Notifikasi\"\n"
           "    const-string v12, \"svara_notifications\"\n"
           "    invoke-static {v0, v8, v9, v12, v7}, " + rowsig + "\n"
           "    move-result-object v14\n"
           "    filled-new-array {v13, v14, v4, v6}, [Lcom/metrolist/music/ui/component/Material3SettingsItem;\n")
    wr(p, s.replace(needle, row))
    print("smali: 3 titik suntik OK")


def resolve_attr(el):
    """Atribut yang merujuk resource milik modul (tidak ada di APK utama) diganti/dibuang."""
    for k in list(el.attrib):
        if k.startswith("{%s}" % TOOLS):
            del el.attrib[k]
            continue
        v = el.attrib[k]
        if v.startswith("@") and not v.startswith("@android:"):
            if k == A + "theme":
                el.attrib[k] = "@android:style/Theme.Translucent.NoTitleBar"
            else:
                del el.attrib[k]
    if el.tag == "meta-data" and el.get(A + "name") == "com.google.android.gms.version":
        el.attrib[A + "value"] = "12451000"
    for k in ("authorities", "permission"):
        v = el.get(A + k)
        if v:
            el.set(A + k, v.replace("com.svara.ext.", "com.svara.app."))
    for c in el:
        resolve_attr(c)


SKIP = {"com.google.firebase.provider.FirebaseInitProvider"}


def manifest(src, ext_manifest):
    ET.register_namespace("android", ANDROID)
    ET.register_namespace("tools", TOOLS)
    host_p = os.path.join(src, "AndroidManifest.xml")
    h = rd(host_p)
    ext = ET.parse(ext_manifest).getroot()
    app = ext.find("application")
    if app is None:
        die("manifest modul tidak punya <application>")

    def present(tag, name):
        return re.search(r'<%s\b[^>]*android:name="%s"' % (tag, re.escape(name)), h) is not None

    def ser(el):
        s = ET.tostring(el, encoding="unicode")
        s = re.sub(r'\s+xmlns:(ns\d+|tools)="[^"]*"', "", s)
        s = re.sub(r'\s+xmlns:android="[^"]*"', "", s)
        return s

    perms, comps = [], []
    for el in list(ext):
        if el.tag in ("uses-permission", "uses-permission-sdk-23", "permission"):
            resolve_attr(el)
            n = el.get(A + "name") or ""
            n = n.replace("com.svara.ext.", "com.svara.app.")
            el.set(A + "name", n)
            if not present(el.tag, n):
                perms.append("    " + ser(el))
    skipped = []
    for el in list(app):
        if el.tag not in ("activity", "activity-alias", "service", "receiver", "provider", "meta-data", "uses-library", "uses-native-library"):
            continue
        resolve_attr(el)
        name = el.get(A + "name")
        if name in SKIP:  # Firebase diinisialisasi manual di Hook.init; provider otomatis tidak dipakai
            skipped.append(name)
            continue
        if name and present(el.tag, name):
            skipped.append(name)
            continue
        comps.append("        " + ser(el))
    # <queries> milik modul digabung ke manifest utama
    qs = ext.find("queries")
    qblock = ""
    if qs is not None:
        inner = "".join("        " + ser(c) + "\n" for c in qs)
        if "<queries>" in h:
            h = h.replace("<queries>", "<queries>\n" + inner, 1)
        else:
            qblock = "    <queries>\n" + inner + "    </queries>\n"
    once(h, "<application ", "<application> di manifest")
    h = h.replace("    <application ", "\n".join(perms) + ("\n" if perms else "") + qblock + "    <application ", 1)
    if "</application>" not in h:
        die("manifest utama tidak punya </application>")
    h = h.replace("</application>", "\n".join(comps) + "\n    </application>", 1)
    wr(host_p, h)
    print("manifest: +%d izin, +%d komponen (dilewati karena sudah ada: %d)" % (len(perms), len(comps), len(skipped)))
    for c in comps:
        m = re.search(r'android:name="([^"]+)"', c)
        print("   +", c.strip().split()[0], m.group(1) if m else "")


def dex(unsigned, ext_apk):
    z = zipfile.ZipFile(unsigned)
    names = z.namelist()
    n = len([x for x in names if re.fullmatch(r"classes\d*\.dex", x)])
    z.close()
    ez = zipfile.ZipFile(ext_apk)
    dexes = sorted([x for x in ez.namelist() if re.fullmatch(r"classes\d*\.dex", x)])
    if not dexes:
        die("APK modul tidak berisi classes.dex")
    out = unsigned + ".tmp"
    with zipfile.ZipFile(unsigned) as zin, zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as zout:
        for it in zin.infolist():
            data = zin.read(it.filename)
            zi = zipfile.ZipInfo(it.filename, it.date_time)
            zi.compress_type = it.compress_type
            zi.external_attr = it.external_attr
            zout.writestr(zi, data)
        for d in dexes:
            n += 1
            nm = "classes%d.dex" % n
            zout.writestr(nm, ez.read(d))
            print("dex: %s -> %s (%d KB)" % (d, nm, len(ez.read(d)) // 1024))
    shutil.move(out, unsigned)


if __name__ == "__main__":
    if len(sys.argv) < 3:
        die(__doc__)
    cmd = sys.argv[1]
    if cmd == "smali":
        smali(sys.argv[2])
    elif cmd == "manifest":
        manifest(sys.argv[2], sys.argv[3])
    elif cmd == "dex":
        dex(sys.argv[2], sys.argv[3])
    else:
        die(__doc__)
