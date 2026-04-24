package hu.unideb.inf.travelweatherassistant.network;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface WeatherApiService {
    // Requests current weather data for the given coordinates from Open-Meteo.
    @GET("forecast")
    Call<WeatherResponse> getCurrentWeather(
            @Query("latitude") double latitude,
            @Query("longitude") double longitude,
            @Query("current") String currentFields,
            @Query("timezone") String timezone
    );
}
