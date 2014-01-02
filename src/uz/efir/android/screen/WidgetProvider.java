/*
 * Copyright (C) 2011 Shuhrat Dehkanov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uz.efir.android.screen;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;
import android.widget.RemoteViews;

/**
 * Provides "Never time out" and "Stay on" ON/OFF widget
 */
public class WidgetProvider extends AppWidgetProvider {
    private static final String TAG = "WidgetProvider";
    private static final ComponentName SOFFT_APPWIDGET =
            new ComponentName("uz.efir.android.screen", "uz.efir.android.screen.WidgetProvider");
    private static final int BUTTON_NEVER_TIMEOUT = 0;
    private static final int BUTTON_STAY_ON = 1;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager,
            int[] appWidgetIds) {
        // Update each requested appWidgetId
        RemoteViews view = buildUpdate(context);

        for (int appWidgetId : appWidgetIds) {
            appWidgetManager.updateAppWidget(appWidgetId, view);
        }
    }

    @Override
    public void onEnabled(Context context) {
        PackageManager pm = context.getPackageManager();
        if (pm != null) {
            pm.setComponentEnabledSetting(SOFFT_APPWIDGET,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
        }
    }

    @Override
    public void onDisabled(Context context) {
        PackageManager pm = context.getPackageManager();
        if (pm != null) {
            pm.setComponentEnabledSetting(SOFFT_APPWIDGET,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        }
    }

    /**
     * Load image for given widget and build {@link RemoteViews} for it.
     */
    static RemoteViews buildUpdate(Context context) {
        RemoteViews views = new RemoteViews(context.getPackageName(),
                R.layout.widget);
        views.setOnClickPendingIntent(R.id.btn_never_timeout,
                getLaunchPendingIntent(context, BUTTON_NEVER_TIMEOUT));
        views.setOnClickPendingIntent(R.id.btn_stayon,
                getLaunchPendingIntent(context, BUTTON_STAY_ON));

        updateButtons(views, context);
        return views;
    }

    /**
     * Updates the widget when something changes, or when a button is pushed.
     *
     * @param context   The Context
     */
    public static void updateWidget(Context context) {
        RemoteViews views = buildUpdate(context);
        // Update specific list of appWidgetIds if given, otherwise default to all
        final AppWidgetManager gm = AppWidgetManager.getInstance(context);
        if (gm != null) {
            gm.updateAppWidget(SOFFT_APPWIDGET, views);
        }
    }

    /**
     * Updates the buttons based on the current database value.
     *
     * @param views   The RemoteViews to update.
     * @param context   The Context
     */
    private static void updateButtons(RemoteViews views, Context context) {

        if (getMode(context, BUTTON_NEVER_TIMEOUT)) {
            views.setImageViewResource(R.id.img_never_timeout,
                                       R.drawable.ic_never_timeout_on);
            views.setImageViewResource(R.id.ind_never_timeout,
                                       R.drawable.appwidget_indicator_on_l);
        } else {
            views.setImageViewResource(R.id.img_never_timeout,
                                       R.drawable.ic_never_timeout_off);
            views.setImageViewResource(R.id.ind_never_timeout,
                                       R.drawable.appwidget_indicator_off_l);
        }

        if (getMode(context, BUTTON_STAY_ON)) {
            views.setImageViewResource(R.id.img_stayon,
                                       R.drawable.ic_stay_on_while_plugged_in_on);
            views.setImageViewResource(R.id.ind_stayon,
                                       R.drawable.appwidget_indicator_on_r);
        } else {
            views.setImageViewResource(R.id.img_stayon,
                                       R.drawable.ic_stay_on_while_plugged_in_off);
            views.setImageViewResource(R.id.ind_stayon,
                                       R.drawable.appwidget_indicator_off_r);
        }
    }

    /**
     * Creates PendingIntent to notify the widget of a button click.
     *
     *
     * @param context    The Context
     * @return  PendingIntent
     */
    private static PendingIntent getLaunchPendingIntent(Context context, int buttonId) {
        Intent launchIntent = new Intent();
        launchIntent.setClass(context, WidgetProvider.class);
        launchIntent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        launchIntent.setData(Uri.parse("custom:" + buttonId));
        return PendingIntent.getBroadcast(context, 0 /* no requestCode */,
                launchIntent, 0 /* no flags */);
    }

    /**
     * Receives and processes a button pressed intent or state change.
     *
     * @param context   The Context
     * @param intent  Indicates the pressed button.
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent.hasCategory(Intent.CATEGORY_ALTERNATIVE) && intent.getData() != null) {
            toggleMode(context,
                    Integer.parseInt(intent.getData().getSchemeSpecificPart()));
        } else {
            // Don't fall-through to updating the widget.  The Intent
            // was something unrelated or that our super class took
            // care of.
            return;
        }

        // State changes fall through
        updateWidget(context);
    }

    /**
     * Gets state of SCREEN_OFF_TIMEOUT and STAY_ON_WHILE_PLUGGED_IN modes.
     *
     * @param context   The Context
     * @param which SCREEN_OFF_TIMEOUT or STAY_ON_WHILE_PLUGGED_IN
     * @return true if SCREEN_OFF_TIMEOUT is -1 (never time out) or STAY_ON_WHILE_PLUGGED_IN mode is on.
     */
    private static boolean getMode(Context context, int which) {
        if (which == BUTTON_NEVER_TIMEOUT) {
            try {
                return Settings.System.getInt(context.getContentResolver(),
                        Settings.System.SCREEN_OFF_TIMEOUT) < 0;
                } catch (SettingNotFoundException snfe) {
                    Log.d(TAG, "SCREEN_OFF_TIMEOUT row is not found");
                }
            return false;
        } else { // which == BUTTON_STAY_ON (there is no other option, but this)
            return Settings.System.getInt(context.getContentResolver(),
                    Settings.System.STAY_ON_WHILE_PLUGGED_IN, 0) > 0;
        }
    }

    /**
     * Change SCREEN_OFF_TIMEOUT and STAY_ON_WHILE_PLUGGED_IN modes.
     *
     * @param context   The Context
     * @param whichButton SCREEN_OFF_TIMEOUT or STAY_ON_WHILE_PLUGGED_IN
     */
    private void toggleMode(Context context, int whichButton) {
        switch (whichButton) {
            case BUTTON_NEVER_TIMEOUT:
                if (getMode(context, BUTTON_NEVER_TIMEOUT)) {
                    Settings.System.putInt(context.getContentResolver(),
                            Settings.System.SCREEN_OFF_TIMEOUT,
                            Settings.System.getInt(context.getContentResolver(), "uz_efir_screen_off", 60000));
                } else {
                    Settings.System.putInt(context.getContentResolver(),
                            Settings.System.SCREEN_OFF_TIMEOUT, -1);
                }
                break;
            case BUTTON_STAY_ON:
                if (getMode(context, BUTTON_STAY_ON)) {
                    Settings.System.putInt(context.getContentResolver(),
                            Settings.System.STAY_ON_WHILE_PLUGGED_IN, 0);
                } else {
                    Settings.System.putInt(context.getContentResolver(),
                            Settings.System.STAY_ON_WHILE_PLUGGED_IN,
                            BatteryManager.BATTERY_PLUGGED_AC | BatteryManager.BATTERY_PLUGGED_USB);
                }
                break;
            default:
                Log.e(TAG, "Can not toggle mode for button " + whichButton);
        }
    }
}
