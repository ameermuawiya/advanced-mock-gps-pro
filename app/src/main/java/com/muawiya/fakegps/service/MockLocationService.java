package com.muawiya.fakegps.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.muawiya.fakegps.MainActivity;
import com.muawiya.fakegps.R;
import com.muawiya.fakegps.engine.MockLocationEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Foreground Service that owns the Mock Location Engine.
 * It manages single point mocking, route simulation, and floating joystick coordinates updates.
 */
public class MockLocationService extends Service {
    private static final String TAG = "MockLocationService";
    private static final String CHANNEL_ID = "mock_location_service_channel";
    private static final int NOTIFICATION_ID = 2468;

    // Intents action strings
    public static final String ACTION_START = "com.muawiya.fakegps.service.START";
    public static final String ACTION_STOP = "com.muawiya.fakegps.service.STOP";
    public static final String ACTION_PAUSE = "com.muawiya.fakegps.service.PAUSE";
    public static final String ACTION_RESUME = "com.muawiya.fakegps.service.RESUME";
    public static final String ACTION_LOCATION_UPDATED = "com.muawiya.fakegps.service.LOCATION_UPDATED";

    // Static variables as single source of truth for the UI
    public static volatile double sLatitude = 37.7749;
    public static volatile double sLongitude = -122.4194;
    public static volatile boolean sIsRunning = false;
    public static volatile boolean sIsPaused = false;
    public static volatile String sLocationName = "San Francisco, CA";

    // Advanced accuracy variables
    public static volatile float sAccuracy = 5.0f;     // meters
    public static volatile double sAltitude = 12.0;    // meters
    public static volatile float sBearing = 90.0f;     // degrees
    public static volatile float sSpeed = 0.0f;        // m/s

    // Randomization toggles
    public static volatile boolean sRandomizeAccuracy = false;
    public static volatile boolean sRandomizeAltitude = false;
    public static volatile boolean sRandomizeSpeed = false;

    // Route Simulation state
    public static final List<double[]> sRoutePoints = new ArrayList<>();
    public static volatile int sCurrentRouteIndex = 0;
    public static volatile boolean sIsRouteSimulationActive = false;
    public static volatile float sRouteSpeedSetting = 5.0f; // speed setting in m/s
    public static volatile boolean sRouteRepeat = false;
    public static volatile boolean sRouteReverse = false;
    private static boolean sIsReversedDirection = false;

    private MockLocationEngine mockEngine;
    private Handler serviceHandler;
    private Runnable injectionRunnable;
    private final Random random = new Random();

