package com.muawiya.fakegps.data;

import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

import com.muawiya.fakegps.R;

public class DataBackupHelper {
    private static final String TAG = "DataBackupHelper";

    /**
     * Serializes Favorites into CSV format and triggers a Share Sheet.
     */
    public static void exportFavorites(Context context, List<FavoriteEntity> favorites) {
        if (favorites == null || favorites.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.backup_toast_no_favorites), Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder csv = new StringBuilder("Name,Latitude,Longitude,Category,Timestamp\n");
        for (FavoriteEntity f : favorites) {
            csv.append(String.format("%s,%.6f,%.6f,%s,%d\n",
                escapeCsv(f.getName()), f.getLatitude(), f.getLongitude(), escapeCsv(f.getCategory()), f.getTimestamp()));
        }

        shareTextFile(context, "mock_location_favorites.csv", csv.toString());
    }

    /**
     * Serializes History into CSV format and shares it.
     */
    public static void exportHistory(Context context, List<HistoryEntity> logs) {
        if (logs == null || logs.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.backup_toast_no_history), Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder csv = new StringBuilder("Name,Latitude,Longitude,Timestamp\n");
        for (HistoryEntity h : logs) {
            csv.append(String.format("%s,%.6f,%.6f,%d\n",
                escapeCsv(h.getName()), h.getLatitude(), h.getLongitude(), h.getTimestamp()));
        }

        shareTextFile(context, "mock_location_history.csv", csv.toString());
    }

    /**
     * Parses a CSV string and inserts records into Favorites.
     */
    public static List<FavoriteEntity> parseFavoritesCsv(String csvData) {
        List<FavoriteEntity> list = new ArrayList<>();
        try {
            String[] lines = csvData.split("\n");
            for (int i = 1; i < lines.length; i++) { // Skip header
                String line = lines[i].trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split(",");
                if (parts.length >= 3) {
                    String name = parts[0].replace("\"", "");
                    double lat = Double.parseDouble(parts[1]);
                    double lng = Double.parseDouble(parts[2]);
                    String cat = parts.length >= 4 ? parts[3].replace("\"", "") : "Default";
                    long ts = parts.length >= 5 ? Long.parseLong(parts[4]) : System.currentTimeMillis();

                    list.add(new FavoriteEntity(name, lat, lng, cat, ts));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing favorites CSV", e);
        }
        return list;
    }

    /**
     * Backs up the SQLite database to the external storage folder.
     */
    public static void backupDatabase(Context context) {
        File dbFile = context.getDatabasePath("mock_location_db");
        if (!dbFile.exists()) {
            Toast.makeText(context, context.getString(R.string.backup_toast_db_not_exist), Toast.LENGTH_SHORT).show();
            return;
        }

        File backupDir = context.getExternalFilesDir("backups");
        if (backupDir == null) return;

        File backupFile = new File(backupDir, "mock_location_db.bak");

        try {
            copyFile(dbFile, backupFile);
            Toast.makeText(context, context.getString(R.string.backup_toast_backup_success, backupFile.getName()), Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Log.e(TAG, "Database backup failed", e);
            Toast.makeText(context, context.getString(R.string.backup_toast_backup_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Restores the SQLite database from backups.
     */
    public static void restoreDatabase(Context context) {
        File backupDir = context.getExternalFilesDir("backups");
        if (backupDir == null) return;

        File backupFile = new File(backupDir, "mock_location_db.bak");
        if (!backupFile.exists()) {
            Toast.makeText(context, context.getString(R.string.backup_toast_restore_not_found, backupFile.getAbsolutePath()), Toast.LENGTH_LONG).show();
            return;
        }

        File dbFile = context.getDatabasePath("mock_location_db");

        try {
            // Close database first (done by invoking clean singleton or letting room handle it automatically on rewrite)
            LocationDatabase.getDatabase(context).close();
            copyFile(backupFile, dbFile);
            
            // Delete accessory WAL and SHM files to force Room to load from the main DB file
            new File(dbFile.getAbsolutePath() + "-wal").delete();
            new File(dbFile.getAbsolutePath() + "-shm").delete();

            Toast.makeText(context, context.getString(R.string.backup_toast_restore_success), Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Log.e(TAG, "Database restore failed", e);
            Toast.makeText(context, context.getString(R.string.backup_toast_restore_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Returns generic Quick Location Presets to satisfy user request.
     */
    public static List<FavoriteEntity> getQuickLocationPresets() {
        List<FavoriteEntity> presets = new ArrayList<>();
        presets.add(new FavoriteEntity("Golden Gate Bridge, SF", 37.8199, -122.4783, "Preset", System.currentTimeMillis()));
        presets.add(new FavoriteEntity("Eiffel Tower, Paris", 48.8584, 2.2945, "Preset", System.currentTimeMillis()));
        presets.add(new FavoriteEntity("Enyo Loop Shibuya, Tokyo", 35.6580, 139.7016, "Preset", System.currentTimeMillis()));
        presets.add(new FavoriteEntity("London Eye, UK", 51.5033, -0.1195, "Preset", System.currentTimeMillis()));
        presets.add(new FavoriteEntity("Sydney Opera House, AU", -33.8568, 151.2153, "Preset", System.currentTimeMillis()));
        presets.add(new FavoriteEntity("Taj Mahal, India", 27.1751, 78.0421, "Preset", System.currentTimeMillis()));
        return presets;
    }

    private static String escapeCsv(String val) {
        if (val == null) return "";
        if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }

    private static void shareTextFile(Context context, String filename, String content) {
        try {
            File externalDir = context.getExternalCacheDir();
            if (externalDir == null) return;

            File tempFile = new File(externalDir, filename);
            FileOutputStream fos = new FileOutputStream(tempFile);
            fos.write(content.getBytes());
            fos.close();

            // Share text raw using Share intent
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_SUBJECT, filename);
            intent.putExtra(Intent.EXTRA_TEXT, content);
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.backup_share_chooser_title)));

        } catch (IOException e) {
            Log.e(TAG, "Error writing temporary text share file", e);
            Toast.makeText(context, context.getString(R.string.backup_toast_export_error), Toast.LENGTH_SHORT).show();
        }
    }

    private static void copyFile(File source, File dest) throws IOException {
        try (FileChannel sourceChannel = new FileInputStream(source).getChannel();
             FileChannel destChannel = new FileOutputStream(dest).getChannel()) {
            destChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
        }
    }
}
