package com.svara.ext;

import android.app.Activity;
import android.os.Bundle;

/** Transparan: langsung membuka dialog Google lalu menutup diri. Dipakai baris "Masuk dengan Google" di "Kamu". */
public class LoginActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            LoginFlow.start(this, (ok, msg) -> {
                if (msg != null) Ui.toast(this, msg);
                if (ok) Hook.afterLogin(this);
                finish();
            });
        } catch (Throwable t) { finish(); }
    }
}
