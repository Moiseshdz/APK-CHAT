package com.chatlan.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final int FILE_CHOOSER_REQUEST = 7001;
    private static final int NOTIFICATION_PERMISSION_REQUEST = 7002;

    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);

        webView.addJavascriptInterface(new PushBridge(this), "ChatLANPush");
        webView.setWebViewClient(new WebViewClient());

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(
                    WebView webView,
                    ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams) {

                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = filePathCallback;

                Intent intent;
                try {
                    intent = fileChooserParams.createIntent();
                } catch (Exception e) {
                    intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                }

                startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                return true;
            }
        });

        requestNotificationPermissionIfNeeded();
        refreshFcmToken();

        webView.loadUrl("file:///android_asset/index.html");
        handleNotificationIntent(getIntent());
    }

    public void refreshFcmToken() {
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null) return;
            String token = task.getResult();
            getSharedPreferences("chatlan_push", Context.MODE_PRIVATE)
                    .edit().putString("fcm_token", token).apply();
            pushTokenToWeb(token);
        });
    }

    private String deviceId() {
        String id = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ANDROID_ID);
        return id == null || id.isEmpty() ? "android" : id;
    }

    private void pushTokenToWeb(String token) {
        if (webView == null || token == null) return;
        String js = "window.ChatLANReceiveNativeToken && window.ChatLANReceiveNativeToken("
                + JSONObject.quote(token) + "," + JSONObject.quote(deviceId()) + ");";
        webView.post(() -> webView.evaluateJavascript(js, null));
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        String token = getSharedPreferences("chatlan_push", Context.MODE_PRIVATE)
                .getString("fcm_token", "");
        if (!token.isEmpty()) pushTokenToWeb(token);
        handleNotificationIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNotificationIntent(intent);
    }

    private void handleNotificationIntent(Intent intent) {
        if (intent == null || webView == null) return;

        String chatId = intent.getStringExtra("chatId");
        if (chatId == null || chatId.isEmpty()) return;

        String peerUid = intent.getStringExtra("peerUid");
        String peerName = intent.getStringExtra("peerName");

        final String js =
                "window.ChatLANOpenFromPush && window.ChatLANOpenFromPush("
                + JSONObject.quote(chatId) + ","
                + JSONObject.quote(peerUid == null ? "" : peerUid) + ","
                + JSONObject.quote(peerName == null ? "" : peerName) + ");";

        webView.postDelayed(() -> webView.evaluateJavascript(js, null), 900);
        intent.removeExtra("chatId");
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST) {
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    result = new Uri[count];
                    for (int i = 0; i < count; i++) {
                        result[i] = data.getClipData().getItemAt(i).getUri();
                    }
                } else if (data.getData() != null) {
                    result = new Uri[]{data.getData()};
                }
            }

            if (fileCallback != null) {
                fileCallback.onReceiveValue(result);
                fileCallback = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}
