package com.muawiya.fakegps.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.muawiya.fakegps.R;

public class SettingsFragment extends Fragment {

    /*
     * Inflates the custom settings layout containing the Material Toolbar and FrameLayout.
     * Initializes and embeds the SettingsPreferenceFragment into the container.
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        getChildFragmentManager()
                .beginTransaction()
                .replace(R.id.settings_container, new SettingsPreferenceFragment())
                .commit();

        return view;
    }
}
