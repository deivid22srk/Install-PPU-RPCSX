package com.my.newproject118.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

public class HomeFragment extends Fragment {
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Create simple layout programmatically
        TextView textView = new TextView(getContext());
        textView.setText("RPCS3 Mod Installer\n\nBem-vindo ao instalador de mods PPU!\n\nUse o botão de instalação no menu inferior para instalar seus mods PPU.");
        textView.setTextSize(18);
        textView.setPadding(32, 32, 32, 32);
        textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        
        return textView;
    }
}