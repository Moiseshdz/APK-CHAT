package com.chatlan.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class ChatLANMessagingService extends FirebaseMessagingService {
    private static final String CHANNEL_ID = "chatlan_messages";

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        getSharedPreferences("chatlan_push", Context.MODE_PRIVATE)
                .edit().putString("fcm_token", token).apply();
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        Map<String, String> data = remoteMessage.getData();
        String senderName = value(data, "senderName", "ChatLAN");
        String body = value(data, "body", "Nuevo mensaje");
        String chatId = value(data, "chatId", "group:general");
        String peerUid = value(data, "senderId", "");
        String peerName = senderName;

        createChannel();

        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        open.putExtra("chatId", chatId);
        open.putExtra("peerUid", peerUid);
        open.putExtra("peerName", peerName);

        int requestCode = (chatId + System.currentTimeMillis()).hashCode();
        PendingIntent pending = PendingIntent.getActivity(
                this,
                requestCode,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT |
                        (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle(senderName)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setContentIntent(pending);

        NotificationManager nm =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(requestCode, b.build());
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Mensajes de ChatLAN",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Mensajes nuevos, chats privados y grupo general");
            channel.enableVibration(true);

            NotificationManager nm =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(channel);
        }
    }

    private static String value(Map<String, String> map, String key, String fallback) {
        String v = map.get(key);
        return v == null || v.isEmpty() ? fallback : v;
    }
}
