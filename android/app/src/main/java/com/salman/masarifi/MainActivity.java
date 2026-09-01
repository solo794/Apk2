package com.salman.masarifi;

import android.os.Build;
import android.os.Bundle;
import android.view.Display;
import android.view.WindowManager;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestHighestRefreshRate();
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
