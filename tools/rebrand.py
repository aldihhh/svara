#!/usr/bin/env python3
# Svara rebrand: dijalankan di GitHub Actions setelah "apktool d". Ubah nilai di DEFAULTS kalau ada yang salah.
import os, re, sys, shutil, glob, io
ROOT, ASSETS, REPORT = sys.argv[1:4]
E = {
 'NEW_NAME':'Svara', 'OLD_NAME':'Auralys',
 'NEW_PKG':'com.svara.app', 'OLD_PKG':'com.auralyst.app',
 'VERSION_NAME':'1.1.0', 'VERSION_CODE':'110',
 'DEV_NAME':'Aldih', 'OLD_DEV_NAME':'DAPZ.DEV', 'OLD_VERSION':'4.3.3',
 'TELEGRAM_NEW':'iniiwal', 'TELEGRAM_OLD':'ryuzinetshh',
 'TIKTOK_NEW':'hhhhhalldi', 'TIKTOK_OLD':'dappxml29',   # jadi https://www.tiktok.com/@hhhhhalldi
 'SUPPORT_URL_NEW':'https://svara.aldih.com', 'SUPPORT_URL_OLD':'https://support-admin-kappa.vercel.app',
 'WEBSITE_URL':'https://aldih.com', 'DONATE_URL':'https://donate.aldih.com',
 'SEED_OLD':'-0x12aa9c', 'SEED_NEW':'-0xa48306',        # warna tema 0xFFED5564 -> 0xFF5B7CFA (indigo)
 'BRAND_BG':'#121318',
 'SEED2_OLD':'-0x3d9dc100000000L', 'SEED2_NEW':'-0xa4830600000000L',   # warna tema Auralys 0xFFC2623F -> 0xFF5B7CFA
}
log = []
def L(s=""): log.append(s); print(s)

TEXT_EXT = ('.smali', '.xml', '.yml', '.json', '.properties', '.txt')
SKIP_DIRS = {'lib', 'original', 'unknown', 'kotlin', 'META-INF', 'assets'}
def rd(p):
    with open(p, 'r', encoding='utf-8', errors='surrogateescape', newline='') as f: return f.read()
def wr(p, s):
    with open(p, 'w', encoding='utf-8', errors='surrogateescape', newline='') as f: f.write(s)
def files():
    for dp, dn, fn in os.walk(ROOT):
        if dp == ROOT: dn[:] = [d for d in dn if d not in SKIP_DIRS]
        for n in fn:
            if n.endswith(TEXT_EXT): yield os.path.join(dp, n)

# ---- 1) penggantian teks biasa, semua file ----
PLAIN = [
  (E['OLD_PKG'], E['NEW_PKG'], 'package (titik)'),
  (E['OLD_PKG'].replace('.', '/'), E['NEW_PKG'].replace('.', '/'), 'package (slash)'),
  (E['SUPPORT_URL_OLD'], E['SUPPORT_URL_NEW'], 'website support'),
  (E['TELEGRAM_OLD'], E['TELEGRAM_NEW'], 'handle telegram'),
  (E['TIKTOK_OLD'], E['TIKTOK_NEW'], 'handle tiktok'),
]
DONATE_RE = re.compile(r'https?://(?:www\.)?(?:saweria\.co|trakteer\.id|teer\.id|ko-fi\.com|buymeacoffee\.com|sociabuzz\.com|paypal\.me|patreon\.com)[^"\'<>\s]*')
SEEDS = [(re.compile(r'(?<![0-9a-zA-Z_])' + re.escape(o) + r'(?![0-9a-zA-Z_])'), n) for o, n in ((E['SEED_OLD'], E['SEED_NEW']), (E['SEED2_OLD'], E['SEED2_NEW']), ('-0x3d9dc1', '-0xa48306'))]
CS_RE = re.compile(r'(?<![A-Za-z0-9_.$])' + re.escape(E['OLD_NAME']) + r'(?![A-Za-z0-9_$])')
CS_UP = re.compile(r'(?<![A-Za-z0-9_.$])' + re.escape(E['OLD_NAME'].upper()) + r'(?![A-Za-z0-9_$])')
XML_TEXT = re.compile(r'>([^<]+)<')
WORD_RE = re.compile(r'\b' + re.escape(E['OLD_NAME']) + r'\b', re.I)
def case_map(m):
    t = m.group(0)
    return E['NEW_NAME'].upper() if t.isupper() else (E['NEW_NAME'].lower() if t.islower() else E['NEW_NAME'])

