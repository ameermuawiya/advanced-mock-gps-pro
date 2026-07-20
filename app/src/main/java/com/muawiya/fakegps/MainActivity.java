package com.muawiya.fakegps;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.muawiya.fakegps.data.LocationRepository;
import com.muawiya.fakegps.databinding.ActivityMainBinding;
import com.muawiya.fakegps.service.MockLocationService;
import com.muawiya.fakegps.ui.favorites.FavoritesFragment;
import com.muawiya.fakegps.ui.history.HistoryFragment;
import com.muawiya.fakegps.ui.main.MapFragment;
import com.muawiya.fakegps.ui.settings.SettingsFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    public static int sSelectedTabId = com.muawiya.fakegps.R.id.nav_map;

    private ActivityMainBinding binding;
    private LocationRepository repository;
    private ExecutorService executor;

    // Active fragments instantiation and caching
    private final MapFragment mapFragment = new MapFragment();
    private final FavoritesFragment favoritesFragment = new FavoritesFragment();
    private final HistoryFragment historyLogsFragment = new HistoryFragment();
    private final SettingsFragment settingsFragment = new SettingsFragment();

    private Fragment activeFragment;
    private BroadcastReceiver locationStatusReceiver;
    private ActivityResultLauncher<String[]> permissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setupThemeAndPersistence();
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new LocationRepository(this);
        executor = Executors.newSingleThreadExecutor();

        setupNavigationAndListeners();
        configurePermissionLauncher();
        registerStatusBroadcastReceiver();

        int selectedId = sSelectedTabId;
        if (savedInstanceState != null) {
            selectedId = savedInstanceState.getInt("selected_nav_id", selectedId);
        }
        sSelectedTabId = selectedId;
        binding.bottomNav.setSelectedItemId(selectedId);

        Fragment fragmentToLoad = mapFragment;
        if (selectedId == com.muawiya.fakegps.R.id.nav_favorites) {
            fragmentToLoad = favoritesFragment;
        } else if (selectedId == com.muawiya.fakegps.R.id.nav_history) {
            fragmentToLoad = historyLogsFragment;
        } else if (selectedId == com.muawiya.fakegps.R.id.nav_settings) {
            fragmentToLoad = settingsFragment;
        }
        loadFragment(fragmentToLoad);
    }

    private void migrateToBooleanPreference(android.content.SharedPreferences prefs, String key, boolean defaultValue) {
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

    private void setupThemeAndPersistence() {
        // Read theme settings synchronously from SharedPreferences before contentView inflation
        android.content.SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this);
        
        // Migrate keys to prevent ClassCastException if they were stored as Strings previously
        migrateToBooleanPreference(prefs, "dynamic_colors", true);
        migrateToBooleanPreference(prefs, "auto_restore_last_location", true);
        migrateToBooleanPreference(prefs, "compass_layer", true);
        migrateToBooleanPreference(prefs, "traffic_layer", false);
        migrateToBooleanPreference(prefs, "buildings_layer", true);
        migrateToBooleanPreference(prefs, "screen_awake", false);

        String theme = prefs.getString("selected_theme", "system");
        boolean useDynamic = prefs.getBoolean("dynamic_colors", true);

        // Apply Material You Dynamic Theme if enabled and supported (Android 12+)
        if (useDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            com.google.android.material.color.DynamicColors.applyIfAvailable(this);
        }

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

        // Auto-restore last selected location if option is enabled
        boolean autoRestore = prefs.getBoolean("auto_restore_last_location", true);
        if (autoRestore) {
            String lastLatStr = prefs.getString("last_latitude", null);
            String lastLngStr = prefs.getString("last_longitude", null);
            String lastLocName = prefs.getString("last_location_name", null);
            if (lastLatStr != null && lastLngStr != null) {
                try {
                    MockLocationService.sLatitude = Double.parseDouble(lastLatStr);
                    MockLocationService.sLongitude = Double.parseDouble(lastLngStr);
                    if (lastLocName != null) {
                        MockLocationService.sLocationName = lastLocName;
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    private void setupNavigationAndListeners() {
        binding.bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            sSelectedTabId = itemId;
            if (itemId == R.id.nav_map) {
                loadFragment(mapFragment);
                return true;
            } else if (itemId == R.id.nav_favorites) {
                loadFragment(favoritesFragment);
                return true;
            } else if (itemId == R.id.nav_history) {
                loadFragment(historyLogsFragment);
                return true;
            } else if (itemId == R.id.nav_settings) {
                loadFragment(settingsFragment);
                return true;
            }
            return false;
        });
    }

    private void loadFragment(Fragment fragment) {
        if (fragment == activeFragment) return;
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, fragment);
        transaction.commit();
        activeFragment = fragment;
        refreshMockStatusBarLayout();
    }

    // --- Permissions Verification Flow ---
    private void configurePermissionLauncher() {
        permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            result -> {
                boolean allGranted = true;
                for (Boolean granted : result.values()) {
                    if (!granted) {
                        allGranted = false;
                        break;
                    }
                }

                if (allGranted) {
                    executeStartMockingCycle();
                } else {
                    Toast.makeText(this, getString(R.string.activity_main_toast_permissions_required), Toast.LENGTH_LONG).show();
                }
            }
        );
    }

    public void verifyPermissionsAndStartMocking() {
        List<String> list = new ArrayList<>();
        list.add(Manifest.permission.ACCESS_FINE_LOCATION);
        list.add(Manifest.permission.ACCESS_COARSE_LOCATION);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        boolean needRequest = false;
        for (String p : list) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                needRequest = true;
                break;
            }
        }

        if (needRequest) {
            permissionLauncher.launch(list.toArray(new String[0]));
        } else {
            executeStartMockingCycle();
        }
    }

    private void executeStartMockingCycle() {
        if (!isMockAppSelected()) {
            showDeveloperInstructionsDialog();
            return;
        }

        // Start Service
        Intent intent = new Intent(this, MockLocationService.class);
        intent.setAction(MockLocationService.ACTION_START);
        ContextCompat.startForegroundService(this, intent);

        Toast.makeText(this, getString(R.string.activity_main_toast_service_enabled), Toast.LENGTH_SHORT).show();
        updateFabStyling(true);

        // Auto backup last updated position coords
        executor.execute(() -> {
            repository.setSetting("last_latitude", String.valueOf(MockLocationService.sLatitude));
            repository.setSetting("last_longitude", String.valueOf(MockLocationService.sLongitude));
            repository.setSetting("last_location_name", MockLocationService.sLocationName);
        });

        // Refresh Map if it's active
        if (activeFragment instanceof MapFragment) {
            ((MapFragment) activeFragment).updateLocationCardUIState();
        }
    }

    private boolean isMockAppSelected() {
        try {
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            lm.addTestProvider("test_chk_provider", false, false, false, false, false, false, false, 1, 1);
            lm.removeTestProvider("test_chk_provider");
            return true;
        } catch (SecurityException e) {
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    private void showDeveloperInstructionsDialog() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_dev_opts_title)
            .setMessage(R.string.dialog_dev_opts_message)
            .setPositiveButton(R.string.dialog_dev_opts_btn_open, (dialog, which) -> {
                try {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
                    startActivity(intent);
                } catch (Exception e) {
                    try {
                        Intent intent = new Intent(Settings.ACTION_SETTINGS);
                        startActivity(intent);
                    } catch (Exception ignored) {
                        Toast.makeText(this, getString(R.string.activity_main_toast_settings_open_fail), Toast.LENGTH_SHORT).show();
                    }
                }
            })
            .setNegativeButton(R.string.dialog_dev_opts_btn_cancel, null)
            .show();
    }

    public void stopLocationMockingService() {
        Intent intent = new Intent(this, MockLocationService.class);
        intent.setAction(MockLocationService.ACTION_STOP);
        startService(intent);

        Toast.makeText(this, getString(R.string.activity_main_toast_provider_stopped), Toast.LENGTH_SHORT).show();
        updateFabStyling(false);

        // Refresh Map details card if active
        if (activeFragment instanceof MapFragment) {
            ((MapFragment) activeFragment).updateLocationCardUIState();
        }
    }

    private void updateFabStyling(boolean running) {
        // Obsoleted - Floating Action Button removed by user request
    }

    private android.content.res.ColorStateList ColorStateListHelper(int resId) {
        return android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, resId));
    }

    public void refreshMockStatusBarLayout() {
        // Obsoleted - Status bar removed by user request
    }

    public void navigateToAndCenterCoordinates(double lat, double lng) {
        binding.bottomNav.setSelectedItemId(R.id.nav_map);
        loadFragment(mapFragment);
        mapFragment.centerAndZoomCoordinates(lat, lng);
        refreshMockStatusBarLayout();
    }

    private void registerStatusBroadcastReceiver() {
        locationStatusReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (MockLocationService.ACTION_LOCATION_UPDATED.equals(intent.getAction())) {
                    refreshMockStatusBarLayout();
                }
            }
        };

        IntentFilter filter = new IntentFilter(MockLocationService.ACTION_LOCATION_UPDATED);
        registerReceiver(locationStatusReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshMockStatusBarLayout();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (binding != null) {
            outState.putInt("selected_nav_id", binding.bottomNav.getSelectedItemId());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (locationStatusReceiver != null) {
            try {
                unregisterReceiver(locationStatusReceiver);
            } catch (Exception ignored) {}
        }
        if (executor != null) {
            executor.shutdown();
        }
    }
}
