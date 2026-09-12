package com.salman.masarifi;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

/**
 * Home-screen widget: two buttons ("type" / "speak") that jump straight into MainActivity with a
 * distinct action string on the launch Intent. It never touches app data itself — RemoteViews
 * can't reliably host a real text field anyway (no official EditText support), so the widget's
 * only job is telling the already-running (or freshly-launched) app which quick-add flow to run;
 * MainActivity.handleQuickAddIntent() stashes that choice for JS to pick up (see QuickAddPlugin).
 *
 * Labels follow the APP's language toggle, not the device locale: a RemoteViews widget normally
 * picks up locale-qualified resources from the system language, which would leave the widget in
 * Arabic for someone who switched the app itself to English (and vice versa). The app mirrors its
 * current language into SharedPreferences (QuickAddPlugin.setWidgetLang) and pushes a refresh, so
 * the two always agree.
 */
public class QuickAddWidgetProvider extends AppWidgetProvider {
    static final String ACTION_QUICK_ADD_TEXT = "com.salman.masarifi.QUICK_ADD_TEXT";
    static final String ACTION_QUICK_ADD_VOICE = "com.salman.masarifi.QUICK_ADD_VOICE";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        SharedPreferences prefs = context.getSharedPreferences(QuickAddPlugin.PREFS, Context.MODE_PRIVATE);
        boolean en = "en".equals(prefs.getString(QuickAddPlugin.KEY_LANG, "ar"));
        for (int id : appWidgetIds) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_quick_add);
            views.setTextViewText(R.id.widgetTitle, en ? "➕ Add an expense" : "➕ أضف مصروف");
            views.setTextViewText(R.id.widgetTextBtn, en ? "✏️ Type" : "✏️ اكتب");
            views.setTextViewText(R.id.widgetVoiceBtn, en ? "🎤 Speak" : "🎤 اتكلم");
            views.setOnClickPendingIntent(R.id.widgetTextBtn, launchPendingIntent(context, ACTION_QUICK_ADD_TEXT, 1));
            views.setOnClickPendingIntent(R.id.widgetVoiceBtn, launchPendingIntent(context, ACTION_QUICK_ADD_VOICE, 2));
            manager.updateAppWidget(id, views);
        }
    }

    /** Re-renders every placed instance of this widget — called when the app's language changes. */
    static void refreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new android.content.ComponentName(context, QuickAddWidgetProvider.class));
        if (ids != null && ids.length > 0) new QuickAddWidgetProvider().onUpdate(context, manager, ids);
    }

    private PendingIntent launchPendingIntent(Context context, String action, int requestCode) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setAction(action);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
