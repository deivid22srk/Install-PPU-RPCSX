package com.my.newproject118;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.documentfile.provider.DocumentFile;
import java.io.File;
import java.io.FileOutputStream;
import android.net.Uri;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import com.my.newproject118.RPCS3Helper;

public class BackupService extends Service {
    private static final String TAG = "BackupService";
    private static final String CHANNEL_ID = "BackupChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final int COMPLETED_NOTIFICATION_ID = 2;
    public static final String ACTION_UPDATE_PROGRESS = "com.my.newproject118.ACTION_UPDATE_PROGRESS";
    public static final String ACTION_BACKUP_COMPLETE = "com.my.newproject118.ACTION_BACKUP_COMPLETE";
    public static final String EXTRA_PROGRESS = "progress";
    public static final String EXTRA_SUCCESS = "success";

    private NotificationManager notificationManager;
    private String gameTitle;
    private String gameId;
    private String appVer;
    private String version;
    private File zipFile;
    private String backupFolderUri;

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            gameTitle = intent.getStringExtra("gameTitle");
            gameId = intent.getStringExtra("gameId");
            appVer = intent.getStringExtra("appVer");
            version = intent.getStringExtra("version");
            backupFolderUri = intent.getStringExtra("backupFolderUri");

            startForeground(NOTIFICATION_ID, buildProgressNotification(0));
            new Thread(() -> performBackup()).start();
        }
        return START_NOT_STICKY;
    }

    private void performBackup() {
        boolean success = false;
        
        // Validate before starting
        if (!RPCS3Helper.hasGamePPUs(this, gameId)) {
            Log.w(TAG, "Tentativa de backup sem PPUs para: " + gameId);
            sendBackupResult(false);
            stopSelf();
            return;
        }
        
        try {
            DocumentFile backupDir = null;
            
            if (backupFolderUri != null) {
                // Use user selected folder
                android.net.Uri folderUri = android.net.Uri.parse(backupFolderUri);
                backupDir = DocumentFile.fromTreeUri(this, folderUri);
                Log.d(TAG, "Usando pasta selecionada pelo usuário: " + folderUri.toString());
            } else {
                // Fallback to default folder
                File defaultBackupDir = new File(Environment.getExternalStorageDirectory(), "INSTALL PPU RPCS3/BACKUP");
                if (!defaultBackupDir.exists() && !defaultBackupDir.mkdirs()) {
                    Log.e(TAG, "Falha ao criar diretório de backup padrão");
                    sendBackupResult(false);
                    stopSelf();
                    return;
                }
                backupDir = DocumentFile.fromFile(defaultBackupDir);
                Log.d(TAG, "Usando pasta de backup padrão");
            }
            
            if (backupDir == null || !backupDir.exists()) {
                Log.e(TAG, "Pasta de backup não é válida");
                sendBackupResult(false);
                stopSelf();
                return;
            }

            String safeGameTitle = gameTitle.replaceAll("[\\\\/:*?\"<>|]", "_");
            DocumentFile zipDocFile = backupDir.createFile("application/zip", safeGameTitle + ".zip");
            
            if (zipDocFile == null) {
                Log.e(TAG, "Falha ao criar arquivo ZIP na pasta selecionada");
                sendBackupResult(false);
                stopSelf();
                return;
            }
            
            Log.d(TAG, "Criando backup em: " + zipDocFile.getUri().toString());

            try (java.io.OutputStream outputStream = getContentResolver().openOutputStream(zipDocFile.getUri());
                 ZipOutputStream zipOut = new ZipOutputStream(outputStream)) {

                DocumentFile ppuFolder = RPCS3Helper.getGamePPUFolder(this, gameId);
                int totalFiles = countFiles(ppuFolder);
                int processedFiles = 0;

                if (ppuFolder != null && ppuFolder.exists()) {
                    Log.d(TAG, "Pasta PPU encontrada: " + ppuFolder.getUri());
                    DocumentFile[] ppuFiles = ppuFolder.listFiles();
                    if (ppuFiles != null && ppuFiles.length > 0) {
                        Log.d(TAG, "PPU folder contains " + ppuFiles.length + " files/folders");
                        processedFiles = zipFolder(ppuFolder, "PPU/cache/cache/" + gameId, zipOut, processedFiles, totalFiles);
                    } else {
                        Log.w(TAG, "PPU folder is empty: " + ppuFolder.getUri());
                    }
                } else {
                    Log.w(TAG, "Pasta cache/cache/" + gameId + " não encontrada. Verifique se o jogo tem PPUs compiladas.");
                }

                Log.d(TAG, "Adicionando profile.json");
                zipOut.putNextEntry(new ZipEntry("profile.json"));
                String profileJson = createProfileJson();
                zipOut.write(profileJson.getBytes());
                zipOut.closeEntry();
                processedFiles++;
                updateProgress((processedFiles * 100) / totalFiles);

                success = true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Erro no backup: " + e.getMessage());
            e.printStackTrace();
            // Note: DocumentFile cleanup would be handled here if needed
        }
        sendBackupResult(success);
        stopSelf();
    }

    private void updateProgress(int progress) {
        notificationManager.notify(NOTIFICATION_ID, buildProgressNotification(progress));
        Intent intent = new Intent(ACTION_UPDATE_PROGRESS);
        intent.putExtra(EXTRA_PROGRESS, progress);
        sendBroadcast(intent);
    }

    private void sendBackupResult(boolean success) {
        notificationManager.cancel(NOTIFICATION_ID);
        notificationManager.notify(COMPLETED_NOTIFICATION_ID, buildCompletionNotification(success));
        Intent intent = new Intent(ACTION_BACKUP_COMPLETE);
        intent.putExtra(EXTRA_SUCCESS, success);
        sendBroadcast(intent);
    }

    private Notification buildProgressNotification(int progress) {
        Intent intent = new Intent(this, GameListLoader.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Backup em andamento")
                .setContentText("Compactando " + gameTitle + " (" + progress + "%)")
                .setSmallIcon(android.R.drawable.ic_menu_save)
                .setProgress(100, progress, false)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private Notification buildCompletionNotification(boolean success) {
        Intent intent = new Intent(this, GameListLoader.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_save)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        if (success) {
            builder.setContentTitle("Backup concluído")
                   .setContentText("O backup de " + gameTitle + " foi concluído com sucesso.");
        } else {
            builder.setContentTitle("Erro no backup")
                   .setContentText("Falha ao criar o backup de " + gameTitle + ".");
        }

        return builder.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Backup Service", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Canal para notificações de backup");
            notificationManager.createNotificationChannel(channel);
        }
    }

    // Removed navigateToPpuFolder - now using RPCS3Helper.getGamePPUFolder()

    // Removed findSubFolder - now using RPCS3Helper.findSubFolder()

    private int countFiles(DocumentFile folder) {
        int count = 0;
        if (folder != null && folder.exists()) {
            DocumentFile[] files = folder.listFiles();
            if (files != null) {
                Log.d(TAG, "Counting files in " + folder.getName() + ": " + files.length + " items");
                for (DocumentFile file : files) {
                    if (file.isDirectory()) {
                        count += countFiles(file);
                    } else {
                        count++;
                    }
                }
            } else {
                Log.w(TAG, "No files found in " + folder.getName());
            }
        }
        Log.d(TAG, "Total file count: " + count + " + 1 (profile.json)");
        return count + 1; // +1 para profile.json
    }

    private int zipFolder(DocumentFile folder, String zipEntryName, ZipOutputStream zipOut, int processedFiles, int totalFiles) throws IOException {
        DocumentFile[] files = folder.listFiles();
        if (files == null || files.length == 0) return processedFiles;

        for (DocumentFile file : files) {
            String entryName = zipEntryName + "/" + file.getName();
            if (file.isDirectory()) {
                processedFiles = zipFolder(file, entryName, zipOut, processedFiles, totalFiles);
            } else {
                zipOut.putNextEntry(new ZipEntry(entryName));
                try (InputStream inputStream = getContentResolver().openInputStream(file.getUri())) {
                    if (inputStream != null) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = inputStream.read(buffer)) > 0) {
                            zipOut.write(buffer, 0, len);
                        }
                    }
                }
                zipOut.closeEntry();
                processedFiles++;
                updateProgress((processedFiles * 100) / totalFiles);
            }
        }
        return processedFiles;
    }

    private String createProfileJson() {
        return "{\n" +
                "  \"type\": \"" + gameTitle + "\",\n" +
                "  \"versionName\": \"" + appVer + "\",\n" +
                "  \"versionCode\": \"" + version + "\",\n" +
                "  \"description\": \"GAME: " + gameId + "\",\n" +
                "  \"files\": [\n" +
                "    {\n" +
                "      \"source\": \"PPU\",\n" +
                "      \"target\": \"cache/cache/" + gameId + "\"\n" +
                "    }\n" +
                "  ]\n" +
                "}";
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}