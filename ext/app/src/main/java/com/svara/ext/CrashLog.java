package com.svara.ext;

import android.app.Application;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Menulis laporan crash ke folder Download (svara-crash-*.txt) supaya bisa dibuka dari HP tanpa komputer. */
final class CrashLog {
    static final class Handler implements Thread.UncaughtExceptionHandler {
        private final Thread.UncaughtExceptionHandler prev;
        Handler(Thread.UncaughtExceptionHandler p) { prev = p; }
        @Override public void uncaughtException(Thread t, Throwable e) {
            try { write(e, t); } catch (Throwable ignore) {}
            if (prev != null) prev.uncaughtException(t, e);
        }
    }

    static void install() {
        try {
            Thread.UncaughtExceptionHandler cur = Thread.getDefaultUncaughtExceptionHandler();
            if (!(cur instanceof Handler)) Thread.setDefaultUncaughtExceptionHandler(new Handler(cur));
        } catch (Throwable ignore) {}
    }

    private static Context ctx() {
        try {
            Application a = (Application) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
            return a;
        } catch (Throwable t) { return null; }
    }

    static void write(Throwable e, Thread t) throws Exception {
        Context c = ctx();
        if (c == null) return;
        StringWriter sw = new StringWriter(); PrintWriter pw = new PrintWriter(sw);
        pw.println("Svara crash  " + new Date());
        pw.println("Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")  " + Build.MANUFACTURER + " " + Build.MODEL);
        try { pw.println("Versi app " + Api.version(c) + "  proses " + android.app.Application.getProcessName()); } catch (Throwable ignore) {}
        pw.println("Thread " + (t == null ? "?" : t.getName()));
        pw.println(); e.printStackTrace(pw); pw.flush();
        byte[] data = sw.toString().getBytes("UTF-8");
        String name = "svara-crash-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".txt";
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                v.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri u = c.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (u != null) { try (OutputStream o = c.getContentResolver().openOutputStream(u)) { o.write(data); } return; }
            }
        } catch (Throwable ignore) {}
        try (FileOutputStream o = new FileOutputStream(new File(c.getFilesDir(), name))) { o.write(data); }
    }
}
