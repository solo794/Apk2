package com.salman.masarifi;

import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Thin bridge to AndroidX Biometric so the app's data can be gated behind the device's own
 * fingerprint/face/PIN prompt. There is no in-app password to manage — this only ever asks the
 * OS "is this the device owner?" and reports success/failure back to JS.
 */
@CapacitorPlugin(name = "BiometricAuth")
public class BiometricAuthPlugin extends Plugin {

    private static final int ALLOWED = BiometricManager.Authenticators.BIOMETRIC_WEAK
        | BiometricManager.Authenticators.DEVICE_CREDENTIAL;

    @PluginMethod
    public void isAvailable(PluginCall call) {
        BiometricManager manager = BiometricManager.from(getContext());
        int result = manager.canAuthenticate(ALLOWED);
        JSObject ret = new JSObject();
        ret.put("available", result == BiometricManager.BIOMETRIC_SUCCESS);
        call.resolve(ret);
    }

    @PluginMethod
    public void authenticate(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(call.getString("title", "تأكيد الهوية"))
                .setSubtitle(call.getString("subtitle", "افتح متابعة مصاريفي ببصمتك أو قفل الشاشة"))
                .setAllowedAuthenticators(ALLOWED)
                .build();

            BiometricPrompt prompt = new BiometricPrompt(
                (FragmentActivity) getActivity(),
                ContextCompat.getMainExecutor(getContext()),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        JSObject ret = new JSObject();
                        ret.put("success", true);
                        call.resolve(ret);
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        JSObject ret = new JSObject();
                        ret.put("success", false);
                        ret.put("error", errString != null ? errString.toString() : "error");
                        call.resolve(ret);
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        // one failed fingerprint match — not terminal, the system prompt stays open for another try
                    }
                }
            );
            prompt.authenticate(promptInfo);
        });
    }
}
