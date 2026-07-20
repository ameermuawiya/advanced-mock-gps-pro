package com.muawiya.fakegps.data;

import android.content.Context;
import androidx.lifecycle.LiveData;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocationRepository {
    private final LocationDao locationDao;
    private final Context mContext;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public LocationRepository(Context context) {
        this.mContext = context.getApplicationContext();
        LocationDatabase db = LocationDatabase.getDatabase(context);
        this.locationDao = db.locationDao();
    }

    public LiveData<List<FavoriteEntity>> getAllFavorites() {
        return locationDao.getAllFavorites();
    }

    public LiveData<List<FavoriteEntity>> searchFavorites(String query) {
        return locationDao.searchFavorites("%" + query + "%");
    }

    public void insertFavorite(FavoriteEntity entity) {
        executor.execute(() -> locationDao.insertFavorite(entity));
    }

    public void deleteFavoriteById(int id) {
        executor.execute(() -> locationDao.deleteFavoriteById(id));
    }

    public LiveData<List<HistoryEntity>> getAllHistory() {
        return locationDao.getAllHistory();
    }

    public LiveData<List<HistoryEntity>> searchHistory(String query) {
        return locationDao.searchHistory("%" + query + "%");
    }

    public void insertHistory(HistoryEntity entity) {
        executor.execute(() -> locationDao.insertHistory(entity));
    }

    public void deleteHistoryById(int id) {
        executor.execute(() -> locationDao.deleteHistoryById(id));
    }

    public void setSetting(String key, String value) {
        android.content.SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(mContext);
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            prefs.edit().putBoolean(key, Boolean.parseBoolean(value)).apply();
        } else {
            prefs.edit().putString(key, value).apply();
        }
    }

    public String getSetting(String key, String defaultValue) {
        android.content.SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(mContext);
        return prefs.getString(key, defaultValue);
    }
}