cnt = {k: 0 for k in ['package (titik)','package (slash)','website support','handle telegram','handle tiktok','link donasi','warna tema (seed)','nama di smali (const-string)','nama di strings.xml']}
changed_files = 0
for p in files():
    try: s = rd(p)
    except Exception: continue
    o = s
    for a, b, k in PLAIN:
        if a in s: cnt[k] += s.count(a); s = s.replace(a, b)
    s, n = DONATE_RE.subn(E['DONATE_URL'], s); cnt['link donasi'] += n
    if p.endswith('.smali'):
        for rx, nv in SEEDS:
            s, n = rx.subn(nv, s); cnt['warna tema (seed)'] += n
        if E['OLD_NAME'] in s or E['OLD_NAME'].upper() in s or ('"%s"' % E['OLD_DEV_NAME']) in s or ('"%s"' % E['OLD_VERSION']) in s:
            out = []
            for line in s.split('\n'):
                if 'const-string' in line:
                    line = line.replace('"%s"' % E['OLD_DEV_NAME'], '"%s"' % E['DEV_NAME']).replace('"%s"' % E['OLD_VERSION'], '"%s"' % E['VERSION_NAME'])
                    line, n1 = CS_RE.subn(E['NEW_NAME'], line); line, n2 = CS_UP.subn(E['NEW_NAME'].upper(), line)
                    cnt['nama di smali (const-string)'] += n1 + n2
                out.append(line)
            s = '\n'.join(out)
    elif p.endswith('.xml') and '/res/values' in p.replace(ROOT, '') :
        if os.path.basename(p) in ('strings.xml', 'arrays.xml', 'plurals.xml'):
            def sub(m):
                t, n = WORD_RE.subn(case_map, m.group(1)); cnt['nama di strings.xml'] += n
                return '>' + t + '<'
            s = XML_TEXT.sub(sub, s)
    if s != o: wr(p, s); changed_files += 1
L(f"File teks yang berubah: {changed_files}")
for k, v in cnt.items(): L(f"  {k}: {v}")

# ---- 2) pindah folder package lama kalau ada ----
moved = 0
for sm in glob.glob(os.path.join(ROOT, 'smali*')):
    old = os.path.join(sm, *E['OLD_PKG'].split('.'))
    if os.path.isdir(old):
        new = os.path.join(sm, *E['NEW_PKG'].split('.')); os.makedirs(os.path.dirname(new), exist_ok=True)
        shutil.move(old, new); moved += 1
        par = os.path.dirname(old)
        while par != sm and os.path.isdir(par) and not os.listdir(par): os.rmdir(par); par = os.path.dirname(par)
L(f"Folder smali package dipindah: {moved}")

# ---- 3) manifest + versi ----
mf = os.path.join(ROOT, 'AndroidManifest.xml'); s = rd(mf)
s = re.sub(r'android:versionCode="\d+"', 'android:versionCode="%s"' % E['VERSION_CODE'], s, count=1)
s = re.sub(r'android:versionName="[^"]*"', 'android:versionName="%s"' % E['VERSION_NAME'], s, count=1)
wr(mf, s)
ay = os.path.join(ROOT, 'apktool.yml'); s = rd(ay)
s = re.sub(r"versionCode: '?\d+'?", "versionCode: %s" % E['VERSION_CODE'], s)
s = re.sub(r"versionName: .*", "versionName: %s" % E['VERSION_NAME'], s)
s = re.sub(r": '(\d+)'", r": \1", s)   # apktool terbaru tidak bisa membaca angka bertanda kutip
wr(ay, s)
L(f"Manifest: package={E['NEW_PKG']} versi={E['VERSION_NAME']} ({E['VERSION_CODE']})")

# ---- 4) warna & icon ----
RES = os.path.join(ROOT, 'res')
for cf in glob.glob(os.path.join(RES, 'values*', 'colors.xml')):
    s = rd(cf)
    s2 = re.sub(r'(<color name="(?:ic_launcher_background|ic_launcher_static_background)">)[^<]*(</color>)', r'\g<1>%s\g<2>' % E['BRAND_BG'], s)
    if s2 != s: wr(cf, s2)
