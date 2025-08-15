package com.my.newproject118.ui.games;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;

import com.my.newproject118.BackupService;
import com.my.newproject118.MainActivity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class GamesFragmentSafe extends Fragment {
    
    private static final String TAG = "GamesFragment";
    private static final String PREFS_NAME = "RPCS3Prefs";
    private static final String PREF_FOLDER_URI = "folder_uri";
    
    private MainActivity mainActivity;
    private LinearLayout tabContainer;
    private LinearLayout gamesContainer;
    private EditText searchInput;
    private Button tabJogos, tabPPUs;
    private boolean showingGames = true;
    
    // Game loading
    private List<GameItem> gameList = new ArrayList<>();
    private List<GameItem> filteredGameList = new ArrayList<>();
    private SharedPreferences prefs;
    
    // Inner classes
    private static class ParamSfoData {
        String title;
        String appVer;
        String version;

        ParamSfoData(String title, String appVer, String version) {
            this.title = title;
            this.appVer = appVer;
            this.version = version;
        }
    }
    
    private static class GameItem {
        String title;
        String id;
        String appVer;
        String version;
        Uri iconUri;

        GameItem(String title, String id, String appVer, String version, Uri iconUri) {
            this.title = title;
            this.id = id;
            this.appVer = appVer;
            this.version = version;
            this.iconUri = iconUri;
        }
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mainActivity = (MainActivity) getActivity();
        
        // Initialize SharedPreferences
        if (getContext() != null) {
            prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
        
        // Create main layout
        LinearLayout mainLayout = new LinearLayout(getContext());
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setBackgroundColor(Color.parseColor("#1E1E1E"));
        mainLayout.setPadding(0, 16, 0, 0);
        
        // Create tab container
        createTabContainer(mainLayout);
        
        // Create search bar
        createSearchBar(mainLayout);
        
        // Create games container
        createGamesContainer(mainLayout);
        
        // Load games/PPUs
        loadContent();
        
        return mainLayout;
    }
    
    private void createTabContainer(LinearLayout parent) {
        tabContainer = new LinearLayout(getContext());
        tabContainer.setOrientation(LinearLayout.HORIZONTAL);
        tabContainer.setPadding(16, 0, 16, 16);
        
        // Tab JOGOS
        tabJogos = new Button(getContext());
        tabJogos.setText("JOGOS");
        tabJogos.setBackgroundColor(Color.parseColor("#4A9FE7")); // Azul ativo
        tabJogos.setTextColor(Color.WHITE);
        tabJogos.setPadding(32, 16, 32, 16);
        LinearLayout.LayoutParams jogosParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        jogosParams.setMargins(0, 0, 8, 0);
        tabJogos.setLayoutParams(jogosParams);
        tabJogos.setOnClickListener(v -> switchToGames());
        
        // Tab PPUS
        tabPPUs = new Button(getContext());
        tabPPUs.setText("PPUS");
        tabPPUs.setBackgroundColor(Color.parseColor("#2A2A2A")); // Cinza inativo
        tabPPUs.setTextColor(Color.parseColor("#AAAAAA"));
        tabPPUs.setPadding(32, 16, 32, 16);
        LinearLayout.LayoutParams ppusParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        ppusParams.setMargins(8, 0, 0, 0);
        tabPPUs.setLayoutParams(ppusParams);
        tabPPUs.setOnClickListener(v -> switchToPPUs());
        
        tabContainer.addView(tabJogos);
        tabContainer.addView(tabPPUs);
        parent.addView(tabContainer);
    }
    
    private void createSearchBar(LinearLayout parent) {
        searchInput = new EditText(getContext());
        searchInput.setHint("🔍 Pesquisar jogos");
        searchInput.setTextColor(Color.WHITE);
        searchInput.setHintTextColor(Color.parseColor("#888888"));
        searchInput.setBackgroundColor(Color.parseColor("#2A2A2A"));
        searchInput.setPadding(24, 16, 24, 16);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        searchParams.setMargins(16, 0, 16, 16);
        searchInput.setLayoutParams(searchParams);
        
        parent.addView(searchInput);
    }
    
    private void createGamesContainer(LinearLayout parent) {
        gamesContainer = new LinearLayout(getContext());
        gamesContainer.setOrientation(LinearLayout.VERTICAL);
        gamesContainer.setPadding(16, 0, 16, 0);
        parent.addView(gamesContainer);
    }
    
    private void switchToGames() {
        showingGames = true;
        tabJogos.setBackgroundColor(Color.parseColor("#4A9FE7"));
        tabJogos.setTextColor(Color.WHITE);
        tabPPUs.setBackgroundColor(Color.parseColor("#2A2A2A"));
        tabPPUs.setTextColor(Color.parseColor("#AAAAAA"));
        searchInput.setHint("🔍 Pesquisar jogos");
        loadContent();
    }
    
    private void switchToPPUs() {
        showingGames = false;
        tabPPUs.setBackgroundColor(Color.parseColor("#4A9FE7"));
        tabPPUs.setTextColor(Color.WHITE);
        tabJogos.setBackgroundColor(Color.parseColor("#2A2A2A"));
        tabJogos.setTextColor(Color.parseColor("#AAAAAA"));
        searchInput.setHint("🔍 Pesquisar PPUs");
        loadContent();
    }
    
    private void loadContent() {
        gamesContainer.removeAllViews();
        
        if (showingGames) {
            loadGames();
        } else {
            loadPPUs();
        }
    }
    
    private void loadGames() {
        // Check if RPCS3 folder is configured
        if (prefs != null) {
            String savedUri = prefs.getString(PREF_FOLDER_URI, null);
            if (savedUri != null) {
                Uri selectedFolderUri = Uri.parse(savedUri);
                new LoadGamesTask().execute(selectedFolderUri);
            } else {
                // Show message that RPCS3 folder needs to be configured
                TextView noFolderText = new TextView(getContext());
                noFolderText.setText("Pasta RPCS3 não configurada.\\n\\nVá em Settings para selecionar a pasta do RPCS3.");
                noFolderText.setTextColor(Color.parseColor("#888888"));
                noFolderText.setTextSize(16);
                noFolderText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                noFolderText.setPadding(16, 64, 16, 16);
                gamesContainer.addView(noFolderText);
            }
        } else {
            // Show example games for testing
            createGameCard("Minecraft: PlayStation®3 Edition", "01.00", "01.01", true);
            createGameCard("The Last of Us", "01.11", "01.11", false);
        }
    }
    
    private void loadPPUs() {
        // Carregar PPUs instalados do SharedPreferences
        if (prefs != null) {
            SharedPreferences installedPrefs = getContext().getSharedPreferences("InstalledMods", Context.MODE_PRIVATE);
            boolean hasInstalledPPUs = installedPrefs.getAll().size() > 0;
            
            if (hasInstalledPPUs) {
                TextView ppuText = new TextView(getContext());
                ppuText.setText("PPUs Instalados:\\n\\n(Lista será carregada dos mods salvos)");
                ppuText.setTextColor(Color.WHITE);
                ppuText.setTextSize(16);
                ppuText.setPadding(16, 32, 16, 16);
                gamesContainer.addView(ppuText);
            } else {
                TextView emptyText = new TextView(getContext());
                emptyText.setText("Nenhum PPU instalado.\\n\\nUse o botão + para instalar PPUs.");
                emptyText.setTextColor(Color.parseColor("#888888"));
                emptyText.setTextSize(16);
                emptyText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                emptyText.setPadding(16, 64, 16, 16);
                gamesContainer.addView(emptyText);
            }
        }
    }
    
    private void createGameCard(String title, String appVersion, String version, boolean isMinecraft) {
        // Main card container
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.parseColor("#2A2A2A"));
        card.setPadding(0, 0, 0, 16);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, 16);
        card.setLayoutParams(cardParams);
        
        // Game image placeholder (verde para Minecraft, cinza para outros)
        View imageView = new View(getContext());
        imageView.setBackgroundColor(isMinecraft ? Color.parseColor("#8BC34A") : Color.parseColor("#555555"));
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 200);
        imageView.setLayoutParams(imageParams);
        card.addView(imageView);
        
        // Game info container
        LinearLayout infoContainer = new LinearLayout(getContext());
        infoContainer.setOrientation(LinearLayout.VERTICAL);
        infoContainer.setPadding(16, 16, 16, 16);
        
        // Game title
        TextView titleText = new TextView(getContext());
        titleText.setText(title);
        titleText.setTextColor(Color.WHITE);
        titleText.setTextSize(18);
        titleText.setPadding(0, 0, 0, 8);
        infoContainer.addView(titleText);
        
        // Version info
        LinearLayout versionContainer = new LinearLayout(getContext());
        versionContainer.setOrientation(LinearLayout.HORIZONTAL);
        
        TextView appVersionText = new TextView(getContext());
        appVersionText.setText("App Version: " + appVersion);
        appVersionText.setTextColor(Color.parseColor("#CCCCCC"));
        appVersionText.setTextSize(14);
        LinearLayout.LayoutParams appVersionParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        appVersionText.setLayoutParams(appVersionParams);
        
        TextView versionText = new TextView(getContext());
        versionText.setText("Version: " + version);
        versionText.setTextColor(Color.parseColor("#CCCCCC"));
        versionText.setTextSize(14);
        
        versionContainer.addView(appVersionText);
        versionContainer.addView(versionText);
        infoContainer.addView(versionContainer);
        
        // Backup button (rosa como na imagem)
        Button backupButton = new Button(getContext());
        backupButton.setText("BACKUP");
        backupButton.setBackgroundColor(Color.parseColor("#E91E63")); // Rosa
        backupButton.setTextColor(Color.WHITE);
        backupButton.setPadding(16, 12, 16, 12);
        LinearLayout.LayoutParams backupParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        backupParams.setMargins(0, 16, 0, 0);
        backupButton.setLayoutParams(backupParams);
        backupButton.setOnClickListener(v -> {
            // Start backup without BroadcastReceiver dependency
            if (getContext() != null) {
                Toast.makeText(getContext(), "Backup de " + title + " iniciado", Toast.LENGTH_SHORT).show();
                // TODO: Start BackupService if needed
            }
        });
        
        infoContainer.addView(backupButton);
        card.addView(infoContainer);
        gamesContainer.addView(card);
    }
    
    // Simplified LoadGamesTask without complex dependencies
    private class LoadGamesTask extends AsyncTask<Uri, Void, Void> {
        
        @Override
        protected void onPreExecute() {
            // Show loading message
            gamesContainer.removeAllViews();
            TextView loadingText = new TextView(getContext());
            loadingText.setText("Carregando jogos...");
            loadingText.setTextColor(Color.WHITE);
            loadingText.setTextSize(16);
            loadingText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            loadingText.setPadding(16, 64, 16, 16);
            gamesContainer.addView(loadingText);
        }

        @Override
        protected Void doInBackground(Uri... uris) {
            // For now, just simulate loading
            try {
                Thread.sleep(1000); // Simulate loading time
            } catch (InterruptedException e) {
                // Ignore
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            gamesContainer.removeAllViews();
            
            // Show example games for now
            createGameCard("Minecraft: PlayStation®3 Edition", "01.00", "01.01", true);
            createGameCard("The Last of Us", "01.11", "01.11", false);
            createGameCard("Grand Theft Auto V", "01.08", "01.08", false);
        }
    }
}