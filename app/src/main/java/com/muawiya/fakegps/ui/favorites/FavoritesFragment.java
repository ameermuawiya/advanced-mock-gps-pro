package com.muawiya.fakegps.ui.favorites;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.muawiya.fakegps.MainActivity;
import com.muawiya.fakegps.R;
import com.muawiya.fakegps.data.FavoriteEntity;
import com.muawiya.fakegps.data.LocationRepository;
import com.muawiya.fakegps.databinding.FragmentFavoritesBinding;
import com.muawiya.fakegps.ui.LocationAdapter;
import com.muawiya.fakegps.service.MockLocationService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FavoritesFragment extends Fragment implements LocationAdapter.OnLocationItemClickListener {

    private static final int REQ_VOICE_SPEECH = 2002;

    private FragmentFavoritesBinding binding;
    private LocationRepository repository;
    private LocationAdapter adapter;
    private List<FavoriteEntity> cachedFavorites = new ArrayList<>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repository = new LocationRepository(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentFavoritesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.tabRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new LocationAdapter(true, this);
        binding.tabRecyclerView.setAdapter(adapter);

        setupSearchInputFilter();

        // Dynamically observe favorites to enable live star toggles syncing
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
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.fragment_favorites_hint_search));
            startActivityForResult(intent, REQ_VOICE_SPEECH);
        } catch (Exception e) {
            Toast.makeText(getContext(), getString(R.string.favorites_fragment_toast_speech_unavailable), Toast.LENGTH_SHORT).show();
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
        binding.tabSearchInput.setHint("Search favorite locations...");
        binding.txtEmptyMessage.setText("No starred favorites saved.");

        if (filterQuery == null || filterQuery.trim().isEmpty()) {
            repository.getAllFavorites().observe(getViewLifecycleOwner(), this::updateAdapterFromFavorites);
        } else {
            repository.searchFavorites(filterQuery).observe(getViewLifecycleOwner(), this::updateAdapterFromFavorites);
        }
    }

    private void updateAdapterFromFavorites(List<FavoriteEntity> list) {
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
        for (FavoriteEntity f : list) {
            adapterItems.add(new LocationAdapter.LocationItem(
                f.getId(), f.getName(), f.getLatitude(), f.getLongitude(), f.getCategory(), f.getTimestamp(), true
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
        repository.deleteFavoriteById(item.id);
        Toast.makeText(requireContext(), getString(R.string.favorites_fragment_toast_deleted), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onFavoriteSpot(LocationAdapter.LocationItem item) {
        // Already in favorites
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
