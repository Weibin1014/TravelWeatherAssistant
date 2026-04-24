package hu.unideb.inf.travelweatherassistant.data;

import androidx.room.Database;
import androidx.room.RoomDatabase;

/*
 * Room database configuration.
 *
 * entities tells Room which tables exist in this database.
 * version is used for database migrations when the structure changes.
 */
@Database(entities = {FavoriteCity.class}, version = 1, exportSchema = false)
public abstract class FavoriteCityDatabase extends RoomDatabase {
    // This DAO is the only access point for favorite city table operations.
    public abstract FavoriteCityDao favoriteCityDao();
}
