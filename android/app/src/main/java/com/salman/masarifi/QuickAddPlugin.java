package com.salman.masarifi;

import android.content.Context;
import android.content.SharedPreferences;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Bridges the home-screen widget to JS: MainActivity stashes which widget button was tapped
 * ("text" or "voice") in SharedPreferences the moment the launch intent arrives, and JS polls
 * this plugin at the same points it already polls for pending bank-SMS transactions (app boot +
 * resume-from-background) rather than having native code inject JS directly — the app's own
 * state (openModal/startVoiceExpenseInput depend on it) isn't guaranteed ready yet at that point,
 * so JS decides when it's safe to act instead of racing a native->WebView call.
 */
@CapacitorPlugin(name = "QuickAdd")
public class QuickAddPlugin extends Plugin {
    public static final String PREFS = "quick_add_widget";
    public static final String KEY_MODE = "pending_mode";

    @PluginMethod
    public void getPendingAction(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("mode", prefs().getString(KEY_MODE, null));
        call.resolve(ret);
    }

    @PluginMethod
    public void clearPendingAction(PluginCall call) {
        prefs().edit().remove(KEY_MODE).apply();
        call.resolve();
    }

    private SharedPreferences prefs() {
        return getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
