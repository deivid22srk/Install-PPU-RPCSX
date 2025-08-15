package com.my.newproject118.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

public class SettingsFragment extends Fragment {
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Create simple layout programmatically
        TextView textView = new TextView(getContext());
        textView.setText("Configurações\n\nConfigurações básicas do aplicativo.\n\n(Interface será implementada conforme necessário)");
        textView.setTextSize(16);
        textView.setPadding(32, 32, 32, 32);
        textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        
        return textView;
    }
}