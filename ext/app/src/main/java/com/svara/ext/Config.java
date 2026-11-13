package com.svara.ext;

/** Diisi otomatis oleh tools/gen_config.py saat build (nilai dari GitHub Secrets / google-services.json). */
final class Config {
    static final String BASE_URL = "@BASE_URL@";
    static final String APP_KEY = "@APP_KEY@";
    static final String WEB_CLIENT_ID = "@WEB_CLIENT_ID@";
    static final String FB_APP_ID = "@FB_APP_ID@";
    static final String FB_API_KEY = "@FB_API_KEY@";
    static final String FB_PROJECT_ID = "@FB_PROJECT_ID@";
    static final String FB_SENDER_ID = "@FB_SENDER_ID@";
    static final String TOPIC = "announcements";
    private Config() {}
}
