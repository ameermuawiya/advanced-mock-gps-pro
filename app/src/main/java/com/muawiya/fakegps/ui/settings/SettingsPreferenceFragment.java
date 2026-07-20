package com.muawiya.fakegps.ui.settings;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.muawiya.fakegps.R;
import com.muawiya.fakegps.data.LocationRepository;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsPreferenceFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {

    private LocationRepository repository;
    private ExecutorService executor;

    /*
     * Initializes the preferences from the XML resource file.
     * Sets up the repository, executor service, and custom click listeners.
     */
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);

        repository = new LocationRepository(requireContext());
        executor = Executors.newSingleThreadExecutor();

        androidx.preference.PreferenceManager.setDefaultValues(requireContext(), R.xml.preferences, false);

        setupCustomPreferenceClickListeners();
    }

    /*
     * Adjusts the padding of the RecyclerView list to prevent layout gaps.
     * Ensures the view scrolls properly with applied padding.
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getListView() != null) {
            getListView().setPadding(16, 16, 16, 16);
            getListView().setClipToPadding(false);
        }
    }

    /*
     * Assigns click listeners to specific preferences.
     * Handles navigation to developer settings, about dialog, and custom Material list dialogs.
     */
    private void setupCustomPreferenceClickListeners() {
        Preference devSettings = findPreference("btn_dev_settings");
        if (devSettings != null) {
            devSettings.setOnPreferenceClickListener(pref -> {
                try {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
                    startActivity(intent);
                } catch (Exception e) {
                    try {
                        Intent intent = new Intent(Settings.ACTION_SETTINGS);
                        startActivity(intent);
                    } catch (Exception err) {
                        Toast.makeText(getContext(), getString(R.string.settings_fragment_toast_dev_opts_inaccessible), Toast.LENGTH_SHORT).show();
                    }
                }
                return true;
            });
        }

        Preference aboutPref = findPreference("btn_about");
        if (aboutPref != null) {
            aboutPref.setOnPreferenceClickListener(pref -> {
                showAboutDeveloperDialog();
                return true;
            });
        }

        Preference themePref = findPreference("selected_theme");
        if (themePref != null) {
            themePref.setOnPreferenceClickListener(pref -> {
                showMaterialListDialog(
                        pref, 
                        R.array.theme_entries, 
                        R.array.theme_values, 
                        "selected_theme", 
                        "system"
                );
                return true;
            });
        }

        Preference mapStylePref = findPreference("selected_map_style");
        if (mapStylePref != null) {
            mapStylePref.setOnPreferenceClickListener(pref -> {
                showMaterialListDialog(
                        pref, 
                        R.array.map_style_entries, 
                        R.array.map_style_values, 
                        "selected_map_style", 
                        "normal"
                );
                return true;
            });
        }
    }

    /*
     * Displays a Material 3 single-choice dialog for list preferences.
     * Saves the selected value into SharedPreferences upon user selection.
     */
    private void showMaterialListDialog(Preference preference, int entriesArrayId, int valuesArrayId, String prefKey, String defaultValue) {
        String[] entries = getResources().getStringArray(entriesArrayId);
        String[] values = getResources().getStringArray(valuesArrayId);
        
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        if (prefs == null) return;
        
        String currentValue = prefs.getString(prefKey, defaultValue);
        int selectedIndex = 0;
        
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(currentValue)) {
                selectedIndex = i;
                break;
            }
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(preference.getTitle())
                .setSingleChoiceItems(entries, selectedIndex, (dialog, which) -> {
                    String selectedValue = values[which];
                    prefs.edit().putString(prefKey, selectedValue).apply();
                    dialog.dismiss();
                })
                .show();
    }

    /*
     * Inflates and displays the custom about developer dialog.
     * Utilizes MaterialAlertDialogBuilder for modern styling.
     */
    private void showAboutDeveloperDialog() {
        if (getContext() == null) return;

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_about_developer, null);
        new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setPositiveButton(R.string.btn_ok, null)
                .show();
    }

    /*
     * Registers the shared preference change listener.
     * Ensures the fragment listens for setting changes while visible.
     */
    @Override
    public void onResume() {
        super.onResume();
        if (getPreferenceScreen() != null && getPreferenceScreen().getSharedPreferences() != null) {
            getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        }
    }

    /*
     * Unregisters the shared preference change listener.
     * Prevents memory leaks when the fragment is paused.
     */
    @Override
    public void onPause() {
        super.onPause();
        if (getPreferenceScreen() != null && getPreferenceScreen().getSharedPreferences() != null) {
            getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        }
    }

    /*
     * Responds to changes in shared preferences.
     * Updates UI themes, dynamic colors, and saves states via the repository.
     */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key == null) return;

        if ("selected_theme".equals(key)) {
            String theme = sharedPreferences.getString(key, "system");
            executor.execute(() -> repository.setSetting("selected_theme", theme));
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    switch (theme) {
                        case "light":
                            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                            break;
                        case "dark":
                            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                            break;
                        case "system":
                        default:
                            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                            break;
                    }
                    getActivity().recreate();
                });
            }
        } else if ("dynamic_colors".equals(key)) {
            boolean val = true;
            try {
                val = sharedPreferences.getBoolean(key, true);
            } catch (ClassCastException e) {
                try {
                    String strVal = sharedPreferences.getString(key, "true");
                    val = "true".equalsIgnoreCase(strVal);
                } catch (Exception ignored) {}
            }
            final boolean finalVal = val;
            executor.execute(() -> repository.setSetting("dynamic_colors", String.valueOf(finalVal)));
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), getString(R.string.settings_fragment_toast_applying_theme), Toast.LENGTH_SHORT).show();
                    getActivity().recreate();
                });
            }
        } else {
            try {
                boolean val = sharedPreferences.getBoolean(key, false);
                executor.execute(() -> repository.setSetting(key, String.valueOf(val)));
            } catch (ClassCastException e) {
                try {
                    String strVal = sharedPreferences.getString(key, "");
                    executor.execute(() -> repository.setSetting(key, strVal));
                } catch (Exception ignored) {}
            }
        }
    }

    /*
     * Shuts down the background executor service.
     * Cleans up running background threads to avoid leaks.
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.shutdown();
        }
    }
}
