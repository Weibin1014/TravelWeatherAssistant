package hu.unideb.inf.travelweatherassistant.data;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {FavoriteCity.class}, version = 1, exportSchema = false)
public abstract class FavoriteCityDatabase extends RoomDatabase {
    public abstract FavoriteCityDao favoriteCityDao();
}
