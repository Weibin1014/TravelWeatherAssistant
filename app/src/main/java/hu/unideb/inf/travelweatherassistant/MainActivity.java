package hu.unideb.inf.travelweatherassistant;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import hu.unideb.inf.travelweatherassistant.data.FavoriteCity;
import hu.unideb.inf.travelweatherassistant.data.FavoriteCityDatabase;
import hu.unideb.inf.travelweatherassistant.databinding.ActivityMainBinding;
import hu.unideb.inf.travelweatherassistant.network.GeocodingResponse;
import hu.unideb.inf.travelweatherassistant.network.WeatherResponse;
import hu.unideb.inf.travelweatherassistant.repository.WeatherRepository;
import hu.unideb.inf.travelweatherassistant.ui.FavoriteCityAdapter;
import hu.unideb.inf.travelweatherassistant.util.OutfitAdvisor;
import hu.unideb.inf.travelweatherassistant.util.WeatherInterpreter;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/*
 * MainActivity is the main screen of the app.
 *
 * It connects all important Android course topics:
 * 1. UI: reads and updates views from activity_main.xml with ViewBinding.
 * 2. Location: gets the user's current GPS/network location.
 * 3. Network/API: calls Open-Meteo APIs through WeatherRepository and Retrofit.
 * 4. Persistent storage: saves favorite cities into a Room database.
 * 5. Communication solution: shows Android notifications with travel advice.
 * 6. Creative extension: gives outfit advice based on the weather.
 *
 * The activity intentionally delegates API details to WeatherRepository and
 * weather-code logic to WeatherInterpreter/OutfitAdvisor, so the code is easier to explain.
 */
public class MainActivity extends AppCompatActivity {
    // Android notifications on version 8.0+ must belong to a channel.
    private static final String NOTIFICATION_CHANNEL_ID = "travel_weather_alerts";

    // A fixed id lets the app update/replace the same notification if needed.
    private static final int WEATHER_NOTIFICATION_ID = 101;

    // ViewBinding gives type-safe access to XML views without using findViewById many times.
    private ActivityMainBinding binding;

    // Repository hides the Retrofit setup and keeps API-calling code outside the Activity.
    private WeatherRepository weatherRepository;

    // Room database object used to store and read favorite cities.
    private FavoriteCityDatabase database;

    // RecyclerView adapter responsible for drawing the favorite city list.
    private FavoriteCityAdapter favoriteCityAdapter;

    // Room database operations must not run on the main UI thread.
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();

    // The app starts with Budapest as a default location before the user searches or uses GPS.
    private String currentCityName = "Budapest";
    private String currentCountry = "Hungary";
    private double currentLatitude = 47.4979;
    private double currentLongitude = 19.0402;
    private String currentCondition = "Unknown weather";
    private double currentTemperature = 0.0;
    private String currentAdvice = "Travel advice will be generated from real weather data.";
    private String currentOutfitAdvice = "Clothing advice will appear after weather data is loaded.";