    @Override
    public void onCreate() {
        super.onCreate();
        mockEngine = new MockLocationEngine(this);
        serviceHandler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            Log.d(TAG, "onStartCommand Action: " + action);

            switch (action) {
                case ACTION_START:
                    startMockingFlow();
                    break;
                case ACTION_STOP:
                    stopMockingFlow();
                    break;
                case ACTION_PAUSE:
                    pauseMockingFlow();
                    break;
                case ACTION_RESUME:
                    resumeMockingFlow();
                    break;
            }
        }
        return START_NOT_STICKY;
    }

    private void startMockingFlow() {
        try {
            mockEngine.startMocking();
            sIsRunning = true;
            sIsPaused = false;

            // Start foreground with notification
            startForeground(NOTIFICATION_ID, buildNotification());

            // Start repeating injection thread
            startPeriodicInjection();

            sendLocationBroadcast();
        } catch (SecurityException e) {
            Log.e(TAG, "No mock location rights.", e);
            stopSelf();
        }
    }

    private void pauseMockingFlow() {
        sIsPaused = true;
        updateNotification();
        sendLocationBroadcast();
    }

    private void resumeMockingFlow() {
        sIsPaused = false;
        updateNotification();
        sendLocationBroadcast();
    }

    private void stopMockingFlow() {
        sIsRunning = false;
        sIsPaused = false;
        sIsRouteSimulationActive = false;

        if (mockEngine != null) {
            mockEngine.stopMocking();
        }

        if (serviceHandler != null && injectionRunnable != null) {
            serviceHandler.removeCallbacks(injectionRunnable);
        }

        sendLocationBroadcast();
        stopForeground(true);
        stopSelf();
    }

    private void startPeriodicInjection() {
        if (injectionRunnable != null) {
            serviceHandler.removeCallbacks(injectionRunnable);
        }

        injectionRunnable = new Runnable() {
            @Override
            public void run() {
                if (sIsRunning) {
                    if (!sIsPaused) {
                        processMockMovement();
                    }
                    injectCurrentLocation();
                }

                // Schedule next injection (frequency 1000ms = 1Hz)
                serviceHandler.postDelayed(this, 1000);
            }
        };

        serviceHandler.post(injectionRunnable);
    }

    private void processMockMovement() {
        // 1. Process Route Simulation
        if (sIsRouteSimulationActive && !sRoutePoints.isEmpty()) {
            double[] target = sRoutePoints.get(sCurrentRouteIndex);
            double distance = calculateDistanceInMeters(sLatitude, sLongitude, target[0], target[1]);

            float frameSpeed = sRouteSpeedSetting; // speed m/s
            if (sRandomizeSpeed) {
                frameSpeed += (random.nextFloat() - 0.5f) * 2.0f; // variation +/- 1m/s
                if (frameSpeed < 0) frameSpeed = 0.5f;
            }

            sSpeed = frameSpeed;

            if (distance <= frameSpeed) {
                // Snap to current target
                sLatitude = target[0];
                sLongitude = target[1];
                sBearing = (float) calculateHeading(sLatitude, sLongitude, target[0], target[1]);

                // Go to next index
                advanceRouteIndex();
            } else {
                // Interpolate along the line
                double bearingRad = Math.toRadians(calculateHeading(sLatitude, sLongitude, target[0], target[1]));
                sBearing = (float) Math.toDegrees(bearingRad);

                double latOffset = (frameSpeed / 111111.0) * Math.sin(bearingRad);
                double lngOffset = (frameSpeed / (111111.0 * Math.cos(Math.toRadians(sLatitude)))) * Math.cos(bearingRad);

                sLatitude += latOffset;
                sLongitude += lngOffset;
            }
        }


    }

    private void advanceRouteIndex() {
        if (!sRouteReverse) {
            // Normal forward direction
            sCurrentRouteIndex++;
            if (sCurrentRouteIndex >= sRoutePoints.size()) {
                if (sRouteRepeat) {
                    sCurrentRouteIndex = 0;
                } else {
                    sIsRouteSimulationActive = false;
                    sSpeed = 0;
                }
            }
        } else {
            // Reversing ping-pong action or simple reverse path loop
            if (sIsReversedDirection) {
                sCurrentRouteIndex--;
                if (sCurrentRouteIndex < 0) {
                    if (sRouteRepeat) {
                        sCurrentRouteIndex = 1;
                        sIsReversedDirection = false;
                    } else {
                        sIsRouteSimulationActive = false;
                        sSpeed = 0;
                    }
                }
            } else {
                sCurrentRouteIndex++;
                if (sCurrentRouteIndex >= sRoutePoints.size()) {
                    if (sRouteRepeat) {
                        sCurrentRouteIndex = sRoutePoints.size() - 2;
                        sIsReversedDirection = true;
                    } else {
                        sIsRouteSimulationActive = false;
                        sSpeed = 0;
                    }
                }
            }
        }
    }

    private void injectCurrentLocation() {
        float effectiveAccuracy = sAccuracy;
        if (sRandomizeAccuracy) {
            effectiveAccuracy += (random.nextFloat() - 0.5f) * 3.0f; // variation +/- 1.5m
            if (effectiveAccuracy < 1.0f) effectiveAccuracy = 1.0f;
        }

        double effectiveAltitude = sAltitude;
        if (sRandomizeAltitude) {
            effectiveAltitude += (random.nextDouble() - 0.5) * 5.0; // variation +/- 2.5m
        }

        mockEngine.injectLocation(
            sLatitude,
            sLongitude,
            effectiveAccuracy,
            effectiveAltitude,
            sSpeed,
            sBearing
        );

        sendLocationBroadcast();
        updateNotification();
    }

    private void sendLocationBroadcast() {
        Intent intent = new Intent(ACTION_LOCATION_UPDATED);
        intent.putExtra("lat", sLatitude);
        intent.putExtra("lng", sLongitude);
        intent.putExtra("speed", sSpeed);
        intent.putExtra("bearing", sBearing);
        intent.putExtra("altitude", sAltitude);
        intent.putExtra("accuracy", sAccuracy);
        sendBroadcast(intent);
    }

    private void updateNotification() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null && sIsRunning) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification());
        }
    }

    private Notification buildNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Pause/Resume actions
        Intent pmIntent = new Intent(this, MockLocationService.class);
        pmIntent.setAction(sIsPaused ? ACTION_RESUME : ACTION_PAUSE);
        PendingIntent pauseResumePending = PendingIntent.getService(
            this, 1, pmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String pauseResumeLabel = sIsPaused ? getString(R.string.notification_btn_resume) : getString(R.string.notification_btn_pause);

        // Stop Action
        Intent stopIntent = new Intent(this, MockLocationService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String statusStr = sIsPaused ? getString(R.string.notification_mock_paused) : getString(R.string.notification_mock_running);
        String contentText = String.format("Status: %s\nLat: %.5f, Lng: %.5f", statusStr, sLatitude, sLongitude);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title_active))
            .setOnlyAlertOnce(true)
            .setContentText(contentText)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(
                getString(R.string.notification_spot_prefix, sLocationName) + "\n" +
                getString(R.string.notification_coords_fmt, sLatitude, sLongitude) + "\n" +
                getString(R.string.notification_speed_bearing_fmt, sSpeed, sBearing) + "\n" +
                getString(R.string.notification_status_fmt, statusStr)
            ))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, pauseResumeLabel, pauseResumePending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.notification_btn_stop), stopPending)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            );
            serviceChannel.setDescription(getString(R.string.notification_channel_desc));
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // --- Geolocation Math helpers ---
    private static double calculateDistanceInMeters(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371e3; // earth radius in meters
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double deltaPhi = Math.toRadians(lat2 - lat1);
        double deltaLambda = Math.toRadians(lon2 - lon1);

        double a = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2) +
                   Math.cos(phi1) * Math.cos(phi2) *
                   Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }

    private static double calculateHeading(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double lambda1 = Math.toRadians(lon1);
        double lambda2 = Math.toRadians(lon2);

        double y = Math.sin(lambda2 - lambda1) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2) -
                   Math.sin(phi1) * Math.cos(phi2) * Math.cos(lambda2 - lambda1);
        double brng = Math.atan2(y, x);

        return (Math.toDegrees(brng) + 360) % 360;
    }
}
