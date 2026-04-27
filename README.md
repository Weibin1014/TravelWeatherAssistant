# TripCast

TripCast is an Android app built in Java. It helps users check real-time weather, receive travel advice, and get clothing suggestions before going outside or travelling to another city.

## What the app does

The app can show weather information for a searched city or for the user's current location. It uses real weather data and turns it into practical advice that is easy to understand.

The main screen shows:

- City name and country
- Current temperature
- Weather condition
- Weather icon
- Feels-like temperature
- Humidity
- Wind speed
- Rain amount
- Travel advice
- Outfit/clothing advice

## Main features

- Search weather by city name
- Get weather using the phone's current GPS location
- Load real-time weather from the Open-Meteo Weather API
- Convert city names into coordinates with the Open-Meteo Geocoding API
- Save favorite cities locally
- Display favorite cities in a RecyclerView list
- Tap a favorite city to reload its weather
- Swipe a favorite city left or right to delete it
- Generate weather-based travel advice
- Generate outfit advice based on temperature, rain, snow, wind and storms
- Show an Android notification with the current advice
- Use a clean Material-style interface

## Outfit Advisor

The creative part of the app is the `OutfitAdvisor` module.

It works like a small local agent. It reads the current weather data and recommends what the user should wear. For example, it can suggest a winter coat, light jacket, raincoat, waterproof shoes, sunglasses, sunscreen, or windbreaker depending on the weather.

This feature currently works without an API key because it is rule-based. The code is separated from `MainActivity`, so it can later be replaced with a real LLM API call if needed.

## Favorite cities

Users can save the currently displayed city as a favorite. Favorites are stored on the device with a Room database. The favorite list also shows the last saved temperature and weather condition.

## Notifications

The app can show a weather notification. The notification includes the current travel advice and outfit suggestion, so the user can quickly check what to prepare before going outside.

## Background concepts

### Location and permissions

The app uses Android location services to read the user's current location. Because location is sensitive personal data, Android requires location permissions. The app requests location permission only when the user presses the GPS weather button.

### Weather API and geocoding

The weather API needs latitude and longitude. When the user searches by city name, the app first calls the geocoding API to convert the city name into coordinates. Then it sends those coordinates to the weather API to get current weather data.

### Retrofit and JSON parsing

Retrofit is used to define API requests as Java interfaces. Gson is used with Retrofit to convert JSON responses into Java model classes such as `WeatherResponse` and `GeocodingResponse`.

### Room database

Room is used for local persistent storage. In this app, `FavoriteCity` is the entity, `FavoriteCityDao` contains database operations, and `FavoriteCityDatabase` provides the database instance.

### RecyclerView

RecyclerView is used to display the saved favorite cities efficiently. The adapter converts each `FavoriteCity` object into one visible row in the list.

### Notifications

Android notifications are used to show short advice outside the main app screen. The app creates a notification channel and then displays the current travel and outfit advice.

### Rule-based agent idea

The `OutfitAdvisor` module is rule-based. It checks weather values such as apparent temperature, rain, snow, wind speed and thunderstorm conditions, then generates a clothing recommendation. This is not a real LLM yet, but it follows a similar idea: taking structured weather input and producing human-friendly advice.

## Technologies used

- Java
- Android Studio
- Open-Meteo Weather API
- Open-Meteo Geocoding API
- Retrofit
- Gson converter
- Room database
- RecyclerView
- Material Components
- Android location services
- Android notifications

## References

- [Android Developers: Request location permissions](https://developer.android.com/develop/sensors-and-location/location/permissions)
- [Android Developers: Request location access at runtime](https://developer.android.com/develop/sensors-and-location/location/permissions/runtime)
- [Android Developers: Save data in a local database using Room](https://developer.android.com/room)
- [Android Developers: Create dynamic lists with RecyclerView](https://developer.android.com/develop/ui/views/layout/recyclerview)
- [Android Developers: About notifications](https://developer.android.com/guide/topics/ui/notifiers/notifications.html)
- [Open-Meteo Weather API documentation](https://open-meteo.com/en/docs)
- [Open-Meteo Geocoding API documentation](https://open-meteo.com/en/docs/geocoding-api)
- [Retrofit official documentation](https://square.github.io/retrofit/)
- [Gson User Guide](https://google.github.io/gson/UserGuide.html)

## How to run

1. Open this folder in Android Studio.
2. Wait for Gradle sync to finish.
3. Run the `app` configuration on an emulator or Android phone.
4. For GPS weather, grant location permission.
5. For weather notifications, grant notification permission on Android 13 or newer.

## Build check

The project was checked with:

```bash
./gradlew assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```
