package hu.unideb.inf.travelweatherassistant.network;

import com.google.gson.annotations.SerializedName;

public class WeatherResponse {
    public double latitude;
    public double longitude;
    public CurrentWeather current;

    public static class CurrentWeather {
        public String time;

        // SerializedName maps Java-friendly names to the JSON field names returned by the API.
        @SerializedName("temperature_2m")
        public double temperature;

        @SerializedName("relative_humidity_2m")
        public int humidity;

        @SerializedName("apparent_temperature")
        public double apparentTemperature;

        public double precipitation;
        public double rain;

        @SerializedName("weather_code")
        public int weatherCode;

        @SerializedName("wind_speed_10m")
        public double windSpeed;
    }
}
