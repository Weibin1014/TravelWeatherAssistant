package hu.unideb.inf.travelweatherassistant.network;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/*
 * Retrofit API interface for city search.
 *
 * Retrofit reads these annotations and builds the HTTP request.
 * Base URL is defined in WeatherRepository; this file only defines the endpoint.
 */
public interface GeocodingApiService {
    // Converts a city name into coordinates using the Open-Meteo Geocoding API.
    @GET("search")
    Call<GeocodingResponse> searchCity(
            @Query("name") String cityName,
            @Query("count") int count,
            @Query("language") String language,
            @Query("format") String format
    );
}
