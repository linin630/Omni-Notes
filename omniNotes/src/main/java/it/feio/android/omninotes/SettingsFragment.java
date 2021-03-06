/*******************************************************************************
 * Copyright 2014 Federico Iosue (federico.iosue@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package it.feio.android.omninotes;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.*;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import com.afollestad.materialdialogs.MaterialDialog;
import com.google.analytics.tracking.android.Fields;
import com.google.analytics.tracking.android.MapBuilder;
import it.feio.android.omninotes.async.DataBackupIntentService;
import it.feio.android.omninotes.models.ONStyle;
import it.feio.android.omninotes.utils.*;
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;


public class SettingsFragment extends PreferenceFragment {

    private SharedPreferences prefs;

    AboutOrStatsThread mAboutOrStatsThread;
    private int aboutClickCounter = 0;
    private final int SPRINGPAD_IMPORT = 0;
    private final int RINGTONE_REQUEST_CODE = 100;
    public final static String XML_NAME = "xmlName";
	private Activity activity;


	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int xmlId = getXmlId() > 0 ? getXmlId() : R.xml.settings;
        addPreferencesFromResource(xmlId);
    }


	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		this.activity = activity;
		Log.d("asd", activity.toString());
		prefs = activity.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_MULTI_PROCESS);
		setTitle();
	}


	private int getXmlId() {
        if (getArguments() == null || !getArguments().containsKey(XML_NAME)) return 0;
        String xmlName = getArguments().getString(XML_NAME);
        return getActivity().getResources().getIdentifier(xmlName, "xml",
                getActivity().getPackageName());
    }


    private void setTitle() {
        String title = getString(R.string.settings);
        if (getArguments() != null && getArguments().containsKey(XML_NAME)) {
            String xmlName = getArguments().getString(XML_NAME);
            if (!TextUtils.isEmpty(xmlName)) {
                int stringResourceId = getActivity().getResources().getIdentifier(xmlName.replace("settings_",
                                "settings_screen_"), "string", getActivity().getPackageName());
                title = stringResourceId != 0 ? getString(stringResourceId) : title;
            }
        }
        Toolbar toolbar = ((Toolbar) getActivity().findViewById(R.id.toolbar));
        if (toolbar != null) toolbar.setTitle(title);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                getActivity().onBackPressed();
                break;
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        super.onPreferenceTreeClick(preferenceScreen, preference);
        if (preference instanceof PreferenceScreen) {
            ((SettingsActivity) getActivity()).switchToScreen(preference.getKey());
        }
        return false;
    }


    @SuppressWarnings("deprecation")
    @Override
    public void onResume() {
        super.onResume();

        // Export notes
        Preference export = findPreference("settings_export_data");
        if (export != null) {
			export.setOnPreferenceClickListener(arg0 -> {

				// Inflate layout
				LayoutInflater inflater = getActivity().getLayoutInflater();
				View v = inflater.inflate(R.layout.dialog_backup_layout, null);

				// Finds actually saved backups names
				final List<String> backups = Arrays.asList(StorageHelper.getExternalStoragePublicDir().list());

				// Sets default export file name
				SimpleDateFormat sdf = new SimpleDateFormat(Constants.DATE_FORMAT_EXPORT);
				String fileName = sdf.format(Calendar.getInstance().getTime());
				final EditText fileNameEditText = (EditText) v.findViewById(R.id.export_file_name);
				final TextView backupExistingTextView = (TextView) v.findViewById(R.id.backup_existing);
				fileNameEditText.setHint(fileName);
				fileNameEditText.addTextChangedListener(new TextWatcher() {
					@Override
					public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {}
					@Override
					public void beforeTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {}
					@Override
					public void afterTextChanged(Editable arg0) {
						if (backups.contains(arg0.toString())) {
							backupExistingTextView.setText(R.string.backup_existing);
						} else {
							backupExistingTextView.setText("");
						}
					}
				});

				new MaterialDialog.Builder(getActivity())
						.title(R.string.data_export_message)
						.customView(v, false)
						.content(R.string.delete_note_confirmation)
						.positiveText(R.string.confirm)
						.callback(new MaterialDialog.ButtonCallback() {
							@Override
							public void onPositive(MaterialDialog materialDialog) {
								// An IntentService will be launched to accomplish the export task
								Intent service = new Intent(getActivity(), DataBackupIntentService.class);
								service.setAction(DataBackupIntentService.ACTION_DATA_EXPORT);
								String backupName = StringUtils.isEmpty(fileNameEditText.getText().toString()) ?
										fileNameEditText.getHint().toString() : fileNameEditText.getText().toString();
								service.putExtra(DataBackupIntentService.INTENT_BACKUP_NAME, backupName);
								getActivity().startService(service);
							}
						}).build().show();

				return false;
			});
		}


        // Import notes
        Preference importData = findPreference("settings_import_data");
        if (importData != null) {
            importData.setOnPreferenceClickListener(arg0 -> {

				final CharSequence[] backups = StorageHelper.getExternalStoragePublicDir().list();

				if (backups.length == 0) {
					((SettingsActivity)getActivity()).showMessage(R.string.no_backups_available, ONStyle.WARN);
				} else {

					MaterialDialog importDialog = new MaterialDialog.Builder(getActivity())
							.title(R.string.data_import_message)
							.content(R.string.delete_note_confirmation)
							.items(backups)
							.positiveText(R.string.confirm)
							.callback(new MaterialDialog.ButtonCallback() {
								@Override
								public void onPositive(MaterialDialog materialDialog) {

								}
							}).build();

					// OnShow is overridden to allow long-click on item so user can remove them
					importDialog.setOnShowListener(dialog -> {

						ListView lv = importDialog.getListView();
						assert lv != null;
						lv.setOnItemClickListener((parent, view, position, id) -> {

							// Retrieves backup size
							File backupDir = StorageHelper.getBackupDir(backups[position].toString());
							long size = StorageHelper.getSize(backupDir) / 1024;
							String sizeString = size > 1024 ? size / 1024 + "Mb" : size + "Kb";

							// Check preference presence
							String prefName = StorageHelper.getSharedPreferencesFile(getActivity()).getName();
							boolean hasPreferences = (new File(backupDir, prefName)).exists();

							String message = backups[position]
									+ " (" + sizeString
									+ (hasPreferences ? " " + getString(R.string.settings_included) : "")
									+ ")";

							new MaterialDialog.Builder(getActivity())
									.title(R.string.confirm_restoring_backup)
									.content(message)
									.positiveText(R.string.confirm)
									.callback(new MaterialDialog.ButtonCallback() {
										@Override
										public void onPositive(MaterialDialog materialDialog) {
											importDialog.dismiss();
											// An IntentService will be launched to accomplish the import task
											Intent service = new Intent(getActivity(),
													DataBackupIntentService.class);
											service.setAction(DataBackupIntentService.ACTION_DATA_IMPORT);
											service.putExtra(DataBackupIntentService.INTENT_BACKUP_NAME,
													backups[position]);
											getActivity().startService(service);
										}
									}).build().show();
						});

						// Creation of backup removal dialog
						lv.setOnItemLongClickListener((parent, view, position, id) -> {

							// Retrieves backup size
							File backupDir = StorageHelper.getBackupDir(backups[position].toString());
							long size = StorageHelper.getSize(backupDir) / 1024;
							String sizeString = size > 1024 ? size / 1024 + "Mb" : size + "Kb";

							new MaterialDialog.Builder(getActivity())
									.title(R.string.confirm_removing_backup)
									.content(backups[position] + "" + " (" + sizeString + ")")
									.positiveText(R.string.confirm)
									.callback(new MaterialDialog.ButtonCallback() {
										@Override
										public void onPositive(MaterialDialog materialDialog) {
											importDialog.dismiss();
											// An IntentService will be launched to accomplish the deletion task
											Intent service = new Intent(getActivity(),
													DataBackupIntentService.class);
											service.setAction(DataBackupIntentService.ACTION_DATA_DELETE);
											service.putExtra(DataBackupIntentService.INTENT_BACKUP_NAME,
													backups[position]);
											getActivity().startService(service);
										}
									}).build().show();

							return true;
						});
					});

					importDialog.show();
				}
				return false;
			});
        }


        // Import notes from Springpad export zip file
        Preference importFromSpringpad = findPreference("settings_import_from_springpad");
        if (importFromSpringpad != null) {
            importFromSpringpad.setOnPreferenceClickListener(arg0 -> {
				Intent intent;
				intent = new Intent(Intent.ACTION_GET_CONTENT);
				intent.addCategory(Intent.CATEGORY_OPENABLE);
				intent.setType("application/zip");
				if (!IntentChecker.isAvailable(getActivity(), intent, null)) {
					Toast.makeText(getActivity(), R.string.feature_not_available_on_this_device,
							Toast.LENGTH_SHORT).show();
					return false;
				}
				startActivityForResult(intent, SPRINGPAD_IMPORT);
				return false;
			});
        }


//		Preference syncWithDrive = findPreference("settings_backup_drive");
//		importFromSpringpad.setOnPreferenceClickListener(new OnPreferenceClickListener() {
//			@Override
//			public boolean onPreferenceClick(Preference arg0) {
//				Intent intent;
//				intent = new Intent(Intent.ACTION_GET_CONTENT);
//				intent.addCategory(Intent.CATEGORY_OPENABLE);
//				intent.setType("application/zip");
//				if (!IntentChecker.isAvailable(getActivity(), intent, null)) {
//					Crouton.makeText(getActivity(), R.string.feature_not_available_on_this_device,
// ONStyle.ALERT).show();
//					return false;
//				}
//				startActivityForResult(intent, SPRINGPAD_IMPORT);
//				return false;
//			}
//		});


        // Swiping action
        final CheckBoxPreference swipeToTrash = (CheckBoxPreference) findPreference("settings_swipe_to_trash");
        if (swipeToTrash != null) {
            if (prefs.getBoolean("settings_swipe_to_trash", false)) {
                swipeToTrash.setChecked(true);
                swipeToTrash.setSummary(getResources().getString(R.string.settings_swipe_to_trash_summary_2));
            } else {
                swipeToTrash.setSummary(getResources().getString(R.string.settings_swipe_to_trash_summary_1));
                swipeToTrash.setChecked(false);
            }
            swipeToTrash.setOnPreferenceChangeListener((preference, newValue) -> {
				if ((Boolean) newValue) {
					swipeToTrash.setSummary(getResources().getString(R.string.settings_swipe_to_trash_summary_2));
				} else {
					swipeToTrash.setSummary(getResources().getString(R.string.settings_swipe_to_trash_summary_1));
				}
				swipeToTrash.setChecked((Boolean) newValue);
				return false;
			});
        }


        // Show uncategorized notes in menu
        final CheckBoxPreference showUncategorized = (CheckBoxPreference) findPreference(Constants
                .PREF_SHOW_UNCATEGORIZED);
        if (showUncategorized != null) {
            showUncategorized.setOnPreferenceChangeListener((preference, newValue) -> {
				showUncategorized.setChecked((Boolean) newValue);
				return false;
			});
        }


        // Show Automatically adds location to new notes
        final CheckBoxPreference autoLocation = (CheckBoxPreference) findPreference(Constants.PREF_AUTO_LOCATION);
        if (autoLocation != null) {
            autoLocation.setOnPreferenceChangeListener((preference, newValue) -> {
				autoLocation.setChecked((Boolean) newValue);
				return false;
			});
        }


        // Maximum video attachment size
        final EditTextPreference maxVideoSize = (EditTextPreference) findPreference("settings_max_video_size");
        if (maxVideoSize != null) {
            String maxVideoSizeValue = prefs.getString("settings_max_video_size", getString(R.string.not_set));
            maxVideoSize.setSummary(getString(R.string.settings_max_video_size_summary) + ": " + String.valueOf
                    (maxVideoSizeValue));
            maxVideoSize.setOnPreferenceChangeListener((preference, newValue) -> {
				maxVideoSize.setSummary(getString(R.string.settings_max_video_size_summary) + ": " + String
						.valueOf(newValue));
				prefs.edit().putString("settings_max_video_size", newValue.toString()).commit();
				return false;
			});
        }


        // Set notes' protection password
        Preference password = findPreference("settings_password");
        if (password != null) {
            password.setOnPreferenceClickListener(preference -> {
				Intent passwordIntent = new Intent(getActivity(), PasswordActivity.class);
				startActivity(passwordIntent);
				return false;
			});
        }


        // Use password to grant application access
        final CheckBoxPreference passwordAccess = (CheckBoxPreference) findPreference("settings_password_access");
        if (passwordAccess != null) {
            if (prefs.getString(Constants.PREF_PASSWORD, null) == null) {
                passwordAccess.setEnabled(false);
                passwordAccess.setChecked(false);
            } else {
                passwordAccess.setEnabled(true);
            }
            passwordAccess.setOnPreferenceChangeListener((preference, newValue) -> {
				BaseActivity.requestPassword(getActivity(), passwordConfirmed -> {
					if (passwordConfirmed) {
						passwordAccess.setChecked((Boolean) newValue);
					}
				});
				return false;
			});
        }


        // Languages
        ListPreference lang = (ListPreference) findPreference("settings_language");
        if (lang != null) {
            String languageName = getResources().getConfiguration().locale.getDisplayName();
            lang.setSummary(languageName.substring(0, 1).toUpperCase(getResources().getConfiguration().locale)
                    + languageName.substring(1, languageName.length()));
            lang.setOnPreferenceChangeListener((preference, value) -> {
				OmniNotes.updateLanguage(getActivity(), value.toString());
				MiscUtils.restartApp(getActivity().getApplicationContext(), MainActivity.class);
				return false;
			});
        }


        // Text size
        final ListPreference textSize = (ListPreference) findPreference("settings_text_size");
        if (textSize != null) {
            int textSizeIndex = textSize.findIndexOfValue(prefs.getString("settings_text_size", "default"));
            String textSizeString = getResources().getStringArray(R.array.text_size)[textSizeIndex];
            textSize.setSummary(textSizeString);
            textSize.setOnPreferenceChangeListener((preference, newValue) -> {
				int textSizeIndex1 = textSize.findIndexOfValue(newValue.toString());
				String checklistString = getResources().getStringArray(R.array.text_size)[textSizeIndex1];
				textSize.setSummary(checklistString);
				prefs.edit().putString("settings_text_size", newValue.toString()).commit();
				textSize.setValueIndex(textSizeIndex1);
				return false;
			});
        }


        // Application's colors
        final ListPreference colorsApp = (ListPreference) findPreference("settings_colors_app");
        if (colorsApp != null) {
            int colorsAppIndex = colorsApp.findIndexOfValue(prefs.getString("settings_colors_app",
                    Constants.PREF_COLORS_APP_DEFAULT));
            String colorsAppString = getResources().getStringArray(R.array.colors_app)[colorsAppIndex];
            colorsApp.setSummary(colorsAppString);
            colorsApp.setOnPreferenceChangeListener((preference, newValue) -> {
				int colorsAppIndex1 = colorsApp.findIndexOfValue(newValue.toString());
				String colorsAppString1 = getResources().getStringArray(R.array.colors_app)[colorsAppIndex1];
				colorsApp.setSummary(colorsAppString1);
				prefs.edit().putString("settings_colors_app", newValue.toString()).commit();
				colorsApp.setValueIndex(colorsAppIndex1);
				return false;
			});
        }


        // Checklists
        final ListPreference checklist = (ListPreference) findPreference("settings_checked_items_behavior");
        if (checklist != null) {
            int checklistIndex = checklist.findIndexOfValue(prefs.getString("settings_checked_items_behavior", "0"));
            String checklistString = getResources().getStringArray(R.array.checked_items_behavior)[checklistIndex];
            checklist.setSummary(checklistString);
            checklist.setOnPreferenceChangeListener((preference, newValue) -> {
				int checklistIndex1 = checklist.findIndexOfValue(newValue.toString());
				String checklistString1 = getResources().getStringArray(R.array.checked_items_behavior)
						[checklistIndex1];
				checklist.setSummary(checklistString1);
				prefs.edit().putString("settings_checked_items_behavior", newValue.toString()).commit();
				checklist.setValueIndex(checklistIndex1);
				return false;
			});
        }


        // Widget's colors
        final ListPreference colorsWidget = (ListPreference) findPreference("settings_colors_widget");
        if (colorsWidget != null) {
            int colorsWidgetIndex = colorsWidget.findIndexOfValue(prefs.getString("settings_colors_widget",
                    Constants.PREF_COLORS_APP_DEFAULT));
            String colorsWidgetString = getResources().getStringArray(R.array.colors_widget)[colorsWidgetIndex];
            colorsWidget.setSummary(colorsWidgetString);
            colorsWidget.setOnPreferenceChangeListener((preference, newValue) -> {
				int colorsWidgetIndex1 = colorsWidget.findIndexOfValue(newValue.toString());
				String colorsWidgetString1 = getResources().getStringArray(R.array.colors_widget)[colorsWidgetIndex1];
				colorsWidget.setSummary(colorsWidgetString1);
				prefs.edit().putString("settings_colors_widget", newValue.toString()).commit();
				colorsWidget.setValueIndex(colorsWidgetIndex1);
				return false;
			});
        }


        // Notification snooze delay
        final EditTextPreference snoozeDelay = (EditTextPreference) findPreference
                ("settings_notification_snooze_delay");
        if (snoozeDelay != null) {
            String snooze = prefs.getString("settings_notification_snooze_delay", Constants.PREF_SNOOZE_DEFAULT);
            snooze = TextUtils.isEmpty(snooze) ? Constants.PREF_SNOOZE_DEFAULT : snooze;
            snoozeDelay.setSummary(String.valueOf(snooze) + " " + getString(R.string.minutes));
            snoozeDelay.setOnPreferenceChangeListener((preference, newValue) -> {
				String snoozeUpdated = TextUtils.isEmpty(String.valueOf(newValue)) ? Constants
						.PREF_SNOOZE_DEFAULT : String.valueOf(newValue);
				snoozeDelay.setSummary(snoozeUpdated + " " + getString(R.string.minutes));
				prefs.edit().putString("settings_notification_snooze_delay", snoozeUpdated).apply();
				return false;
			});
        }


        // Changelog
        Preference changelog = findPreference("settings_changelog");
        if (changelog != null) {
            changelog.setOnPreferenceClickListener(arg0 -> {
				new MaterialDialog.Builder(activity)
						.customView(R.layout.activity_changelog, false)
						.positiveText(R.string.ok)
						.build().show();
				return false;
			});
            // Retrieval of installed app version to write it as summary
            PackageInfo pInfo;
            String versionString = "";
            try {
                pInfo = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0);
                versionString = pInfo.versionName;
            } catch (NameNotFoundException e) {
                Log.e(Constants.TAG, "Error retrieving version", e);
            }
            changelog.setSummary(versionString);
        }


        // Settings reset
        Preference resetData = findPreference("reset_all_data");
        if (resetData != null) {
            resetData.setOnPreferenceClickListener(arg0 -> {

				new MaterialDialog.Builder(activity)
						.content(R.string.reset_all_data_confirmation)
						.positiveText(R.string.confirm)
						.callback(new MaterialDialog.ButtonCallback() {
							@Override
							public void onPositive(MaterialDialog dialog) {
								prefs.edit().clear().commit();
								File db = getActivity().getDatabasePath(Constants.DATABASE_NAME);
								StorageHelper.delete(getActivity(), db.getAbsolutePath());
								File attachmentsDir = StorageHelper.getAttachmentDir(getActivity());
								StorageHelper.delete(getActivity(), attachmentsDir.getAbsolutePath());
								File cacheDir = StorageHelper.getCacheDir(getActivity());
								StorageHelper.delete(getActivity(), cacheDir.getAbsolutePath());
								MiscUtils.restartApp(getActivity().getApplicationContext(), MainActivity.class);
							}
						})
						.build().show();

				return false;
			});
        }


//		// Instructions
//		Preference instructions = findPreference("settings_tour_show_again");
//		instructions.setOnPreferenceClickListener(new OnPreferenceClickListener() {
//			@Override
//			public boolean onPreferenceClick(Preference arg0) {
//				AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getActivity());
//				// set dialog message
//				alertDialogBuilder
//						.setMessage(getString(R.string.settings_tour_show_again_summary) + "?")
//						.setPositiveButton(getString(R.string.confirm), new DialogInterface.OnClickListener() {
//							public void onClick(DialogInterface dialog, int id) {
//								AppTourHelper.reset(getActivity());
//								prefs.edit()
//										.putString(Constants.PREF_NAVIGATION,
//												getResources().getStringArray(R.array.navigation_list_codes)[0])
//										.commit();
//								OmniNotes.restartApp(getApplicationContext());
//							}
//						}).setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
//							public void onClick(DialogInterface dialog, int id) {
//								dialog.cancel();
//							}
//						});
//				// create alert dialog
//				AlertDialog alertDialog = alertDialogBuilder.create();
//				// show it
//				alertDialog.show();
//				return false;
//			}
//		});


        // Donations
//        Preference donation = findPreference("settings_donation");
//        if (donation != null) {
//            donation.setOnPreferenceClickListener(new OnPreferenceClickListener() {
//                @Override
//                public boolean onPreferenceClick(Preference preference) {
//                    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getActivity());
//
//                    ArrayList<ImageAndTextItem> options = new ArrayList<ImageAndTextItem>();
//                    options.add(new ImageAndTextItem(R.drawable.ic_paypal, getString(R.string.paypal)));
//                    options.add(new ImageAndTextItem(R.drawable.ic_bitcoin, getString(R.string.bitcoin)));
//
//                    alertDialogBuilder
//                            .setAdapter(new ImageAndTextAdapter(getActivity(), options),
//                                    new DialogInterface.OnClickListener() {
//                                        @Override
//                                        public void onClick(DialogInterface dialog, int which) {
//                                            switch (which) {
//                                                case 0:
//                                                    Intent intentPaypal = new Intent(Intent.ACTION_VIEW);
//                                                    intentPaypal.setData(Uri.parse(getString(R.string.paypal_url)));
//                                                    startActivity(intentPaypal);
//                                                    break;
//                                                case 1:
//                                                    Intent intentBitcoin = new Intent(Intent.ACTION_VIEW);
//                                                    intentBitcoin.setData(Uri.parse(getString(R.string.bitcoin_url)));
//                                                    startActivity(intentBitcoin);
//                                                    break;
//                                            }
//                                        }
//                                    });
//
//
//                    // create alert dialog
//                    AlertDialog alertDialog = alertDialogBuilder.create();
//                    // show it
//                    alertDialog.show();
//                    return false;
//                }
//            });
//        }


        // About
        Preference about = findPreference("settings_about");
        if (about != null) {
            about.setOnPreferenceClickListener(arg0 -> {
				if (mAboutOrStatsThread != null && !mAboutOrStatsThread.isAlive()) {
					aboutClickCounter = 0;
				}
				if (aboutClickCounter == 0) {
					aboutClickCounter++;
					mAboutOrStatsThread = new AboutOrStatsThread(getActivity(), aboutClickCounter);
					mAboutOrStatsThread.start();
				} else {
					mAboutOrStatsThread.setAboutClickCounter(++aboutClickCounter);
				}
				return false;
			});
        }
    }


    @Override
    public void onStart() {
        // GA tracking
        OmniNotes.getGaTracker().set(Fields.SCREEN_NAME, getClass().getName());
        OmniNotes.getGaTracker().send(MapBuilder.createAppView().build());
        super.onStart();
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (resultCode == Activity.RESULT_OK) {
            switch (requestCode) {
                case SPRINGPAD_IMPORT:
                    Uri filesUri = intent.getData();
                    String path = FileHelper.getPath(getActivity(), filesUri);
                    // An IntentService will be launched to accomplish the import task
                    Intent service = new Intent(getActivity(), DataBackupIntentService.class);
                    service.setAction(DataBackupIntentService.ACTION_DATA_IMPORT_SPRINGPAD);
                    service.putExtra(DataBackupIntentService.EXTRA_SPRINGPAD_BACKUP, path);
                    getActivity().startService(service);
                    break;

                case RINGTONE_REQUEST_CODE:
                    Uri uri = intent.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
					String notificationSound = uri == null ? null : uri.toString();
                    prefs.edit().putString("settings_notification_ringtone", notificationSound).apply();
                    break;
            }
        }
    }
}


/**
 * Thread to launch about screen or stats dialog depending on clicks
 *
 * @author fede
 */
