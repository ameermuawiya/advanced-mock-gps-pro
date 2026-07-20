package com.muawiya.fakegps.ui.history;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.muawiya.fakegps.MainActivity;
import com.muawiya.fakegps.R;
import com.muawiya.fakegps.data.DataBackupHelper;
import com.muawiya.fakegps.data.FavoriteEntity;
import com.muawiya.fakegps.data.HistoryEntity;
import com.muawiya.fakegps.data.LocationRepository;
import com.muawiya.fakegps.databinding.FragmentHistoryBinding;
import com.muawiya.fakegps.ui.LocationAdapter;
import com.muawiya.fakegps.service.MockLocationService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HistoryFragment extends Fragment implements LocationAdapter.OnLocationItemClickListener {

    private static final int REQ_VOICE_SPEECH = 2002;

    private FragmentHistoryBinding binding;
    private LocationRepository repository;
    private LocationAdapter adapter;
    private List<FavoriteEntity> cachedFavorites = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHistoryBinding.inflate(inflater, container, false);
        repository = new LocationRepository(requireContext());
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.tabRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new LocationAdapter(false, this);
        binding.tabRecyclerView.setAdapter(adapter);

        setupSearchInputFilter();

        // Dynamically observe favorites to enable live star toggles syncing in history logs list
        repository.getAllFavorites().observe(getViewLifecycleOwner(), favs -> {
            if (favs != null) {
                cachedFavorites = favs;
                loadDatabaseDetails(binding.tabSearchInput.getText().toString());
            }
        });

        loadDatabaseDetails("");
    }

    private void setupSearchInputFilter() {
        binding.tabSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                loadDatabaseDetails(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        binding.tabSearchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH || 
                    (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER)) {
                loadDatabaseDetails(binding.tabSearchInput.getText().toString());
                if (getContext() != null) {
                    android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) 
                            requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    }
                }
                binding.tabSearchInput.clearFocus();
                return true;
            }
            return false;
        });

        binding.btnTabSearchSubmit.setOnClickListener(v -> {
            loadDatabaseDetails(binding.tabSearchInput.getText().toString());
            if (getContext() != null) {
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) 
                        requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                }
            }
            binding.tabSearchInput.clearFocus();
        });

        binding.btnTabVoiceSearch.setOnClickListener(v -> startVoiceSpeechInput());
    }

    private void startVoiceSpeechInput() {
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.fragment_history_hint_search));
            startActivityForResult(intent, REQ_VOICE_SPEECH);
        } catch (Exception e) {
            Toast.makeText(getContext(), getString(R.string.map_fragment_toast_voice_speech_unavailable), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_VOICE_SPEECH && resultCode == Activity.RESULT_OK && data != null) {
            ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (matches != null && !matches.isEmpty()) {
                String spokenText = matches.get(0);
                binding.tabSearchInput.setText(spokenText);
                loadDatabaseDetails(spokenText);
            }
        }
    }

    private void loadDatabaseDetails(String filterQuery) {
        binding.tabSearchInput.setHint("Search history logs...");
        binding.txtEmptyMessage.setText("No history coordinates recorded.");

        if (filterQuery == null || filterQuery.trim().isEmpty()) {
            repository.getAllHistory().observe(getViewLifecycleOwner(), this::updateAdapterFromHistory);
        } else {
            repository.searchHistory(filterQuery).observe(getViewLifecycleOwner(), this::updateAdapterFromHistory);
        }
    }

    private boolean checkProximityMatch(double lat, double lng) {
        if (cachedFavorites == null) return false;
        for (FavoriteEntity fav : cachedFavorites) {
            if (Math.abs(fav.getLatitude() - lat) < 0.0001 && Math.abs(fav.getLongitude() - lng) < 0.0001) {
                return true;
            }
        }
        return false;
    }

    private FavoriteEntity retrieveFavoriteByCoords(double lat, double lng) {
        if (cachedFavorites == null) return null;
        for (FavoriteEntity fav : cachedFavorites) {
            if (Math.abs(fav.getLatitude() - lat) < 0.0001 && Math.abs(fav.getLongitude() - lng) < 0.0001) {
                return fav;
            }
        }
        return null;
    }

    private void updateAdapterFromHistory(List<HistoryEntity> list) {
        if (binding == null) return;
        if (list == null || list.isEmpty()) {
            binding.emptyPlaceholder.setVisibility(View.VISIBLE);
            binding.tabRecyclerView.setVisibility(View.GONE);
            adapter.setItems(new ArrayList<>());
            return;
        }

        binding.emptyPlaceholder.setVisibility(View.GONE);
        binding.tabRecyclerView.setVisibility(View.VISIBLE);

        List<LocationAdapter.LocationItem> adapterItems = new ArrayList<>();
        for (HistoryEntity h : list) {
            boolean isStarred = checkProximityMatch(h.getLatitude(), h.getLongitude());
            adapterItems.add(new LocationAdapter.LocationItem(
                h.getId(), h.getName(), h.getLatitude(), h.getLongitude(), "History", h.getTimestamp(), isStarred
            ));
        }
        adapter.setItems(adapterItems);
    }

    @Override
    public void onLoadSpot(LocationAdapter.LocationItem item) {
        MockLocationService.sLatitude = item.latitude;
        MockLocationService.sLongitude = item.longitude;
        MockLocationService.sLocationName = item.name;

        if (requireActivity() instanceof MainActivity) {
            MainActivity activity = (MainActivity) requireActivity();
            activity.navigateToAndCenterCoordinates(item.latitude, item.longitude);
            Toast.makeText(requireContext(), getString(R.string.favorites_fragment_toast_selected_set), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDeleteSpot(LocationAdapter.LocationItem item) {
        repository.deleteHistoryById(item.id);
        Toast.makeText(requireContext(), getString(R.string.history_fragment_toast_deleted), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onFavoriteSpot(LocationAdapter.LocationItem item) {
        FavoriteEntity currentStoredFav = retrieveFavoriteByCoords(item.latitude, item.longitude);
        if (currentStoredFav != null) {
            repository.deleteFavoriteById(currentStoredFav.getId());
            Toast.makeText(requireContext(), getString(R.string.history_fragment_toast_unstarred), Toast.LENGTH_SHORT).show();
        } else {
            FavoriteEntity fav = new FavoriteEntity(item.name, item.latitude, item.longitude, "Saved", System.currentTimeMillis());
            repository.insertFavorite(fav);
            Toast.makeText(requireContext(), getString(R.string.history_fragment_toast_starred), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onEditSpot(LocationAdapter.LocationItem item) {
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
