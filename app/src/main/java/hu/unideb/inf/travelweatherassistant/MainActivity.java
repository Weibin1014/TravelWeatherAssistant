package hu.unideb.inf.travelweatherassistant;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
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

import java.io.IOException;
import java.util.ArrayList;
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
 * 6. BroadcastReceiver: monitors network connection changes.
 * 7. Torch/CameraManager: flashes the phone torch as a weather safety alert.
 * 8. Creative extension: gives outfit advice based on the weather.
 *
 * The activity intentionally delegates API details to WeatherRepository and
 * weather-code logic to WeatherInterpreter/OutfitAdvisor, so the code is easier to explain.
 */
public class MainActivity extends AppCompatActivity {
    // Android notifications on version 8.0+ must belong to a channel.
    private static final String NOTIFICATION_CHANNEL_ID = "travel_weather_alerts";

    // A fixed id lets the app update/replace the same notification if needed.
    private static final int WEATHER_NOTIFICATION_ID = 101;

    // Weather only needs city-level accuracy, so the GPS button can respond quickly.
    private static final long LOCATION_TIMEOUT_MS = 3000;
    private static final long RECENT_LOCATION_MAX_AGE_MS = 2 * 60 * 1000;
    private static final float WEATHER_LOCATION_ACCURACY_METERS = 5000f;

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

    // BroadcastReceiver listens for Android system broadcasts while the Activity is visible.
    private BroadcastReceiver networkReceiver;
    private boolean networkReceiverRegistered = false;

    // CameraManager controls the device torch/flashlight for the weather alert demo.
    private CameraManager cameraManager;
    private String torchCameraId;
    private final Handler torchHandler = new Handler(Looper.getMainLooper());

    // The app starts with Budapest as a default location before the user searches or uses GPS.
    private String currentCityName = "Budapest";
    private String currentCountry = "Hungary";
    private double currentLatitude = 47.4979;
    private double currentLongitude = 19.0402;
    private String currentCondition = "Unknown weather";
    private double currentTemperature = 0.0;
    private String currentAdvice = "Travel advice will be generated from real weather data.";
    private String currentOutfitAdvice = "Clothing advice will appear after weather data is loaded.";
    private long lastUpdatedAt = 0L;

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

