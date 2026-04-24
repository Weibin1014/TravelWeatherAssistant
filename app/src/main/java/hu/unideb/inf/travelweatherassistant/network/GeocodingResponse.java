package hu.unideb.inf.travelweatherassistant.network;

import java.util.List;

/*
 * Java model for the JSON response from the geocoding API.
 *
 * Gson fills these fields automatically. The field names match the JSON keys,
 * so most of them do not need @SerializedName.
 */
public class GeocodingResponse {
    // Retrofit/Gson fills this list from the JSON field named "results".
    public List<GeocodingResult> results;

    /*
     * One possible city result from the API.
     *
     * Example: searching "Paris" can return Paris in France and other cities
     * named Paris in different countries. MainActivity currently uses the first result.
     */
    public static class GeocodingResult {
        // Only the fields used by the app are declared here.
        public String name;
        public double latitude;
        public double longitude;
        public String country;
        public String admin1;
    }
}
