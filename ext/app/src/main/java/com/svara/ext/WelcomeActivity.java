package com.svara.ext;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Layar sambutan (sekali): memakai pola yang sama dengan halaman Akun/Kamu — judul kiri, kartu bulat, tombol pil. */
public class WelcomeActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        final boolean dark = Hook.isDark(this);
        final int bg = dark ? Ui.BG : Ui.L_BG, bg2 = dark ? Color2.DARK_BG2 : Color2.LIGHT_BG2;
        final int tx = dark ? Ui.TEXT : Ui.L_TEXT, mu = dark ? Ui.MUTED : Ui.L_MUTED;
        final int card = dark ? Ui.SURFACE : Ui.L_SURFACE;

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(this, 20), Ui.dp(this, 36), Ui.dp(this, 20), Ui.dp(this, 20));

        ImageView logo = new ImageView(this);
        try { logo.setImageDrawable(getPackageManager().getApplicationIcon(getPackageName())); } catch (Throwable ignore) {}
        col.addView(logo, Ui.lp(Ui.dp(this, 72), Ui.dp(this, 72), Ui.dp(this, 4), 0, 0, Ui.dp(this, 20)));

        TextView t = Ui.text(this, "Selamat datang di Svara", 30, tx, true);
        t.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
        col.addView(t, new LinearLayout.LayoutParams(-1, -2));
        TextView d = Ui.text(this, "Masuk untuk menyimpan playlist di akunmu, mengatur nama dan foto, dan mendapat info dari pengembang.", 15, mu, false);
        d.setLineSpacing(0, 1.25f); d.setPadding(Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 22));
        col.addView(d, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setBackground(Ui.rounded(card, 28, this));
        String[][] rows = {{"Playlist tersinkron", "Tersimpan di akun dan ikut ke perangkat baru."},
                {"Info untukmu", "Pesan khusus dari pengembang muncul di Notifikasi."},
                {"Data aman", "Hanya email, nama, foto, dan playlist. Bisa dihapus kapan saja."}};
        for (int i = 0; i < rows.length; i++) {
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.VERTICAL);
            r.setPadding(Ui.dp(this, 20), Ui.dp(this, 14), Ui.dp(this, 20), Ui.dp(this, 14));
            r.addView(Ui.text(this, rows[i][0], 16, tx, true));
            TextView s = Ui.text(this, rows[i][1], 14, mu, false);
            s.setPadding(0, Ui.dp(this, 2), 0, 0);
            r.addView(s);
            group.addView(r, new LinearLayout.LayoutParams(-1, -2));
            if (i < rows.length - 1) {
                View line = new View(this);
                line.setBackgroundColor(dark ? 0x1AFFFFFF : 0x1F3C282D);
                group.addView(line, Ui.lp(-1, 1, Ui.dp(this, 17), 0, Ui.dp(this, 17), 0));
            }
        }
        col.addView(group, new LinearLayout.LayoutParams(-1, -2));

        View spacer = new View(this);
        col.addView(spacer, new LinearLayout.LayoutParams(0, Ui.dp(this, 28), 1f));

        final TextView login = Ui.button(this, "Masuk dengan Google", true);
        col.addView(login, Ui.lp(-1, -2, 0, 0, 0, Ui.dp(this, 10)));
        TextView skip = Ui.button(this, "Lewati", false);
        col.addView(skip, new LinearLayout.LayoutParams(-1, -2));
        TextView note = Ui.text(this, "Login bersifat opsional. Tanpa login, kamu tetap bisa memutar musik.", 13, mu, false);
        note.setGravity(Gravity.CENTER); note.setPadding(0, Ui.dp(this, 14), 0, 0);
        col.addView(note, new LinearLayout.LayoutParams(-1, -2));

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true); sv.setVerticalScrollBarEnabled(false);
        sv.addView(col, new ScrollView.LayoutParams(-1, -1));
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{bg, bg, bg2});
        sv.setBackground(g);
        Ui.applyInsets(sv, 0);
        setContentView(sv);

        skip.setOnClickListener(v -> finish());
        login.setOnClickListener(v -> {
            login.setEnabled(false); login.setText("Membuka Google…");
            LoginFlow.start(this, (ok, msg) -> {
                if (msg != null) Ui.toast(this, msg);
                if (ok) { Hook.afterLogin(this); finish(); }
                else { login.setEnabled(true); login.setText("Masuk dengan Google"); }
            });
        });
    }

    @Override public void onBackPressed() { finish(); }

    @Override protected void onDestroy() {
        super.onDestroy();
        try { Store.put(this, "welcome_done", "1"); } catch (Throwable ignore) {}
    }

    private static final class Color2 {
        static final int DARK_BG2 = android.graphics.Color.parseColor("#120C0C"), LIGHT_BG2 = android.graphics.Color.parseColor("#FFFBFC");
    }
}
