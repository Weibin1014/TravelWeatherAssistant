package hu.unideb.inf.travelweatherassistant.data;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/*
 * FavoriteCity is a Room Entity.
 *
 * In Room, an Entity represents one database table. Each object of this class
 * becomes one row in the favorite_cities table.
 */
@Entity(tableName = "favorite_cities")
public class FavoriteCity {
    // Room uses this auto-generated id as the primary key for each saved city.
    @PrimaryKey(autoGenerate = true)
    public int id;

    // Basic city information and the coordinates needed for the weather API.
    public String name;
    public String country;
    public double latitude;
    public double longitude;

    // Last known weather values are stored for a quick favorites list preview.
    public String lastCondition;
    public double lastTemperature;
    public long updatedAt;

    // Room needs an empty constructor to recreate objects from the database.
    public FavoriteCity() {
    }

    /*
     * This constructor is used by our own Java code when saving a city.
     * Room ignores it because Room uses the empty constructor above.
     */
    @Ignore
    public FavoriteCity(String name, String country, double latitude, double longitude,
                        String lastCondition, double lastTemperature, long updatedAt) {
        this.name = name;
        this.country = country;
        this.latitude = latitude;
        this.longitude = longitude;
        this.lastCondition = lastCondition;
        this.lastTemperature = lastTemperature;
        this.updatedAt = updatedAt;
    }
}
