package com.svara.ext;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.UUID;

final class Store {
    private static SharedPreferences sp;
    static synchronized SharedPreferences sp(Context c) {
        if (sp == null) sp = c.getApplicationContext().getSharedPreferences("svara_ext", Context.MODE_PRIVATE);
        return sp;
    }
    static String installId(Context c) {
        String v = sp(c).getString("install_id", null);
        if (v == null) { v = UUID.randomUUID().toString(); sp(c).edit().putString("install_id", v).apply(); }
        return v;
    }
    static String get(Context c, String k) { return sp(c).getString(k, ""); }
    static void put(Context c, String k, String v) { sp(c).edit().putString(k, v).apply(); }
    static boolean loggedIn(Context c) { return !get(c, "refresh").isEmpty(); }
    static void clearAuth(Context c) {
        sp(c).edit().remove("access").remove("refresh").remove("exp").remove("user_name").remove("user_email").remove("user_avatar").apply();
    }
}
