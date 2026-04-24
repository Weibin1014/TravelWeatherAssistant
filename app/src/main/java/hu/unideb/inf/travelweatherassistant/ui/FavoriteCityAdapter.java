package hu.unideb.inf.travelweatherassistant.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import hu.unideb.inf.travelweatherassistant.R;
import hu.unideb.inf.travelweatherassistant.data.FavoriteCity;
import hu.unideb.inf.travelweatherassistant.databinding.ItemFavoriteCityBinding;

public class FavoriteCityAdapter extends RecyclerView.Adapter<FavoriteCityAdapter.FavoriteCityViewHolder> {
    public interface OnFavoriteCityClickListener {
        void onFavoriteCityClick(FavoriteCity city);
    }

    private final List<FavoriteCity> cities = new ArrayList<>();
    private final OnFavoriteCityClickListener listener;

    public FavoriteCityAdapter(OnFavoriteCityClickListener listener) {
        this.listener = listener;
    }

    public void setCities(List<FavoriteCity> newCities) {
        cities.clear();
        cities.addAll(newCities);
        notifyDataSetChanged();
    }

    public FavoriteCity getCityAt(int position) {
        return cities.get(position);
    }

    @NonNull
    @Override
    public FavoriteCityViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_favorite_city, parent, false);
        return new FavoriteCityViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FavoriteCityViewHolder holder, int position) {
        holder.bind(cities.get(position));
    }

    @Override
    public int getItemCount() {
        return cities.size();
    }

    class FavoriteCityViewHolder extends RecyclerView.ViewHolder {
        private final ItemFavoriteCityBinding binding;

        FavoriteCityViewHolder(@NonNull View itemView) {
            super(itemView);
            binding = ItemFavoriteCityBinding.bind(itemView);
        }

        void bind(FavoriteCity city) {
            binding.favoriteNameTextView.setText(city.name + ", " + city.country);
            binding.favoriteDetailsTextView.setText(String.format(Locale.getDefault(),
                    "%.1f°C · %s", city.lastTemperature, city.lastCondition));
            binding.favoriteIconTextView.setText(iconForCondition(city.lastCondition));
            itemView.setOnClickListener(v -> listener.onFavoriteCityClick(city));
        }

        private String iconForCondition(String condition) {
            if (condition == null) return "☁";
            String lower = condition.toLowerCase(Locale.ROOT);
            if (lower.contains("clear")) return "☀";
            if (lower.contains("rain") || lower.contains("drizzle")) return "☔";
            if (lower.contains("snow")) return "❄";
            if (lower.contains("thunder")) return "⛈";
            if (lower.contains("fog")) return "🌫";
            return "☁";
        }
    }
}
