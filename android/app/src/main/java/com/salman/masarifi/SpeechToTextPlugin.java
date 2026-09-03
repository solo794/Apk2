package com.salman.masarifi;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.speech.RecognizerIntent;
import androidx.activity.result.ActivityResult;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import java.util.ArrayList;

/**
 * Thin bridge to Android's built-in speech recognizer (part of the core SDK — no extra
 * dependency, unlike biometric/Google Sign-In). Launches the system's own "Listening..." dialog
 * (RecognizerIntent.ACTION_RECOGNIZE_SPEECH) and hands the single best transcript back to JS as
 * a plain string; JS does all the parsing (amount/category/source guessing) and always shows the
 * result for the user to review before saving anything — this plugin never writes app data.
 */
@CapacitorPlugin(
    name = "SpeechToText",
    permissions = { @Permission(strings = { Manifest.permission.RECORD_AUDIO }, alias = "record_audio") }
)
public class SpeechToTextPlugin extends Plugin {

    @PluginMethod
    public void start(PluginCall call) {
        if (getPermissionState("record_audio") == PermissionState.GRANTED) {
            startListening(call);
        } else {
            requestPermissionForAlias("record_audio", call, "recordAudioCallback");
        }
    }

    @PermissionCallback
    private void recordAudioCallback(PluginCall call) {
        if (getPermissionState("record_audio") == PermissionState.GRANTED) {
            startListening(call);
        } else {
            JSObject ret = new JSObject();
            ret.put("success", false);
            ret.put("error", "permission_denied");
            call.resolve(ret);
        }
    }

    private void startListening(PluginCall call) {
        String locale = call.getString("locale", "ar-EG");
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        try {
            startActivityForResult(call, intent, "listenResult");
        } catch (ActivityNotFoundException e) {
            // No speech-recognition service on this device (e.g. no Google app) — rare, but
            // possible on some OEM ROMs without Google services.
            JSObject ret = new JSObject();
            ret.put("success", false);
            ret.put("error", "not_available");
            call.resolve(ret);
        }
    }

    @ActivityCallback
    private void listenResult(PluginCall call, ActivityResult result) {
        if (call == null) return;
        JSObject ret = new JSObject();
        ArrayList<String> matches = (result.getResultCode() == Activity.RESULT_OK && result.getData() != null)
            ? result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            : null;
        if (matches != null && !matches.isEmpty()) {
            ret.put("success", true);
            ret.put("text", matches.get(0));
        } else {
            ret.put("success", false);
            ret.put("error", "no_match");
        }
        call.resolve(ret);
    }
}
