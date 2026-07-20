package com.muawiya.fakegps.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.muawiya.fakegps.R;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LocationAdapter extends RecyclerView.Adapter<LocationAdapter.ViewHolder> {

    public interface OnLocationItemClickListener {
        void onLoadSpot(LocationItem item);
        void onDeleteSpot(LocationItem item);
        void onFavoriteSpot(LocationItem item);
        void onEditSpot(LocationItem item);
    }

    public static class LocationItem {
        public int id;
        public String name;
        public double latitude;
        public double longitude;
        public String category;
        public long timestamp;
        public boolean isStarred;

        public LocationItem(int id, String name, double latitude, double longitude, String category, long timestamp, boolean isStarred) {
            this.id = id;
            this.name = name;
            this.latitude = latitude;
            this.longitude = longitude;
            this.category = category;
            this.timestamp = timestamp;
            this.isStarred = isStarred;
        }
    }

    private final boolean isFavorites;
    private List<LocationItem> items;
    private final OnLocationItemClickListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    public LocationAdapter(boolean isFavorites, OnLocationItemClickListener listener) {
        this.isFavorites = isFavorites;
        this.items = new ArrayList<>();
        this.listener = listener;
    }

    public void setItems(List<LocationItem> items) {
        this.items = items != null ? items : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_location, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LocationItem item = items.get(position);
        holder.titleView.setText(item.name);
        holder.subtitleView.setText(String.format(Locale.US, "%.6f, %.6f", item.latitude, item.longitude));
        holder.timeView.setText(dateFormat.format(new Date(item.timestamp)));

        if (item.isStarred) {
            holder.btnFavorite.setImageResource(R.drawable.ic_star);
        } else {
            holder.btnFavorite.setImageResource(R.drawable.ic_star_outline);
        }

        // Hide Edit and Go/Load buttons, only show appropriate actions per list style
        holder.btnEdit.setVisibility(View.GONE);
        holder.btnLoad.setVisibility(View.GONE);

        if (isFavorites) {
            holder.btnFavorite.setVisibility(View.GONE);
        } else {
            holder.btnFavorite.setVisibility(View.VISIBLE);
        }
        holder.btnDelete.setVisibility(View.VISIBLE);

        // Clicking the item view itself loads it in Map
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onLoadSpot(item);
        });

        holder.btnLoad.setOnClickListener(v -> {
            if (listener != null) listener.onLoadSpot(item);
        });

        holder.btnFavorite.setOnClickListener(v -> {
            if (listener != null) listener.onFavoriteSpot(item);
        });

        holder.btnEdit.setOnClickListener(v -> {
            if (listener != null) listener.onEditSpot(item);
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDeleteSpot(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView titleView;
        final TextView subtitleView;
        final TextView timeView;
        final ImageButton btnLoad;
        final ImageButton btnFavorite;
        final ImageButton btnEdit;
        final ImageButton btnDelete;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            titleView = itemView.findViewById(R.id.item_title);
            subtitleView = itemView.findViewById(R.id.item_subtitle);
            timeView = itemView.findViewById(R.id.item_time);
            btnLoad = itemView.findViewById(R.id.item_btn_load);
            btnFavorite = itemView.findViewById(R.id.item_btn_favorite);
            btnEdit = itemView.findViewById(R.id.item_btn_edit);
            btnDelete = itemView.findViewById(R.id.item_btn_delete);
        }
    }
}
