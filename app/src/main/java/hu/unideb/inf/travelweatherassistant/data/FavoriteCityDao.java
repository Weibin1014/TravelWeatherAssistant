package hu.unideb.inf.travelweatherassistant.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface FavoriteCityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(FavoriteCity city);

    @Delete
    void delete(FavoriteCity city);

    @Query("SELECT * FROM favorite_cities ORDER BY updatedAt DESC")
    LiveData<List<FavoriteCity>> getAllFavorites();

    @Query("SELECT COUNT(*) FROM favorite_cities WHERE name = :name AND country = :country")
    int countByName(String name, String country);
}
