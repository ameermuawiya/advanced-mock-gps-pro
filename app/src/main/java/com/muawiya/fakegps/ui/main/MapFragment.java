package com.muawiya.fakegps.ui.main;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.muawiya.fakegps.MainActivity;
import com.muawiya.fakegps.R;
import com.muawiya.fakegps.data.FavoriteEntity;
import com.muawiya.fakegps.data.HistoryEntity;
import com.muawiya.fakegps.data.LocationRepository;
import com.muawiya.fakegps.databinding.FragmentMapBinding;
import com.muawiya.fakegps.service.MockLocationService;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MapFragment extends Fragment implements OnMapReadyCallback, GoogleMap.OnMapLongClickListener, GoogleMap.OnMarkerDragListener {

    private static final String TAG = "MapFragment";

    private FragmentMapBinding binding;
    private GoogleMap googleMap;
    private Marker targetMarker;
    private Marker currentPhysicalDotMarker;
    private com.google.android.gms.maps.model.Circle currentPhysicalGlowCircle;
    private LocationRepository repository;

    // Advanced Route Creator Fields
    private final List<LatLng> activeRoutePoints = new ArrayList<>();
    private Polyline activeRoutePolyline;
    private BroadcastReceiver mapUpdateReceiver;

    // Star tracking cache list
    private List<FavoriteEntity> cachedFavorites = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentMapBinding.inflate(inflater, container, false);
        this.repository = new LocationRepository(requireContext());
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Load Maps SDK Fragment inside
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map_fragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        setupSearchAndEnterTriggers();
        setupFloatingLayoutTriggers();
        setupRouteWaypointsControls();
        registerLocationUpdateReceiver();
        setupBottomLocationCardControls();

        // Dynamically listen/observe favorites to sync star visual status updates
        repository.getAllFavorites().observe(getViewLifecycleOwner(), favorites -> {
            if (favorites != null) {
                cachedFavorites = favorites;
                updateStarIconForCurrentLocation();
            }
        });
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        this.googleMap = map;

        // Default coordinate centers mapping
        LatLng initialPosition = new LatLng(MockLocationService.sLatitude, MockLocationService.sLongitude);
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(initialPosition, 14.0f));

        setupMapProperties();
        applyDefaultMapStyleFromSettings();
        placeDragTargetMarker(initialPosition);

        // Tap gestures listeners
        googleMap.setOnMapLongClickListener(this);
        googleMap.setOnMarkerDragListener(this);
        
        // Initialize display stats
        updateLocationCardUIState();
        updateRealCoordinatesDisplay();
    }

    private void setupMapProperties() {
        if (googleMap == null || getContext() == null) return;
        try {
            android.content.SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext());
            boolean compass = prefs.getBoolean("compass_layer", true);
            boolean traffic = prefs.getBoolean("traffic_layer", false);
            boolean buildings = prefs.getBoolean("buildings_layer", true);

            googleMap.getUiSettings().setZoomControlsEnabled(true);
            googleMap.getUiSettings().setCompassEnabled(compass);
            googleMap.getUiSettings().setIndoorLevelPickerEnabled(true);
            googleMap.setTrafficEnabled(traffic);
            googleMap.setBuildingsEnabled(buildings);
        } catch (SecurityException ignored) {}
    }

    private void placeDragTargetMarker(LatLng position) {
        if (googleMap == null) return;

        if (targetMarker != null) {
            targetMarker.setPosition(position);
        } else {
            targetMarker = googleMap.addMarker(new MarkerOptions()
                .position(position)
                .title("Drag to select mock spot")
                .draggable(true)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
        }
    }

    // --- Bottom Location Info Card controls and actions ---
    private void setupBottomLocationCardControls() {
        binding.btnFavoriteStar.setOnClickListener(v -> toggleFavoriteCurrentLocation());

        binding.btnCardStartStop.setOnClickListener(v -> {
            if (requireActivity() instanceof MainActivity) {
                MainActivity mainAct = (MainActivity) requireActivity();
                if (MockLocationService.sIsRunning) {
                    mainAct.stopLocationMockingService();
                } else {
                    mainAct.verifyPermissionsAndStartMocking();
                }
                // Delay slightly to allow status update and sync layout visually
                v.postDelayed(this::updateLocationCardUIState, 150);
            }
        });
    }

    public void updateLocationCardUIState() {
        if (binding == null) return;

        double lat = MockLocationService.sLatitude;
        double lng = MockLocationService.sLongitude;
        String name = MockLocationService.sLocationName;
        if (name == null || name.isEmpty() || "Custom spot on Map".equalsIgnoreCase(name)) {
            name = "Selected coordinates spot";
        }

        binding.txtSelectedTitle.setText(name);
        binding.txtSelectedCoords.setText(String.format(Locale.US, "Mock target coordinates: %.6f, %.6f", lat, lng));

        // Start/Stop layout states with highly-interactive visual feedback (Red when active, Green when idle)
        if (MockLocationService.sIsRunning) {
            binding.btnCardStartStop.setText("Stop Spoofing Service");
            binding.btnCardStartStop.setIcon(ContextCompat.getDrawable(requireContext(), R.drawable.ic_stop));
            binding.btnCardStartStop.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#D32F2F"))); // crimson red when active
            binding.btnCardStartStop.setTextColor(Color.WHITE);
            binding.btnCardStartStop.setIconTintResource(android.R.color.white);
        } else {
            binding.btnCardStartStop.setText("Start Spoofing Location");
            binding.btnCardStartStop.setIcon(ContextCompat.getDrawable(requireContext(), R.drawable.ic_play_arrow));
            binding.btnCardStartStop.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#2E7D32"))); // green when idle
            binding.btnCardStartStop.setTextColor(Color.WHITE);
            binding.btnCardStartStop.setIconTintResource(android.R.color.white);
        }

        updateStarIconForCurrentLocation();
    }

    private void updateStarIconForCurrentLocation() {
        if (binding == null || getContext() == null) return;
        boolean starred = isLocationCurrentlyStarred(MockLocationService.sLatitude, MockLocationService.sLongitude);
        if (starred) {
            binding.btnFavoriteStar.setImageResource(R.drawable.ic_star);
        } else {
            binding.btnFavoriteStar.setImageResource(R.drawable.ic_star_outline);
            binding.btnFavoriteStar.setColorFilter(null);
        }
    }

    private boolean isLocationCurrentlyStarred(double lat, double lng) {
        for (FavoriteEntity fav : cachedFavorites) {
            if (Math.abs(fav.getLatitude() - lat) < 0.0001 && Math.abs(fav.getLongitude() - lng) < 0.0001) {
                return true;
            }
        }
        return false;
    }

    private void toggleFavoriteCurrentLocation() {
        double lat = MockLocationService.sLatitude;
        double lng = MockLocationService.sLongitude;
        String name = MockLocationService.sLocationName;
        if (name == null || name.isEmpty() || "Custom spot on Map".equalsIgnoreCase(name)) {
            name = "Selected coordinates spot";
        }

        FavoriteEntity existing = null;
        for (FavoriteEntity fav : cachedFavorites) {
            if (Math.abs(fav.getLatitude() - lat) < 0.0001 && Math.abs(fav.getLongitude() - lng) < 0.0001) {
                existing = fav;
                break;
            }
        }

        if (existing != null) {
            repository.deleteFavoriteById(existing.getId());
            Toast.makeText(getContext(), getString(R.string.map_fragment_toast_starred_removed), Toast.LENGTH_SHORT).show();
        } else {
            FavoriteEntity newFav = new FavoriteEntity(name, lat, lng, "Starred", System.currentTimeMillis());
            repository.insertFavorite(newFav);
            Toast.makeText(getContext(), getString(R.string.map_fragment_toast_starred_saved), Toast.LENGTH_SHORT).show();
        }
    }

    private void updateRealCoordinatesDisplay() {
        if (binding == null || getContext() == null) return;
        try {
            android.location.LocationManager lm = (android.location.LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                android.location.Location loc = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER);
                if (loc == null) {
                    loc = lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER);
                }
                if (loc != null) {
                    binding.txtOriginalCoords.setText(String.format(Locale.US, "Real GPS: %.6f, %.6f (Acc: %.1fm)", loc.getLatitude(), loc.getLongitude(), loc.getAccuracy()));
                } else {
                    binding.txtOriginalCoords.setText("Real GPS: Active");
                }
            } else {
                binding.txtOriginalCoords.setText("Real GPS: (Tap to authorize tracking permissions)");
            }
        } catch (Exception e) {
            binding.txtOriginalCoords.setText("Real GPS: Fetching signal...");
        }
    }

    private void applyDefaultMapStyleFromSettings() {
        if (googleMap == null || getContext() == null) return;
        android.content.SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext());
        String style = prefs.getString("selected_map_style", "normal");
        int mapType;
        switch (style) {
            case "satellite":
                mapType = GoogleMap.MAP_TYPE_SATELLITE;
                break;
            case "terrain":
                mapType = GoogleMap.MAP_TYPE_TERRAIN;
                break;
            case "hybrid":
                mapType = GoogleMap.MAP_TYPE_HYBRID;
                break;
            case "normal":
            default:
                mapType = GoogleMap.MAP_TYPE_NORMAL;
                break;
        }
        if (googleMap.getMapType() != mapType) {
            googleMap.setMapType(mapType);
        }

        // Apply dark mode style if the user's current theme is Dark Mode
        boolean isDarkTheme = false;
        String selectedTheme = prefs.getString("selected_theme", "system");
        if ("dark".equals(selectedTheme)) {
            isDarkTheme = true;
        } else if ("light".equals(selectedTheme)) {
            isDarkTheme = false;
        } else {
            int nightModeFlags = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            if (nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                isDarkTheme = true;
            }
        }

        if (isDarkTheme && (mapType == GoogleMap.MAP_TYPE_NORMAL || mapType == GoogleMap.MAP_TYPE_TERRAIN)) {
            try {
                boolean success = googleMap.setMapStyle(
                    com.google.android.gms.maps.model.MapStyleOptions.loadRawResourceStyle(
                        requireContext(), R.raw.map_style_dark
                    )
                );
                if (!success) {
                    android.util.Log.e("MapFragment", "Style parsing failed.");
                }
            } catch (android.content.res.Resources.NotFoundException e) {
                android.util.Log.e("MapFragment", "Can't find style. Error: ", e);
            }
        } else {
            googleMap.setMapStyle(null);
        }
    }

    // --- Search engine implementations ---
    private void setupSearchAndEnterTriggers() {
        binding.searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                triggerSearchQueryParsing();
                if (getContext() != null) {
                    android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    }
                }
                binding.searchInput.clearFocus();
                return true;
            }
            return false;
        });

        binding.btnSearchSubmit.setOnClickListener(v -> {
            triggerSearchQueryParsing();
            if (getContext() != null) {
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                }
            }
            binding.searchInput.clearFocus();
        });
        
        binding.btnVoiceSearch.setOnClickListener(v -> startVoiceSpeechInput());
    }

    private void startVoiceSpeechInput() {
        try {
            Intent intent = new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            intent.putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, getString(R.string.fragment_map_hint_search_places));
            startActivityForResult(intent, 2002);
        } catch (Exception e) {
            Toast.makeText(getContext(), getString(R.string.map_fragment_toast_voice_speech_unavailable), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 2002 && resultCode == android.app.Activity.RESULT_OK && data != null) {
            ArrayList<String> matches = data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);
            if (matches != null && !matches.isEmpty()) {
                String spokenText = matches.get(0);
                binding.searchInput.setText(spokenText);
                triggerSearchQueryParsing();
            }
        } else if (requestCode == 5005 && resultCode == android.app.Activity.RESULT_OK) {
            android.location.LocationManager lm = (android.location.LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
            proceedWithFetchingPhysicalLocation(lm);
        }
    }

    private void triggerSearchQueryParsing() {
        String query = binding.searchInput.getText().toString().trim();
        if (query.isEmpty()) {
            Toast.makeText(getContext(), getString(R.string.map_fragment_toast_empty_search), Toast.LENGTH_SHORT).show();
            return;
        }

        // Case 1: Coordinate parsing
        LatLng parsedCoords = parseCoordinates(query);
        if (parsedCoords != null) {
            zoomToParsedPosition(parsedCoords, "Coordinates custom spot");
            return;
        }

        // Case 2: Extract from Google Maps link
        LatLng linkCoords = parseGoogleMapsUrl(query);
        if (linkCoords != null) {
            zoomToParsedPosition(linkCoords, "Coord link source");
            return;
        }

        // Case 3: Standard Geocoding place name lookup
        geodecodeQueryName(query);
    }

    @Nullable
    private LatLng parseCoordinates(String query) {
        try {
            String regex = "^(-?\\d+\\.\\d+)\\s*,\\s*(-?\\d+\\.\\d+)$";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(query.replace(" ", ""));
            if (matcher.find()) {
                double lat = Double.parseDouble(matcher.group(1));
                double lng = Double.parseDouble(matcher.group(2));
                return new LatLng(lat, lng);
            }
        } catch (Exception ignored) {}
        return null;
    }

    @Nullable
    private LatLng parseGoogleMapsUrl(String url) {
        try {
            String regex = "@(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(url);
            if (matcher.find()) {
                double lat = Double.parseDouble(matcher.group(1));
                double lng = Double.parseDouble(matcher.group(2));
                return new LatLng(lat, lng);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void geodecodeQueryName(String placeName) {
        if (!Geocoder.isPresent()) {
            Toast.makeText(getContext(), getString(R.string.map_fragment_toast_geocoder_offline), Toast.LENGTH_SHORT).show();
            return;
        }

        Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocationName(placeName, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                LatLng resultSpot = new LatLng(address.getLatitude(), address.getLongitude());
                zoomToParsedPosition(resultSpot, address.getAddressLine(0));
            } else {
                Toast.makeText(getContext(), getString(R.string.map_fragment_toast_geocoder_not_found, placeName), Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            Log.e(TAG, "Geocoding connection timeout exception", e);
            Toast.makeText(getContext(), getString(R.string.map_fragment_toast_geocoder_error), Toast.LENGTH_SHORT).show();
        }
    }

    private void zoomToParsedPosition(LatLng spot, String addressLabel) {
        if (googleMap == null) return;
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(spot, 15.5f));
        placeDragTargetMarker(spot);
        saveLocationSelectedState(spot, addressLabel);
        updateLocationCardUIState();
        Toast.makeText(getContext(), getString(R.string.map_fragment_toast_address_opened, addressLabel), Toast.LENGTH_SHORT).show();
    }

    private void saveLocationSelectedState(LatLng latLng, String addressName) {
        MockLocationService.sLatitude = latLng.latitude;
        MockLocationService.sLongitude = latLng.longitude;
        MockLocationService.sLocationName = addressName;

        // Auto persist selections to Room history log
        repository.insertHistory(new HistoryEntity(
            addressName, latLng.latitude, latLng.longitude, System.currentTimeMillis()
        ));

        // Let MainActivity refresh quick status bar
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).refreshMockStatusBarLayout();
        }
    }

    public void centerAndZoomCoordinates(double lat, double lng) {
        LatLng spot = new LatLng(lat, lng);
        if (googleMap != null) {
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(spot, 16.0f));
            placeDragTargetMarker(spot);
        }
        updateLocationCardUIState();
    }

    // --- Switch map styles instantly ---
    private void setupFloatingLayoutTriggers() {
        binding.fabMapLayers.setOnClickListener(v -> showMapStyleSelectionDialog());

        // Centering camera zoom to physical self location
        binding.fabZoomCurrent.setOnClickListener(v -> zoomToPhysicalSelfLocation());

        // Centering camera zoom to selected mock position
        binding.fabZoomSelected.setOnClickListener(v -> {
            if (googleMap != null) {
                LatLng selectedLatLng = new LatLng(MockLocationService.sLatitude, MockLocationService.sLongitude);
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(selectedLatLng, 15.5f));
                placeDragTargetMarker(selectedLatLng);
                Toast.makeText(getContext(), "Centered on selected mock position", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private int getThemeColor(int attrRes) {
        if (getContext() == null) return Color.BLUE;
        android.util.TypedValue typedValue = new android.util.TypedValue();
        if (requireContext().getTheme().resolveAttribute(attrRes, typedValue, true)) {
            return typedValue.data;
        }
        return Color.BLUE;
    }

    private com.google.android.gms.maps.model.BitmapDescriptor createCurrentLocationDotIcon() {
        int color = getThemeColor(R.attr.colorPrimary);
        int size = 48; // size of dot in pixels
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        
        android.graphics.Paint paint = new android.graphics.Paint();
        paint.setAntiAlias(true);
        
        // Draw white outer stroke boundary for contrast
        paint.setColor(Color.WHITE);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2, paint);
        
        // Draw center filled primary color dot
        paint.setColor(color);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 6, paint);
        
        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    private void drawCurrentLocationGlowCircle(LatLng position) {
        if (googleMap == null || getContext() == null) return;
        
        if (currentPhysicalGlowCircle != null) {
            currentPhysicalGlowCircle.remove();
        }
        
        int primaryColor = getThemeColor(R.attr.colorPrimary);
        
        // Extract RGB and apply custom alpha to match Google Maps beautiful transparent styling
        int fillAlpha = 30; // 0-255 opacity
        int strokeAlpha = 120;
        int fillColor = Color.argb(fillAlpha, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor));
        int strokeColor = Color.argb(strokeAlpha, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor));
        
        com.google.android.gms.maps.model.CircleOptions circleOptions = new com.google.android.gms.maps.model.CircleOptions()
                .center(position)
                .radius(60) // radius in meters
                .strokeWidth(3f)
                .strokeColor(strokeColor)
                .fillColor(fillColor);
                
        currentPhysicalGlowCircle = googleMap.addCircle(circleOptions);
    }

    private void showPhysicalLocationOnMap(LatLng realLatLng) {
        if (googleMap == null || getContext() == null) return;
        
        // Draw glow circle
        drawCurrentLocationGlowCircle(realLatLng);
        
        // Place/update dot marker with anchor at center
        if (currentPhysicalDotMarker != null) {
            currentPhysicalDotMarker.setPosition(realLatLng);
        } else {
            currentPhysicalDotMarker = googleMap.addMarker(new MarkerOptions()
                .position(realLatLng)
                .anchor(0.5f, 0.5f)
                .flat(true)
                .icon(createCurrentLocationDotIcon())
                .title("Your Current Location"));
        }
    }

    private void zoomToPhysicalSelfLocation() {
        if (getContext() == null || googleMap == null) return;
        try {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                if (requireActivity() instanceof MainActivity) {
                    ((MainActivity) requireActivity()).verifyPermissionsAndStartMocking();
                } else {
                    Toast.makeText(getContext(), getString(R.string.map_fragment_toast_permissions_not_granted), Toast.LENGTH_SHORT).show();
                }
                return;
            }

            android.location.LocationManager lm = (android.location.LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
            boolean gpsEnabled = false;
            try {
                gpsEnabled = lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER);
            } catch (Exception ex) {}

            if (gpsEnabled) {
                proceedWithFetchingPhysicalLocation(lm);
            } else {
                com.google.android.gms.location.LocationRequest locationRequest = new com.google.android.gms.location.LocationRequest.Builder(
                        com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 1000)
                        .build();

                com.google.android.gms.location.LocationSettingsRequest.Builder settingsBuilder = 
                        new com.google.android.gms.location.LocationSettingsRequest.Builder()
                        .addLocationRequest(locationRequest)
                        .setAlwaysShow(true);

                com.google.android.gms.location.SettingsClient settingsClient = 
                        com.google.android.gms.location.LocationServices.getSettingsClient(requireContext());

                settingsClient.checkLocationSettings(settingsBuilder.build())
                    .addOnCompleteListener(task -> {
                        try {
                            task.getResult(com.google.android.gms.common.api.ApiException.class);
                            proceedWithFetchingPhysicalLocation(lm);
                        } catch (com.google.android.gms.common.api.ApiException exception) {
                            if (exception.getStatusCode() == com.google.android.gms.location.LocationSettingsStatusCodes.RESOLUTION_REQUIRED) {
                                try {
                                    com.google.android.gms.common.api.ResolvableApiException resolvable = (com.google.android.gms.common.api.ResolvableApiException) exception;
                                    resolvable.startResolutionForResult(requireActivity(), 5005);
                                } catch (Exception e) {
                                    Log.e(TAG, "Resolution failed", e);
                                    Toast.makeText(getContext(), getString(R.string.map_fragment_toast_error_gps_sensor), Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                Toast.makeText(getContext(), getString(R.string.map_fragment_toast_error_gps_sensor), Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
            }
        } catch (Exception e) {
            Log.e(TAG, "Fail to fetch real current physical location", e);
            Toast.makeText(getContext(), getString(R.string.map_fragment_toast_error_gps_sensor), Toast.LENGTH_SHORT).show();
        }
    }

    private void proceedWithFetchingPhysicalLocation(android.location.LocationManager lm) {
        if (getContext() == null || googleMap == null) return;
        try {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                android.location.Location loc = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER);
                if (loc == null) {
                    loc = lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER);
                }
                if (loc == null) {
                    loc = lm.getLastKnownLocation(android.location.LocationManager.PASSIVE_PROVIDER);
                }

                if (loc != null) {
                    LatLng realLatLng = new LatLng(loc.getLatitude(), loc.getLongitude());
                    // Focus/relocate view to current location
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(realLatLng, 15.5f));
                    
                    // Show beautiful Google Maps style current location indicator
                    showPhysicalLocationOnMap(realLatLng);
                    
                    // Update display of physical coordinates
                    updateRealCoordinatesDisplay();
                    
                    Toast.makeText(getContext(), getString(R.string.map_fragment_toast_centered_physical), Toast.LENGTH_SHORT).show();
                } else {
                    // Try to request a temporary single update or show warning
                    Toast.makeText(getContext(), getString(R.string.map_fragment_toast_signal_not_available), Toast.LENGTH_LONG).show();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "proceedWithFetchingPhysicalLocation failed", e);
        }
    }

    private void showMapStyleSelectionDialog() {
        if (getContext() == null) return;
        String[] options = getResources().getStringArray(R.array.map_style_entries);
        String[] values = getResources().getStringArray(R.array.map_style_values);
        
        android.content.SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext());
        String currentStyle = prefs.getString("selected_map_style", "normal");
        
        int checkedItem = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(currentStyle)) {
                checkedItem = i;
                break;
            }
        }

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.pref_title_map_style)
                .setSingleChoiceItems(options, checkedItem, (dialog, which) -> {
                    String selected = values[which];
                    
                    // Persist setting dynamically to SharedPreferences
                    prefs.edit().putString("selected_map_style", selected).apply();
                    
                    // Sync to Database repository
                    repository.setSetting("selected_map_style", selected);
                    
                    // Instantly apply map rendering layer style
                    applyDefaultMapStyleFromSettings();
                    
                    Toast.makeText(getContext(), getString(R.string.map_fragment_toast_style_updated, options[which]), Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.btn_close, null)
                .show();
    }

    // --- Route simulations waypoints engine ---
    private void setupRouteWaypointsControls() {
        binding.btnRouteClose.setOnClickListener(v -> {
            clearRouteCreationTrack();
            binding.routeControlCard.setVisibility(View.GONE);
        });

        binding.btnRouteClear.setOnClickListener(v -> clearRouteCreationTrack());

        binding.btnRoutePlayPause.setOnClickListener(v -> {
            if (activeRoutePoints.isEmpty()) {
                Toast.makeText(getContext(), getString(R.string.map_fragment_toast_add_path_first), Toast.LENGTH_SHORT).show();
                return;
            }

            if (!MockLocationService.sIsRunning) {
                Toast.makeText(getContext(), getString(R.string.map_fragment_toast_enable_mock_first), Toast.LENGTH_LONG).show();
                return;
            }

            if (MockLocationService.sIsRouteSimulationActive) {
                MockLocationService.sIsRouteSimulationActive = false;
                binding.btnRoutePlayPause.setImageResource(R.drawable.ic_play_arrow);
                Toast.makeText(getContext(), getString(R.string.map_fragment_toast_simulation_paused), Toast.LENGTH_SHORT).show();
            } else {
                MockLocationService.sRoutePoints.clear();
                for (LatLng pt : activeRoutePoints) {
                    MockLocationService.sRoutePoints.add(new double[]{pt.latitude, pt.longitude});
                }

                MockLocationService.sIsRouteSimulationActive = true;
                MockLocationService.sCurrentRouteIndex = 0;
                binding.btnRoutePlayPause.setImageResource(android.R.drawable.ic_media_pause);
                Toast.makeText(getContext(), getString(R.string.map_fragment_toast_initiating_routing), Toast.LENGTH_SHORT).show();
            }
        });

        // Quick simulation speed presets (Walk, Cycle, Drive modes)
        binding.btnModeWalk.setOnClickListener(v -> {
            MockLocationService.sRouteSpeedSetting = 1.4f; // 1.4 m/s (5 km/h)
            binding.txtRouteSpeed.setText(getString(R.string.map_fragment_speed_lbl, getString(R.string.map_fragment_speed_walk), 1.4f, 5.0f));
        });

        binding.btnModeCycle.setOnClickListener(v -> {
            MockLocationService.sRouteSpeedSetting = 5.5f; // 5.5 m/s (20 km/h)
            binding.txtRouteSpeed.setText(getString(R.string.map_fragment_speed_lbl, getString(R.string.map_fragment_speed_cycle), 5.5f, 20.0f));
        });

        binding.btnModeDrive.setOnClickListener(v -> {
            MockLocationService.sRouteSpeedSetting = 13.8f; // 13.8 m/s (50 km/h)
            binding.txtRouteSpeed.setText(getString(R.string.map_fragment_speed_lbl, getString(R.string.map_fragment_speed_drive), 13.8f, 50.0f));
        });

        binding.btnRouteRepeat.setOnClickListener(v -> {
            MockLocationService.sRouteRepeat = !MockLocationService.sRouteRepeat;
            String prompt = MockLocationService.sRouteRepeat ? getString(R.string.map_fragment_toast_repeat_on) : getString(R.string.map_fragment_toast_repeat_off);
            Toast.makeText(getContext(), prompt, Toast.LENGTH_SHORT).show();
        });

        binding.btnRouteReverse.setOnClickListener(v -> {
            MockLocationService.sRouteReverse = !MockLocationService.sRouteReverse;
            String prompt = MockLocationService.sRouteReverse ? getString(R.string.map_fragment_toast_reverse_on) : getString(R.string.map_fragment_toast_reverse_off);
            Toast.makeText(getContext(), prompt, Toast.LENGTH_SHORT).show();
        });
    }

    private void addWaypointToRoute(LatLng point) {
        activeRoutePoints.add(point);
        binding.routeControlCard.setVisibility(View.VISIBLE);

        if (activeRoutePolyline != null) {
            activeRoutePolyline.remove();
        }

        PolylineOptions pathOptions = new PolylineOptions()
            .addAll(activeRoutePoints)
            .width(8f)
            .color(Color.BLUE)
            .geodesic(true);
        activeRoutePolyline = googleMap.addPolyline(pathOptions);

        googleMap.addMarker(new MarkerOptions()
            .position(point)
            .title("Waypoint #" + activeRoutePoints.size())
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
    }

    private void clearRouteCreationTrack() {
        activeRoutePoints.clear();
        if (googleMap != null) {
            googleMap.clear();
            targetMarker = null;
            placeDragTargetMarker(new LatLng(MockLocationService.sLatitude, MockLocationService.sLongitude));
        }
        if (activeRoutePolyline != null) {
            activeRoutePolyline.remove();
            activeRoutePolyline = null;
        }
        MockLocationService.sIsRouteSimulationActive = false;
        MockLocationService.sRoutePoints.clear();
        binding.btnRoutePlayPause.setImageResource(R.drawable.ic_play_arrow);
    }

    private void registerLocationUpdateReceiver() {
        mapUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (MockLocationService.ACTION_LOCATION_UPDATED.equals(intent.getAction()) && googleMap != null) {
                    double lat = intent.getDoubleExtra("lat", MockLocationService.sLatitude);
                    double lng = intent.getDoubleExtra("lng", MockLocationService.sLongitude);

                    if (MockLocationService.sIsRouteSimulationActive) {
                        LatLng spotNow = new LatLng(lat, lng);
                        googleMap.moveCamera(CameraUpdateFactory.newLatLng(spotNow));
                        placeDragTargetMarker(spotNow);
                        updateLocationCardUIState();
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter(MockLocationService.ACTION_LOCATION_UPDATED);
        requireActivity().registerReceiver(mapUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    // --- OnMapLongClickListener and Dragging callbacks ---
    @Override
    public void onMapLongClick(@NonNull LatLng latLng) {
        if (binding.routeControlCard.getVisibility() == View.VISIBLE) {
            // Add route simulation waypoints if the route panel is visible
            addWaypointToRoute(latLng);
            Toast.makeText(getContext(), getString(R.string.map_fragment_toast_waypoint_added), Toast.LENGTH_SHORT).show();
        } else {
            // Default elegant long-tap behavior selects location
            placeDragTargetMarker(latLng);
            geocodeAddressAndSelect(latLng);
        }
    }

    private void geocodeAddressAndSelect(LatLng latLng) {
        new Thread(() -> {
            String addressName = "Custom selected spot icon";
            if (Geocoder.isPresent() && getContext() != null) {
                Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
                try {
                    List<Address> addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);
                    if (addresses != null && !addresses.isEmpty()) {
                        addressName = addresses.get(0).getAddressLine(0);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Reverse geocoding connection issue", e);
                }
            }
            final String finalAddressName = addressName;
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    saveLocationSelectedState(latLng, finalAddressName);
                    updateLocationCardUIState();
                });
            }
        }).start();
    }

    @Override
    public void onMarkerDragStart(@NonNull Marker marker) {}

    @Override
    public void onMarkerDrag(@NonNull Marker marker) {}

    @Override
    public void onMarkerDragEnd(@NonNull Marker marker) {
        LatLng finalPos = marker.getPosition();
        geocodeAddressAndSelect(finalPos);
    }

    @Override
    public void onResume() {
        super.onResume();
        LatLng active = new LatLng(MockLocationService.sLatitude, MockLocationService.sLongitude);
        if (googleMap != null) {
            googleMap.moveCamera(CameraUpdateFactory.newLatLng(active));
            placeDragTargetMarker(active);
        }
        updateLocationCardUIState();
        updateRealCoordinatesDisplay();
        applyDefaultMapStyleFromSettings();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        googleMap = null;
        targetMarker = null;
        currentPhysicalDotMarker = null;
        currentPhysicalGlowCircle = null;
        if (mapUpdateReceiver != null) {
            try {
                requireActivity().unregisterReceiver(mapUpdateReceiver);
            } catch (Exception ignored) {}
        }
    }
}
