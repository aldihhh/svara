package com.svara.ext;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Tema & helper tampilan (warna sama dengan website Svara, mode gelap). */
final class Ui {
    static final int BG = Color.parseColor("#191315"), SURFACE = Color.parseColor("#2C2628"), SURFACE2 = Color.parseColor("#362F31"),
            TEXT = Color.parseColor("#F6E3E5"), MUTED = Color.parseColor("#C9B3B6"), ACCENT = Color.parseColor("#F0B6C4"),
            ACCENT_INK = Color.parseColor("#4B2030"), DANGER = Color.parseColor("#FFB4AB");
    static final int L_BG = Color.parseColor("#F1EBED"), L_SURFACE = Color.parseColor("#FFFBFC"), L_SURFACE2 = Color.parseColor("#F6EEF0"),
            L_TEXT = Color.parseColor("#3D3133"), L_MUTED = Color.parseColor("#6F5F63"), L_ACCENT = Color.parseColor("#8C3F55");
    static final Handler MAIN = new Handler(Looper.getMainLooper());
    static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };

    static int dp(Context c, float v) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()); }

    static GradientDrawable rounded(int color, float radiusDp, Context c) {
        GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(c, radiusDp)); return g;
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c); t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return t;
    }

    static TextView button(Context c, String s, boolean primary) {
        boolean d = Hook.isDark(c);
        TextView b = text(c, s, 15, primary ? (d ? ACCENT_INK : Color.WHITE) : (d ? TEXT : L_TEXT), true);
        b.setGravity(Gravity.CENTER); b.setPadding(dp(c, 24), dp(c, 14), dp(c, 24), dp(c, 14));
        b.setBackground(rounded(primary ? (d ? ACCENT : L_ACCENT) : (d ? SURFACE2 : L_SURFACE2), 28, c)); b.setClickable(true); b.setFocusable(true);
        return b;
    }

    static LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h); p.setMargins(l, t, r, b); return p;
    }

    /** Padding mengikuti status bar / navigation bar (aplikasi dipaksa edge-to-edge di Android 15+). */
    @SuppressWarnings("deprecation")
    static void applyInsets(View root, int basePad) {
        root.setOnApplyWindowInsetsListener((v, in) -> {
            v.setPadding(in.getSystemWindowInsetLeft(), in.getSystemWindowInsetTop(), in.getSystemWindowInsetRight(), in.getSystemWindowInsetBottom());
            return in;
        });
    }

    static void image(final ImageView iv, final String url) {
        if (url == null || url.isEmpty()) { iv.setVisibility(View.GONE); return; }
        iv.setVisibility(View.VISIBLE); iv.setTag(url);
        Bitmap hit = CACHE.get(url);
        if (hit != null) { iv.setImageBitmap(hit); return; }
        iv.setImageDrawable(null);
        POOL.execute(() -> {
            HttpURLConnection h = null;
            try {
                h = (HttpURLConnection) new URL(url).openConnection();
                h.setConnectTimeout(10000); h.setReadTimeout(15000);
                try (InputStream in = h.getInputStream()) {
                    BitmapFactory.Options o = new BitmapFactory.Options(); o.inSampleSize = 1;
                    byte[] data = readAll(in);
                    BitmapFactory.Options b = new BitmapFactory.Options(); b.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(data, 0, data.length, b);
                    o.inSampleSize = Math.max(1, b.outWidth / 1200);
                    final Bitmap bm = BitmapFactory.decodeByteArray(data, 0, data.length, o);
                    if (bm != null) { CACHE.put(url, bm); MAIN.post(() -> { if (url.equals(iv.getTag())) iv.setImageBitmap(bm); }); }
                }
            } catch (Throwable ignore) { MAIN.post(() -> { if (url.equals(iv.getTag())) iv.setVisibility(View.GONE); }); }
            finally { if (h != null) h.disconnect(); }
        });
    }

    private static byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream(); byte[] b = new byte[8192]; int n; int t = 0;
        while ((n = in.read(b)) > 0) { o.write(b, 0, n); t += n; if (t > 8_000_000) break; }
        return o.toByteArray();
    }

    static String ago(String iso) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US); f.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date d = f.parse(iso.length() > 19 ? iso.substring(0, 19) : iso);
            long s = (System.currentTimeMillis() - d.getTime()) / 1000;
            if (s < 60) return "baru saja";
            if (s < 3600) return (s / 60) + " menit lalu";
            if (s < 86400) return (s / 3600) + " jam lalu";
            if (s < 86400 * 7) return (s / 86400) + " hari lalu";
            return new SimpleDateFormat("d MMM yyyy", new Locale("id", "ID")).format(d);
        } catch (Throwable t) { return ""; }
    }

    static void toast0(Context c, String s) { android.widget.Toast.makeText(c, s, android.widget.Toast.LENGTH_SHORT).show(); }
    static void toast(Activity a, String s) { android.widget.Toast.makeText(a, s, android.widget.Toast.LENGTH_LONG).show(); }
}
