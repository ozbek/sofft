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
import android.os.Bundle;
import android.os.Handler;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

public class OffTimeOut extends PreferenceActivity implements DialogInterface.OnDismissListener {
    private CheckBoxPreference mNeverTimeOutCheckBoxPref;
    private CheckBoxPreference mStayOnWhilePluggedCheckBoxPref;
    private EditText editText;
    private InputMethodManager imm;
    private ContentResolver cr;
    private static final int DIALOG_CUSTOM_TIMEOUT = 101;
    private static final int DIALOG_DEFAULT_TIMEOUT = 202;
    private static final String SCREEN_OFF_TIMEOUT = "uz_efir_screen_off";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.setTitle(R.string.app_fullname);
        imm = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        cr = getContentResolver();

        try {
            Settings.System.getInt(cr, SCREEN_OFF_TIMEOUT);
        } catch (SettingNotFoundException snfe1) {
            try {
                int settingsValue = Settings.System.getInt(cr, Settings.System.SCREEN_OFF_TIMEOUT);
                Settings.System.putInt(cr, SCREEN_OFF_TIMEOUT, settingsValue);
            } catch (SettingNotFoundException snfe2) {
                // OK, give up and just set it as one minute!
                Settings.System.putInt(cr, SCREEN_OFF_TIMEOUT, 60000);
            }
        }

        addPreferencesFromResource(R.xml.preference_screentimeout);
        mNeverTimeOutCheckBoxPref = (CheckBoxPreference) getPreferenceScreen().findPreference("never");
        mStayOnWhilePluggedCheckBoxPref = (CheckBoxPreference) getPreferenceScreen().findPreference("plugged");
    }

    @Override
    protected void onResume() {
        super.onResume();
        mNeverTimeOutCheckBoxPref.setChecked(Settings.System.getInt(cr,
                Settings.System.SCREEN_OFF_TIMEOUT, -1) == -1);
        mStayOnWhilePluggedCheckBoxPref.setChecked(Settings.System.getInt(cr,
                Settings.System.STAY_ON_WHILE_PLUGGED_IN, 0) != 0);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        String key = preference.getKey();
        if (preference == mNeverTimeOutCheckBoxPref) {
            if (mNeverTimeOutCheckBoxPref.isChecked()) {
                Settings.System.putInt(cr,
                        Settings.System.SCREEN_OFF_TIMEOUT, -1);
            } else {
                Settings.System.putInt(cr,
                        Settings.System.SCREEN_OFF_TIMEOUT,
                        Settings.System.getInt(cr, SCREEN_OFF_TIMEOUT, 60000));
            }
        } else if ("custom".equals(key)) {
            showDialog(DIALOG_CUSTOM_TIMEOUT);
        } else if ("default_timeout".equals(key)) {
            showDialog(DIALOG_DEFAULT_TIMEOUT);
        } else if (preference == mStayOnWhilePluggedCheckBoxPref) {
            Settings.System.putInt(cr,
                    Settings.System.STAY_ON_WHILE_PLUGGED_IN,
                    mStayOnWhilePluggedCheckBoxPref.isChecked() ? (BatteryManager.BATTERY_PLUGGED_AC | BatteryManager.BATTERY_PLUGGED_USB)
                            : 0);
        }

        return false;
    }

    @Override
    protected Dialog onCreateDialog (int id) {
        LayoutInflater layoutInflater = LayoutInflater.from(this);
        final View textInputView = layoutInflater.inflate(R.layout.dialog, null);

        switch (id) {
            case DIALOG_CUSTOM_TIMEOUT:
                editText = (EditText)textInputView.findViewById(R.id.custom_box);
                textInputView.findViewById(R.id.default_box).setVisibility(View.GONE);
                return new AlertDialog.Builder(this)
                    .setView(textInputView)
                    .setTitle(R.string.custom_timeout)
                    .setMessage(R.string.custom_timeout_dialog_msg)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            String inputString = editText.getText().toString();
                            if (inputString.length() > 0) { // Skip if nothing was entered
                                int userInput;
                                try {
                                    userInput = Integer.parseInt(inputString);
                                    userInput = userInput * 60 * 1000;
                                } catch (NumberFormatException nfe) {
                                    // Snap, something went wrong!
                                    userInput = Settings.System.getInt(cr, SCREEN_OFF_TIMEOUT, 60000);
                                }
                                
                                if (!(userInput <= 0)) {
                                    /* Use the value only if it is something positive.
                                     * Don't be fooled by inputString.length() > 0 above.
                                     * Users are *crazy*, they will try to input several 'zero's just to see what will happen.
                                     */                                     
                                    Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, userInput);
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
                editText = (EditText)textInputView.findViewById(R.id.default_box);
                textInputView.findViewById(R.id.custom_box).setVisibility(View.GONE);
                return new AlertDialog.Builder(this)
                    .setView(textInputView)
                    .setTitle(R.string.default_timeout)
                    .setMessage(R.string.default_timeout_dialog_msg)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // See the comments above
                            String inputString = editText.getText().toString();
                            if (inputString.length() > 0) {
                                int userInput;
                                try {
                                    userInput = Integer.parseInt(inputString);
                                    userInput = userInput * 60 * 1000;
                                } catch (NumberFormatException nfe) {
                                    userInput = Settings.System.getInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, 60000);
                                }

                                if (!(userInput <= 0))
                                    Settings.System.putInt(cr, SCREEN_OFF_TIMEOUT, userInput);
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

            default:
                break;
        } // End switch

        return null;
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        dialog.setOnDismissListener(this);
        String defText="";
        int currentValue = 0;
        switch (id) {
            case DIALOG_CUSTOM_TIMEOUT:
                currentValue = Settings.System.getInt(cr,
                        Settings.System.SCREEN_OFF_TIMEOUT, 60000);
                if (currentValue <= 0) break; // So that we won't have to do all the heck routines below 

                defText = new Integer(currentValue / (60 * 1000)).toString();
                if (currentValue < 60000) {
                    /* If the current screen timeout value is less than 60 seconds
                     * show it as a fraction of a minute
                     */                     
                    defText = new Double(currentValue / (60.0 * 1000)).toString();
                }
                break;

            case DIALOG_DEFAULT_TIMEOUT:
                currentValue = Settings.System.getInt(cr,
                        SCREEN_OFF_TIMEOUT, 60000);
                if (currentValue <= 0) break;

                defText = new Integer(currentValue / (60 * 1000)).toString();
                if (currentValue < 60000) {
                    defText = new Double(currentValue / (60.0 * 1000)).toString();
                }
        } // End switch

        editText.setText(defText);
        editText.selectAll();
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            public void run() {
                imm.showSoftInput(editText, 1);
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
