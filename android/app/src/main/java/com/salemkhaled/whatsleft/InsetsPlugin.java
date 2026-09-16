package com.salemkhaled.whatsleft;

import android.view.View;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Lets JS ASK for the system-bar insets instead of only waiting to be told.
 *
 * MainActivity already pushes them into CSS variables from an OnApplyWindowInsetsListener, but that
 * push races the page load: the listener fires once on the first layout pass, and if that happens
 * before index.html finishes loading, the inline style it writes belongs to the document being
 * replaced. After that the listener only fires again when the insets CHANGE (rotation, keyboard),
 * so on a normal launch it may never run again and --sa-top stays unset — the header then renders
 * under the status bar. Which side of the race a device lands on is pure timing, which is why the
 * same build looked correct on one phone and overlapped on another.
 *
 * A pull has no race: JS calls this once the document exists, and the answer is read live from the
 * view tree. The push listener stays for changes that happen later.
 */
@CapacitorPlugin(name = "Insets")
public class InsetsPlugin extends Plugin {

    @PluginMethod
    public void get(PluginCall call) {
        JSObject ret = new JSObject();
        View webView = getBridge().getWebView();
        WindowInsetsCompat windowInsets = webView == null ? null : ViewCompat.getRootWindowInsets(webView);
        Insets bars = windowInsets == null
            ? Insets.NONE
            : windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
        float density = getContext().getResources().getDisplayMetrics().density;
        // CSS pixels, same unit the --sa-* variables are written in.
        ret.put("top", bars.top / density);
        ret.put("bottom", bars.bottom / density);
        call.resolve(ret);
    }
}