    /*
     * Handles camera permission for the torch alert.
     *
     * The app only asks for this permission when the user presses the torch button,
     * because the flashlight is an optional demonstration feature.
     */
    private final ActivityResultLauncher<String> cameraPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startTorchWeatherAlert();
                } else {
                    showStatus("Camera permission was denied. Torch alert cannot run.");
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
        setupNetworkReceiver();
        setupTorch();
        createNotificationChannel();
        setupButtons();
        loadWeatherForCity(currentCityName, currentCountry, currentLatitude, currentLongitude);
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Register the receiver only while the screen is visible to avoid leaks.
        registerNetworkReceiver();
        updateNetworkStatus(isNetworkAvailable());
    }

    @Override
    protected void onStop() {
        super.onStop();

        // Dynamic receivers should be unregistered when the Activity stops.
        unregisterNetworkReceiver();
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
        binding.refreshWeatherButton.setOnClickListener(v -> refreshCurrentWeather());
        binding.notifyButton.setOnClickListener(v -> requestNotificationAndShow());
        binding.torchAlertButton.setOnClickListener(v -> requestTorchWeatherAlert());

        // Demo buttons reduce typing during the final presentation.
        binding.budapestButton.setOnClickListener(v ->
                loadDemoCity("Budapest", "Hungary", 47.4979, 19.0402));
        binding.debrecenButton.setOnClickListener(v ->
                loadDemoCity("Debrecen", "Hungary", 47.5316, 21.6273));
        binding.londonButton.setOnClickListener(v ->
                loadDemoCity("London", "United Kingdom", 51.5072, -0.1276));
    }

    /*
     * Creates the BroadcastReceiver used for the network-status feature.
     *
     * A BroadcastReceiver is an Android component that reacts to system or app
     * messages. Here it listens for connectivity changes and updates the UI pill
     * at the top of the screen.
     */
    private void setupNetworkReceiver() {
        networkReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateNetworkStatus(isNetworkAvailable());
            }
        };
    }

    /*
     * Registers the network BroadcastReceiver dynamically.
     *
     * Dynamic registration is a safe choice for this app because the receiver is
     * only needed while MainActivity is open.
     */
    private void registerNetworkReceiver() {
        if (networkReceiver == null || networkReceiverRegistered) {
            return;
        }

        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(networkReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(networkReceiver, filter);
        }
        networkReceiverRegistered = true;
    }

    /*
     * Unregisters the BroadcastReceiver when the Activity is no longer visible.
     */
    private void unregisterNetworkReceiver() {
        if (!networkReceiverRegistered) {
            return;
        }

        unregisterReceiver(networkReceiver);
        networkReceiverRegistered = false;
    }

    /*
     * Checks whether the phone currently has an internet-capable network.
     *
     * The app already uses the internet for Open-Meteo. This method makes that
     * hidden dependency visible to the user and also demonstrates connectivity APIs.
     */
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return false;
        }

        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork == null) {
            return false;
        }

        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    /*
     * Updates the small network status view shown near the app header.
     */
    private void updateNetworkStatus(boolean online) {
        if (online) {
            binding.networkStatusTextView.setText("Network: online - weather API ready");
        } else {
            binding.networkStatusTextView.setText("Network: offline - saved cities still available");
        }
    }

    /*
     * Finds a camera that has a flashlight.
     *
     * CameraManager is a lower-level Android API. The app does not take photos;
     * it only uses the torch mode of a camera with flash support.
     */
    private void setupTorch() {
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        if (cameraManager == null) {
            return;
        }

        try {
            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                Boolean flashAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (Boolean.TRUE.equals(flashAvailable)) {
                    torchCameraId = cameraId;
                    return;
                }
            }
        } catch (CameraAccessException ignored) {
            // If the camera service cannot be reached, the app can still run without torch.
        }
    }

    /*
     * Starts the torch weather alert flow.
     *
     * The alert is manual on purpose: the app never turns on the flashlight by
     * itself. The user presses the button during a demo or when they want a visual alert.
     */
    private void requestTorchWeatherAlert() {
        if (torchCameraId == null) {
            setupTorch();
        }

        if (torchCameraId == null) {
            showStatus("No flashlight is available on this device.");
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
            return;
        }

        startTorchWeatherAlert();
    }

    /*
     * Flashes the torch in a short pattern.
     *
     * This is connected to the weather app idea as a safety alert. For example,
     * it could be used during rain, storm, fog or low visibility.
     */
    private void startTorchWeatherAlert() {
        if (cameraManager == null || torchCameraId == null) {
            showStatus("Torch alert is not available on this device.");
            return;
        }

        showStatus("Torch weather alert flashing...");
        torchHandler.removeCallbacksAndMessages(null);

        boolean[] pattern = {true, false, true, false, true, false};
        long delayMs = 0;
        for (boolean state : pattern) {
            final boolean torchOn = state;
            torchHandler.postDelayed(() -> setTorchState(torchOn), delayMs);
            delayMs += 280;
        }
        torchHandler.postDelayed(() -> showStatus("Torch weather alert finished."), delayMs + 80);
    }

    /*
     * Safely changes the torch state.
     */
    private void setTorchState(boolean enabled) {
        if (cameraManager == null || torchCameraId == null) {
            return;
        }

        try {
            cameraManager.setTorchMode(torchCameraId, enabled);
        } catch (CameraAccessException | SecurityException ignored) {
            showStatus("Unable to control the flashlight on this device.");
        }
    }

    /*
     * Loads one of the prepared demo cities.
     *
     * This makes the live presentation more stable because the user can show
     * city search results without depending on typing speed or spelling.
     */
    private void loadDemoCity(String cityName, String country, double latitude, double longitude) {
        binding.searchCityEditText.setText(cityName);
        hideKeyboard();
        loadWeatherForCity(cityName, country, latitude, longitude);
    }

    /*
     * Reloads weather for the city that is currently displayed.
     */
    private void refreshCurrentWeather() {
        loadWeatherForCity(currentCityName, currentCountry, currentLatitude, currentLongitude);
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
                    showStatus("No city found. Try a more specific name, for example Debrecen.");
                    return;
                }

                GeocodingResponse.GeocodingResult firstResult = body.results.get(0);
                String country = firstResult.country == null ? "Unknown" : firstResult.country;
                loadWeatherForCity(firstResult.name, country, firstResult.latitude, firstResult.longitude);
            }

            @Override
            public void onFailure(@NonNull Call<GeocodingResponse> call, @NonNull Throwable t) {
                showLoading(false, friendlyNetworkError("search the city"));
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
     * The GPS button should not reuse the previously searched city. It first tries
     * a recent device location for speed, then asks Android for a fresh update if
     * no good recent location is available.
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

        Location cachedLocation = getBestLastKnownLocation(locationManager);
        if (cachedLocation != null && isWeatherLocationReady(cachedLocation)) {
            showLoading(true, "Using recent device location...");
            loadWeatherForResolvedLocation(cachedLocation);
            return;
        }

        requestSingleLocationUpdate(locationManager);
    }

    /*
     * Finds the best recent cached location from all enabled providers.
     *
     * Android can have several providers, for example GPS_PROVIDER and NETWORK_PROVIDER.
     * For weather, a recent city-level location is more useful than an old but
     * extremely precise location.
     */
    private Location getBestLastKnownLocation(LocationManager locationManager) {
        List<String> providers = locationManager.getProviders(true);
        Location bestLocation = null;
        for (String provider : providers) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return null;
            }
            Location location = locationManager.getLastKnownLocation(provider);
            if (location != null && isRecentLocation(location) && isBetterLocation(location, bestLocation)) {
                bestLocation = location;
            }
        }
        return bestLocation;
    }

    private boolean isRecentLocation(Location location) {
        long age = System.currentTimeMillis() - location.getTime();
        return location.getTime() == 0 || (age >= 0 && age < RECENT_LOCATION_MAX_AGE_MS);
    }

    private String[] knownEnglishLocationName(double latitude, double longitude) {
        if (isNear(latitude, longitude, 47.5316, 21.6273, 25)) {
            return new String[]{"Debrecen", "Hungary"};
        }
        if (isNear(latitude, longitude, 47.4979, 19.0402, 25)) {
            return new String[]{"Budapest", "Hungary"};
        }
        if (isNear(latitude, longitude, 51.5072, -0.1276, 25)) {
            return new String[]{"London", "United Kingdom"};
        }
        return null;
    }

    private boolean isNear(double latitude, double longitude, double targetLatitude,
                           double targetLongitude, double maxDistanceKm) {
        float[] distance = new float[1];
        Location.distanceBetween(latitude, longitude, targetLatitude, targetLongitude, distance);
        return distance[0] <= maxDistanceKm * 1000;
    }

    private boolean isFreshLocation(Location location) {
        return isRecentLocation(location);
    }

    private boolean isWeatherLocationReady(Location location) {
        return isFreshLocation(location)
                && (!location.hasAccuracy() || location.getAccuracy() <= WEATHER_LOCATION_ACCURACY_METERS);
    }

    private boolean isBetterLocation(Location newLocation, Location currentBest) {
        if (currentBest == null) {
            return true;
        }

        // For weather, a clearly more accurate city-level position is usually
        // better than simply preferring the GPS provider name.
        if (newLocation.hasAccuracy() && currentBest.hasAccuracy()) {
            float accuracyDifference = newLocation.getAccuracy() - currentBest.getAccuracy();
            if (Math.abs(accuracyDifference) > 500) {
                return accuracyDifference < 0;
            }
        } else if (newLocation.hasAccuracy() && !currentBest.hasAccuracy()) {
            return true;
        }

        if (LocationManager.GPS_PROVIDER.equals(newLocation.getProvider())
                && !LocationManager.GPS_PROVIDER.equals(currentBest.getProvider())) {
            return true;
        }
        return newLocation.getTime() > currentBest.getTime();
    }

    private List<String> getLocationProvidersInPreferredOrder(LocationManager locationManager) {
        List<String> orderedProviders = new ArrayList<>();

        // Prefer GPS, but still allow network location as a practical fallback.
        // On many phones or emulators, pure GPS may not get a fix indoors.
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            orderedProviders.add(LocationManager.GPS_PROVIDER);
        }

        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            orderedProviders.add(LocationManager.NETWORK_PROVIDER);
        }
        for (String provider : locationManager.getProviders(true)) {
            if (!orderedProviders.contains(provider)) {
                orderedProviders.add(provider);
            }
        }
        return orderedProviders;
    }

    /*
     * Requests one new location update.
     *
     * This method is used by the GPS button. It prefers fresh location data so the
     * result does not stay on a city that was previously searched manually.
     */
    private void requestSingleLocationUpdate(LocationManager locationManager) {
        List<String> providers = getLocationProvidersInPreferredOrder(locationManager);
        if (providers.isEmpty()) {
            showStatus("Please enable location services on the device.");
            return;
        }

        showLoading(true, "Getting current GPS location...");

        Handler handler = new Handler(Looper.getMainLooper());
        boolean[] locationHandled = {false};
        Location[] bestFreshLocation = {null};
        LocationListener[] listenerHolder = new LocationListener[1];
        Runnable[] timeoutHolder = new Runnable[1];

        listenerHolder[0] = location -> {
            if (locationHandled[0]) {
                return;
            }
            if (!isFreshLocation(location)) {
                return;
            }

            if (isBetterLocation(location, bestFreshLocation[0])) {
                bestFreshLocation[0] = location;
            }

            // Weather only needs city-level precision. Once a fresh location is
            // accurate enough for weather, update the card immediately instead of
            // waiting for a perfect GPS fix.
            if (isWeatherLocationReady(location)) {
                locationHandled[0] = true;
                if (timeoutHolder[0] != null) {
                    handler.removeCallbacks(timeoutHolder[0]);
                }
                locationManager.removeUpdates(listenerHolder[0]);
                showStatus("Current location received.");
                loadWeatherForResolvedLocation(location);
            }
        };

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            showStatus("Location permission is required for GPS weather.");
            return;
        }

        int registeredProviders = 0;
        for (String provider : providers) {
            try {
                locationManager.requestLocationUpdates(provider, 0, 0, listenerHolder[0], Looper.getMainLooper());
                registeredProviders++;
            } catch (SecurityException ignored) {
                // The app may have approximate location only; skip providers Android refuses.
            } catch (IllegalArgumentException ignored) {
                // Some devices may report a provider that cannot be used.
            }
        }

        if (registeredProviders == 0) {
            showLoading(false, "No available location provider. Please enable GPS and try again.");
            return;
        }

        timeoutHolder[0] = () -> {
            if (locationHandled[0]) {
                return;
            }
            locationHandled[0] = true;
            locationManager.removeUpdates(listenerHolder[0]);

            if (bestFreshLocation[0] != null) {
                showStatus("Using best current location.");
                loadWeatherForResolvedLocation(bestFreshLocation[0]);
            } else {
                Location cachedLocation = getBestLastKnownLocation(locationManager);
                if (cachedLocation != null && isRecentLocation(cachedLocation)) {
                    showStatus("Using recent device location.");
                    loadWeatherForResolvedLocation(cachedLocation);
                } else {
                    showLoading(false, "Could not get a current GPS location. Please enable location and try again.");
                }
            }
        };
        handler.postDelayed(timeoutHolder[0], LOCATION_TIMEOUT_MS);
    }

    private void loadWeatherForResolvedLocation(Location location) {
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();

        databaseExecutor.execute(() -> {
            String cityName = "Current location";
            String countryName = "GPS";
            String[] knownName = knownEnglishLocationName(latitude, longitude);

            if (knownName != null) {
                cityName = knownName[0];
                countryName = knownName[1];
            } else if (Geocoder.isPresent()) {
                try {
                    // Use English for reverse geocoding so the app UI stays consistent.
                    Geocoder geocoder = new Geocoder(this, Locale.ENGLISH);
                    List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
                    if (addresses != null && !addresses.isEmpty()) {
                        Address address = addresses.get(0);
                        if (address.getLocality() != null) {
                            cityName = address.getLocality();
                        } else if (address.getSubAdminArea() != null) {
                            cityName = address.getSubAdminArea();
                        }
                        if (address.getCountryName() != null) {
                            countryName = address.getCountryName();
                        }
                    }
                } catch (IOException ignored) {
                    // If reverse geocoding fails, the weather can still be loaded by coordinates.
                }
            }

            String finalCityName = cityName;
            String finalCountryName = countryName;
            runOnUiThread(() -> loadWeatherForCity(finalCityName, finalCountryName, latitude, longitude));
        });
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
                    showStatus("Weather data is not available right now. Please try Refresh or choose another city.");
                    return;
                }
                renderWeather(cityName, country, body.current);
            }

            @Override
            public void onFailure(@NonNull Call<WeatherResponse> call, @NonNull Throwable t) {
                showLoading(false, friendlyNetworkError("load weather"));
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
        lastUpdatedAt = System.currentTimeMillis();

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
                lastUpdatedAt == 0L ? System.currentTimeMillis() : lastUpdatedAt
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
     * Gives a clean user-facing message instead of showing low-level network errors.
     */
    private String friendlyNetworkError(String action) {
        return "Unable to " + action + ". Please check your internet connection and try again.";
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

        // Make sure the torch is off if the Activity is closed during the flash pattern.
        torchHandler.removeCallbacksAndMessages(null);
        setTorchState(false);

        // Stop the database thread when the Activity is destroyed.
        databaseExecutor.shutdown();
    }
}