SOLID = '<?xml version="1.0" encoding="utf-8"?>\n<shape xmlns:android="http://schemas.android.com/apk/res/android">\n    <solid android:color="%s"/>\n</shape>\n' % E['BRAND_BG']
for f in glob.glob(os.path.join(RES, 'drawable*', 'ic_launcher_background_v31.xml')): wr(f, SOLID)

def drop(name):
    for f in glob.glob(os.path.join(RES, 'drawable*', name + '.*')) + glob.glob(os.path.join(RES, 'drawable*', name)): os.remove(f)
def write_vec(name, src):
    drop(name); os.makedirs(os.path.join(RES, 'drawable'), exist_ok=True)
    shutil.copy(src, os.path.join(RES, 'drawable', name + '.xml'))
VEC = os.path.join(ASSETS, 'vector')
fg_names = ['ic_launcher_foreground', 'ic_launcher_foreground_v31', 'ic_launcher_static_foreground_vec']
fg_names += sorted({os.path.basename(f).rsplit('.', 1)[0] for f in glob.glob(os.path.join(RES, 'drawable*', 'ic_launcher_fg_*'))})
for n in fg_names: write_vec(n, os.path.join(VEC, 'fg.xml'))
write_vec('ic_launcher_monochrome', os.path.join(VEC, 'mono.xml'))
write_vec('app_logo', os.path.join(VEC, 'app_logo.xml'))       # logo besar di dalam app
write_vec('small_icon', os.path.join(VEC, 'small_icon.xml'))   # ikon notifikasi
L(f"Logo vektor baru: {len(fg_names)} foreground launcher + monochrome + app_logo + small_icon")
leg = 0
for d in ('mdpi', 'hdpi', 'xhdpi', 'xxhdpi', 'xxxhdpi'):
    md = os.path.join(RES, 'mipmap-' + d)
    if not os.path.isdir(md): continue
    for n in ('ic_launcher', 'ic_launcher_round', 'ic_launcher_static'):
        for f in glob.glob(os.path.join(md, n + '.*')): os.remove(f)
        shutil.copy(os.path.join(ASSETS, 'legacy', d, n + '.png'), os.path.join(md, n + '.png')); leg += 1
L(f"Icon legacy diganti: {leg} file")

# ---- 5) banner developer ----
banners = [os.path.join(dp, n) for dp, _, fn in os.walk(RES) for n in fn if n == 'dev_banner.mp4']
for b in banners: shutil.copy(os.path.join(ASSETS, 'dev_banner.mp4'), b)
L(f"dev_banner.mp4 diganti dengan animasi gelombang: {len(banners)} file")

# ---- 5b) gambar lain yang masih membawa identitas lama ----
REPL = {'dapz_avatar.png': 'png/svara_avatar.png', 'tv_banner.png': 'png/tv_banner.png', 'qris.png': 'png/qris.png'}
for n in ('glass', 'wide', 'tall', 'square', 'strip', 'tiny'): REPL['aw_preview_%s.png' % n] = 'previews/aw_preview_%s.png' % n
done = []
for dp, _, fn in os.walk(RES):
    for n in fn:
        if n in REPL: shutil.copy(os.path.join(ASSETS, REPL[n]), os.path.join(dp, n)); done.append(n)
L("Gambar diganti: " + ", ".join(sorted(done)))

# ---- 5c) kelas baru + patch smali (tiap patch dicek, gagal = dilaporkan, bukan crash) ----
SPILL = os.environ.get('SVARA_SPILL', 'io').split(',')
def smali_dirs(): return sorted(glob.glob(os.path.join(ROOT, 'smali*')), key=lambda d: (len(d), d))
def smali_path(rel):
    for sm in smali_dirs():
        p = os.path.join(sm, *rel.split('/'))
        if os.path.isfile(p): return p
LAST = os.path.join(ROOT, 'smali_classes%d' % (len(smali_dirs()) + 1)); os.makedirs(LAST, exist_ok=True)
# dex pertama hampir penuh (batas 65536 tipe): pindahkan paket library ke dex baru supaya kelas baru muat
for pk in SPILL:
    src = os.path.join(ROOT, 'smali', *pk.split('/'))
    if os.path.isdir(src):
        dst = os.path.join(LAST, *pk.split('/')); os.makedirs(os.path.dirname(dst), exist_ok=True); shutil.move(src, dst)
NEWCLS = os.path.join(LAST, 'com', 'svara', 'app'); os.makedirs(NEWCLS, exist_ok=True)
for n in ('Mark.smali', 'OpenUrl.smali'):
    t = rd(os.path.join(ASSETS, 'smali', n)).replace('@@DONATE@@', E['DONATE_URL']); wr(os.path.join(NEWCLS, n), t)
