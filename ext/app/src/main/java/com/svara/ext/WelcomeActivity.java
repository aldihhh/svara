package com.svara.ext;

import android.app.Activity;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Layar sambutan saat pertama kali dibuka: masuk dengan Google atau lewati. */
public class WelcomeActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        final boolean dark = Hook.isDark(this);
        final int bg = dark ? Ui.BG : Ui.L_BG, tx = dark ? Ui.TEXT : Ui.L_TEXT, mu = dark ? Ui.MUTED : Ui.L_MUTED;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(bg);
        int p = Ui.dp(this, 28); root.setPadding(p, p, p, p);
        Ui.applyInsets(root, 0);

        View spacer = new View(this); root.addView(spacer, new LinearLayout.LayoutParams(0, 0, 1f));
        ImageView logo = new ImageView(this);
        try { logo.setImageDrawable(getPackageManager().getApplicationIcon(getPackageName())); } catch (Throwable ignore) {}
        root.addView(logo, Ui.lp(Ui.dp(this, 96), Ui.dp(this, 96), 0, 0, 0, Ui.dp(this, 22)));
        TextView t = Ui.text(this, "Selamat datang di Svara", 24, tx, true); t.setGravity(Gravity.CENTER);
        root.addView(t, new LinearLayout.LayoutParams(-1, -2));
        TextView d = Ui.text(this, "Masuk untuk menyimpan playlist di akunmu, mengatur nama & foto profil, dan mendapat info dari pengembang. Kamu juga bisa lewati dan langsung memutar musik.", 14, mu, false);
        d.setGravity(Gravity.CENTER); d.setLineSpacing(0, 1.25f); d.setPadding(0, Ui.dp(this, 10), 0, 0);
        root.addView(d, new LinearLayout.LayoutParams(-1, -2));
        View spacer2 = new View(this); root.addView(spacer2, new LinearLayout.LayoutParams(0, 0, 1.2f));

        final TextView login = Ui.button(this, "Masuk dengan Google", true);
        root.addView(login, Ui.lp(-1, -2, 0, 0, 0, Ui.dp(this, 10)));
        TextView skip = Ui.button(this, "Lewati", false);
        skip.setTextColor(tx); skip.setBackground(Ui.rounded(dark ? Ui.SURFACE2 : Ui.L_SURFACE2, 28, this));
        root.addView(skip, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);

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

    @Override protected void onDestroy() {
        super.onDestroy();
        try { Store.put(this, "welcome_done", "1"); } catch (Throwable ignore) {}
    }
}
