package com.muawiya.fakegps;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;
import com.google.android.material.color.DynamicColors;

public class FakeGpsApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        
        applyUserPreferences();
    }

    public void applyUserPreferences() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        
        // Migrate prefs to make sure we don't crash due to types
        migrateToBooleanPreference(prefs, "dynamic_colors", true);
        migrateToBooleanPreference(prefs, "auto_restore_last_location", true);
        migrateToBooleanPreference(prefs, "compass_layer", true);
        migrateToBooleanPreference(prefs, "traffic_layer", false);
        migrateToBooleanPreference(prefs, "buildings_layer", true);
        migrateToBooleanPreference(prefs, "screen_awake", false);

        String theme = prefs.getString("selected_theme", "system");
        boolean useDynamic = prefs.getBoolean("dynamic_colors", true);

        // Apply dynamic colors
        if (useDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DynamicColors.applyToActivitiesIfAvailable(this);
        }

        // Apply selected theme
        switch (theme != null ? theme : "system") {
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
    }

    private void migrateToBooleanPreference(SharedPreferences prefs, String key, boolean defaultValue) {
        try {
            prefs.getBoolean(key, defaultValue);
        } catch (ClassCastException e) {
            try {
                String valStr = prefs.getString(key, null);
                boolean boolVal = defaultValue;
                if (valStr != null) {
                    boolVal = "true".equalsIgnoreCase(valStr) || "1".equals(valStr);
                }
                prefs.edit().remove(key).putBoolean(key, boolVal).apply();
            } catch (Exception ignored) {}
        }
    }
}
