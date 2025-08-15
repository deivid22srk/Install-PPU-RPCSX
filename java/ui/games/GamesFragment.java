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
        mainActivity = (MainActivity) getActivity();
        
        // Initialize SharedPreferences
        prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
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
        String savedUri = prefs.getString(PREF_FOLDER_URI, null);
        if (savedUri != null) {
            Uri selectedFolderUri = Uri.parse(savedUri);
            new LoadGamesTask().execute(selectedFolderUri);
        } else {
            // Show message that RPCS3 folder needs to be configured
            TextView noFolderText = new TextView(getContext());
            noFolderText.setText("Pasta RPCS3 não configurada.\n\nVá em Settings para selecionar a pasta do RPCS3.");
            noFolderText.setTextColor(Color.parseColor("#888888"));
            noFolderText.setTextSize(16);
            noFolderText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            noFolderText.setPadding(16, 64, 16, 16);
            gamesContainer.addView(noFolderText);
        }
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
            // TODO: Implementar backup usando BackupService.java existente
            if (getContext() != null) {
                android.widget.Toast.makeText(getContext(), "Backup de " + title + " iniciado", android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        
        infoContainer.addView(backupButton);
        card.addView(infoContainer);
        gamesContainer.addView(card);
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
            if (uris.length == 0) return null;
            Uri selectedFolderUri = uris[0];
            
            if (selectedFolderUri == null) return null;

            DocumentFile rootFolder = DocumentFile.fromTreeUri(getContext(), selectedFolderUri);
            if (rootFolder == null || !rootFolder.exists()) {
                Log.e(TAG, "Root folder inválido ou não existe");
                return null;
            }

            DocumentFile configFolder = findSubFolder(rootFolder, "config");
            if (configFolder == null) {
                Log.e(TAG, "Pasta config não encontrada");
                return null;
            }

            gameList.clear();

            DocumentFile devHdd0Folder = findSubFolder(configFolder, "dev_hdd0");
            if (devHdd0Folder != null) {
                DocumentFile gameFolder = findSubFolder(devHdd0Folder, "game");
                if (gameFolder != null) {
                    processGameFolder(gameFolder, "");
                }
            }

            DocumentFile gamesFolder = findSubFolder(configFolder, "games");
            if (gamesFolder != null) {
                processGameFolder(gamesFolder, "PS3_GAME");
            }

            return null;
        }
        
        private void processGameFolder(DocumentFile parentFolder, String subFolder) {
            DocumentFile[] files = parentFolder.listFiles();
            if (files == null) return;
            
            for (DocumentFile folder : files) {
                if (folder.isDirectory()) {
                    DocumentFile targetFolder = subFolder.isEmpty() ? folder : findSubFolder(folder, subFolder);
                    if (targetFolder != null) {
                        DocumentFile paramFile = targetFolder.findFile("PARAM.SFO");
                        DocumentFile iconFile = targetFolder.findFile("ICON0.PNG");
                        if (paramFile != null && paramFile.exists()) {
                            ParamSfoData data = extractParamSfoData(paramFile);
                            if (data != null && data.title != null) {
                                String gameId = folder.getName();
                                Uri iconUri = (iconFile != null && iconFile.exists()) ? iconFile.getUri() : null;
                                gameList.add(new GameItem(data.title, gameId, data.appVer, data.version, iconUri));
                            }
                        }
                    }
                }
            }
        }

        @Override
        protected void onPostExecute(Void result) {
            gamesContainer.removeAllViews();
            filteredGameList.clear();
            filteredGameList.addAll(gameList);
            
            if (filteredGameList.isEmpty()) {
                TextView emptyText = new TextView(getContext());
                emptyText.setText("Nenhum jogo encontrado.\n\nVerifique se a pasta do RPCS3 está configurada corretamente.");
                emptyText.setTextColor(Color.parseColor("#888888"));
                emptyText.setTextSize(16);
                emptyText.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                emptyText.setPadding(16, 64, 16, 16);
                gamesContainer.addView(emptyText);
            } else {
                // Show games using real data
                for (GameItem game : filteredGameList) {
                    createRealGameCard(game);
                }
            }
        }
    }
    
    private void createRealGameCard(GameItem game) {
        // Main card container
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.parseColor("#2A2A2A"));
        card.setPadding(0, 0, 0, 16);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, 16);
        card.setLayoutParams(cardParams);
        
        // Game image/icon
        ImageView imageView = new ImageView(getContext());
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 200);
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
                // Use default color based on game
                boolean isMinecraft = game.title.toLowerCase().contains("minecraft");
                imageView.setBackgroundColor(isMinecraft ? Color.parseColor("#8BC34A") : Color.parseColor("#555555"));
            }
        } else {
            // Use default color based on game
            boolean isMinecraft = game.title.toLowerCase().contains("minecraft");
            imageView.setBackgroundColor(isMinecraft ? Color.parseColor("#8BC34A") : Color.parseColor("#555555"));
        }
        card.addView(imageView);
        
        // Game info container
        LinearLayout infoContainer = new LinearLayout(getContext());
        infoContainer.setOrientation(LinearLayout.VERTICAL);
        infoContainer.setPadding(16, 16, 16, 16);
        
        // Game title
        TextView titleText = new TextView(getContext());
        titleText.setText(game.title);
        titleText.setTextColor(Color.WHITE);
        titleText.setTextSize(18);
        titleText.setPadding(0, 0, 0, 8);
        infoContainer.addView(titleText);
        
        // Version info
        LinearLayout versionContainer = new LinearLayout(getContext());
        versionContainer.setOrientation(LinearLayout.HORIZONTAL);
        
        TextView appVersionText = new TextView(getContext());
        appVersionText.setText("App Version: " + game.appVer);
        appVersionText.setTextColor(Color.parseColor("#CCCCCC"));
        appVersionText.setTextSize(14);
        LinearLayout.LayoutParams appVersionParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        appVersionText.setLayoutParams(appVersionParams);
        
        TextView versionText = new TextView(getContext());
        versionText.setText("Version: " + game.version);
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
            // Start backup using BackupService
            Intent serviceIntent = new Intent(getContext(), BackupService.class);
            serviceIntent.putExtra("gameId", game.id);
            serviceIntent.putExtra("gameTitle", game.title);
            serviceIntent.putExtra("appVer", game.appVer);
            serviceIntent.putExtra("version", game.version);
            getContext().startForegroundService(serviceIntent);
            
            Toast.makeText(getContext(), "Backup de " + game.title + " iniciado", Toast.LENGTH_SHORT).show();
        });
        
        infoContainer.addView(backupButton);
        card.addView(infoContainer);
        gamesContainer.addView(card);
    }
}