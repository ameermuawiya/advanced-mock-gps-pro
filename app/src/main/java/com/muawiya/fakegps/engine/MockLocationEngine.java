package com.muawiya.fakegps.engine;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

public class MockLocationEngine {
    private static final String TAG = "MockLocationEngine";
    private final Context context;
    private LocationManager locationManager;
    private boolean isMocking = false;

    public MockLocationEngine(Context context) {
        this.context = context;
        this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    public void startMocking() {
        if (isMocking) return;
        try {
            setupProvider(LocationManager.GPS_PROVIDER);
            setupProvider(LocationManager.NETWORK_PROVIDER);
            isMocking = true;
            Log.d(TAG, "Mocking started successfully");
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException starting mock provider. Is 'Allow mock locations' option enabled in developer options?", e);
            throw e;
        } catch (Exception e) {
            Log.e(TAG, "Error starting mock provider", e);
        }
    }

    public void stopMocking() {
        if (!isMocking) return;
        try {
            removeProvider(LocationManager.GPS_PROVIDER);
            removeProvider(LocationManager.NETWORK_PROVIDER);
            isMocking = false;
            Log.d(TAG, "Mocking stopped successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping mock provider", e);
        }
    }

    public void injectLocation(double latitude, double longitude, float accuracy, double altitude, float speed, float bearing) {
        if (!isMocking) return;
        try {
            injectToProvider(LocationManager.GPS_PROVIDER, latitude, longitude, accuracy, altitude, speed, bearing);
            injectToProvider(LocationManager.NETWORK_PROVIDER, latitude, longitude, accuracy, altitude, speed, bearing);
        } catch (Exception e) {
            Log.e(TAG, "Error injecting location", e);
        }
    }

    private void setupProvider(String providerName) {
        try {
            try {
                locationManager.removeTestProvider(providerName);
            } catch (Exception ignored) {}

            locationManager.addTestProvider(
                    providerName,
                    false, // requiresNetwork
                    false, // requiresSatellite
                    false, // requiresCell
                    false, // hasMonetaryCost
                    true,  // supportsAltitude
                    true,  // supportsSpeed
                    true,  // supportsBearing
                    1,     // powerRequirement
                    1      // accuracy
            );
            locationManager.setTestProviderEnabled(providerName, true);
        } catch (Exception e) {
            Log.e(TAG, "Failed to setup provider " + providerName, e);
            throw e;
        }
    }

    private void removeProvider(String providerName) {
        try {
            locationManager.setTestProviderEnabled(providerName, false);
            locationManager.removeTestProvider(providerName);
        } catch (Exception ignored) {}
    }

    private void injectToProvider(String provider, double lat, double lng, float accuracy, double alt, float speed, float bearing) {
        try {
            Location mockLoc = new Location(provider);
            mockLoc.setLatitude(lat);
            mockLoc.setLongitude(lng);
            mockLoc.setAccuracy(accuracy);
            mockLoc.setAltitude(alt);
            mockLoc.setSpeed(speed);
            mockLoc.setBearing(bearing);
            mockLoc.setTime(System.currentTimeMillis());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                mockLoc.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mockLoc.setVerticalAccuracyMeters(1.0f);
                mockLoc.setSpeedAccuracyMetersPerSecond(0.5f);
                mockLoc.setBearingAccuracyDegrees(1.0f);
            }
            locationManager.setTestProviderLocation(provider, mockLoc);
        } catch (Exception e) {
            Log.e(TAG, "Failed to inject to provider " + provider, e);
        }
    }
}
