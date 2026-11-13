package com.svara.ext;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

/**
 * Menampilkan halaman server (/m/notifications, /m/account) dengan desain yang sama persis dengan aplikasi.
 * Hanya host Svara yang boleh dibuka di dalam WebView; tautan lain dibuka di browser.
 */
public class WebActivity extends Activity {
    private static final int REQ_FILE = 4410;
    private WebView web;
    private View offline;
    private ValueCallback<Uri[]> chooser;
    private String path;
    private boolean failed;
    private Runnable backCb;

    static Intent intent(Context c, String path) {
        return new Intent(c, WebActivity.class).putExtra("path", path);
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        path = getIntent().getStringExtra("path");
        if (path == null || !path.startsWith("/m/")) { finish(); return; }
        final boolean dark = Hook.isDark(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(dark ? Ui.BG : Ui.L_BG);
        Ui.applyInsets(root, 0);

        web = new WebView(this);
        web.setBackgroundColor(dark ? Ui.BG : Ui.L_BG);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false); s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(s.getUserAgentString() + " SvaraApp/" + Api.version(this));
        web.addJavascriptInterface(new Bridge(this), "Svara");
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (isOurs(u) && u.getPath() != null && u.getPath().startsWith("/m/")) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Throwable ignore) {}
                return true;
            }
            @Override public void onPageStarted(WebView v, String url, android.graphics.Bitmap f) { failed = false; }
            @Override public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) { failed = true; offline.setVisibility(View.VISIBLE); web.setVisibility(View.INVISIBLE); }
            }
            @Override public void onReceivedHttpError(WebView v, WebResourceRequest r, android.webkit.WebResourceResponse e) {
                if (r.isForMainFrame() && e.getStatusCode() >= 500) { failed = true; offline.setVisibility(View.VISIBLE); web.setVisibility(View.INVISIBLE); }
            }
            @Override public void onPageFinished(WebView v, String url) {
                if (!failed) { offline.setVisibility(View.GONE); web.setVisibility(View.VISIBLE); }
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (chooser != null) chooser.onReceiveValue(null);
                chooser = cb;
                try {
                    Intent i = new Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*");
                    startActivityForResult(Intent.createChooser(i, "Pilih foto"), REQ_FILE);
                } catch (Throwable t) { chooser = null; cb.onReceiveValue(null); return false; }
                return true;
            }
            @Override public boolean onJsAlert(WebView v, String u, String m, JsResult r) {
                new AlertDialog.Builder(WebActivity.this).setMessage(m).setPositiveButton("OK", (d, w) -> r.confirm())
                        .setOnCancelListener(d -> r.cancel()).show();
                return true;
            }
            @Override public boolean onJsConfirm(WebView v, String u, String m, JsResult r) {
                new AlertDialog.Builder(WebActivity.this).setMessage(m).setNegativeButton("Batal", (d, w) -> r.cancel())
                        .setPositiveButton("Ya", (d, w) -> r.confirm()).setOnCancelListener(d -> r.cancel()).show();
                return true;
            }
            @Override public boolean onConsoleMessage(ConsoleMessage m) { return true; }
        });
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout off = new LinearLayout(this);
        off.setOrientation(LinearLayout.VERTICAL); off.setGravity(Gravity.CENTER);
        off.setBackgroundColor(dark ? Ui.BG : Ui.L_BG); off.setPadding(Ui.dp(this, 32), 0, Ui.dp(this, 32), 0);
        TextView m = Ui.text(this, "Tidak bisa terhubung.\nPeriksa koneksi internetmu.", 15, dark ? Ui.MUTED : Ui.L_MUTED, false);
        m.setGravity(Gravity.CENTER); off.addView(m, new LinearLayout.LayoutParams(-2, -2));
        TextView retry = Ui.button(this, "Coba lagi", true);
        retry.setOnClickListener(v -> load());
        off.addView(retry, Ui.lp(-2, -2, 0, Ui.dp(this, 16), 0, 0));
        TextView back = Ui.button(this, "Kembali", false);
        back.setTextColor(dark ? Ui.TEXT : Ui.L_TEXT);
        back.setBackground(Ui.rounded(dark ? Ui.SURFACE2 : Ui.L_SURFACE2, 28, this));
        back.setOnClickListener(v -> finish());
        off.addView(back, Ui.lp(-2, -2, 0, Ui.dp(this, 8), 0, 0));
        off.setVisibility(View.GONE);
        offline = off;
        root.addView(off, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        // Tombol kembali: biarkan halaman menutup lembar-lembar terbuka dulu (window.__back)
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(0, this::onBackPressed);
        }
        current = this;
        load();
    }

    private static WebActivity current;
    /** Dipanggil dari lifecycle: setelah login dari luar halaman ini, muat ulang isinya. */
    static void authChanged() {
        final WebActivity w = current;
        if (w != null) w.runOnUiThread(w::notifyAuth);
    }

    void notifyAuth() {
        try { web.evaluateJavascript("window.onSvaraAuth&&window.onSvaraAuth()", null); } catch (Throwable ignore) {}
    }

    private boolean isOurs(Uri u) {
        try { return Uri.parse(Config.BASE_URL).getHost().equalsIgnoreCase(u.getHost()); } catch (Throwable t) { return false; }
    }

    private void load() {
        failed = false; offline.setVisibility(View.GONE); web.setVisibility(View.VISIBLE);
        Map<String, String> h = new HashMap<>();
        h.put("X-Svara-Key", Config.APP_KEY);
        web.loadUrl(Config.BASE_URL + path, h);
    }

    @Override public void onBackPressed() {
        try {
            web.evaluateJavascript("(function(){try{return window.__back&&window.__back()?1:0}catch(e){return 0}})()", v -> {
                if ("1".equals(v)) return;
                finish();
            });
        } catch (Throwable t) { finish(); }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_FILE && chooser != null) {
            Uri[] out = null;
            if (res == RESULT_OK && data != null && data.getData() != null) out = new Uri[]{data.getData()};
            chooser.onReceiveValue(out); chooser = null;
            return;
        }
        super.onActivityResult(req, res, data);
    }

    @Override protected void onDestroy() {
        if (current == this) current = null;
        try { if (web != null) { ((ViewGroup) web.getParent()).removeView(web); web.destroy(); } } catch (Throwable ignore) {}
        super.onDestroy();
    }
}
