# Travel Weather Assistant

Travel Weather Assistant is an Android app built in Java. It helps users check real-time weather, receive travel advice, and get clothing suggestions before going outside or travelling to another city.

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
