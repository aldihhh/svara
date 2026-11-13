#!/usr/bin/env python3
"""Isi ext/.../Config.java dari environment (GitHub Secrets) dan assets/google-services.json."""
import json, os, sys

root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
cfg = os.path.join(root, "ext/app/src/main/java/com/svara/ext/Config.java")
gs = json.load(open(os.path.join(root, "assets/google-services.json")))
cl = gs["client"][0]
vals = {
    "BASE_URL": os.environ.get("SVARA_BASE_URL", "").strip().rstrip("/") or "https://svara.aldih.com",
    "APP_KEY": os.environ.get("SVARA_APP_KEY", "").strip(),
    "WEB_CLIENT_ID": os.environ.get("SVARA_WEB_CLIENT_ID", "").strip(),
    "FB_APP_ID": cl["client_info"]["mobilesdk_app_id"],
    "FB_API_KEY": cl["api_key"][0]["current_key"],
    "FB_PROJECT_ID": gs["project_info"]["project_id"],
    "FB_SENDER_ID": gs["project_info"]["project_number"],
}
if not vals["APP_KEY"]:
    sys.exit("GAGAL: secret SVARA_APP_KEY belum diisi di GitHub (Settings > Secrets and variables > Actions). Isinya harus sama dengan APP_KEY di .env server.")
if not vals["WEB_CLIENT_ID"] or ".apps.googleusercontent.com" not in vals["WEB_CLIENT_ID"]:
    sys.exit("GAGAL: secret SVARA_WEB_CLIENT_ID belum diisi / formatnya salah (harus Client ID tipe Web, berakhiran .apps.googleusercontent.com).")
if cl["client_info"]["android_client_info"]["package_name"] != "com.svara.app":
    sys.exit("GAGAL: package di google-services.json bukan com.svara.app")
s = open(cfg).read()
for k, v in vals.items():
    assert '"' not in v and "\\" not in v, k
    s = s.replace("@%s@" % k, v)
open(cfg, "w").write(s)
print("Config.java terisi:", {k: (v if k not in ("APP_KEY", "FB_API_KEY") else v[:4] + "…") for k, v in vals.items()})
