package com.salman.masarifi;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/**
 * Home-screen widget: two buttons ("type" / "speak") that jump straight into MainActivity with a
 * distinct action string on the launch Intent. It never touches app data itself — RemoteViews
 * can't reliably host a real text field anyway (no official EditText support), so the widget's
 * only job is telling the already-running (or freshly-launched) app which quick-add flow to run;
 * MainActivity.handleQuickAddIntent() stashes that choice for JS to pick up (see QuickAddPlugin).
 */
public class QuickAddWidgetProvider extends AppWidgetProvider {
    static final String ACTION_QUICK_ADD_TEXT = "com.salman.masarifi.QUICK_ADD_TEXT";
    static final String ACTION_QUICK_ADD_VOICE = "com.salman.masarifi.QUICK_ADD_VOICE";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_quick_add);
            views.setOnClickPendingIntent(R.id.widgetTextBtn, launchPendingIntent(context, ACTION_QUICK_ADD_TEXT, 1));
            views.setOnClickPendingIntent(R.id.widgetVoiceBtn, launchPendingIntent(context, ACTION_QUICK_ADD_VOICE, 2));
            manager.updateAppWidget(id, views);
        }
    }

    private PendingIntent launchPendingIntent(Context context, String action, int requestCode) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setAction(action);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
