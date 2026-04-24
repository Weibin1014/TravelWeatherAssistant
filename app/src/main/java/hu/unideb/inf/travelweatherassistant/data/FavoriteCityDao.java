package hu.unideb.inf.travelweatherassistant.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/*
 * DAO means Data Access Object.
 *
 * This interface defines all database actions that the app needs.
 * Room generates the actual implementation automatically at build time.
 */
@Dao
public interface FavoriteCityDao {
    // Insert a favorite city into the local Room database.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(FavoriteCity city);

    // Delete is used by the swipe gesture in the RecyclerView.
    @Delete
    void delete(FavoriteCity city);

    // LiveData updates the UI automatically whenever the table changes.
    @Query("SELECT * FROM favorite_cities ORDER BY updatedAt DESC")
    LiveData<List<FavoriteCity>> getAllFavorites();

    // Used to prevent duplicate favorite cities.
    @Query("SELECT COUNT(*) FROM favorite_cities WHERE name = :name AND country = :country")
    int countByName(String name, String country);
}
