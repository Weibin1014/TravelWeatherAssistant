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
import hu.unideb.inf.travelweatherassistant.util.WeatherInterpreter;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {
    private static final String NOTIFICATION_CHANNEL_ID = "travel_weather_alerts";
    private static final int WEATHER_NOTIFICATION_ID = 101;

    private ActivityMainBinding binding;
    private WeatherRepository weatherRepository;
    private FavoriteCityDatabase database;
    private FavoriteCityAdapter favoriteCityAdapter;
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();

    private String currentCityName = "Budapest";
    private String currentCountry = "Hungary";
    private double currentLatitude = 47.4979;
    private double currentLongitude = 19.0402;
    private String currentCondition = "Unknown weather";
    private double currentTemperature = 0.0;
    private String currentAdvice = "Travel advice will be generated from real weather data.";

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
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        EdgeToEdge.enable(this);
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        weatherRepository = new WeatherRepository();
        database = Room.databaseBuilder(this, FavoriteCityDatabase.class, "travel_weather_db")
                .fallbackToDestructiveMigration(true)
                .build();

        setupFavoritesList();
        createNotificationChannel();
        setupButtons();
        loadWeatherForCity(currentCityName, currentCountry, currentLatitude, currentLongitude);
    }

    private void setupFavoritesList() {
        favoriteCityAdapter = new FavoriteCityAdapter(city ->
                loadWeatherForCity(city.name, city.country, city.latitude, city.longitude));
        binding.favoritesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.favoritesRecyclerView.setAdapter(favoriteCityAdapter);

        database.favoriteCityDao().getAllFavorites().observe(this, favoriteCities -> {
            favoriteCityAdapter.setCities(favoriteCities);
            binding.emptyFavoritesTextView.setVisibility(favoriteCities.isEmpty() ? View.VISIBLE : View.GONE);
        });

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

    private void setupButtons() {
        binding.currentLocationButton.setOnClickListener(v -> requestLocationWeather());
        binding.searchButton.setOnClickListener(v -> searchCity());
        binding.saveFavoriteButton.setOnClickListener(v -> saveCurrentCity());
        binding.notifyButton.setOnClickListener(v -> requestNotificationAndShow());
    }

    private void searchCity() {
        String query = binding.searchCityEditText.getText().toString().trim();
        if (query.isEmpty()) {
            showStatus("Please type a city name first.");
            return;
        }

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

    private void requestLocationWeather() {
        if (!hasLocationPermission()) {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
            return;
        }
        loadWeatherFromDeviceLocation();
    }

    private boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

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

        requestSingleLocationUpdate(locationManager);
    }

    private Location getBestLastKnownLocation(LocationManager locationManager) {
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

    private void loadWeatherForCity(String cityName, String country, double latitude, double longitude) {
        currentCityName = cityName;
        currentCountry = country;
        currentLatitude = latitude;
        currentLongitude = longitude;

        binding.cityNameTextView.setText(cityName + ", " + country);
        showLoading(true, "Loading weather for " + cityName + "...");

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

    private void renderWeather(String cityName, String country, WeatherResponse.CurrentWeather current) {
        currentCondition = WeatherInterpreter.describeCode(current.weatherCode);
        currentTemperature = current.temperature;
        currentAdvice = WeatherInterpreter.travelAdvice(current);

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
    }

    private void saveCurrentCity() {
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
            int count = database.favoriteCityDao().countByName(currentCityName, currentCountry);
            if (count > 0) {
                runOnUiThread(() -> showStatus(currentCityName + " is already in favorites."));
                return;
            }
            database.favoriteCityDao().insert(favoriteCity);
            runOnUiThread(() -> showStatus("Saved " + currentCityName + " to favorites."));
        });
    }

    private void requestNotificationAndShow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            return;
        }
        showWeatherNotification();
    }

    private void createNotificationChannel() {
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

    private void showWeatherNotification() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            showStatus("Notification permission is required on this Android version.");
            return;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Weather advice for " + currentCityName)
                .setContentText(currentAdvice)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(currentAdvice))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        NotificationManagerCompat.from(this).notify(WEATHER_NOTIFICATION_ID, builder.build());
        showStatus("Notification shown.");
    }

    private void showLoading(boolean loading, String message) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        showStatus(message);
    }

    private void showStatus(String message) {
        binding.statusTextView.setText(message);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void hideKeyboard() {
        InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null && getCurrentFocus() != null) {
            manager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        databaseExecutor.shutdown();
    }
}
