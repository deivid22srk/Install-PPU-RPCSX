package com.my.newproject118;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textview.MaterialTextView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class GameListLoader extends Activity {

    private static final String TAG = "GameListLoader";
    private static final String PREFS_NAME = "RPCS3Prefs";
    private static final String PREF_FOLDER_URI = "folder_uri";

    private MaterialTextView txtSelectedFolder;
    private RecyclerView recyclerViewGames;
    private GameAdapter gameAdapter;
    private List<GameItem> gameList = new ArrayList<>();
    private List<GameItem> filteredGameList = new ArrayList<>();
    public static Uri selectedFolderUri;
    private SharedPreferences prefs;
    private AlertDialog progressDialog;
    private LinearProgressIndicator progressBar;
    private MaterialTextView progressMessage;
    private BroadcastReceiver backupReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.custom_game_list_layout);

        txtSelectedFolder = findViewById(R.id.txtSelectedFolder);
        recyclerViewGames = findViewById(R.id.recyclerViewGames);
        MaterialAutoCompleteTextView searchEditText = findViewById(R.id.searchEditText);

        gameAdapter = new GameAdapter(this, filteredGameList);
        recyclerViewGames.setAdapter(gameAdapter);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        loadSavedFolder();

        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                filterGames(s.toString());
            }
        });

        if (selectedFolderUri != null) {
            new LoadGamesTask().execute();
        }

        setupBackupReceiver();
    }

    private void filterGames(String query) {
        filteredGameList.clear();
        if (query.isEmpty()) {
            filteredGameList.addAll(gameList);
        } else {
            String lowerQuery = query.toLowerCase();
            for (GameItem game : gameList) {
                if (game.title.toLowerCase().contains(lowerQuery) || game.id.toLowerCase().contains(lowerQuery)) {
                    filteredGameList.add(game);
                }
            }
        }
        gameAdapter.notifyDataSetChanged();
    }

    private void setupBackupReceiver() {
        backupReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (BackupService.ACTION_UPDATE_PROGRESS.equals(intent.getAction())) {
                    int progress = intent.getIntExtra(BackupService.EXTRA_PROGRESS, 0);
                    if (progressBar != null) {
                        progressBar.setProgress(progress);
                    }
                } else if (BackupService.ACTION_BACKUP_COMPLETE.equals(intent.getAction())) {
                    boolean success = intent.getBooleanExtra(BackupService.EXTRA_SUCCESS, false);
                    if (progressDialog != null && progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    if (success) {
                        Toast.makeText(GameListLoader.this, "Backup concluído!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(GameListLoader.this, "Erro ao criar backup.", Toast.LENGTH_LONG).show();
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(BackupService.ACTION_UPDATE_PROGRESS);
        filter.addAction(BackupService.ACTION_BACKUP_COMPLETE);
        registerReceiver(backupReceiver, filter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (backupReceiver != null) {
            unregisterReceiver(backupReceiver);
        }
    }

    private void loadSavedFolder() {
        String savedUri = prefs.getString(PREF_FOLDER_URI, null);
        if (savedUri != null) {
            selectedFolderUri = Uri.parse(savedUri);
            txtSelectedFolder.setText("Pasta selecionada: " + selectedFolderUri.toString());
        } else {
            txtSelectedFolder.setText("Nenhuma pasta configurada em RPCS3Prefs" );
        }
    }

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

    private ParamSfoData extractParamSfoData(DocumentFile paramFile) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(paramFile.getUri());
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

    private class LoadGamesTask extends AsyncTask<Void, Void, Void> {
        private AlertDialog loadingDialog;

        @Override
        protected void onPreExecute() {
            if (!isFinishing()) {
                View dialogView = LayoutInflater.from(GameListLoader.this).inflate(R.layout.loading_dialog_layout, null);
                loadingDialog = new MaterialAlertDialogBuilder(GameListLoader.this)
                        .setView(dialogView)
                        .setCancelable(false)
                        .create();
                loadingDialog.show();
            }
        }

        @Override
        protected Void doInBackground(Void... voids) {
            if (selectedFolderUri == null) return null;

            DocumentFile rootFolder = DocumentFile.fromTreeUri(GameListLoader.this, selectedFolderUri);
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
            for (DocumentFile folder : parentFolder.listFiles()) {
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
            if (loadingDialog != null && loadingDialog.isShowing() && !isFinishing()) {
                loadingDialog.dismiss();
            }
            filteredGameList.clear();
            filteredGameList.addAll(gameList);
            gameAdapter.notifyDataSetChanged();
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

    private class GameItem {
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

    private class GameAdapter extends RecyclerView.Adapter<GameAdapter.GameViewHolder> {
        private Context context;
        private List<GameItem> games;

        GameAdapter(Context context, List<GameItem> games) {
            this.context = context;
            this.games = games;
        }

        @NonNull
        @Override
        public GameViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(context).inflate(R.layout.game_item_layout, parent, false);
            return new GameViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull GameViewHolder holder, int position) {
            GameItem game = games.get(position);
            holder.gameTitleTextView.setText(game.title);
            holder.appVerTextView.setText("App Version: " + game.appVer);
            holder.versionTextView.setText("Version: " + game.version);
            holder.backupButton.setText("Backup");

            if (game.iconUri != null) {
                try {
                    InputStream inputStream = context.getContentResolver().openInputStream(game.iconUri);
                    Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                    holder.iconImageView.setImageBitmap(bitmap);
                    inputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "Erro ao carregar ICON0.PNG: " + e.getMessage());
                    holder.iconImageView.setImageResource(android.R.drawable.ic_menu_gallery);
                }
            } else {
                holder.iconImageView.setImageResource(android.R.drawable.ic_menu_gallery);
            }

            holder.backupButton.setOnClickListener(v -> createBackup(game.id, game.title, game.appVer, game.version));

            // Listener de clique longo para exibir o diálogo
            holder.itemView.setOnLongClickListener(v -> {
                showGameDetailsDialog(game);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return games.size();
        }

        // Função para determinar a região com base no GameId
        private String getRegionFromGameId(String gameId) {
            if (gameId == null || gameId.length() < 4) return "Desconhecida";
            String prefix = gameId.substring(0, 4).toUpperCase();
switch (prefix) {
    case "BLUS":
    case "BCUS":
    case "NPUB":
        return "América do Norte (USA)";
    case "BLES":
    case "BCES":
    case "NPEB":
        return "Europa";
    case "BLJS":
    case "BCJS":
    case "NPJH":
        return "Japão";
    case "BLAS":
    case "BCAS":
    case "NPHB":
        return "Ásia";
    case "BLKS":
    case "BCKS":
    case "NPKS":
        return "Coreia do Sul";
    default:
        return "Desconhecida";
       }
}

        // Função para exibir o diálogo com informações detalhadas
        private void showGameDetailsDialog(GameItem game) {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
            builder.setTitle(game.title);

            // Construir o texto com as informações detalhadas
            String details = "Nome: " + game.title + "\n" +
                    "App Version: " + game.appVer + "\n" +
                    "Version: " + game.version + "\n" +
                    "GameId: " + game.id + "\n" +
                    "Region: " + getRegionFromGameId(game.id);

            builder.setMessage(details);

            // Verificar se o backup existe
            File backupDir = new File(Environment.getExternalStorageDirectory(), "INSTALL PPU RPCS3/BACKUP");
            String safeGameTitle = game.title.replaceAll("[\\\\/:*?\"<>|]", "_");
            File backupFile = new File(backupDir, safeGameTitle + ".zip");
            boolean backupExists = backupFile.exists();

            if (backupExists) {
                // Botão Compartilhar usando FileProvider
                builder.setPositiveButton("Compartilhar", (dialog, which) -> {
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("application/zip");

                    // Usar FileProvider para gerar o URI seguro
                    Uri fileUri = FileProvider.getUriForFile(context, "com.my.newproject118.fileprovider", backupFile);
                    shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
                    shareIntent.putExtra(Intent.EXTRA_TEXT, details);
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); // Conceder permissão ao app receptor
                    context.startActivity(Intent.createChooser(shareIntent, "Compartilhar backup de " + game.title));
                });

                // Botão Excluir
                builder.setNegativeButton("Excluir", (dialog, which) -> {
                    if (backupFile.delete()) {
                        Toast.makeText(context, "Backup excluído com sucesso!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(context, "Erro ao excluir o backup.", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            // Botão Fechar
            builder.setNeutralButton("Fechar", null);

            builder.show();
        }

        class GameViewHolder extends RecyclerView.ViewHolder {
            MaterialTextView gameTitleTextView;
            MaterialTextView appVerTextView;
            MaterialTextView versionTextView;
            ImageView iconImageView;
            MaterialButton backupButton;

            GameViewHolder(@NonNull View itemView) {
                super(itemView);
                gameTitleTextView = itemView.findViewById(R.id.gameTitleTextView);
                appVerTextView = itemView.findViewById(R.id.appVerTextView);
                versionTextView = itemView.findViewById(R.id.versionTextView);
                iconImageView = itemView.findViewById(R.id.iconImageView);
                backupButton = itemView.findViewById(R.id.backupButton);
            }
        }
    }

    private void createBackup(String gameId, String gameTitle, String appVer, String version) {
        if (!isFinishing()) {
            View dialogView = LayoutInflater.from(this).inflate(R.layout.progress_dialog_layout, null);
            progressBar = dialogView.findViewById(R.id.progressBar);
            progressMessage = dialogView.findViewById(R.id.progressMessage);

            progressMessage.setText("Compactando " + gameTitle + "...");
            progressBar.setMax(100);
            progressBar.setProgress(0);

            progressDialog = new MaterialAlertDialogBuilder(this)
                    .setView(dialogView)
                    .setCancelable(false)
                    .create();
            progressDialog.show();
        }

        Intent serviceIntent = new Intent(this, BackupService.class);
        serviceIntent.putExtra("gameId", gameId);
        serviceIntent.putExtra("gameTitle", gameTitle);
        serviceIntent.putExtra("appVer", appVer);
        serviceIntent.putExtra("version", version);
        startForegroundService(serviceIntent);
    }
}