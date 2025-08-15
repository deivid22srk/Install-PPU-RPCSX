package com.my.newproject118.ui.community;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

public class CommunityFragment extends Fragment {
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Create simple layout programmatically
        TextView textView = new TextView(getContext());
        textView.setText("Comunidade\n\n(Funcionalidade de comunidade removida conforme solicitado)\n\nUse a aba 'Instalar' para instalar seus mods PPU locais.");
        textView.setTextSize(16);
        textView.setPadding(32, 32, 32, 32);
        textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        
        return textView;
    }
}