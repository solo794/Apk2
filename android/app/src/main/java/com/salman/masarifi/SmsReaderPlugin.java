package com.salman.masarifi;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

/**
 * Bridges the JS side to SmsReceiver's SharedPreferences queue: request the RECEIVE_SMS runtime
 * permission, read/clear candidate bank transactions the receiver stashed, and read/write the
 * list of recognized bank sender IDs (defaults to QNB/CIB, editable from the app's Settings tab).
 */
@CapacitorPlugin(
    name = "SmsReader",
    permissions = { @Permission(strings = { Manifest.permission.RECEIVE_SMS }, alias = "sms") }
)
public class SmsReaderPlugin extends Plugin {

    @PluginMethod
    public void checkSmsPermission(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("granted", getPermissionState("sms") == PermissionState.GRANTED);
        call.resolve(ret);
    }

    @PluginMethod
    public void requestSmsPermission(PluginCall call) {
        if (getPermissionState("sms") == PermissionState.GRANTED) {
            JSObject ret = new JSObject();
            ret.put("granted", true);
            call.resolve(ret);
        } else {
            requestPermissionForAlias("sms", call, "smsPermsCallback");
        }
    }

    @PermissionCallback
    private void smsPermsCallback(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("granted", getPermissionState("sms") == PermissionState.GRANTED);
        call.resolve(ret);
    }

    @PluginMethod
    public void getPendingTransactions(PluginCall call) {
        JSObject ret = new JSObject();
        try {
            ret.put("items", new JSArray(prefs().getString(SmsReceiver.KEY_PENDING, "[]")));
        } catch (Exception e) {
            ret.put("items", new JSArray());
        }
        call.resolve(ret);
    }

    @PluginMethod
    public void clearPendingTransactions(PluginCall call) {
        prefs().edit().putString(SmsReceiver.KEY_PENDING, "[]").apply();
        call.resolve();
    }

    @PluginMethod
    public void getBankSenders(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("value", prefs().getString(SmsReceiver.KEY_SENDERS, SmsReceiver.DEFAULT_SENDERS));
        call.resolve(ret);
    }

    @PluginMethod
    public void setBankSenders(PluginCall call) {
        String value = call.getString("value", SmsReceiver.DEFAULT_SENDERS);
        prefs().edit().putString(SmsReceiver.KEY_SENDERS, value).apply();
        call.resolve();
    }

    private SharedPreferences prefs() {
        return getContext().getSharedPreferences(SmsReceiver.PREFS, Context.MODE_PRIVATE);
    }
}