L("Kelas baru: com.svara.app.Mark (logo animasi), com.svara.app.OpenUrl (buka link donasi)")
LN = r'(?:\s|\.line \d+)*'
patch_log = []
def patch(rel, pattern, repl, label, flags=re.S):
    p = smali_path(rel)
    if not p: patch_log.append(f"  [GAGAL] {label}: {rel} tidak ketemu"); return
    t = rd(p); new, n = re.subn(pattern, repl, t, flags=flags)
    if n != 1: patch_log.append(f"  [GAGAL] {label}: pola cocok {n}x (harus 1x)"); return
    wr(p, new); patch_log.append(f"  [OK]    {label}")
T = 'com/metrolist/music/'
# logo splash + loader: ganti gambar lama (kubah/matahari) dengan gelombang Svara
patch(T+'ui/component/DialogKt.smali',
  r'\.method public static final drawAuralysMark-RIQooxk\(Landroidx/compose/ui/graphics/drawscope/DrawScope;JJFFF\)V.*?\n\.end method',
  lambda m: '.method public static final drawAuralysMark-RIQooxk(Landroidx/compose/ui/graphics/drawscope/DrawScope;JJFFF)V\n    .locals 0\n    invoke-static/range {p0 .. p7}, Lcom/svara/app/Mark;->draw(Landroidx/compose/ui/graphics/drawscope/DrawScope;JJFFF)V\n    return-void\n.end method',
  'splash + loader memakai logo Svara')
# traktir kopi: klik langsung buka web (bukan dialog QRIS)
patch(T+'ui/screens/settings/AboutScreenKt.smali',
  r'(new-instance v4, )Lcom/metrolist/music/ui/screens/search/SearchScreenKt\$\$ExternalSyntheticLambda18;(' + LN + r'const/16 v6, 0x1c' + LN + r')invoke-direct \{v4, v1, v6\}, Lcom/metrolist/music/ui/screens/search/SearchScreenKt\$\$ExternalSyntheticLambda18;-><init>\(Landroidx/compose/runtime/MutableState;B\)V',
  r'\1Lcom/svara/app/OpenUrl;\2invoke-direct {v4}, Lcom/svara/app/OpenUrl;-><init>()V',
  'tombol traktir kopi -> ' + E['DONATE_URL'])
patch(T+'ui/component/SystemIcons.smali',
  r'\.method public static final SupportDialog\(Lkotlin/jvm/functions/Function0;Landroidx/compose/runtime/Composer;I\)V.*?\n\.end method',
  lambda m: '.method public static final SupportDialog(Lkotlin/jvm/functions/Function0;Landroidx/compose/runtime/Composer;I)V\n    .locals 0\n    return-void\n.end method',
  'dialog QRIS dimatikan')
# login YouTube Music: tutup semua pintu masuk ke layar login
patch(T+'ui/screens/search/SearchScreenKt$$ExternalSyntheticLambda12.smali',
  r'(const-string v0, "login"' + LN + r')invoke-static \{p0, v0\}, Landroidx/navigation/NavHostController;->navigate\$default\(Landroidx/navigation/NavHostController;Ljava/lang/String;\)V',
  r'\1', 'login YTM: navigasi dari pengaturan dihapus')
patch(T+'App$onCreate$1.smali',
  r'(const-string p1, "login"' + LN + r')invoke-static \{p0, p1\}, Landroidx/navigation/NavHostController;->navigate\$default\(Landroidx/navigation/NavHostController;Ljava/lang/String;\)V',
  r'\1', 'login YTM: navigasi otomatis dihapus')
patch(T+'ui/screens/settings/SyncSettingsKt.smali',
  r'(invoke-direct/range \{v32 \.\. v41\}, Lcom/metrolist/music/ui/component/Material3SettingsItem;-><init>\([^\n]*\)V' + LN + r'move-object/from16 v0, v32' + LN + r')invoke-virtual \{v7, v0\}, Lkotlin/collections/builders/ListBuilder;->add\(Ljava/lang/Object;\)Z(' + LN + r'if-nez v2, :cond_15)',
  r'\1\2', 'login YTM: baris Login di Sinkronisasi dihapus')
