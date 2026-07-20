package com.muawiya.fakegps.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface LocationDao {
    @Query("SELECT * FROM favorites ORDER BY timestamp DESC")
    LiveData<List<FavoriteEntity>> getAllFavorites();

    @Query("SELECT * FROM favorites WHERE name LIKE :query OR category LIKE :query ORDER BY timestamp DESC")
    LiveData<List<FavoriteEntity>> searchFavorites(String query);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertFavorite(FavoriteEntity entity);

    @Query("DELETE FROM favorites WHERE id = :id")
    void deleteFavoriteById(int id);

    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    LiveData<List<HistoryEntity>> getAllHistory();

    @Query("SELECT * FROM history WHERE name LIKE :query OR category LIKE :query ORDER BY timestamp DESC")
    LiveData<List<HistoryEntity>> searchHistory(String query);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertHistory(HistoryEntity entity);

    @Query("DELETE FROM history WHERE id = :id")
    void deleteHistoryById(int id);
}