class AboutOrStatsThread extends Thread {

    private final int ABOUT_CLICK_DELAY = 400;
    private int ABOUT_CLICKS_REQUIRED = 3;
    private int aboutClickCounter;
    private Context mContext;
    private boolean startAbout = true;

    private int aboutClickCounterInternal;


    AboutOrStatsThread(Context mContext, int aboutClickCounter) {
        this.mContext = mContext;
        this.aboutClickCounterInternal = aboutClickCounter;

    }


    @Override
    public void run() {
        try {
            Thread.sleep(ABOUT_CLICK_DELAY);
            while (aboutClickCounterInternal != aboutClickCounter) {
                if (aboutClickCounter >= ABOUT_CLICKS_REQUIRED) {
                    // Launches StatsActivity
                    Intent statsIntent = new Intent(mContext,
                            StatsActivity.class);
                    mContext.startActivity(statsIntent);
                    startAbout = false;
                    break;
                }
                Thread.sleep(ABOUT_CLICK_DELAY);
                aboutClickCounterInternal = aboutClickCounter;
            }
            if (startAbout) {
                // Launches about Activity
                Intent aboutIntent = new Intent(mContext, AboutActivity.class);
                mContext.startActivity(aboutIntent);
            }
        } catch (InterruptedException e) {
            Log.w(Constants.TAG, "Thread interrupted, I don't care", e);
        }
    }


    public void setAboutClickCounter(int aboutClickCounter) {
        this.aboutClickCounter = aboutClickCounter;
    }
}