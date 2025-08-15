package com.my.newproject118.ui.games;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.my.newproject118.BackupService;
import com.my.newproject118.MainActivity;
import com.my.newproject118.R;
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
    
    // Views
    private MaterialButton tabJogos, tabPPUs;
    private TextInputEditText searchInput;
    private RecyclerView gamesRecycler;
    private View loadingContainer, emptyContainer;
    private TextView loadingText, emptyTitle, emptyMessage;
    
    // Data
    private boolean showingGames = true;
    private List<GameItem> gameList = new ArrayList<>();
    private List<GameItem> filteredGameList = new ArrayList<>();
    private GamesAdapter adapter;
    private BroadcastReceiver backupReceiver;
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_games, container, false);
        
        initViews(view);
        setupRecyclerView();
        setupTabs();
        setupSearch();
        
        // Set initial state to loading
        showLoading();
        
        // Start with games
        switchToGames();
        
        return view;
    }
    
    private void initViews(View view) {
        // Tab buttons
        tabJogos = view.findViewById(R.id.tab_jogos);
        tabPPUs = view.findViewById(R.id.tab_ppus);
        
        // Search
        searchInput = view.findViewById(R.id.search_input);
        
        // Content views
        gamesRecycler = view.findViewById(R.id.games_recycler);
        loadingContainer = view.findViewById(R.id.loading_container);
        emptyContainer = view.findViewById(R.id.empty_container);
        
        // Text views
        loadingText = view.findViewById(R.id.loading_text);
        emptyTitle = view.findViewById(R.id.empty_title);
        emptyMessage = view.findViewById(R.id.empty_message);
    }
    
    private void setupRecyclerView() {
        adapter = new GamesAdapter(gameList, this::onGameBackup);
        gamesRecycler.setAdapter(adapter);
        gamesRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
    }
    
    private void setupTabs() {
        tabJogos.setOnClickListener(v -> switchToGames());
        tabPPUs.setOnClickListener(v -> switchToPPUs());
    }
    
    private void setupSearch() {
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterGames(s.toString().trim());
            }
            
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }
    
    private void switchToGames() {
        Log.d(TAG, "switchToGames: Switching to games view");
        showingGames = true;
        updateTabAppearance();
        searchInput.setHint("Pesquisar jogos...");
        loadContent();
    }
    
    private void switchToPPUs() {
        showingGames = false;
        updateTabAppearance();
        searchInput.setHint("Pesquisar PPUs...");
        loadContent();
    }
    
    private void updateTabAppearance() {
        if (showingGames) {
            // Games tab active - use custom primary color
            tabJogos.setBackgroundColor(android.graphics.Color.parseColor("#354479"));
            tabJogos.setTextColor(android.graphics.Color.parseColor("#FFFFFF"));
            
            // PPUs tab inactive - use custom secondary color
            tabPPUs.setBackgroundColor(android.graphics.Color.parseColor("#34343A"));
            tabPPUs.setTextColor(android.graphics.Color.parseColor("#CCCCCC"));
        } else {
            // PPUs tab active - use custom primary color
            tabPPUs.setBackgroundColor(android.graphics.Color.parseColor("#354479"));
            tabPPUs.setTextColor(android.graphics.Color.parseColor("#FFFFFF"));
            
            // Games tab inactive - use custom secondary color
            tabJogos.setBackgroundColor(android.graphics.Color.parseColor("#34343A"));
            tabJogos.setTextColor(android.graphics.Color.parseColor("#CCCCCC"));
        }
    }
    
    private void loadContent() {
        Log.d(TAG, "loadContent: showingGames=" + showingGames);
        if (showingGames) {
            loadGames();
        } else {
            loadPPUs();
        }
    }
    
    private void loadGames() {
        Log.d(TAG, "Loading games...");
        
        boolean isConfigured = RPCS3Helper.isRPCS3Configured(getContext());
        Uri folderUri = RPCS3Helper.getRPCS3FolderUri(getContext());
        
        Log.d(TAG, "RPCS3 configured: " + isConfigured);
        Log.d(TAG, "RPCS3 folder URI: " + (folderUri != null ? folderUri.toString() : "null"));
        
        // Test the games folder directly
        DocumentFile gamesFolder = RPCS3Helper.getGamesFolder(getContext());
        Log.d(TAG, "Games folder via RPCS3Helper: " + (gamesFolder != null ? "found" : "not found"));
        
        if (!isConfigured) {
            showEmptyState("Pasta RPCS3 não configurada", "Configure a pasta do RPCS3 nas configurações");
            return;
        }
        
        new LoadGamesTask().execute(folderUri);
    }
    
    private void loadPPUs() {
        // Show empty state for PPUs for now
        showEmptyState("Nenhum PPU instalado", "Use o botão + para instalar PPUs");
    }
    
    private void showLoading() {
        Log.d(TAG, "showLoading: Setting loading state");
        gamesRecycler.setVisibility(View.GONE);
        emptyContainer.setVisibility(View.GONE);
        loadingContainer.setVisibility(View.VISIBLE);
        
        loadingText.setText(showingGames ? "Carregando jogos..." : "Carregando PPUs...");
        Log.d(TAG, "showLoading: Loading state set");
    }
    
    private void showEmptyState(String title, String message) {
        Log.d(TAG, "showEmptyState: " + title + " - " + message);
        loadingContainer.setVisibility(View.GONE);
        gamesRecycler.setVisibility(View.GONE);
        emptyContainer.setVisibility(View.VISIBLE);
        
        emptyTitle.setText(title);
        emptyMessage.setText(message);
    }
    
    private void showContent() {
        Log.d(TAG, "showContent: Making RecyclerView visible");
        loadingContainer.setVisibility(View.GONE);
        emptyContainer.setVisibility(View.GONE);
        gamesRecycler.setVisibility(View.VISIBLE);
        Log.d(TAG, "showContent: RecyclerView visibility set to VISIBLE");
    }
    
    private void filterGames(String query) {
        if (!showingGames || gameList.isEmpty()) {
            return;
        }
        
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
        
        adapter.updateList(filteredGameList);
        
        if (filteredGameList.isEmpty() && !query.isEmpty()) {
            showEmptyState("Nenhum resultado encontrado", "Busca por: \"" + query + "\"");
        } else if (filteredGameList.isEmpty()) {
            showEmptyState("Nenhum jogo encontrado", "Verifique se os jogos estão na pasta do RPCS3");
        } else {
            showContent();
        }
    }
    
    private void onGameBackup(GameItem game) {
        Log.d(TAG, "Backup requested for game: " + game.title);
        
        // Check if backup folder is configured
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String backupFolderUri = prefs.getString("backup_folder_uri", null);
        
        if (backupFolderUri == null) {
            // No backup folder configured, request user to select one
            Log.d(TAG, "No backup folder configured, requesting user selection");
            
            if (getActivity() instanceof MainActivity) {
                MainActivity mainActivity = (MainActivity) getActivity();
                mainActivity.requestBackupFolderSelection();
                
                Toast.makeText(getContext(), 
                    "Por favor, selecione uma pasta para salvar o backup de " + game.title, 
                    Toast.LENGTH_LONG).show();
                
                // Store the pending backup game to retry after folder selection
                prefs.edit().putString("pending_backup_game_id", game.id)
                     .putString("pending_backup_game_title", game.title)
                     .putString("pending_backup_game_appver", game.appVer)
                     .putString("pending_backup_game_version", game.version)
                     .apply();
            }
            return;
        }
        
        // Check if game has PPUs before starting backup
        if (!RPCS3Helper.hasGamePPUs(getContext(), game.id)) {
            Toast.makeText(getContext(), 
                "Nenhum PPU encontrado para " + game.title, 
                Toast.LENGTH_LONG).show();
            Log.w(TAG, "No PPUs found for game: " + game.id);
            return;
        }
        
        // Start backup service with all required data
        Intent backupIntent = new Intent(getContext(), BackupService.class);
        backupIntent.putExtra("gameId", game.id);
        backupIntent.putExtra("gameTitle", game.title);
        backupIntent.putExtra("appVer", game.appVer != null ? game.appVer : "01.00");
        backupIntent.putExtra("version", game.version != null ? game.version : "01.00");
        backupIntent.putExtra("backupFolderUri", backupFolderUri);
        
        getContext().startService(backupIntent);
        
        Toast.makeText(getContext(), "Backup iniciado para " + game.title, Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Backup service started for: " + game.title + " (" + game.id + ")");
    }
    
    @Override
    public void onResume() {
        super.onResume();
        registerBackupReceiver();
    }
    
    @Override
    public void onPause() {
        super.onPause();
        if (backupReceiver != null) {
            try {
                getContext().unregisterReceiver(backupReceiver);
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering backup receiver: " + e.getMessage());
            }
        }
    }
    
    private void registerBackupReceiver() {
        if (backupReceiver == null) {
            backupReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if ("BACKUP_PROGRESS".equals(action) || "BACKUP_COMPLETE".equals(action)) {
                        // Handle backup updates
                        Log.d(TAG, "Backup update received: " + action);
                    }
                }
            };
        }
        
        IntentFilter filter = new IntentFilter();
        filter.addAction("BACKUP_PROGRESS");
        filter.addAction("BACKUP_COMPLETE");
        
        try {
            getContext().registerReceiver(backupReceiver, filter);
        } catch (Exception e) {
            Log.e(TAG, "Error registering backup receiver: " + e.getMessage());
        }
    }
    
    // Game loading task
    private class LoadGamesTask extends AsyncTask<Uri, Void, List<GameItem>> {
        
        @Override
        protected void onPreExecute() {
            showLoading();
        }
        
        @Override
        protected List<GameItem> doInBackground(Uri... uris) {
            List<GameItem> games = new ArrayList<>();
            Log.d(TAG, "doInBackground: Starting to load games");
            
            if (uris.length == 0) {
                Log.e(TAG, "No URIs provided to LoadGamesTask");
                return games;
            }
            
            Uri selectedFolderUri = uris[0];
            if (selectedFolderUri == null) {
                Log.e(TAG, "Selected folder URI is null");
                return games;
            }
            
            Log.d(TAG, "Processing folder: " + selectedFolderUri.toString());
            DocumentFile rootFolder = DocumentFile.fromTreeUri(getContext(), selectedFolderUri);
            if (rootFolder == null || !rootFolder.exists()) {
                Log.e(TAG, "Root folder invalid or doesn't exist: " + selectedFolderUri.toString());
                return games;
            }
            
            Log.d(TAG, "Root folder found: " + rootFolder.getName());
            DocumentFile configFolder = findSubFolder(rootFolder, "config");
            if (configFolder == null) {
                Log.e(TAG, "Config folder not found in " + rootFolder.getUri());
                return games;
            }
            
            Log.d(TAG, "Config folder found, processing games");
            
            // Process dev_hdd0/game folder
            DocumentFile devHdd0Folder = findSubFolder(configFolder, "dev_hdd0");
            if (devHdd0Folder != null) {
                Log.d(TAG, "dev_hdd0 folder found");
                DocumentFile gameFolder = findSubFolder(devHdd0Folder, "game");
                if (gameFolder != null) {
                    Log.d(TAG, "game folder found, processing games");
                    processGameFolder(gameFolder, games, "");
                } else {
                    Log.w(TAG, "game folder not found in dev_hdd0");
                }
            } else {
                Log.w(TAG, "dev_hdd0 folder not found");
            }
            
            // Also check for games folder (some RPCS3 setups)
            DocumentFile gamesFolder = findSubFolder(configFolder, "games");
            if (gamesFolder != null) {
                Log.d(TAG, "games folder found, processing games");
                processGameFolder(gamesFolder, games, "PS3_GAME");
            } else {
                Log.d(TAG, "games folder not found (this is normal for most setups)");
            }
            
            Log.d(TAG, "Game loading completed. Total games found: " + games.size());
            return games;
        }
        
        @Override
        protected void onPostExecute(List<GameItem> games) {
            if (getContext() == null) return;
            
            Log.d(TAG, "onPostExecute: Found " + games.size() + " games");
            
            gameList.clear();
            gameList.addAll(games);
            filteredGameList.clear();
            filteredGameList.addAll(games);
            
            if (games.isEmpty()) {
                Log.d(TAG, "No games found, showing empty state");
                showEmptyState("Nenhum jogo encontrado", "Verifique se os jogos estão instalados na pasta do RPCS3");
            } else {
                Log.d(TAG, "Games found, updating adapter and showing content");
                adapter.updateList(filteredGameList);
                showContent();
            }
        }
        
        private void processGameFolder(DocumentFile parentFolder, List<GameItem> games, String subFolder) {
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
                                
                                GameItem gameItem = new GameItem(gameId, data.title, data.appVer, data.version, iconUri);
                                games.add(gameItem);
                                
                                Log.d(TAG, "Added game: " + data.title + " (" + gameId + ")");
                            } else {
                                Log.w(TAG, "Failed to extract PARAM.SFO data for " + folder.getName());
                            }
                        } else {
                            Log.d(TAG, "No PARAM.SFO found in " + folder.getName());
                        }
                    } else {
                        Log.d(TAG, "Target folder not found for " + folder.getName() + " (subFolder: " + subFolder + ")");
                    }
                }
            }
        }
        
        private DocumentFile findSubFolder(DocumentFile parent, String folderName) {
            if (parent == null) return null;
            DocumentFile[] files = parent.listFiles();
            if (files == null) {
                Log.w(TAG, "listFiles returned null for " + parent.getUri());
                return null;
            }
            for (DocumentFile file : files) {
                if (file.isDirectory() && file.getName() != null && file.getName().equalsIgnoreCase(folderName)) {
                    Log.d(TAG, "Folder found: " + folderName + " at " + file.getUri());
                    return file;
                }
            }
            Log.d(TAG, "Folder not found: " + folderName + " in " + parent.getUri());
            return null;
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
            Log.e(TAG, "Error extracting PARAM.SFO data: " + e.getMessage());
            return null;
        }
    }
    }
    
    // Method called by MainActivity when backup folder is selected
    public void handleBackupFolderSelected(Uri folderUri) {
        Log.d(TAG, "Backup folder selected: " + folderUri.toString());
        
        // Save the backup folder URI in SharedPreferences
        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString("backup_folder_uri", folderUri.toString()).apply();
        
        // Show confirmation message
        Toast.makeText(getContext(), "Pasta de backup configurada com sucesso", Toast.LENGTH_SHORT).show();
        
        // Check if there's a pending backup to process
        String pendingGameId = prefs.getString("pending_backup_game_id", null);
        if (pendingGameId != null) {
            String pendingGameTitle = prefs.getString("pending_backup_game_title", null);
            String pendingGameAppVer = prefs.getString("pending_backup_game_appver", "01.00");
            String pendingGameVersion = prefs.getString("pending_backup_game_version", "01.00");
            
            if (pendingGameTitle != null) {
                Log.d(TAG, "Processing pending backup for: " + pendingGameTitle);
                
                // Clear pending backup data
                prefs.edit()
                    .remove("pending_backup_game_id")
                    .remove("pending_backup_game_title")
                    .remove("pending_backup_game_appver")
                    .remove("pending_backup_game_version")
                    .apply();
                
                // Check if game has PPUs before starting backup
                if (!RPCS3Helper.hasGamePPUs(getContext(), pendingGameId)) {
                    Toast.makeText(getContext(), 
                        "Nenhum PPU encontrado para " + pendingGameTitle, 
                        Toast.LENGTH_LONG).show();
                    Log.w(TAG, "No PPUs found for pending game: " + pendingGameId);
                    return;
                }
                
                // Start backup service for pending game
                Intent backupIntent = new Intent(getContext(), BackupService.class);
                backupIntent.putExtra("gameId", pendingGameId);
                backupIntent.putExtra("gameTitle", pendingGameTitle);
                backupIntent.putExtra("appVer", pendingGameAppVer);
                backupIntent.putExtra("version", pendingGameVersion);
                backupIntent.putExtra("backupFolderUri", folderUri.toString());
                
                getContext().startService(backupIntent);
                
                Toast.makeText(getContext(), "Backup iniciado para " + pendingGameTitle, Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Pending backup service started for: " + pendingGameTitle + " (" + pendingGameId + ")");
            }
        }
        
        // Optionally refresh the games list if needed
        if (showingGames && !gameList.isEmpty()) {
            // Refresh adapter to potentially show backup status
            adapter.notifyDataSetChanged();
        }
    }
    
    // Data classes
    public static class GameItem {
        public final String id;
        public final String title;
        public final String appVer;
        public final String version;
        public final Uri iconUri;
        
        public GameItem(String id, String title, String appVer, String version, Uri iconUri) {
            this.id = id;
            this.title = title;
            this.appVer = appVer;
            this.version = version;
            this.iconUri = iconUri;
        }
    }
    
    public static class ParamSfoData {
        public final String title;
        public final String appVer;
        public final String version;
        
        public ParamSfoData(String title, String appVer, String version) {
            this.title = title;
            this.appVer = appVer;
            this.version = version;
        }
    }
}