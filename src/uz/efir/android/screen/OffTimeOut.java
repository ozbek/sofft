/*
 * Copyright (C) 2010 Shuhrat Dehkanov
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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

/**
 * A simple screen time out controller.
 * Unlike Android's default screen time out settings,
 * this class provides an input form where users can put
 * desired screen time out threshold in minutes.
 */
public class OffTimeOut extends PreferenceActivity implements DialogInterface.OnDismissListener {
    private CheckBoxPreference mNeverTimeOutCheckBoxPref;
    private CheckBoxPreference mStayOnWhilePluggedCheckBoxPref;
    private EditText mEditText;
    private ContentResolver mContentResolver;
    private static final int DIALOG_CUSTOM_TIMEOUT = 101;
    private static final int DIALOG_DEFAULT_TIMEOUT = 202;
    private static final String SOFFT_ROW_NAME = "uz_efir_screen_off";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.app_fullname);

        mContentResolver = getContentResolver();
        try {
            Settings.System.getInt(mContentResolver, SOFFT_ROW_NAME);
        } catch (SettingNotFoundException snfe1) {
            try {
                int settingsValue = Settings.System.getInt(mContentResolver, Settings.System.SCREEN_OFF_TIMEOUT);
                Settings.System.putInt(mContentResolver, SOFFT_ROW_NAME, settingsValue);
            } catch (SettingNotFoundException snfe2) {
                // OK, give up and just set it as one minute!
                Settings.System.putInt(mContentResolver, SOFFT_ROW_NAME, 60000);
            }
        }

        addPreferencesFromResource(R.xml.preference_screentimeout);
        mNeverTimeOutCheckBoxPref = (CheckBoxPreference)getPreferenceScreen().findPreference("never");
        mStayOnWhilePluggedCheckBoxPref = (CheckBoxPreference)getPreferenceScreen().findPreference("plugged");
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN) {
            // STAY_ON_WHILE_PLUGGED_IN is moved to Global table in API level 17
            mStayOnWhilePluggedCheckBoxPref.setEnabled(false);
            mStayOnWhilePluggedCheckBoxPref.setSummary(R.string.stay_on_while_plugged_in_disabled_summary);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mNeverTimeOutCheckBoxPref.setChecked(Settings.System.getInt(mContentResolver,
                Settings.System.SCREEN_OFF_TIMEOUT, -1) == -1);
        mStayOnWhilePluggedCheckBoxPref.setChecked(Settings.System.getInt(mContentResolver, Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN
                ? Settings.Global.STAY_ON_WHILE_PLUGGED_IN : Settings.System.STAY_ON_WHILE_PLUGGED_IN, 0) != 0);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        String key = preference.getKey();
        if (preference.equals(mNeverTimeOutCheckBoxPref)) {
            if (mNeverTimeOutCheckBoxPref.isChecked()) {
                Settings.System.putInt(mContentResolver, Settings.System.SCREEN_OFF_TIMEOUT, -1);
            } else {
                Settings.System.putInt(mContentResolver, Settings.System.SCREEN_OFF_TIMEOUT,
                        Settings.System.getInt(mContentResolver, SOFFT_ROW_NAME, 60000));
            }
            // Update widget button's state
            WidgetProvider.updateWidget(this);
        } else if ("custom".equals(key)) {
            showDialog(DIALOG_CUSTOM_TIMEOUT);
        } else if ("default_timeout".equals(key)) {
            showDialog(DIALOG_DEFAULT_TIMEOUT);
        } else if (preference.equals(mStayOnWhilePluggedCheckBoxPref)) {
            Settings.System.putInt(mContentResolver, Settings.System.STAY_ON_WHILE_PLUGGED_IN,
                    mStayOnWhilePluggedCheckBoxPref.isChecked()
                    ? (BatteryManager.BATTERY_PLUGGED_AC | BatteryManager.BATTERY_PLUGGED_USB) : 0);
            // Update widget button's state
            WidgetProvider.updateWidget(this);
        }

        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    @Override
    protected Dialog onCreateDialog (int id) {
        final View textInputView = getLayoutInflater().inflate(R.layout.dialog, null);

        switch (id) {
            case DIALOG_CUSTOM_TIMEOUT:
                mEditText = (EditText)textInputView.findViewById(R.id.custom_box);
                textInputView.findViewById(R.id.default_box).setVisibility(View.GONE);
                return new AlertDialog.Builder(this)
                    .setView(textInputView)
                    .setTitle(R.string.custom_timeout)
                    .setMessage(R.string.custom_timeout_dialog_msg)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            String inputString = mEditText.getText().toString();
                            if (inputString.length() > 0) { // Skip if nothing was entered
                                int userInput;
                                try {
                                    userInput = Integer.parseInt(inputString);
                                    userInput = userInput * 60 * 1000;
                                } catch (NumberFormatException nfe) {
                                    // Snap, something went wrong!
                                    userInput = Settings.System.getInt(mContentResolver, SOFFT_ROW_NAME, 60000);
                                }

                                if (userInput > 0) {
                                    /* Use the value only if it is something positive.
                                     * Don't be fooled by inputString.length() > 0 above.
                                     * Users are *crazy*, they will try to input several 'zero's just to see what will happen.
                                     */
                                    Settings.System.putInt(mContentResolver, Settings.System.SCREEN_OFF_TIMEOUT, userInput);
                                }
                            }

                            dialog.dismiss();
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    })
                    .create();

            case DIALOG_DEFAULT_TIMEOUT:
                mEditText = (EditText)textInputView.findViewById(R.id.default_box);
                textInputView.findViewById(R.id.custom_box).setVisibility(View.GONE);
                return new AlertDialog.Builder(this)
                    .setView(textInputView)
                    .setTitle(R.string.default_timeout)
                    .setMessage(R.string.default_timeout_dialog_msg)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // See the comments above
                            String inputString = mEditText.getText().toString();
                            if (inputString.length() > 0) {
                                int userInput;
                                try {
                                    userInput = Integer.parseInt(inputString);
                                    userInput = userInput * 60 * 1000;
                                } catch (NumberFormatException nfe) {
                                    userInput = Settings.System.getInt(mContentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 60000);
                                }

                                if (userInput > 0) {
                                    Settings.System.putInt(mContentResolver, SOFFT_ROW_NAME, userInput);
                                }
                            }

                            dialog.dismiss();
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    })
                    .create();

            default:
                break;
        } // End switch

        return null;
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        dialog.setOnDismissListener(this);
        String defText= "";
        int currentValue = 0;
        switch (id) {
            case DIALOG_CUSTOM_TIMEOUT:
                currentValue = Settings.System.getInt(mContentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 60000);
                if (currentValue <= 0) {
                    // So that we won't have to do all the routines below
                    break;
                }

                defText = String.valueOf(currentValue / (60 * 1000));
                if (currentValue < 60000) {
                    /* If the current screen timeout value is less than 60 seconds
                     * show it as a fraction of a minute
                     */
                    defText = String.valueOf(currentValue / (60.0 * 1000));
                }
                break;

            case DIALOG_DEFAULT_TIMEOUT:
                currentValue = Settings.System.getInt(mContentResolver, SOFFT_ROW_NAME, 60000);
                if (currentValue <= 0) {
                    break;
                }

                defText = String.valueOf(currentValue / (60 * 1000));
                if (currentValue < 60000) {
                    defText = String.valueOf(currentValue / (60.0 * 1000));
                }
        } // End switch

        mEditText.setText(defText);
        mEditText.selectAll();
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                InputMethodManager imm = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
                imm.showSoftInput(mEditText, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 200);
    }

    @Override
    public void onDismiss(DialogInterface dialogInterface) {
        /* There is that crazy bug that I could not figure out...
         * That dialogs do not get cleared when dismissed
         * So, remove them here and enjoy the rest of the party!
         */
        removeDialog(DIALOG_CUSTOM_TIMEOUT);
        removeDialog(DIALOG_DEFAULT_TIMEOUT);
    }
}
