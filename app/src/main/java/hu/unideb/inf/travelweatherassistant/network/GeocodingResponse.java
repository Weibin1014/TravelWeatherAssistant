package hu.unideb.inf.travelweatherassistant.network;

import java.util.List;

public class GeocodingResponse {
    // Retrofit/Gson fills this list from the JSON field named "results".
    public List<GeocodingResult> results;

    public static class GeocodingResult {
        // Only the fields used by the app are declared here.
        public String name;
        public double latitude;
        public double longitude;
        public String country;
        public String admin1;
    }
}
