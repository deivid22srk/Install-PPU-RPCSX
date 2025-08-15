package com.my.newproject118.ui.games;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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

import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;

import com.my.newproject118.BackupService;
import com.my.newproject118.MainActivity;
import com.my.newproject118.RPCS3Helper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class GamesFragment extends Fragment {
    
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
    private BroadcastReceiver backupReceiver;
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
        Log.d(TAG, "=== GamesFragment (REAL SYSTEM) created ===");
        
        mainActivity = (MainActivity) getActivity();
        
        // Initialize SharedPreferences
        prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        // Create main layout with Material You background
        LinearLayout mainLayout = new LinearLayout(getContext());
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setBackgroundColor(Color.parseColor("#0F0D13")); // Material You dark background
        mainLayout.setPadding(0, 0, 0, 0);
        
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
    
    @Override
    public void onResume() {
        super.onResume();
        // Setup backup receiver when fragment becomes active
        setupBackupReceiver();
    }
    
    @Override
    public void onPause() {
        super.onPause();
        // Unregister receiver when fragment is paused
        if (backupReceiver != null && getContext() != null) {
            try {
                getContext().unregisterReceiver(backupReceiver);
            } catch (IllegalArgumentException e) {
                // Receiver not registered, ignore
            }
        }
    }
    
    private void createTabContainer(LinearLayout parent) {
        tabContainer = new LinearLayout(getContext());
        tabContainer.setOrientation(LinearLayout.HORIZONTAL);
        tabContainer.setPadding(20, 20, 20, 20);
        tabContainer.setBackgroundColor(Color.parseColor("#1C1B1F")); // Material You surface container
        
        // Add subtle elevation for Material You depth
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            tabContainer.setElevation(1f);
        }
        
        // Tab JOGOS - Material You 3.0 filled tab
        tabJogos = new Button(getContext());
        tabJogos.setText("🎮 JOGOS");
        tabJogos.setBackgroundResource(android.R.drawable.btn_default);
        tabJogos.setBackgroundColor(Color.parseColor("#6750A4")); // Material You primary
        tabJogos.setTextColor(Color.parseColor("#FFFFFF")); // Material You on-primary
        tabJogos.setPadding(28, 18, 28, 18);
        tabJogos.setTextSize(14);
        tabJogos.setTypeface(tabJogos.getTypeface(), android.graphics.Typeface.BOLD);
        
        // Add subtle shadow/elevation for active tab
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            tabJogos.setElevation(3f);
        }
        
        LinearLayout.LayoutParams jogosParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        jogosParams.setMargins(0, 0, 8, 0);
        tabJogos.setLayoutParams(jogosParams);
        tabJogos.setOnClickListener(v -> switchToGames());
        
        // Tab PPUS - Material You 3.0 outlined tab
        tabPPUs = new Button(getContext());
        tabPPUs.setText("💾 PPUs");
        tabPPUs.setBackgroundResource(android.R.drawable.btn_default);
        tabPPUs.setBackgroundColor(Color.parseColor("#2A2930")); // Material You surface container high
        tabPPUs.setTextColor(Color.parseColor("#CAC4D0")); // Material You on-surface-variant
        tabPPUs.setPadding(28, 18, 28, 18);
        tabPPUs.setTextSize(14);
        tabPPUs.setTypeface(tabPPUs.getTypeface(), android.graphics.Typeface.NORMAL);
        
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
        // Material You 3.0 Search container with elevated surface
        LinearLayout searchContainer = new LinearLayout(getContext());
        searchContainer.setOrientation(LinearLayout.HORIZONTAL);
        searchContainer.setBackgroundColor(Color.parseColor("#1E1B24")); // Material You surface container
        searchContainer.setPadding(24, 18, 24, 18);
        
        // Add subtle elevation for Material You depth
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            searchContainer.setElevation(2f);
        }
        
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        containerParams.setMargins(16, 8, 16, 24);
        searchContainer.setLayoutParams(containerParams);
        
        // Material You search icon
        TextView searchIcon = new TextView(getContext());
        searchIcon.setText("🔍");
        searchIcon.setTextSize(20);
        searchIcon.setPadding(0, 0, 18, 0);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        iconParams.gravity = android.view.Gravity.CENTER_VERTICAL;
        searchIcon.setLayoutParams(iconParams);
        
        searchInput = new EditText(getContext());
        searchInput.setHint("Pesquisar jogos...");
        searchInput.setTextColor(Color.parseColor("#E6E0E9")); // Material You on-surface
        searchInput.setHintTextColor(Color.parseColor("#938F96")); // Material You on-surface-variant
        searchInput.setBackgroundColor(Color.TRANSPARENT);
        searchInput.setTextSize(16);
        // Remove underline
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            searchInput.setBackground(null);
        }
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        searchParams.gravity = android.view.Gravity.CENTER_VERTICAL;
        searchInput.setLayoutParams(searchParams);
        
        // Add text watcher for real-time search
        searchInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {
                filterGames(s.toString());
            }
        });
        
        searchContainer.addView(searchIcon);
        searchContainer.addView(searchInput);
        parent.addView(searchContainer);
    }
    
    private void createGamesContainer(LinearLayout parent) {
        gamesContainer = new LinearLayout(getContext());
        gamesContainer.setOrientation(LinearLayout.VERTICAL);
        gamesContainer.setPadding(16, 0, 16, 0);
        parent.addView(gamesContainer);
    }
    
    private void switchToGames() {
        showingGames = true;
        
        // Material You 3.0 active state with elevation
        tabJogos.setText("🎮 JOGOS");
        tabJogos.setBackgroundColor(Color.parseColor("#6750A4")); // Primary
        tabJogos.setTextColor(Color.parseColor("#FFFFFF")); // On-primary
        tabJogos.setTypeface(tabJogos.getTypeface(), android.graphics.Typeface.BOLD);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            tabJogos.setElevation(3f);
        }
        
        // Material You 3.0 inactive state
        tabPPUs.setText("💾 PPUs");
        tabPPUs.setBackgroundColor(Color.parseColor("#2A2930")); // Surface container high
        tabPPUs.setTextColor(Color.parseColor("#CAC4D0")); // On-surface-variant
        tabPPUs.setTypeface(tabPPUs.getTypeface(), android.graphics.Typeface.NORMAL);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            tabPPUs.setElevation(0f);
        }
        
        searchInput.setHint("🔍 Pesquisar jogos...");
        loadContent();
    }
    
    private void switchToPPUs() {
        showingGames = false;
        
        // Material You 3.0 active state with elevation
        tabPPUs.setText("💾 PPUs");
        tabPPUs.setBackgroundColor(Color.parseColor("#6750A4")); // Primary
        tabPPUs.setTextColor(Color.parseColor("#FFFFFF")); // On-primary
        tabPPUs.setTypeface(tabPPUs.getTypeface(), android.graphics.Typeface.BOLD);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            tabPPUs.setElevation(3f);
        }
        
        // Material You 3.0 inactive state
        tabJogos.setText("🎮 JOGOS");
        tabJogos.setBackgroundColor(Color.parseColor("#2A2930")); // Surface container high
        tabJogos.setTextColor(Color.parseColor("#CAC4D0")); // On-surface-variant
        tabJogos.setTypeface(tabJogos.getTypeface(), android.graphics.Typeface.NORMAL);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            tabJogos.setElevation(0f);
        }
        
        searchInput.setHint("🔍 Pesquisar PPUs...");
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
        Log.d(TAG, "Loading games...");
        
        // Add detailed debug information
        boolean isConfigured = RPCS3Helper.isRPCS3Configured(getContext());
        Uri folderUri = RPCS3Helper.getRPCS3FolderUri(getContext());
        
        Log.d(TAG, "RPCS3 configured: " + isConfigured);
        Log.d(TAG, "RPCS3 folder URI: " + (folderUri != null ? folderUri.toString() : "null"));
        
        if (!isConfigured) {
            Log.w(TAG, "RPCS3 folder not configured");
            showConfigurationMessage();
            return;
        }
        
        Log.d(TAG, "Starting LoadGamesTask with URI: " + folderUri.toString());
        new LoadGamesTask().execute(folderUri);
    }
    
    private void showConfigurationMessage() {
        Log.d(TAG, "Showing configuration message");
        
        // Material You empty state container
        LinearLayout emptyContainer = new LinearLayout(getContext());
        emptyContainer.setOrientation(LinearLayout.VERTICAL);
        emptyContainer.setPadding(32, 80, 32, 32);
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        emptyContainer.setLayoutParams(containerParams);
        
        // Empty state icon
        TextView emptyIcon = new TextView(getContext());
        emptyIcon.setText("📁");
        emptyIcon.setTextSize(48);
        emptyIcon.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        emptyIcon.setPadding(0, 0, 0, 24);
        emptyContainer.addView(emptyIcon);
        
        // Empty state title
        TextView titleText = new TextView(getContext());
        titleText.setText("Pasta RPCS3 não configurada");
        titleText.setTextColor(Color.parseColor("#E6E0E9")); // Material You on-surface
        titleText.setTextSize(20);
        titleText.setTypeface(titleText.getTypeface(), android.graphics.Typeface.BOLD);
        titleText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        titleText.setPadding(0, 0, 0, 16);
        emptyContainer.addView(titleText);
        
        // Empty state description
        TextView descText = new TextView(getContext());
        descText.setText("Vá em Settings para selecionar a pasta do RPCS3 e começar a usar o aplicativo.");
        descText.setTextColor(Color.parseColor("#938F96")); // Material You on-surface-variant
        descText.setTextSize(14);
        descText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        descText.setPadding(0, 0, 0, 16);
        emptyContainer.addView(descText);
        
        // Debug badge
        TextView debugBadge = new TextView(getContext());
        debugBadge.setText("🔧 Sistema de detecção real ativo");
        debugBadge.setTextColor(Color.parseColor("#6750A4")); // Material You primary
        debugBadge.setBackgroundColor(Color.parseColor("#1E1B24")); // Material You surface container
        debugBadge.setTextSize(12);
        debugBadge.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        debugBadge.setPadding(16, 8, 16, 8);
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        badgeParams.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        debugBadge.setLayoutParams(badgeParams);
        emptyContainer.addView(debugBadge);
        
        gamesContainer.addView(emptyContainer);
    }
    
    private void loadPPUs() {
        // Carregar PPUs instalados do SharedPreferences
        SharedPreferences prefs = getContext().getSharedPreferences("InstalledMods", getContext().MODE_PRIVATE);
        boolean hasInstalledPPUs = prefs.getAll().size() > 0;
        
        if (hasInstalledPPUs) {
            // TODO: Mostrar PPUs instalados
            TextView ppuText = new TextView(getContext());
            ppuText.setText("PPUs Instalados:\n\n(Lista será carregada dos mods salvos)");
            ppuText.setTextColor(Color.WHITE);
            ppuText.setTextSize(16);
            ppuText.setPadding(16, 32, 16, 16);
            gamesContainer.addView(ppuText);
        } else {
            TextView emptyText = new TextView(getContext());
            emptyText.setText("Nenhum PPU instalado.\n\nUse o botão + para instalar PPUs.");
            emptyText.setTextColor(Color.parseColor("#888888"));
            emptyText.setTextSize(16);
            emptyText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            emptyText.setPadding(16, 64, 16, 16);
            gamesContainer.addView(emptyText);
        }
    }
    

    
    private void setupBackupReceiver() {
        // Only setup if context is available and receiver is not already registered
        if (getContext() == null || backupReceiver != null) {
            return;
        }
        
        backupReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (BackupService.ACTION_UPDATE_PROGRESS.equals(intent.getAction())) {
                    int progress = intent.getIntExtra(BackupService.EXTRA_PROGRESS, 0);
                    // TODO: Update progress if needed
                } else if (BackupService.ACTION_BACKUP_COMPLETE.equals(intent.getAction())) {
                    boolean success = intent.getBooleanExtra(BackupService.EXTRA_SUCCESS, false);
                    if (success && getContext() != null) {
                        Toast.makeText(getContext(), "Backup concluído!", Toast.LENGTH_SHORT).show();
                    } else if (getContext() != null) {
                        Toast.makeText(getContext(), "Erro ao criar backup.", Toast.LENGTH_LONG).show();
                    }
                }
            }
        };
        
        IntentFilter filter = new IntentFilter();
        filter.addAction(BackupService.ACTION_UPDATE_PROGRESS);
        filter.addAction(BackupService.ACTION_BACKUP_COMPLETE);
        
        try {
            getContext().registerReceiver(backupReceiver, filter);
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error registering backup receiver: " + e.getMessage());
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        // Receiver is unregistered in onPause()
        backupReceiver = null;
    }
    
    private ParamSfoData extractParamSfoData(DocumentFile paramFile) {
        try {
            InputStream inputStream = getContext().getContentResolver().openInputStream(paramFile.getUri());
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int nRead;
            byte[] data = new byte[16384];
            while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            inputStream.close();
            byte[] fileData = buffer.toByteArray();

            if (fileData[0] != 0x00 || fileData[1] != 'P' || fileData[2] != 'S' || fileData[3] != 'F') {
                return null;
            }

            int keyTableStart = ByteBuffer.wrap(fileData, 8, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            int dataTableStart = ByteBuffer.wrap(fileData, 12, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            int numEntries = ByteBuffer.wrap(fileData, 16, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();

            String title = null;
            String appVer = null;
            String version = null;

            for (int i = 0; i < numEntries; i++) {
                int entryOffset = 20 + i * 16;
                int keyNameOffset = ByteBuffer.wrap(fileData, entryOffset, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
                int dataLen = ByteBuffer.wrap(fileData, entryOffset + 8, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                int dataOffset = ByteBuffer.wrap(fileData, entryOffset + 12, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();

                int absoluteKeyOffset = keyTableStart + keyNameOffset;
                int absoluteDataOffset = dataTableStart + dataOffset;

                int keyEnd = absoluteKeyOffset;
                while (keyEnd < fileData.length && fileData[keyEnd] != 0) {
                    keyEnd++;
                }

                String key = new String(fileData, absoluteKeyOffset, keyEnd - absoluteKeyOffset, StandardCharsets.ISO_8859_1);
                int actualLength = 0;
                while (actualLength < dataLen && fileData[absoluteDataOffset + actualLength] != 0) {
                    actualLength++;
                }
                String value = new String(fileData, absoluteDataOffset, actualLength, StandardCharsets.UTF_8);

                if ("TITLE".equals(key)) {
                    title = value;
                } else if ("APP_VER".equals(key)) {
                    appVer = value;
                } else if ("VERSION".equals(key)) {
                    version = value;
                }
            }
            return new ParamSfoData(
                    title,
                    appVer != null ? appVer : "01.00",
                    version != null ? version : "01.00"
            );
        } catch (Exception e) {
            Log.e(TAG, "Erro ao extrair dados do PARAM.SFO: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    private DocumentFile findSubFolder(DocumentFile parent, String folderName) {
        if (parent == null) return null;
        DocumentFile[] files = parent.listFiles();
        if (files == null) {
            Log.w(TAG, "listFiles retornou null para " + parent.getUri());
            return null;
        }
        for (DocumentFile file : files) {
            if (file.isDirectory() && file.getName() != null && file.getName().equalsIgnoreCase(folderName)) {
                Log.d(TAG, "Pasta encontrada: " + folderName + " em " + file.getUri());
                return file;
            }
        }
        Log.w(TAG, "Pasta " + folderName + " não encontrada em " + parent.getUri());
        return null;
    }
    
    private class LoadGamesTask extends AsyncTask<Uri, Void, Void> {
        
        @Override
        protected void onPreExecute() {
            // Show loading message with debug info
            gamesContainer.removeAllViews();
            TextView loadingText = new TextView(getContext());
            loadingText.setText("Carregando jogos...\n\n🔍 Detectando jogos reais do RPCS3\n📁 Analisando PARAM.SFO");
            loadingText.setTextColor(Color.WHITE);
            loadingText.setTextSize(16);
            loadingText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            loadingText.setPadding(16, 64, 16, 16);
            gamesContainer.addView(loadingText);
        }

        @Override
        protected Void doInBackground(Uri... uris) {
            if (uris.length == 0) {
                Log.e(TAG, "No URIs provided to LoadGamesTask");
                return null;
            }
            Uri selectedFolderUri = uris[0];
            
            if (selectedFolderUri == null) {
                Log.e(TAG, "Selected folder URI is null");
                return null;
            }

            Log.d(TAG, "Processing folder: " + selectedFolderUri.toString());
            DocumentFile rootFolder = DocumentFile.fromTreeUri(getContext(), selectedFolderUri);
            if (rootFolder == null || !rootFolder.exists()) {
                Log.e(TAG, "Root folder inválido ou não existe: " + selectedFolderUri.toString());
                return null;
            }

            Log.d(TAG, "Root folder found: " + rootFolder.getName());
            DocumentFile configFolder = findSubFolder(rootFolder, "config");
            if (configFolder == null) {
                Log.e(TAG, "Pasta config não encontrada em " + rootFolder.getUri());
                return null;
            }

            Log.d(TAG, "Config folder found, clearing game list");
            gameList.clear();

            DocumentFile devHdd0Folder = findSubFolder(configFolder, "dev_hdd0");
            if (devHdd0Folder != null) {
                Log.d(TAG, "dev_hdd0 folder found");
                DocumentFile gameFolder = findSubFolder(devHdd0Folder, "game");
                if (gameFolder != null) {
                    Log.d(TAG, "game folder found, processing games");
                    processGameFolder(gameFolder, "");
                } else {
                    Log.w(TAG, "game folder not found in dev_hdd0");
                }
            } else {
                Log.w(TAG, "dev_hdd0 folder not found");
            }

            DocumentFile gamesFolder = findSubFolder(configFolder, "games");
            if (gamesFolder != null) {
                Log.d(TAG, "games folder found, processing games");
                processGameFolder(gamesFolder, "PS3_GAME");
            } else {
                Log.d(TAG, "games folder not found (this is normal for most setups)");
            }

            Log.d(TAG, "Game loading completed. Total games found: " + gameList.size());
            return null;
        }
        
        private void processGameFolder(DocumentFile parentFolder, String subFolder) {
            DocumentFile[] files = parentFolder.listFiles();
            if (files == null) {
                Log.w(TAG, "No files found in parent folder: " + parentFolder.getUri());
                return;
            }
            
            Log.d(TAG, "Processing " + files.length + " items in " + parentFolder.getName());
            
            for (DocumentFile folder : files) {
                if (folder.isDirectory() && folder.getName() != null) {
                    Log.d(TAG, "Processing folder: " + folder.getName());
                    DocumentFile targetFolder = subFolder.isEmpty() ? folder : findSubFolder(folder, subFolder);
                    if (targetFolder != null) {
                        DocumentFile paramFile = targetFolder.findFile("PARAM.SFO");
                        DocumentFile iconFile = targetFolder.findFile("ICON0.PNG");
                        
                        Log.d(TAG, "Checking PARAM.SFO in " + folder.getName() + ": " + (paramFile != null && paramFile.exists()));
                        
                        if (paramFile != null && paramFile.exists()) {
                            Log.d(TAG, "Extracting PARAM.SFO data for " + folder.getName());
                            ParamSfoData data = extractParamSfoData(paramFile);
                            if (data != null && data.title != null) {
                                String gameId = folder.getName();
                                Uri iconUri = (iconFile != null && iconFile.exists()) ? iconFile.getUri() : null;
                                Log.d(TAG, "Game found: " + data.title + " (" + gameId + ")");
                                gameList.add(new GameItem(data.title, gameId, data.appVer, data.version, iconUri));
                            } else {
                                Log.w(TAG, "Failed to extract PARAM.SFO data for " + folder.getName());
                            }
                        }
                    } else {
                        Log.w(TAG, "Target folder not found for " + folder.getName());
                    }
                }
            }
        }

        @Override
        protected void onPostExecute(Void result) {
            if (getContext() == null) {
                Log.w(TAG, "Context is null in onPostExecute");
                return;
            }
            
            Log.d(TAG, "LoadGamesTask completed. Games found: " + gameList.size());
            
            gamesContainer.removeAllViews();
            filteredGameList.clear();
            filteredGameList.addAll(gameList);
            
            if (filteredGameList.isEmpty()) {
                Log.w(TAG, "No games found, showing empty message");
                
                // Material You empty state for no games
                LinearLayout emptyContainer = new LinearLayout(getContext());
                emptyContainer.setOrientation(LinearLayout.VERTICAL);
                emptyContainer.setPadding(32, 60, 32, 32);
                
                // Empty state icon
                TextView emptyIcon = new TextView(getContext());
                emptyIcon.setText("🎮");
                emptyIcon.setTextSize(56);
                emptyIcon.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                emptyIcon.setPadding(0, 0, 0, 24);
                emptyContainer.addView(emptyIcon);
                
                // Empty state title
                TextView titleText = new TextView(getContext());
                titleText.setText("Nenhum jogo encontrado");
                titleText.setTextColor(Color.parseColor("#E6E0E9")); // Material You on-surface
                titleText.setTextSize(22);
                titleText.setTypeface(titleText.getTypeface(), android.graphics.Typeface.BOLD);
                titleText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                titleText.setPadding(0, 0, 0, 16);
                emptyContainer.addView(titleText);
                
                // Checklist container
                LinearLayout checklistContainer = new LinearLayout(getContext());
                checklistContainer.setOrientation(LinearLayout.VERTICAL);
                checklistContainer.setBackgroundColor(Color.parseColor("#1E1B24")); // Material You surface container
                checklistContainer.setPadding(20, 16, 20, 16);
                LinearLayout.LayoutParams checklistParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                checklistParams.setMargins(0, 0, 0, 16);
                checklistContainer.setLayoutParams(checklistParams);
                
                TextView checklistTitle = new TextView(getContext());
                checklistTitle.setText("🔍 Verificações:");
                checklistTitle.setTextColor(Color.parseColor("#6750A4")); // Material You primary
                checklistTitle.setTextSize(14);
                checklistTitle.setTypeface(checklistTitle.getTypeface(), android.graphics.Typeface.BOLD);
                checklistTitle.setPadding(0, 0, 0, 12);
                checklistContainer.addView(checklistTitle);
                
                String[] checks = {
                    "✓ Pasta RPCS3 configurada corretamente",
                    "✓ Jogos instalados em /config/dev_hdd0/game/",
                    "✓ Arquivos PARAM.SFO válidos nos jogos"
                };
                
                for (String check : checks) {
                    TextView checkItem = new TextView(getContext());
                    checkItem.setText(check);
                    checkItem.setTextColor(Color.parseColor("#CAC4D0")); // Material You on-surface-variant
                    checkItem.setTextSize(12);
                    checkItem.setPadding(0, 4, 0, 4);
                    checklistContainer.addView(checkItem);
                }
                
                emptyContainer.addView(checklistContainer);
                
                // Status badge
                TextView statusBadge = new TextView(getContext());
                statusBadge.setText("🛠️ Sistema de detecção real ativo");
                statusBadge.setTextColor(Color.parseColor("#5DD87C")); // Material You success
                statusBadge.setBackgroundColor(Color.parseColor("#0F5132")); // Material You success container
                statusBadge.setTextSize(12);
                statusBadge.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                statusBadge.setPadding(16, 8, 16, 8);
                LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                badgeParams.gravity = android.view.Gravity.CENTER_HORIZONTAL;
                statusBadge.setLayoutParams(badgeParams);
                emptyContainer.addView(statusBadge);
                
                gamesContainer.addView(emptyContainer);
            } else {
                Log.d(TAG, "Displaying " + filteredGameList.size() + " games");
                
                // Material You stats container
                LinearLayout statsContainer = new LinearLayout(getContext());
                statsContainer.setOrientation(LinearLayout.HORIZONTAL);
                statsContainer.setBackgroundColor(Color.parseColor("#1E1B24")); // Material You surface container
                statsContainer.setPadding(20, 16, 20, 16);
                LinearLayout.LayoutParams statsParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                statsParams.setMargins(16, 8, 16, 20);
                statsContainer.setLayoutParams(statsParams);
                
                // Add subtle elevation
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    statsContainer.setElevation(1f);
                }
                
                // Total games stat
                LinearLayout totalGamesBox = new LinearLayout(getContext());
                totalGamesBox.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams totalParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                totalGamesBox.setLayoutParams(totalParams);
                
                TextView totalNumber = new TextView(getContext());
                totalNumber.setText(String.valueOf(filteredGameList.size()));
                totalNumber.setTextColor(Color.parseColor("#6750A4")); // Material You primary
                totalNumber.setTextSize(24);
                totalNumber.setTypeface(totalNumber.getTypeface(), android.graphics.Typeface.BOLD);
                totalNumber.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                
                TextView totalLabel = new TextView(getContext());
                totalLabel.setText("🎮 Jogos");
                totalLabel.setTextColor(Color.parseColor("#CAC4D0")); // Material You on-surface-variant
                totalLabel.setTextSize(12);
                totalLabel.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                
                totalGamesBox.addView(totalNumber);
                totalGamesBox.addView(totalLabel);
                
                // Divider
                TextView divider = new TextView(getContext());
                divider.setText("|");
                divider.setTextColor(Color.parseColor("#48454E")); // Material You outline-variant
                divider.setTextSize(18);
                divider.setPadding(16, 0, 16, 0);
                LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                dividerParams.gravity = android.view.Gravity.CENTER_VERTICAL;
                divider.setLayoutParams(dividerParams);
                
                // PPU games stat
                LinearLayout ppuGamesBox = new LinearLayout(getContext());
                ppuGamesBox.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams ppuParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                ppuGamesBox.setLayoutParams(ppuParams);
                
                int gamesWithPPUs = 0;
                for (GameItem game : filteredGameList) {
                    if (RPCS3Helper.hasGamePPUs(getContext(), game.id)) {
                        gamesWithPPUs++;
                    }
                }
                
                TextView ppuNumber = new TextView(getContext());
                ppuNumber.setText(String.valueOf(gamesWithPPUs));
                ppuNumber.setTextColor(Color.parseColor("#5DD87C")); // Material You success
                ppuNumber.setTextSize(24);
                ppuNumber.setTypeface(ppuNumber.getTypeface(), android.graphics.Typeface.BOLD);
                ppuNumber.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                
                TextView ppuLabel = new TextView(getContext());
                ppuLabel.setText("💾 Com PPUs");
                ppuLabel.setTextColor(Color.parseColor("#CAC4D0")); // Material You on-surface-variant
                ppuLabel.setTextSize(12);
                ppuLabel.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                
                ppuGamesBox.addView(ppuNumber);
                ppuGamesBox.addView(ppuLabel);
                
                statsContainer.addView(totalGamesBox);
                statsContainer.addView(divider);
                statsContainer.addView(ppuGamesBox);
                gamesContainer.addView(statsContainer);
                
                // Show games using real data
                for (GameItem game : filteredGameList) {
                    createRealGameCard(game);
                }
            }
        }
    }
    
    private void filterGames(String query) {
        if (!showingGames || gameList.isEmpty()) {
            return;
        }
        
        gamesContainer.removeAllViews();
        filteredGameList.clear();
        
        if (query.isEmpty()) {
            filteredGameList.addAll(gameList);
        } else {
            String lowerQuery = query.toLowerCase();
            for (GameItem game : gameList) {
                if (game.title.toLowerCase().contains(lowerQuery) || 
                    game.id.toLowerCase().contains(lowerQuery)) {
                    filteredGameList.add(game);
                }
            }
        }
        
        Log.d(TAG, "Filtered games: " + filteredGameList.size() + " from " + gameList.size());
        
        if (filteredGameList.isEmpty()) {
            // Material You empty search state
            LinearLayout emptyContainer = new LinearLayout(getContext());
            emptyContainer.setOrientation(LinearLayout.VERTICAL);
            emptyContainer.setPadding(32, 80, 32, 32);
            
            // Search empty icon
            TextView emptyIcon = new TextView(getContext());
            emptyIcon.setText("🔍");
            emptyIcon.setTextSize(48);
            emptyIcon.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            emptyIcon.setPadding(0, 0, 0, 20);
            emptyContainer.addView(emptyIcon);
            
            // Empty search title
            TextView titleText = new TextView(getContext());
            titleText.setText("Nenhum resultado encontrado");
            titleText.setTextColor(Color.parseColor("#E6E0E9")); // Material You on-surface
            titleText.setTextSize(18);
            titleText.setTypeface(titleText.getTypeface(), android.graphics.Typeface.BOLD);
            titleText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            titleText.setPadding(0, 0, 0, 12);
            emptyContainer.addView(titleText);
            
            // Search query
            TextView queryText = new TextView(getContext());
            queryText.setText("Busca por: \"" + query + "\"");
            queryText.setTextColor(Color.parseColor("#938F96")); // Material You on-surface-variant
            queryText.setTextSize(14);
            queryText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            queryText.setPadding(0, 0, 0, 16);
            emptyContainer.addView(queryText);
            
            // Suggestion text
            TextView suggestionText = new TextView(getContext());
            suggestionText.setText("Tente buscar por outro termo ou verifique se os jogos estão instalados.");
            suggestionText.setTextColor(Color.parseColor("#CAC4D0")); // Material You on-surface-variant
            suggestionText.setTextSize(12);
            suggestionText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            emptyContainer.addView(suggestionText);
            
            gamesContainer.addView(emptyContainer);
        } else {
            for (GameItem game : filteredGameList) {
                createRealGameCard(game);
            }
        }
    }
    
    private void createRealGameCard(GameItem game) {
        // Material You 3.0 Card Container
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.parseColor("#1E1B24")); // Material You surface container high
        card.setPadding(0, 0, 0, 0);
        // Add subtle rounded corners effect with elevation
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            card.setElevation(6f);
        }
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(8, 8, 8, 16);
        card.setLayoutParams(cardParams);
        
        // Game image/icon with Material You styling
        ImageView imageView = new ImageView(getContext());
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 180);
        imageView.setLayoutParams(imageParams);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        
        // Load game icon if available
        if (game.iconUri != null) {
            try {
                InputStream inputStream = getContext().getContentResolver().openInputStream(game.iconUri);
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                imageView.setImageBitmap(bitmap);
                inputStream.close();
            } catch (IOException e) {
                // Material You gradient colors for game placeholders
                int colorIndex = game.id.hashCode() % 8;
                String[] gradientColors = {"#6750A4", "#7C4DFF", "#00BCD4", "#4CAF50", "#FF9800", "#E91E63", "#9C27B0", "#3F51B5"};
                imageView.setBackgroundColor(Color.parseColor(gradientColors[colorIndex]));
            }
        } else {
            // Material You gradient colors for game placeholders
            int colorIndex = game.id.hashCode() % 8;
            String[] gradientColors = {"#6750A4", "#7C4DFF", "#00BCD4", "#4CAF50", "#FF9800", "#E91E63", "#9C27B0", "#3F51B5"};
            imageView.setBackgroundColor(Color.parseColor(gradientColors[colorIndex]));
        }
        card.addView(imageView);
        
        // Game info container with Material You padding
        LinearLayout infoContainer = new LinearLayout(getContext());
        infoContainer.setOrientation(LinearLayout.VERTICAL);
        infoContainer.setPadding(20, 20, 20, 20);
        
        // Game title - Material You typography
        TextView titleText = new TextView(getContext());
        titleText.setText(game.title);
        titleText.setTextColor(Color.parseColor("#E6E0E9")); // Material You on-surface
        titleText.setTextSize(18);
        titleText.setTypeface(titleText.getTypeface(), android.graphics.Typeface.BOLD);
        titleText.setPadding(0, 0, 0, 8);
        infoContainer.addView(titleText);
        
        // Game ID and Region - Material You secondary text
        TextView gameIdText = new TextView(getContext());
        gameIdText.setText("ID: " + game.id + " • " + RPCS3Helper.getRegionFromGameId(game.id));
        gameIdText.setTextColor(Color.parseColor("#938F96")); // Material You on-surface-variant
        gameIdText.setTextSize(12);
        gameIdText.setPadding(0, 4, 0, 8);
        infoContainer.addView(gameIdText);
        
        // Version info container
        LinearLayout versionContainer = new LinearLayout(getContext());
        versionContainer.setOrientation(LinearLayout.HORIZONTAL);
        versionContainer.setPadding(0, 0, 0, 12);
        
        TextView appVersionText = new TextView(getContext());
        appVersionText.setText("App: " + game.appVer);
        appVersionText.setTextColor(Color.parseColor("#CAC4D0")); // Material You on-surface-variant
        appVersionText.setTextSize(13);
        LinearLayout.LayoutParams appVersionParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        appVersionText.setLayoutParams(appVersionParams);
        
        TextView versionText = new TextView(getContext());
        versionText.setText("Ver: " + game.version);
        versionText.setTextColor(Color.parseColor("#CAC4D0")); // Material You on-surface-variant
        versionText.setTextSize(13);
        
        versionContainer.addView(appVersionText);
        versionContainer.addView(versionText);
        infoContainer.addView(versionContainer);
        
        // PPU Status with Material You colors
        boolean hasPPUs = RPCS3Helper.hasGamePPUs(getContext(), game.id);
        LinearLayout statusContainer = new LinearLayout(getContext());
        statusContainer.setOrientation(LinearLayout.HORIZONTAL);
        statusContainer.setBackgroundColor(hasPPUs ? Color.parseColor("#0F5132") : Color.parseColor("#663C00")); // Material You success/warning container
        statusContainer.setPadding(12, 8, 12, 8);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.setMargins(0, 0, 0, 16);
        statusContainer.setLayoutParams(statusParams);
        
        TextView statusIcon = new TextView(getContext());
        statusIcon.setText(hasPPUs ? "✓" : "⚠");
        statusIcon.setTextColor(hasPPUs ? Color.parseColor("#5DD87C") : Color.parseColor("#FFCC02")); // Material You success/warning
        statusIcon.setTextSize(14);
        statusIcon.setTypeface(statusIcon.getTypeface(), android.graphics.Typeface.BOLD);
        statusIcon.setPadding(0, 0, 8, 0);
        
        TextView statusText = new TextView(getContext());
        statusText.setText(hasPPUs ? "PPUs compiladas" : "Sem PPUs");
        statusText.setTextColor(hasPPUs ? Color.parseColor("#5DD87C") : Color.parseColor("#FFCC02")); // Material You success/warning
        statusText.setTextSize(12);
        
        statusContainer.addView(statusIcon);
        statusContainer.addView(statusText);
        infoContainer.addView(statusContainer);
        
        // Material You 3.0 Backup button
        Button backupButton = new Button(getContext());
        backupButton.setTextSize(14);
        backupButton.setTypeface(backupButton.getTypeface(), android.graphics.Typeface.BOLD);
        backupButton.setPadding(24, 16, 24, 16);
        
        if (hasPPUs) {
            int fileCount = RPCS3Helper.countGamePPUFiles(getContext(), game.id);
            backupButton.setText("FAZER BACKUP (" + fileCount + " arquivos)");
            backupButton.setBackgroundColor(Color.parseColor("#6750A4")); // Material You primary
            backupButton.setTextColor(Color.parseColor("#FFFFFF")); // Material You on-primary
        } else {
            backupButton.setText("BACKUP NÃO DISPONÍVEL");
            backupButton.setBackgroundColor(Color.parseColor("#48454E")); // Material You surface-variant
            backupButton.setTextColor(Color.parseColor("#938F96")); // Material You on-surface-variant
        }
        
        backupButton.setEnabled(hasPPUs);
        backupButton.setAlpha(hasPPUs ? 1.0f : 0.6f);
        
        LinearLayout.LayoutParams backupParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        backupParams.setMargins(0, 8, 0, 0);
        backupButton.setLayoutParams(backupParams);
        
        backupButton.setOnClickListener(v -> {
            if (!hasPPUs) {
                Toast.makeText(getContext(), "⚠️ Este jogo não possui PPUs compiladas. Execute-o primeiro no RPCS3.", Toast.LENGTH_LONG).show();
                return;
            }
            
            // Request backup folder selection
            requestBackupFolderSelection(game);
        });
        
        infoContainer.addView(backupButton);
        card.addView(infoContainer);
        gamesContainer.addView(card);
    }
    
    // Game being backed up
    private GameItem pendingBackupGame = null;
    
    private void requestBackupFolderSelection(GameItem game) {
        pendingBackupGame = game;
        if (mainActivity != null) {
            mainActivity.requestBackupFolderSelection();
        } else {
            Toast.makeText(getContext(), "Erro ao solicitar seleção de pasta", Toast.LENGTH_SHORT).show();
        }
    }
    
    public void handleBackupFolderSelected(Uri folderUri) {
        if (pendingBackupGame == null) {
            Toast.makeText(getContext(), "Erro: nenhum jogo pendente para backup", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Start backup using BackupService with selected folder
        Intent serviceIntent = new Intent(getContext(), BackupService.class);
        serviceIntent.putExtra("gameId", pendingBackupGame.id);
        serviceIntent.putExtra("gameTitle", pendingBackupGame.title);
        serviceIntent.putExtra("appVer", pendingBackupGame.appVer);
        serviceIntent.putExtra("version", pendingBackupGame.version);
        serviceIntent.putExtra("backupFolderUri", folderUri.toString());
        getContext().startForegroundService(serviceIntent);
        
        Toast.makeText(getContext(), "📦 Backup de " + pendingBackupGame.title + " iniciado", Toast.LENGTH_SHORT).show();
        
        pendingBackupGame = null; // Clear pending game
    }
}