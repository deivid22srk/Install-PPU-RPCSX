package com.my.newproject118;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.documentfile.provider.DocumentFile;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class FastZipExtractor {
    public static final String ACTION_PROGRESS_UPDATE = "com.my.newproject118.PROGRESS_UPDATE";
    public static final String ACTION_EXTRACTION_COMPLETE = "com.my.newproject118.EXTRACTION_COMPLETE";
    public static final String ACTION_EXTRACTION_ERROR = "com.my.newproject118.EXTRACTION_ERROR";
    public static final String EXTRA_CURRENT = "current";
    public static final String EXTRA_TOTAL = "total";
    public static final String EXTRA_MOD_ID = "mod_id";
    public static final String EXTRA_ERROR = "error";

    private static final String TAG = "FastZipExtractor";
    private static final int BASE_BUFFER_SIZE = 8 * 1024 * 1024;
    private static final String TEMP_FILE_NAME = "temp_extract.zip";
    private static final String CHANNEL_ID = "extraction_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final int SUCCESS_NOTIFICATION_ID = 1002;
    private static final int PARALLELISM_LEVEL = Runtime.getRuntime().availableProcessors() * 2;

    private final Context context;
    private final ExecutorService extractionExecutor = Executors.newFixedThreadPool(PARALLELISM_LEVEL);
    private final ExecutorService progressExecutor = Executors.newSingleThreadExecutor();

    public static class FileEntry {
        public String source;
        public String target;

        public FileEntry(String source, String target) {
            this.source = source;
            this.target = target;
        }
    }

    public interface Callback {
        void onJsonProcessed(String type, String description, List<FileEntry> files);
        void onError(String error);
    }

    public interface ProgressCallback {
        void onProgressUpdate(int current, int total);
    }

    public FastZipExtractor(Context context) {
        this.context = context;
    }

    public void processFile(InputStream inputStream, String filePathOrUri, Callback callback) {
        try {
            File tempFile = createTempFile(inputStream);
            ZipFile zipFile = new ZipFile(tempFile);
            
            FileHeader profileJson = zipFile.getFileHeader("profile.json");
            if (profileJson == null) {
                callback.onError("Nenhum profile.json encontrado no arquivo.");
                tempFile.delete();
                return;
            }

            String text = new String(zipFile.getInputStream(profileJson).readAllBytes());
            JSONObject json = new JSONObject(text);
            String type = json.getString("type");
            String description = json.getString("description");
            JSONArray filesArray = json.getJSONArray("files");

            List<FileEntry> fileEntries = new ArrayList<>();
            for (int i = 0; i < filesArray.length(); i++) {
                JSONObject fileObj = filesArray.getJSONObject(i);
                fileEntries.add(new FileEntry(fileObj.getString("source"), fileObj.getString("target")));
            }

            callback.onJsonProcessed(type, description, fileEntries);
            tempFile.delete();

        } catch (Exception e) {
            callback.onError("Erro ao processar arquivo: " + e.getMessage());
        }
    }

    private InputStream getInputStreamFromPathOrUri(String filePathOrUri) throws Exception {
        if (filePathOrUri.startsWith("content://") || filePathOrUri.startsWith("file://")) {
            return new BufferedInputStream(context.getContentResolver().openInputStream(Uri.parse(filePathOrUri)), BASE_BUFFER_SIZE);
        } else {
            File file = new File(filePathOrUri);
            if (file.exists() && file.isFile()) {
                return new BufferedInputStream(new FileInputStream(file), BASE_BUFFER_SIZE);
            }
            throw new Exception("Arquivo não encontrado ou inválido: " + filePathOrUri);
        }
    }

    private File createTempFile(InputStream inputStream) throws Exception {
        File tempFile = new File(context.getCacheDir(), TEMP_FILE_NAME);
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[BASE_BUFFER_SIZE];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                fos.write(buffer, 0, len);
            }
        }
        return tempFile;
    }

    public int countFilesToExtract(List<String> files, String filePathOrUri) throws Exception {
        int totalFiles = 0;
        Map<String, Boolean> sourceMap = new ConcurrentHashMap<>();
        for (String file : files) {
            String[] parts = file.split("\\|");
            if (parts.length > 0) {
                sourceMap.put(parts[0], true);
            }
        }
        
        File tempFile = createTempFile(getInputStreamFromPathOrUri(filePathOrUri));
        try (ZipFile zipFile = new ZipFile(tempFile)) {
            List<FileHeader> fileHeaders = zipFile.getFileHeaders();
            for (FileHeader header : fileHeaders) {
                if (shouldCountFile(header.getFileName(), sourceMap, header.isDirectory())) {
                    totalFiles++;
                }
            }
        } finally {
            tempFile.delete();
        }
        return totalFiles;
    }

    private boolean shouldCountFile(String entryName, Map<String, Boolean> sourceMap, boolean isDirectory) {
        if (isDirectory) return false;
        String normalizedEntry = entryName.replace("\\", "/").trim();
        for (String source : sourceMap.keySet()) {
            if (normalizedEntry.startsWith(source.replace("\\", "/").trim() + "/")) {
                return true;
            }
        }
        return false;
    }

    public String extractFilesToTarget(String targetDirUri, List<String> files, InputStream inputStream,
                                     String filePathOrUri, ProgressCallback progressCallback) throws Exception {
        DocumentFile targetDir = DocumentFile.fromTreeUri(context, Uri.parse(targetDirUri));
        if (targetDir == null || !targetDir.isDirectory()) {
            throw new Exception("Diretório de destino inválido: " + targetDirUri);
        }

        String modId = UUID.randomUUID().toString();
        List<String> extractedFiles = new ArrayList<>();
        Map<String, String> fileMap = new ConcurrentHashMap<>();
        for (String file : files) {
            String[] parts = file.split("\\|");
            if (parts.length >= 2) {
                fileMap.put(parts[0], parts[1]);
            }
        }
        AtomicInteger extractedCount = new AtomicInteger(0);
        int totalFiles = countFilesToExtract(files, filePathOrUri);
        Map<String, DocumentFile> directoryCache = new ConcurrentHashMap<>();
        File tempFile = createTempFile(inputStream);

        try {
            List<FileHeader> fileHeaders = new ZipFile(tempFile).getFileHeaders();
            preCreateDirectories(fileHeaders, fileMap, targetDir, directoryCache);

            int batchSize = Math.max(1, fileHeaders.size() / PARALLELISM_LEVEL);
            List<CompletableFuture<Void>> extractionTasks = new ArrayList<>();

            for (int i = 0; i < fileHeaders.size(); i += batchSize) {
                int endIndex = Math.min(i + batchSize, fileHeaders.size());
                List<FileHeader> batch = fileHeaders.subList(i, endIndex);
                final int batchIndex = i / batchSize;

                CompletableFuture<Void> task = CompletableFuture.runAsync(() -> {
                    try (ZipFile zipFile = new ZipFile(tempFile)) { // Nova instância por batch
                        Log.d(TAG, "Iniciando batch " + batchIndex + " com " + batch.size() + " arquivos");
                        for (FileHeader header : batch) {
                            int count = processEntry(header, zipFile, fileMap, targetDir,
                                    extractedFiles, totalFiles, progressCallback, extractedCount, directoryCache);
                            if (count > 0) {
                                extractedCount.addAndGet(count);
                            }
                        }
                        Log.d(TAG, "Batch " + batchIndex + " concluído");
                    } catch (Exception e) {
                        Log.e(TAG, "Erro no batch " + batchIndex + ": " + e.getMessage());
                    }
                }, extractionExecutor);
                extractionTasks.add(task);
            }

            CompletableFuture.allOf(extractionTasks.toArray(new CompletableFuture[0])).join();
        } finally {
            tempFile.delete();
        }

        if (extractedCount.get() == 0) {
            throw new Exception("Nenhum arquivo foi extraído");
        }

        saveExtractedFiles(modId, extractedFiles, targetDirUri);
        return modId;
    }

    private void preCreateDirectories(List<FileHeader> fileHeaders, Map<String, String> fileMap,
                                    DocumentFile targetDir, Map<String, DocumentFile> directoryCache) {
        for (FileHeader header : fileHeaders) {
            String entryName = header.getFileName();
            String normalizedEntry = entryName.replace("\\", "/").trim();
            if (header.isDirectory()) continue;

            for (String source : fileMap.keySet()) {
                String normalizedSource = source.replace("\\", "/").trim();
                if (normalizedEntry.startsWith(normalizedSource + "/")) {
                    String relativePath = normalizedEntry.substring(normalizedSource.length() + 1);
                    String subPath = relativePath.contains("/") ? relativePath.substring(0, relativePath.lastIndexOf("/")) : "";
                    if (!subPath.isEmpty() && !directoryCache.containsKey(subPath)) {
                        DocumentFile currentDir = targetDir;
                        String[] pathParts = subPath.split("/");
                        StringBuilder currentPath = new StringBuilder();
                        for (String part : pathParts) {
                            if (part.isEmpty()) continue;
                            currentPath.append("/").append(part);
                            String pathKey = currentPath.toString();
                            if (!directoryCache.containsKey(pathKey)) {
                                DocumentFile subDir = currentDir.findFile(part);
                                if (subDir == null || !subDir.isDirectory()) {
                                    subDir = currentDir.createDirectory(part);
                                }
                                if (subDir != null) {
                                    directoryCache.put(pathKey, subDir);
                                    currentDir = subDir;
                                }
                            } else {
                                currentDir = directoryCache.get(pathKey);
                            }
                        }
                    }
                }
            }
        }
    }

    private int processEntry(FileHeader header, ZipFile zipFile, Map<String, String> fileMap,
                           DocumentFile targetDir, List<String> extractedFiles, int totalFiles,
                           ProgressCallback progressCallback, AtomicInteger extractedCount,
                           Map<String, DocumentFile> directoryCache) throws Exception {
        String entryName = header.getFileName();
        String normalizedEntry = entryName.replace("\\", "/").trim();
        
        if (header.isDirectory()) return 0;

        for (Map.Entry<String, String> entry : fileMap.entrySet()) {
            String source = entry.getKey();
            String normalizedSource = source.replace("\\", "/").trim();

            if (normalizedEntry.startsWith(normalizedSource + "/")) {
                String relativePath = normalizedEntry.substring(normalizedSource.length() + 1);
                String subPath = relativePath.contains("/") ? relativePath.substring(0, relativePath.lastIndexOf("/")) : "";
                DocumentFile currentDir = subPath.isEmpty() ? targetDir : directoryCache.get("/" + subPath);
                if (currentDir == null) currentDir = targetDir;
                String fileName = getFileName(relativePath);

                int bufferSize = Math.min(BASE_BUFFER_SIZE, (int) Math.max(1024, header.getUncompressedSize() / PARALLELISM_LEVEL));
                if (createFileFromZip(header, zipFile, currentDir, fileName, bufferSize)) {
                    synchronized (extractedFiles) {
                        extractedFiles.add(entryName + "|" + entry.getValue());
                    }
                    updateProgress(progressCallback, extractedCount.get() + 1, totalFiles);
                    return 1;
                }
            }
        }
        return 0;
    }

    private String getFileName(String relativePath) {
        return relativePath.contains("/") 
            ? relativePath.substring(relativePath.lastIndexOf("/") + 1) 
            : relativePath;
    }

    private boolean createFileFromZip(FileHeader header, ZipFile zipFile, DocumentFile dir, String fileName, int bufferSize) throws Exception {
        DocumentFile newFile;
        synchronized (dir) {
            DocumentFile existingFile = dir.findFile(fileName);
            if (existingFile != null && existingFile.exists()) {
                existingFile.delete();
            }
            newFile = dir.createFile("application/octet-stream", fileName);
        }

        if (newFile == null) {
            Log.e(TAG, "Falha ao criar arquivo: " + fileName);
            return false;
        }

        try (InputStream in = new BufferedInputStream(zipFile.getInputStream(header), bufferSize);
             OutputStream out = new BufferedOutputStream(
                context.getContentResolver().openOutputStream(newFile.getUri()), bufferSize)) {
            byte[] buffer = new byte[bufferSize];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            return true;
        }
    }

    private void updateProgress(ProgressCallback callback, int current, int total) {
        if (callback != null) {
            progressExecutor.execute(() -> callback.onProgressUpdate(current, total));
        }
    }

    private void saveExtractedFiles(String modId, List<String> extractedFiles, String targetDirUri) {
        SharedPreferences prefs = context.getSharedPreferences("InstalledMods", Context.MODE_PRIVATE);
        prefs.edit()
                .putString(modId + "_files", String.join(",", extractedFiles))
                .putString(modId + "_targetDir", targetDirUri)
                .apply();
    }

    public boolean removeMod(String modId) {
        SharedPreferences prefs = context.getSharedPreferences("InstalledMods", Context.MODE_PRIVATE);
        String targetDirUri = prefs.getString(modId + "_targetDir", null);
        String filesString = prefs.getString(modId + "_files", null);

        if (targetDirUri == null || filesString == null) return false;

        DocumentFile targetDir = DocumentFile.fromTreeUri(context, Uri.parse(targetDirUri));
        if (targetDir == null || !targetDir.exists() || !targetDir.isDirectory() || !targetDir.canWrite()) return false;

        boolean atLeastOneDeleted = false;
        String[] files = filesString.split(",");
        for (String file : files) {
            String[] parts = file.split("\\|");
            String entryName = parts[0];
            String relativePath = entryName.startsWith("PPU/") ? entryName.substring(4) : entryName;
            String[] relativeParts = relativePath.split("/");

            DocumentFile currentDir = targetDir;
            for (int i = 0; i < relativeParts.length; i++) {
                if (relativeParts[i].isEmpty()) continue;
                if (i == relativeParts.length - 1) {
                    DocumentFile fileToDelete = currentDir.findFile(relativeParts[i]);
                    if (fileToDelete != null && fileToDelete.exists() && fileToDelete.delete()) {
                        atLeastOneDeleted = true;
                    }
                } else {
                    DocumentFile subDir = currentDir.findFile(relativeParts[i]);
                    if (subDir != null && subDir.isDirectory()) {
                        currentDir = subDir;
                    } else {
                        break;
                    }
                }
            }
        }

        if (atLeastOneDeleted) {
            prefs.edit()
                    .remove(modId + "_type")
                    .remove(modId + "_description")
                    .remove(modId + "_targetDir")
                    .remove(modId + "_files")
                    .apply();
            return true;
        }
        return false;
    }

    public void startExtractionWithForegroundService(String targetDirUri, ArrayList<String> files, String filePathOrUri) {
        Intent serviceIntent = new Intent(context, ExtractionService.class);
        serviceIntent.putExtra("targetDirUri", targetDirUri);
        serviceIntent.putStringArrayListExtra("files", files);
        serviceIntent.putExtra("filePathOrUri", filePathOrUri);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }

    public void shutdown() {
        extractionExecutor.shutdown();
        progressExecutor.shutdown();
    }

    public static class ExtractionService extends Service {
        private NotificationManager notificationManager;
        private NotificationCompat.Builder notificationBuilder;
        private boolean isRunning = false;
        private FastZipExtractor extractor;

        @Override
        public void onCreate() {
            super.onCreate();
            createNotificationChannel();
            notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            extractor = new FastZipExtractor(this);
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            if (isRunning) return START_NOT_STICKY;

            isRunning = true;
            final String targetDirUri = intent.getStringExtra("targetDirUri");
            final ArrayList<String> files = intent.getStringArrayListExtra("files");
            final String filePathOrUri = intent.getStringExtra("filePathOrUri");

            Notification notification = buildInitialNotification();
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }

            new Thread(() -> {
                try {
                    String modId = extractor.extractFilesToTarget(
                            targetDirUri,
                            files,
                            extractor.getInputStreamFromPathOrUri(filePathOrUri),
                            filePathOrUri,
                            (current, total) -> {
                                updateNotification(current, total);
                                Intent progressIntent = new Intent(ACTION_PROGRESS_UPDATE);
                                progressIntent.putExtra(EXTRA_CURRENT, current);
                                progressIntent.putExtra(EXTRA_TOTAL, total);
                                sendBroadcast(progressIntent);
                            }
                    );
                    updateNotificationComplete();
                    showSuccessNotification();

                    Intent broadcastIntent = new Intent(ACTION_EXTRACTION_COMPLETE);
                    broadcastIntent.putExtra(EXTRA_MOD_ID, modId);
                    sendBroadcast(broadcastIntent);
                } catch (Exception e) {
                    updateNotificationError(e.getMessage());
                    Intent broadcastIntent = new Intent(ACTION_EXTRACTION_ERROR);
                    broadcastIntent.putExtra(EXTRA_ERROR, e.getMessage());
                    sendBroadcast(broadcastIntent);
                } finally {
                    stopForeground(true);
                    stopSelf();
                    isRunning = false;
                }
            }).start();

            return START_NOT_STICKY;
        }

        private void createNotificationChannel() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "Extração de Arquivos",
                        NotificationManager.IMPORTANCE_LOW
                );
                channel.setDescription("Mostra o progresso da extração de arquivos");
                NotificationManager manager = getSystemService(NotificationManager.class);
                manager.createNotificationChannel(channel);
            }
        }

        private Notification buildInitialNotification() {
            notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Extraindo arquivos...")
                    .setContentText("Preparando para extrair")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOnlyAlertOnce(true)
                    .setOngoing(true)
                    .setProgress(100, 0, false);
            return notificationBuilder.build();
        }

        private void updateNotification(int current, int total) {
            int progress = (int) ((current / (float) total) * 100);
            notificationBuilder
                    .setContentText("Progresso: " + current + "/" + total)
                    .setProgress(100, progress, false);
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
        }

        private void updateNotificationComplete() {
            notificationBuilder
                    .setContentText("Extração concluída")
                    .setProgress(0, 0, false)
                    .setOngoing(false);
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
        }

        private void updateNotificationError(String error) {
            notificationBuilder
                    .setContentText("Erro: " + error)
                    .setProgress(0, 0, false)
                    .setOngoing(false);
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
        }

        private void showSuccessNotification() {
            NotificationCompat.Builder successBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Extração concluída")
                    .setContentText("Arquivos extraídos com sucesso!")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true);
            notificationManager.notify(SUCCESS_NOTIFICATION_ID, successBuilder.build());
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            extractor.shutdown();
        }

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }
    }
}