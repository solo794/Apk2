package com.salman.masarifi;

import androidx.activity.result.ActivityResult;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;

/**
 * Sign-in only — this plugin never touches app data itself. It hands JS a short-lived OAuth
 * access token scoped to the signed-in user's Drive "appdata" folder (a hidden per-app storage
 * space Drive gives every app, invisible in the user's regular Drive UI); the JS side
 * (googleDriveBackup.js logic in index.html) does the actual upload/download of the same JSON
 * backup format already used for local export/import, directly against the Drive REST API.
 */
@CapacitorPlugin(name = "GoogleAuth")
public class GoogleAuthPlugin extends Plugin {
    private static final String DRIVE_APPDATA_SCOPE = "oauth2:https://www.googleapis.com/auth/drive.appdata";
    private GoogleSignInClient client;

    private GoogleSignInClient client() {
        if (client == null) {
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(new Scope("https://www.googleapis.com/auth/drive.appdata"))
                .build();
            client = GoogleSignIn.getClient(getContext(), gso);
        }
        return client;
    }

    @PluginMethod
    public void signIn(PluginCall call) {
        startActivityForResult(call, client().getSignInIntent(), "signInResult");
    }

    @ActivityCallback
    private void signInResult(PluginCall call, ActivityResult result) {
        if (call == null) return;
        try {
            GoogleSignInAccount account = GoogleSignIn
                .getSignedInAccountFromIntent(result.getData())
                .getResult(ApiException.class);
            call.resolve(accountToJs(account, true));
        } catch (ApiException e) {
            JSObject ret = new JSObject();
            ret.put("success", false);
            ret.put("error", "code:" + e.getStatusCode());
            call.resolve(ret);
        }
    }

    @PluginMethod
    public void signOut(PluginCall call) {
        client().signOut().addOnCompleteListener(task -> call.resolve());
    }

    @PluginMethod
    public void getCurrentAccount(PluginCall call) {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(getContext());
        if (account == null) {
            JSObject ret = new JSObject();
            ret.put("signedIn", false);
            call.resolve(ret);
        } else {
            call.resolve(accountToJs(account, false));
        }
    }

    // Fetches a fresh short-lived access token for the Drive appdata scope. Must run off the
    // main thread — GoogleAuthUtil.getToken() blocks on a network/token-cache round trip.
    @PluginMethod
    public void getAccessToken(PluginCall call) {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(getContext());
        if (account == null || account.getAccount() == null) {
            call.reject("not signed in");
            return;
        }
        new Thread(() -> {
            try {
                String token = GoogleAuthUtil.getToken(getContext(), account.getAccount(), DRIVE_APPDATA_SCOPE);
                JSObject ret = new JSObject();
                ret.put("token", token);
                call.resolve(ret);
            } catch (Exception e) {
                call.reject("token error: " + e.getMessage());
            }
        }).start();
    }

    private JSObject accountToJs(GoogleSignInAccount account, boolean withSuccessFlag) {
        JSObject ret = new JSObject();
        if (withSuccessFlag) ret.put("success", true);
        ret.put("signedIn", true);
        ret.put("email", account.getEmail());
        ret.put("name", account.getDisplayName());
        ret.put("photoUrl", account.getPhotoUrl() != null ? account.getPhotoUrl().toString() : null);
        return ret;
    }
}
