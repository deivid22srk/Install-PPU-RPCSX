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
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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

            startForeground(NOTIFICATION_ID, buildProgressNotification(0));
            new Thread(() -> performBackup()).start();
        }
        return START_NOT_STICKY;
    }

    private void performBackup() {
        boolean success = false;
        try {
            File backupDir = new File(Environment.getExternalStorageDirectory(), "INSTALL PPU RPCS3/BACKUP");
            if (!backupDir.exists()) {
                if (!backupDir.mkdirs()) {
                    Log.e(TAG, "Falha ao criar diretório de backup");
                    sendBackupResult(false);
                    stopSelf();
                    return;
                }
            }

            String safeGameTitle = gameTitle.replaceAll("[\\\\/:*?\"<>|]", "_");
            zipFile = new File(backupDir, safeGameTitle + ".zip");
            Log.d(TAG, "Criando backup em: " + zipFile.getAbsolutePath());

            try (FileOutputStream fos = new FileOutputStream(zipFile);
                 ZipOutputStream zipOut = new ZipOutputStream(fos)) {

                DocumentFile rootFolder = DocumentFile.fromTreeUri(this, GameListLoader.selectedFolderUri);
                DocumentFile ppuFolder = navigateToPpuFolder(rootFolder);
                int totalFiles = countFiles(ppuFolder);
                int processedFiles = 0;

                if (ppuFolder != null && ppuFolder.exists()) {
                    Log.d(TAG, "Pasta PPU encontrada: " + ppuFolder.getUri());
                    processedFiles = zipFolder(ppuFolder, "PPU/cache/cache/" + gameId, zipOut, processedFiles, totalFiles);
                } else {
                    Log.w(TAG, "Pasta cache/cache/" + gameId + " não encontrada");
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
            if (zipFile != null && zipFile.exists()) {
                zipFile.delete();
            }
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

    private DocumentFile navigateToPpuFolder(DocumentFile rootFolder) {
        String[] path = {"cache", "cache", gameId};
        DocumentFile current = rootFolder;
        for (String folderName : path) {
            current = findSubFolder(current, folderName);
            if (current == null) {
                Log.w(TAG, "Parou no caminho: " + folderName + " não encontrado");
                return null;
            }
        }
        return current;
    }

    private DocumentFile findSubFolder(DocumentFile parent, String folderName) {
        if (parent == null) return null;
        DocumentFile[] files = parent.listFiles();
        if (files == null) return null;
        for (DocumentFile file : files) {
            if (file.isDirectory() && file.getName() != null && file.getName().equalsIgnoreCase(folderName)) {
                return file;
            }
        }
        return null;
    }

    private int countFiles(DocumentFile folder) {
        int count = 0;
        if (folder != null && folder.exists()) {
            DocumentFile[] files = folder.listFiles();
            if (files != null) {
                for (DocumentFile file : files) {
                    if (file.isDirectory()) {
                        count += countFiles(file);
                    } else {
                        count++;
                    }
                }
            }
        }
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