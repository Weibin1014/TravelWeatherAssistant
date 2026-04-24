# Travel Weather Assistant

An Android final project app built in Java for the Mobile Development course.

## Main idea

Travel Weather Assistant helps users check real-time weather for their current GPS location or for a searched city. The app also generates simple travel advice, for example whether the user should bring an umbrella, wear warm clothes, or use sunscreen.

## Main features

- Get weather for the current GPS location
- Search weather by city name
- Call the Open-Meteo third-party weather and geocoding APIs
- Show temperature, weather condition, humidity, wind speed and rain amount
- Generate travel advice from the current weather data
- Save favorite cities locally with Room database
- Tap a favorite city to reload its weather
- Swipe a favorite city to delete it
- Show an Android notification with weather advice
- Material-style responsive UI

## Course topics used

- Week 2-3: User interface design
- Week 5: Persistent data storage with Room
- Week 6: Localization / GPS location
- Week 8: High-level network communication
- Week 10: Multimedia-style weather icons and visual UI
- Week 11: API calling
- Week 12: Repository pattern and separated app layers

## Third-party technologies

- Open-Meteo Weather API
- Open-Meteo Geocoding API
- Retrofit
- Gson converter
- Room database
- Material Components

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