    /*
     * Handles the result of the runtime location permission dialog.
     *
     * Android considers location a dangerous permission. This means declaring it
     * in AndroidManifest.xml is not enough; the user must also approve it while
     * the app is running.
     */
    private final ActivityResultLauncher<String[]> locationPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean fine = result.get(Manifest.permission.ACCESS_FINE_LOCATION);
                Boolean coarse = result.get(Manifest.permission.ACCESS_COARSE_LOCATION);
                if (Boolean.TRUE.equals(fine) || Boolean.TRUE.equals(coarse)) {
                    loadWeatherFromDeviceLocation();
                } else {
                    showStatus("Location permission was denied. You can still search cities manually.");
                }
            });

    /*
     * Handles notification permission.
     *
     * On Android 13/API 33 and newer, apps must ask before posting notifications.
     * On older versions this permission is not required, but the same button still works.
     */
    private final ActivityResultLauncher<String> notificationPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    showWeatherNotification();
                } else {
                    showStatus("Notification permission was denied.");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Inflate the XML layout and prepare the screen.
        binding = ActivityMainBinding.inflate(getLayoutInflater());

        // EdgeToEdge makes the app draw behind system bars for a modern layout.
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());

        // Add padding equal to the status/navigation bars so content is not hidden.
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        weatherRepository = new WeatherRepository();

        // Build the Room database. The database file is stored locally on the device.
        database = Room.databaseBuilder(this, FavoriteCityDatabase.class, "travel_weather_db")
                .fallbackToDestructiveMigration(true)
                .build();

        setupFavoritesList();
        createNotificationChannel();
        setupButtons();
        loadWeatherForCity(currentCityName, currentCountry, currentLatitude, currentLongitude);
    }

    /*
     * Prepares the favorite city RecyclerView.
     *
     * The RecyclerView displays Room data. LiveData keeps it synchronized:
     * when we insert or delete a city, Room emits a new list automatically.
     */
    private void setupFavoritesList() {
        // The adapter displays saved cities and reloads weather when a favorite is tapped.
        favoriteCityAdapter = new FavoriteCityAdapter(city ->
                loadWeatherForCity(city.name, city.country, city.latitude, city.longitude));
        binding.favoritesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.favoritesRecyclerView.setAdapter(favoriteCityAdapter);

        // LiveData automatically refreshes the list when Room data changes.
        database.favoriteCityDao().getAllFavorites().observe(this, favoriteCities -> {
            favoriteCityAdapter.setCities(favoriteCities);
            binding.emptyFavoritesTextView.setVisibility(favoriteCities.isEmpty() ? View.VISIBLE : View.GONE);
        });

        // Swipe left or right to remove a saved city from the Room database.
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                FavoriteCity city = favoriteCityAdapter.getCityAt(viewHolder.getAbsoluteAdapterPosition());
                databaseExecutor.execute(() -> database.favoriteCityDao().delete(city));
                showStatus("Deleted " + city.name + " from favorites.");
            }
        });
        itemTouchHelper.attachToRecyclerView(binding.favoritesRecyclerView);
    }

    /*
     * Connects UI buttons to Java methods.
     *
     * This is the event-listener part of the UI topic from the course.
     */
    private void setupButtons() {
        binding.currentLocationButton.setOnClickListener(v -> requestLocationWeather());
        binding.searchButton.setOnClickListener(v -> searchCity());
        binding.saveFavoriteButton.setOnClickListener(v -> saveCurrentCity());
        binding.notifyButton.setOnClickListener(v -> requestNotificationAndShow());
    }

    /*
     * Searches a city by name.
     *
     * Weather APIs usually need latitude and longitude, not a city string.
     * Therefore this method first calls the geocoding API, receives coordinates,
     * and then calls loadWeatherForCity() with those coordinates.
     */
    private void searchCity() {
        String query = binding.searchCityEditText.getText().toString().trim();
        if (query.isEmpty()) {
            showStatus("Please type a city name first.");
            return;
        }

        // First call the geocoding API to convert a city name into latitude and longitude.
        hideKeyboard();
        showLoading(true, "Searching city...");
        weatherRepository.searchCity(query).enqueue(new Callback<GeocodingResponse>() {
            @Override
            public void onResponse(@NonNull Call<GeocodingResponse> call,
                                   @NonNull Response<GeocodingResponse> response) {
                showLoading(false, "City search completed.");
                GeocodingResponse body = response.body();
                if (!response.isSuccessful() || body == null || body.results == null || body.results.isEmpty()) {
                    showStatus("No city found. Try a more specific name.");
                    return;
                }

                GeocodingResponse.GeocodingResult firstResult = body.results.get(0);
                String country = firstResult.country == null ? "Unknown" : firstResult.country;
                loadWeatherForCity(firstResult.name, country, firstResult.latitude, firstResult.longitude);
            }

            @Override
            public void onFailure(@NonNull Call<GeocodingResponse> call, @NonNull Throwable t) {
                showLoading(false, "Could not search city: " + t.getMessage());
            }
        });
    }

    /*
     * Starts the "weather by current location" flow.
     *
     * If permission is missing, the app asks for it. If permission already exists,
     * it directly tries to read the device location.
     */
    private void requestLocationWeather() {
        // Location is a dangerous permission, so it must be requested at runtime.
        if (!hasLocationPermission()) {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
            return;
        }
        loadWeatherFromDeviceLocation();
    }

    /*
     * Checks whether the app can access at least one type of location.
     *
     * Fine location is GPS-level precision. Coarse location is approximate
     * network/cell-tower precision. Either is enough for a weather forecast.
     */
    private boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /*
     * Reads the device location and loads weather for it.
     *
     * The method first tries "last known location" because it is fast and saves battery.
     * If no cached location exists, it requests one fresh location update.
     */
    private void loadWeatherFromDeviceLocation() {
        if (!hasLocationPermission()) {
            showStatus("Location permission is required for GPS weather.");
            return;
        }

        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            showStatus("Location service is not available on this device.");
            return;
        }

        Location lastLocation = getBestLastKnownLocation(locationManager);
        if (lastLocation != null) {
            loadWeatherForCity("Current location", "GPS", lastLocation.getLatitude(), lastLocation.getLongitude());
            return;
        }

        // If Android has no cached location, request one fresh location update.
        requestSingleLocationUpdate(locationManager);
    }

    /*
     * Finds the best cached location from all enabled providers.
     *
     * Android can have several providers, for example GPS_PROVIDER and NETWORK_PROVIDER.
     * The smaller accuracy value means the location is more precise.
     */
    private Location getBestLastKnownLocation(LocationManager locationManager) {
        // Try every enabled provider and keep the most accurate last known location.
        List<String> providers = locationManager.getProviders(true);
        Location bestLocation = null;
        for (String provider : providers) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return null;
            }
            Location location = locationManager.getLastKnownLocation(provider);
            if (location != null && (bestLocation == null || location.getAccuracy() < bestLocation.getAccuracy())) {
                bestLocation = location;
            }
        }
        return bestLocation;
    }

    /*
     * Requests one new location update.
     *
     * This is used only when the device has no cached location. In a real production
     * app we might keep listening for multiple updates, but for this course project
     * one update is enough to demonstrate localization.
     */
    private void requestSingleLocationUpdate(LocationManager locationManager) {
        List<String> providers = locationManager.getProviders(true);
        if (providers.isEmpty()) {
            showStatus("Please enable location services on the device.");
            return;
        }

        String provider = providers.contains(LocationManager.NETWORK_PROVIDER)
                ? LocationManager.NETWORK_PROVIDER
                : providers.get(0);
        showLoading(true, "Waiting for GPS location...");

        LocationListener listener = location -> {
            showLoading(false, "Location received.");
            loadWeatherForCity("Current location", "GPS", location.getLatitude(), location.getLongitude());
        };

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            showStatus("Location permission is required for GPS weather.");
            return;
        }
        locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper());
    }

    /*
     * Loads weather from the weather API for known coordinates.
     *
     * This method is used by three different features:
     * 1. Default Budapest weather on app startup.
     * 2. Searched city weather after geocoding.
     * 3. Current GPS location weather.
     */
    private void loadWeatherForCity(String cityName, String country, double latitude, double longitude) {
        currentCityName = cityName;
        currentCountry = country;
        currentLatitude = latitude;
        currentLongitude = longitude;

        binding.cityNameTextView.setText(cityName + ", " + country);
        showLoading(true, "Loading weather for " + cityName + "...");

        // Retrofit runs the network request asynchronously and returns on the main thread.
        weatherRepository.fetchWeather(latitude, longitude).enqueue(new Callback<WeatherResponse>() {
            @Override
            public void onResponse(@NonNull Call<WeatherResponse> call,
                                   @NonNull Response<WeatherResponse> response) {
                showLoading(false, "Weather updated.");
                WeatherResponse body = response.body();
                if (!response.isSuccessful() || body == null || body.current == null) {
                    showStatus("Weather API did not return usable data.");
                    return;
                }
                renderWeather(cityName, country, body.current);
            }

            @Override
            public void onFailure(@NonNull Call<WeatherResponse> call, @NonNull Throwable t) {
                showLoading(false, "Could not load weather: " + t.getMessage());
            }
        });
    }

    /*
     * Updates the UI after a successful weather response.
     *
     * The raw API response contains numbers such as weather_code. The app converts
     * those numbers into readable condition text, icons, travel advice and outfit advice.
     */
    private void renderWeather(String cityName, String country, WeatherResponse.CurrentWeather current) {
        // Convert API values into user-friendly text, icons and travel advice.
        currentCondition = WeatherInterpreter.describeCode(current.weatherCode);
        currentTemperature = current.temperature;
        currentAdvice = WeatherInterpreter.travelAdvice(current);
        currentOutfitAdvice = OutfitAdvisor.suggestOutfit(current);

        binding.cityNameTextView.setText(cityName + ", " + country);
        binding.weatherIconTextView.setText(WeatherInterpreter.iconForCode(current.weatherCode));
        binding.temperatureTextView.setText(String.format(Locale.getDefault(), "%.1f°C", current.temperature));
        binding.conditionTextView.setText(currentCondition);
        binding.detailsTextView.setText(String.format(Locale.getDefault(),
                "Feels like %.1f°C · Humidity %d%% · Wind %.1f km/h · Rain %.1f mm",
                current.apparentTemperature,
                current.humidity,
                current.windSpeed,
                current.rain));
        binding.adviceTextView.setText(currentAdvice);
        binding.outfitAdviceTextView.setText(currentOutfitAdvice);
    }

    /*
     * Saves the currently displayed city to the Room database.
     *
     * The app also saves a short snapshot of the latest weather, so the favorites
     * list can show temperature and condition without immediately calling the API.
     */
    private void saveCurrentCity() {
        // Store the current weather summary so the favorites list can show useful details.
        FavoriteCity favoriteCity = new FavoriteCity(
                currentCityName,
                currentCountry,
                currentLatitude,
                currentLongitude,
                currentCondition,
                currentTemperature,
                System.currentTimeMillis()
        );

        databaseExecutor.execute(() -> {
            // Avoid saving the same city twice.
            int count = database.favoriteCityDao().countByName(currentCityName, currentCountry);
            if (count > 0) {
                runOnUiThread(() -> showStatus(currentCityName + " is already in favorites."));
                return;
            }
            database.favoriteCityDao().insert(favoriteCity);
            runOnUiThread(() -> showStatus("Saved " + currentCityName + " to favorites."));
        });
    }

    /*
     * Checks notification permission and then displays the weather advice notification.
     */
    private void requestNotificationAndShow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            return;
        }
        showWeatherNotification();
    }

    /*
     * Creates a notification channel for weather alerts.
     *
     * This must be done before showing notifications on Android 8.0/API 26+.
     */
    private void createNotificationChannel() {
        // Notification channels are required on Android 8.0 and newer.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Weather travel alerts",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription("Notifications with weather-based travel advice.");
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
    }

    /*
     * Builds and shows a system notification.
     *
     * BigTextStyle is used because travel advice may be longer than one line.
     */
    private void showWeatherNotification() {
        // The notification contains the generated travel advice for the current city.
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            showStatus("Notification permission is required on this Android version.");
            return;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Weather advice for " + currentCityName)
                .setContentText(currentAdvice)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(currentAdvice + "\n\nOutfit: " + currentOutfitAdvice))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        NotificationManagerCompat.from(this).notify(WEATHER_NOTIFICATION_ID, builder.build());
        showStatus("Notification shown.");
    }

    /*
     * Shows or hides the loading spinner and writes a short status message.
     */
    private void showLoading(boolean loading, String message) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        showStatus(message);
    }

    /*
     * Displays feedback both in the status TextView and as a Toast.
     */
    private void showStatus(String message) {
        binding.statusTextView.setText(message);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    /*
     * Hides the soft keyboard after the user presses Search.
     */
    private void hideKeyboard() {
        InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null && getCurrentFocus() != null) {
            manager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Stop the database thread when the Activity is destroyed.
        databaseExecutor.shutdown();
    }
}
