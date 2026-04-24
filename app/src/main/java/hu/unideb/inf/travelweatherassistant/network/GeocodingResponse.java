package hu.unideb.inf.travelweatherassistant.network;

import java.util.List;

public class GeocodingResponse {
    public List<GeocodingResult> results;

    public static class GeocodingResult {
        public String name;
        public double latitude;
        public double longitude;
        public String country;
        public String admin1;
    }
}
