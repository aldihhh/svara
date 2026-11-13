package com.svara.ext;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

final class Push {
    static final String CHANNEL = "announcements";

    static void ensureChannel(Context c) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm.getNotificationChannel(CHANNEL) == null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL, "Pengumuman Svara", NotificationManager.IMPORTANCE_HIGH);
                ch.setDescription("Pengumuman dan info dari pengembang");
                nm.createNotificationChannel(ch);
            }
        } catch (Throwable ignore) {}
    }

    /** Dipakai saat push tiba ketika aplikasi sedang terbuka (FCM tidak menampilkan sendiri). */
    static void show(Context c, String title, String body, String type, String id) {
        try {
            ensureChannel(c);
            Intent i = WebActivity.intent(c, "/m/notifications" + (type != null && id != null ? "?open=" + android.net.Uri.encode(type + ":" + id) : ""))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(c, (int) (System.currentTimeMillis() & 0xfffffff), i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            int icon = c.getApplicationInfo().icon != 0 ? c.getApplicationInfo().icon : android.R.drawable.ic_dialog_info;
            Notification.Builder b = new Notification.Builder(c, CHANNEL)
                    .setSmallIcon(icon).setContentTitle(title).setContentText(body)
                    .setStyle(new Notification.BigTextStyle().bigText(body))
                    .setAutoCancel(true).setContentIntent(pi);
            ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).notify((int) (System.currentTimeMillis() & 0xfffffff), b.build());
        } catch (Throwable ignore) {}
    }
}
