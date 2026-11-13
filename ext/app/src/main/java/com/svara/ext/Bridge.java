package com.svara.ext;

import android.app.Activity;
import android.content.Intent;
import android.webkit.JavascriptInterface;

/** Jembatan JS: window.Svara dipakai halaman /m/* di dalam WebActivity. */
final class Bridge {
    private final WebActivity act;
    Bridge(WebActivity a) { act = a; }

    @JavascriptInterface public String key() { return Config.APP_KEY; }

    /** Dipanggil dari JS pada thread WebView (bukan UI thread), jadi aman memanggil jaringan. */
    @JavascriptInterface public String token() {
        try { return Api.freshToken(act.getApplicationContext()); } catch (Throwable t) { return ""; }
    }

    @JavascriptInterface public boolean dark() { return Hook.isDark(act); }
    @JavascriptInterface public boolean loggedIn() { return Store.loggedIn(act.getApplicationContext()); }
    @JavascriptInterface public void close() { act.runOnUiThread(act::finish); }

    @JavascriptInterface public void login() {
        act.runOnUiThread(() -> LoginFlow.start(act, (ok, msg) -> {
            if (msg != null) Ui.toast(act, msg);
            if (ok) { Hook.afterLoginQuiet(act); act.notifyAuth(); }
        }));
    }

    @JavascriptInterface public void logout() {
        final android.content.Context c = act.getApplicationContext();
        Ui.POOL.execute(() -> { Api.logout(c); Ui.MAIN.post(act::notifyAuth); });
    }

    /** Akun sudah dihapus / sesi tidak berlaku di server. */
    @JavascriptInterface public void loggedOut() { Store.clearAuth(act.getApplicationContext()); }

    @JavascriptInterface public void profile(String name, String avatar) {
        Store.put(act.getApplicationContext(), "user_name", name == null ? "" : name);
        Store.put(act.getApplicationContext(), "user_avatar", avatar == null ? "" : avatar);
    }
}
