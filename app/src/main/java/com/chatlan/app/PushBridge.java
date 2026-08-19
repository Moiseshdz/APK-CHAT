package com.chatlan.app;

import android.content.Context;
import android.provider.Settings;
import android.webkit.JavascriptInterface;

public final class PushBridge {
    private final Context context;
    private final MainActivity activity;

    PushBridge(MainActivity activity) {
        this.context = activity.getApplicationContext();
        this.activity = activity;
    }

    @JavascriptInterface
    public String getFcmToken() {
        return context.getSharedPreferences("chatlan_push", Context.MODE_PRIVATE)
                .getString("fcm_token", "");
    }

    @JavascriptInterface
    public String getDeviceId() {
        String id = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.ANDROID_ID);
        return id == null || id.isEmpty() ? "android" : id;
    }

    @JavascriptInterface
    public void refreshToken() {
        activity.refreshFcmToken();
    }
}
