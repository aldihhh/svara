package com.svara.ext;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class PushService extends FirebaseMessagingService {
    @Override public void onNewToken(String token) {
        try {
            Store.put(this, "fcm", token);
            final android.content.Context c = getApplicationContext();
            new Thread(() -> Api.registerDevice(c, token)).start();
        } catch (Throwable ignore) {}
    }

    @Override public void onMessageReceived(RemoteMessage m) {
        try {
            Map<String, String> d = m.getData();
            String title = m.getNotification() != null ? m.getNotification().getTitle() : d.get("title");
            String body = m.getNotification() != null ? m.getNotification().getBody() : d.get("body");
            if (title == null && body == null) return;
            Push.show(getApplicationContext(), title == null ? "Svara" : title, body == null ? "" : body, d.get("type"), d.get("id"));
        } catch (Throwable ignore) {}
    }
}
