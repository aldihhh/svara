package com.svara.ext;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;

/**
 * Titik masuk dari aplikasi utama. Semua dibungkus try/catch: kegagalan modul ini
 * tidak boleh membuat aplikasi musik crash.
 */
public final class Hook {
    public static final String ROUTE = "svara_notifications";
    public static final String ROUTE_ACCOUNT = "svara_account";
    private static Application app;
    private static boolean permAsked, welcomeShown;
    private static long lastDarkProbe;

    private Hook() {}

    /** Dipanggil paling awal (App.<clinit>): pasang pencatat crash. */
    public static void early() { CrashLog.install(); }

    /** Dipanggil dari App.onCreate (disuntik lewat smali). */
    public static void init(Application a) {
        try {
            CrashLog.install();
            app = a;
            Push.ensureChannel(a);
            initFirebase(a);
            a.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override public void onActivityCreated(Activity act, Bundle b) { CrashLog.install(); handlePushIntent(act); }
                @Override public void onActivityResumed(Activity act) { probeDark(act); maybeWelcome(act); askNotifPermission(act); }
                @Override public void onActivityStarted(Activity act) {}
                @Override public void onActivityPaused(Activity act) {}
                @Override public void onActivityStopped(Activity act) {}
                @Override public void onActivitySaveInstanceState(Activity act, Bundle b) {}
                @Override public void onActivityDestroyed(Activity act) {}
            });
            new Thread(() -> { try { Api.ping(a); } catch (Throwable ignore) {} }, "svara-ping").start();
        } catch (Throwable ignore) {}
    }

    private static void initFirebase(Application a) {
        try {
            if (FirebaseApp.getApps(a).isEmpty()) {
                FirebaseOptions o = new FirebaseOptions.Builder()
                        .setApplicationId(Config.FB_APP_ID).setApiKey(Config.FB_API_KEY)
                        .setProjectId(Config.FB_PROJECT_ID).setGcmSenderId(Config.FB_SENDER_ID).build();
                FirebaseApp.initializeApp(a, o);
            }
            FirebaseMessaging fm = FirebaseMessaging.getInstance();
            fm.subscribeToTopic(Config.TOPIC);
            fm.getToken().addOnCompleteListener(t -> {
                try {
                    if (t.isSuccessful() && t.getResult() != null) {
                        final String tok = t.getResult();
                        Store.put(a, "fcm", tok);
                        new Thread(() -> Api.registerDevice(a, tok), "svara-dev").start();
                    }
                } catch (Throwable ignore) {}
            });
        } catch (Throwable ignore) {}
    }

    /** Judul baris akun di "Kamu": "Masuk dengan Google" bila belum login, "Akun" bila sudah. */
    public static String accountTitle() {
        try { return app != null && Store.loggedIn(app) ? "Akun" : "Masuk dengan Google"; } catch (Throwable t) { return "Akun"; }
    }

    /** Dipanggil dari lambda baris menu "Kamu". true = sudah ditangani. */
    public static boolean route(String r) {
        try {
            if (app == null || r == null) return false;
            if (ROUTE.equals(r)) {
                app.startActivity(WebActivity.intent(app, "/m/notifications").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return true;
            }
            if (ROUTE_ACCOUNT.equals(r)) {
                Intent i = Store.loggedIn(app) ? WebActivity.intent(app, "/m/account") : new Intent(app, LoginActivity.class);
                app.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return true;
            }
            return false;
        } catch (Throwable t) { return false; }
    }

    /** Setelah login berhasil dari layar sambutan / baris "Kamu". */
    static void afterLogin(Context c) {
        try { Ui.toast0(c, "Berhasil masuk"); afterLoginQuiet(c); } catch (Throwable ignore) {}
    }

    static void afterLoginQuiet(Context c) {
        try { WebActivity.authChanged(); } catch (Throwable ignore) {}
    }

    /** Tema aplikasi utama (gelap/terang) yang terakhir terdeteksi; cadangan: tema sistem. */
    static boolean isDark(Context c) {
        try {
            String v = Store.get(c, "dark");
            if (!v.isEmpty()) return "1".equals(v);
        } catch (Throwable ignore) {}
        return (c.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    /** Baca 1 piksel di tepi kiri jendela aplikasi utama untuk tahu tema yang sedang dipakai (gelap/terang). */
    private static void probeDark(final Activity act) {
        try {
            if (act.getClass().getName().startsWith("com.svara.ext.")) return;
            long now = System.currentTimeMillis();
            if (now - lastDarkProbe < 4000) return;
            lastDarkProbe = now;
            Ui.MAIN.postDelayed(() -> {
                try {
                    if (act.isFinishing() || act.isDestroyed()) return;
                    View v = act.getWindow().getDecorView();
                    if (v.getWidth() < 20 || v.getHeight() < 20) return;
                    final android.graphics.Bitmap bm = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888);
                    int[] loc = new int[2]; v.getLocationInWindow(loc);
                    android.graphics.Rect r = new android.graphics.Rect(loc[0] + 3, loc[1] + v.getHeight() / 2, loc[0] + 4, loc[1] + v.getHeight() / 2 + 1);
                    android.view.PixelCopy.request(act.getWindow(), r, bm, res -> {
                        try {
                            if (res != android.view.PixelCopy.SUCCESS) return;
                            int px = bm.getPixel(0, 0);
                            double lum = (0.299 * android.graphics.Color.red(px) + 0.587 * android.graphics.Color.green(px) + 0.114 * android.graphics.Color.blue(px)) / 255.0;
                            Store.put(act.getApplicationContext(), "dark", lum < 0.5 ? "1" : "0");
                        } catch (Throwable ignore) {}
                    }, Ui.MAIN);
                } catch (Throwable ignore) {}
            }, 900);
        } catch (Throwable ignore) {}
    }

    /** Layar sambutan: sekali saja, hanya bila belum login. */
    private static void maybeWelcome(Activity act) {
        try {
            if (welcomeShown || act.getClass().getName().startsWith("com.svara.ext.")) return;
            if (!Store.get(act, "welcome_done").isEmpty() || Store.loggedIn(act)) return;
            welcomeShown = true;
            act.startActivity(new Intent(act, WelcomeActivity.class));
        } catch (Throwable ignore) {}
    }

    /** Ketuk notifikasi push saat aplikasi di latar belakang: FCM membuka MainActivity dengan extras data. */
    private static void handlePushIntent(Activity act) {
        try {
            Intent i = act.getIntent();
            if (i == null || act.getClass().getName().startsWith("com.svara.ext.")) return;
            Bundle e = i.getExtras();
            if (e == null || e.getString("google.message_id") == null) return;
            String type = e.getString("type"), id = e.getString("id");
            if (type == null) return;
            i.removeExtra("google.message_id");
            act.startActivity(WebActivity.intent(act, "/m/notifications" + (id != null ? "?open=" + android.net.Uri.encode(type + ":" + id) : "")));
        } catch (Throwable ignore) {}
    }

    private static void askNotifPermission(Activity act) {
        try {
            if (permAsked || Build.VERSION.SDK_INT < 33 || act.getClass().getName().startsWith("com.svara.ext.")) return;
            if (act.checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED) { permAsked = true; return; }
            permAsked = true;
            act.requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7731);
        } catch (Throwable ignore) {}
    }
}