patch(T+'MainActivity$$ExternalSyntheticLambda20.smali',
  r'invoke-static \{v5, v4, v3, v13\}, (Lcom/metrolist/music/ui/component/SystemIcons;->WelcomeScreen\()',
  r'invoke-static {v4, v4, v3, v13}, \1', 'login YTM: tombol di layar welcome = lanjut (1)')
patch(T+'MainActivity$$ExternalSyntheticLambda1.smali',
  r'invoke-static \{v5, v4, v0, v1\}, (Lcom/metrolist/music/ui/component/SystemIcons;->WelcomeScreen\()',
  r'invoke-static {v4, v4, v0, v1}, \1', 'login YTM: tombol di layar welcome = lanjut (2)')
# teks tombol welcome
for sf, a, b in (('values', 'Get started', 'Skip'), ('values-in', 'Mulai', 'Lewati')):
    f = os.path.join(RES, sf, 'strings.xml')
    if os.path.isfile(f):
        t = rd(f)
        t = re.sub(r'(<string name="welcome_login">)[^<]*(</string>)', r'\g<1>' + a + r'\g<2>', t)
        t = re.sub(r'(<string name="welcome_skip">)[^<]*(</string>)', r'\g<1>' + b + r'\g<2>', t); wr(f, t)
L("Hasil patch smali:")
for x in patch_log: L(x)

# ---- 6) laporan untuk tahap lanjut ----
L(""); L("=== PREFLIGHT ===")
L(f"res/layout: {'ada' if os.path.isdir(os.path.join(RES,'layout')) else 'TIDAK ADA (curiga hasil jadx, build bisa gagal)'} | res/raw: {'ada' if os.path.isdir(os.path.join(RES,'raw')) else 'tidak ada'}")
L("smali dirs: " + ", ".join(sorted(os.path.basename(x) for x in glob.glob(os.path.join(ROOT, 'smali*')))))
pub = rd(os.path.join(RES, 'values', 'public.xml'))
def rid(t, n):
    m = re.search(r'<public type="%s" name="%s" id="(0x[0-9a-fA-F]+)"' % (t, n), pub); return m.group(1).lower() if m else None
smali_files = [p for p in files() if p.endswith('.smali')]
needles = {}
for t, n in (('string','support_title'), ('string','support_dialog_title'), ('string','credits_developer'), ('raw','dev_banner'), ('string','hub_cat_about')):
    h = rid(t, n)
    if h: needles[n + ' (' + h + ')'] = h
    else: L(f"{n}: id tidak ketemu di public.xml")
for key in (E['TELEGRAM_NEW'], E['TIKTOK_NEW'], E['SUPPORT_URL_NEW'].replace('https://','')): needles["link '" + key + "'"] = key
hits = {k: [] for k in needles}
left = []; pat = re.compile(r'ryuzi|dappxml|kappa|' + re.escape(E['OLD_NAME']), re.I)
for p in smali_files:                       # satu kali jalan, hemat memori
    s = rd(p)
    for k, v in needles.items():
        if v in s: hits[k].append(p)
    if len(left) < 40 and pat.search(s):
        for ln in s.split('\n'):
            t = ln.strip()
            if t.startswith('const-string') and pat.search(t) and 'metrolist' not in t.lower():
                left.append(os.path.basename(p) + ': ' + t[:150])
                if len(left) >= 40: break
L(""); L("=== FILE YANG MEMAKAI RESOURCE / LINK PENTING ===")
focus = []
for k, r in hits.items():
    L(f"{k}: {len(r)} file")
    for p in r[:5]: L("   " + os.path.relpath(p, ROOT))
    if r and (k.startswith('support_title') or k.startswith('credits_developer') or k.startswith("link 'iniiwal'")): focus.append(r[0])
L(""); L("=== ISI TEKS (const-string) FILE TERKAIT ===")
seen = set()
for p in focus:
    if p in seen: continue
    seen.add(p); lines = []
    for ln in rd(p).split('\n'):
        t = ln.strip()
        if t.startswith('const-string') and ',' in t:
            v = t.split(',', 1)[1].strip()
            if v not in lines and len(v) < 160: lines.append(v)
    L(f"--- {os.path.relpath(p, ROOT)} ({len(lines)} teks)")
    for v in lines[:45]: L("   " + v)
L(""); L("=== SISA NAMA LAMA (const-string) ===")
for x in left: L("   " + x)
L(f"(ditampilkan {len(left)})")
with open(REPORT, 'w') as f: f.write('\n'.join(log))
