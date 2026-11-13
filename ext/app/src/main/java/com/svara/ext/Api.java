package com.svara.ext;

import android.content.Context;
import android.os.Build;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Klien HTTP minimal untuk Svara API v1 (hanya API bawaan Android, tanpa library). */
final class Api {
    static final class Res {
        final int code; final JSONObject json;
        Res(int c, JSONObject j) { code = c; json = j; }
        boolean ok() { return code >= 200 && code < 300; }
        String error() { return json != null ? json.optString("error", "Terjadi kesalahan (" + code + ")") : "Tidak bisa terhubung ke server"; }
    }

    static String version(Context c) {
        try { return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName; } catch (Throwable t) { return "0"; }
    }

    private static Res raw(Context c, String method, String path, JSONObject body, boolean auth) {
        HttpURLConnection h = null;
        try {
            h = (HttpURLConnection) new URL(Config.BASE_URL + "/api/v1" + path).openConnection();
            h.setRequestMethod(method);
            h.setConnectTimeout(12000); h.setReadTimeout(20000);
            h.setRequestProperty("X-Svara-Key", Config.APP_KEY);
            h.setRequestProperty("X-Install-Id", Store.installId(c));
            h.setRequestProperty("Accept", "application/json");
            h.setRequestProperty("User-Agent", "Svara/" + version(c) + " (Android " + Build.VERSION.RELEASE + ")");
            if (auth) h.setRequestProperty("Authorization", "Bearer " + Store.get(c, "access"));
            if (body != null) {
                h.setDoOutput(true);
                h.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (OutputStream o = h.getOutputStream()) { o.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
            }
            int code = h.getResponseCode();
            InputStream in = code >= 400 ? h.getErrorStream() : h.getInputStream();
            String txt = in == null ? "" : read(in);
            JSONObject j = null;
            try { if (!txt.isEmpty()) j = new JSONObject(txt); } catch (Throwable ignore) {}
            return new Res(code, j);
        } catch (Throwable t) {
            return new Res(0, null);
        } finally { if (h != null) h.disconnect(); }
    }

    private static String read(InputStream in) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[8192]; int n; long total = 0;
        while ((n = in.read(buf)) > 0) { b.write(buf, 0, n); total += n; if (total > 2_000_000) break; }
        return b.toString("UTF-8");
    }

    /** Request biasa; bila auth=true dan 401, coba refresh token sekali lalu ulangi. */
    static Res call(Context c, String method, String path, JSONObject body, boolean auth) {
        Res r = raw(c, method, path, body, auth);
        if (auth && r.code == 401 && refresh(c)) r = raw(c, method, path, body, true);
        return r;
    }

    private static synchronized boolean refresh(Context c) {
        String rt = Store.get(c, "refresh");
        if (rt.isEmpty()) return false;
        try {
            Res r = raw(c, "POST", "/auth/refresh", new JSONObject().put("refresh_token", rt), false);
            if (r.ok() && r.json != null) { saveTokens(c, r.json); return true; }
            if (r.code == 401 || r.code == 403 || r.code == 400) Store.clearAuth(c); // refresh sudah tidak berlaku
        } catch (Throwable ignore) {}
        return false;
    }

    static void saveTokens(Context c, JSONObject j) {
        Store.put(c, "access", j.optString("access_token"));
        Store.put(c, "exp", String.valueOf(System.currentTimeMillis() / 1000 + j.optLong("expires_in", 600)));
        Store.put(c, "refresh", j.optString("refresh_token", Store.get(c, "refresh")));
        JSONObject u = j.optJSONObject("user");
        if (u != null) {
            Store.put(c, "user_name", u.optString("name"));
            Store.put(c, "user_email", u.optString("email"));
            Store.put(c, "user_avatar", u.optString("avatar"));
        }
    }

    /** Access token yang masih berlaku (refresh dulu bila tinggal <90 dtk). Panggil dari thread latar. */
    static String freshToken(Context c) {
        if (!Store.loggedIn(c)) return "";
        long exp = 0;
        try { exp = Long.parseLong(Store.get(c, "exp")); } catch (Throwable ignore) {}
        if (Store.get(c, "access").isEmpty() || exp - System.currentTimeMillis() / 1000 < 90) refresh(c);
        return Store.get(c, "access");
    }

    static void ping(Context c) {
        try { call(c, "POST", "/ping", new JSONObject().put("version", version(c)).put("model", Build.MODEL), Store.loggedIn(c)); } catch (Throwable ignore) {}
    }

    static void registerDevice(Context c, String fcm) {
        if (fcm == null || fcm.length() < 20) return;
        try { call(c, "POST", "/devices", new JSONObject().put("fcm_token", fcm).put("app_version", version(c)), Store.loggedIn(c)); } catch (Throwable ignore) {}
    }

    static Res loginGoogle(Context c, String idToken) throws Exception {
        JSONObject b = new JSONObject().put("id_token", idToken).put("fcm_token", Store.get(c, "fcm"))
                .put("app_version", version(c)).put("model", Build.MODEL);
        Res r = raw(c, "POST", "/auth/google", b, false);
        if (r.ok() && r.json != null) saveTokens(c, r.json);
        return r;
    }

    static void logout(Context c) {
        try {
            call(c, "POST", "/auth/logout", new JSONObject().put("refresh_token", Store.get(c, "refresh")).put("fcm_token", Store.get(c, "fcm")), false);
        } catch (Throwable ignore) {}
        Store.clearAuth(c);
    }
}
