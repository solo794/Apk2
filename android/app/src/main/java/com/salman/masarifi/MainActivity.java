package com.salman.masarifi;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.Display;
import android.view.WindowManager;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Custom plugins must be registered before super.onCreate() builds the bridge.
        registerPlugin(SmsReaderPlugin.class);
        registerPlugin(BiometricAuthPlugin.class);
        registerPlugin(GoogleAuthPlugin.class);
        registerPlugin(SpeechToTextPlugin.class);
        registerPlugin(QuickAddPlugin.class);
        super.onCreate(savedInstanceState);
        requestHighestRefreshRate();
        handleQuickAddIntent(getIntent());
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleQuickAddIntent(intent);
    }

    // Stashes which widget button was tapped (if any) for JS to pick up once the app's own state
    // is ready — see QuickAddPlugin. Doesn't touch the WebView/bridge directly, so there's no
    // race with the app's async boot sequence (loadState() etc.).
    private void handleQuickAddIntent(Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String mode = null;
        if (QuickAddWidgetProvider.ACTION_QUICK_ADD_TEXT.equals(intent.getAction())) mode = "text";
        else if (QuickAddWidgetProvider.ACTION_QUICK_ADD_VOICE.equals(intent.getAction())) mode = "voice";
        if (mode == null) return;
        SharedPreferences prefs = getSharedPreferences(QuickAddPlugin.PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(QuickAddPlugin.KEY_MODE, mode).apply();
    }

    // On some devices/OEMs the app window defaults to 60Hz even on 90/120Hz-capable
    // screens, which caps the WebView's scroll rendering to 60fps too. Explicitly ask
    // for the display's highest refresh rate at the same resolution so scrolling isn't
    // artificially throttled.
    private void requestHighestRefreshRate() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return; // Display.Mode needs API 23+
        Display display = getWindowManager().getDefaultDisplay();
        if (display == null) return;

        Display.Mode current = display.getMode();
        Display.Mode best = current;
        for (Display.Mode mode : display.getSupportedModes()) {
            boolean sameResolution = mode.getPhysicalWidth() == current.getPhysicalWidth()
                    && mode.getPhysicalHeight() == current.getPhysicalHeight();
            if (sameResolution && mode.getRefreshRate() > best.getRefreshRate()) {
                best = mode;
            }
        }

        if (best.getModeId() != current.getModeId()) {
            WindowManager.LayoutParams params = getWindow().getAttributes();
            params.preferredDisplayModeId = best.getModeId();
            getWindow().setAttributes(params);
        }
    }
}
