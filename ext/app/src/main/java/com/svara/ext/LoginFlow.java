package com.svara.ext;

import android.app.Activity;
import android.os.CancellationSignal;

import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

/** Login Google (Credential Manager) yang dipakai bersama oleh layar sambutan, "Kamu", dan halaman akun. */
final class LoginFlow {
    interface Done { void finished(boolean ok, String message); }

    static void start(final Activity act, final Done done) {
        try {
            CredentialManager cm = CredentialManager.create(act);
            GetCredentialRequest req = new GetCredentialRequest.Builder()
                    .addCredentialOption(new GetSignInWithGoogleOption.Builder(Config.WEB_CLIENT_ID).build()).build();
            cm.getCredentialAsync(act, req, new CancellationSignal(), run -> Ui.MAIN.post(run),
                    new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                        @Override public void onResult(GetCredentialResponse r) {
                            try {
                                if (r.getCredential() instanceof CustomCredential
                                        && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(r.getCredential().getType())) {
                                    final String idToken = GoogleIdTokenCredential.createFrom(r.getCredential().getData()).getIdToken();
                                    final android.content.Context app = act.getApplicationContext();
                                    Ui.POOL.execute(() -> {
                                        String err = null;
                                        try { Api.Res res = Api.loginGoogle(app, idToken); if (!res.ok()) err = res.error(); }
                                        catch (Throwable t) { err = "Login gagal"; }
                                        if (err == null) Api.registerDevice(app, Store.get(app, "fcm"));
                                        final String e = err;
                                        Ui.MAIN.post(() -> done.finished(e == null, e));
                                    });
                                } else done.finished(false, "Jenis kredensial tidak dikenali");
                            } catch (Throwable t) { done.finished(false, "Login gagal: " + t.getClass().getSimpleName()); }
                        }
                        @Override public void onError(GetCredentialException e) {
                            if (e instanceof GetCredentialCancellationException) { done.finished(false, null); return; }
                            done.finished(false, "Login Google gagal (" + e.getType() + "). Pastikan SHA-1 & package sudah didaftarkan di Google Cloud.");
                        }
                    });
        } catch (Throwable t) { done.finished(false, "Login Google tidak bisa dibuka: " + t.getClass().getSimpleName()); }
    }

    private LoginFlow() {}
}
