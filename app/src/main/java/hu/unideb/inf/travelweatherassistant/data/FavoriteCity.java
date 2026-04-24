package hu.unideb.inf.travelweatherassistant.data;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "favorite_cities")
public class FavoriteCity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String name;
    public String country;
    public double latitude;
    public double longitude;
    public String lastCondition;
    public double lastTemperature;
    public long updatedAt;

    public FavoriteCity() {
    }

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
