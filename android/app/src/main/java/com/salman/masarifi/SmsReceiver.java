package com.salman.masarifi;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import androidx.core.app.NotificationCompat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Listens for incoming SMS (manifest-registered so it also fires while the app process isn't
 * running) and, when the sender looks like one of the configured bank sender IDs, stashes the raw
 * message in SharedPreferences for the JS side to parse/confirm on next open, and shows a system
 * notification right away so the user isn't stuck waiting for the app to be opened.
 *
 * This never creates an expense by itself — it only surfaces a *candidate* transaction that the
 * user reviews and confirms inside the app (see SmsReaderPlugin + the JS parseBankSms() flow).
 */
public class SmsReceiver extends BroadcastReceiver {
    static final String PREFS = "masarifi_sms_prefs";
    static final String KEY_PENDING = "pending_sms";
    static final String KEY_SENDERS = "bank_senders";
    static final String DEFAULT_SENDERS = "QNB,CIB";
    private static final String CHANNEL_ID = "sms_tx_channel";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String[] senders = prefs.getString(KEY_SENDERS, DEFAULT_SENDERS).split(",");

        // One broadcast can carry several PDUs: either a few whole SMS messages received in the
        // same batch, or the several parts of ONE long/multi-part message the carrier split up.
        // getMessagesFromIntent() hands back one SmsMessage per PDU either way, so a naive
        // "handle every SmsMessage as its own transaction" loop (the old code) processes each
        // part of a long message separately — this is exactly the "same message read twice, once
        // whole and once as a fragment with a wrong-looking sender" the user reported, since a
        // continuation part occasionally reports no/garbled originating address. Group parts by
        // sender (in order) and concatenate their bodies back into one logical message first; a
        // part with no address is folded into whichever message is currently being built instead
        // of starting a bogus separate entry.
        LinkedHashMap<String, StringBuilder> bodyBySender = new LinkedHashMap<>();
        String lastSender = null;
        for (SmsMessage msg : Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
            String part = msg.getMessageBody();
            if (part == null) continue;
            String sender = msg.getOriginatingAddress();
            if (sender == null || sender.trim().isEmpty()) sender = lastSender;
            if (sender == null) continue;
            lastSender = sender;
            StringBuilder sb = bodyBySender.get(sender);
            if (sb == null) {
                sb = new StringBuilder();
                bodyBySender.put(sender, sb);
            }
            sb.append(part);
        }

        long now = System.currentTimeMillis();
        for (Map.Entry<String, StringBuilder> entry : bodyBySender.entrySet()) {
            String sender = entry.getKey();
            String body = entry.getValue().toString();
            if (!matchesBank(sender, senders)) continue;

            storePending(prefs, sender, body, now);
            showNotification(context, sender, body);
        }
    }

    private boolean matchesBank(String sender, String[] senders) {
        String s = sender.toUpperCase();
        for (String b : senders) {
            String trimmed = b.trim().toUpperCase();
            if (!trimmed.isEmpty() && s.contains(trimmed)) return true;
        }
        return false;
    }

    private void storePending(SharedPreferences prefs, String sender, String body, long ts) {
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY_PENDING, "[]"));
            JSONObject obj = new JSONObject();
            obj.put("sender", sender);
            obj.put("body", body);
            obj.put("timestamp", ts);
            arr.put(obj);
            prefs.edit().putString(KEY_PENDING, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void showNotification(Context context, String sender, String body) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "معاملات بنكية مكتشفة", NotificationManager.IMPORTANCE_HIGH
            );
            nm.createNotificationChannel(channel);
        }

        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent pi = PendingIntent.getActivity(context, 0, launchIntent, flags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("معاملة بنكية جديدة")
            .setContentText("رسالة من " + sender + " — افتح التطبيق للمراجعة")
            .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi);

        nm.notify((int) (System.currentTimeMillis() % Integer.MAX_VALUE), builder.build());
    }
}
