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
import android.os.IBinder; // Added this import
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.core.app.NotificationCompat;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedList;
import java.util.Queue;

public class DownloadService extends Service {
    private static final String CHANNEL_ID = "DownloadChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final int COMPLETED_NOTIFICATION_ID = 2;
    private NotificationManager notificationManager;
    private Queue<DownloadTask> downloadQueue = new LinkedList<>();
    private boolean isDownloading = false;
    private NotificationCompat.Builder builder;

    public static final String ACTION_ADD_DOWNLOAD = "ACTION_ADD_DOWNLOAD";
    public static final String EXTRA_URL = "EXTRA_URL";
    public static final String EXTRA_TITLE = "EXTRA_TITLE";
    public static final String EXTRA_GAME_ID = "EXTRA_GAME_ID";
    public static final String ACTION_DOWNLOAD_PROGRESS = "ACTION_DOWNLOAD_PROGRESS";
    public static final String ACTION_DOWNLOAD_COMPLETE = "ACTION_DOWNLOAD_COMPLETE";
    public static final String EXTRA_PROGRESS = "EXTRA_PROGRESS";

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, getNotification(0, "Initializing download..."));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_ADD_DOWNLOAD.equals(intent.getAction())) {
            String url = intent.getStringExtra(EXTRA_URL);
            String title = intent.getStringExtra(EXTRA_TITLE);
            String gameId = intent.getStringExtra(EXTRA_GAME_ID);
            addToQueue(url, title, gameId);
        }
        return START_STICKY;
    }

    private void addToQueue(String url, String title, String gameId) {
        downloadQueue.offer(new DownloadTask(url, title, gameId));
        if (!isDownloading) {
            processNextDownload();
        }
    }

    private void processNextDownload() {
        DownloadTask task = downloadQueue.poll();
        if (task != null) {
            isDownloading = true;
            new Thread(() -> downloadFile(task)).start();
        } else {
            stopForeground(true);
            stopSelf();
        }
    }

    private void downloadFile(DownloadTask task) {
        try {
            URL url = new URL(task.url);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.connect();

            int fileLength = connection.getContentLength();
            InputStream input = connection.getInputStream();
            File directory = new File(Environment.getExternalStorageDirectory(), "INSTALL PPU RPCS3");
            if (!directory.exists()) directory.mkdirs();
            File file = new File(directory, task.title + ".zip");
            FileOutputStream output = new FileOutputStream(file);

            byte[] buffer = new byte[1024];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                output.write(buffer, 0, count);
                int progress = (fileLength > 0) ? (int) (total * 100 / fileLength) : 0;
                updateNotification(progress, "Downloading " + task.title);
                sendProgressUpdate(task.gameId, progress);
            }

            output.close();
            input.close();
            connection.disconnect();

            getSharedPreferences("DownloadPrefs", MODE_PRIVATE)
                    .edit()
                    .putBoolean(task.gameId + "_downloaded", true)
                    .apply();

            showDownloadCompletedNotification(task.title);
            sendDownloadComplete(task.gameId);

        } catch (Exception e) {
            e.printStackTrace();
            updateNotification(0, "Download failed: " + task.title);
        } finally {
            isDownloading = false;
            processNextDownload();
        }
    }

    private void sendProgressUpdate(String gameId, int progress) {
        Intent intent = new Intent(ACTION_DOWNLOAD_PROGRESS);
        intent.putExtra(EXTRA_GAME_ID, gameId);
        intent.putExtra(EXTRA_PROGRESS, progress);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void sendDownloadComplete(String gameId) {
        Intent intent = new Intent(ACTION_DOWNLOAD_COMPLETE);
        intent.putExtra(EXTRA_GAME_ID, gameId);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void updateNotification(int progress, String message) {
        notificationManager.notify(NOTIFICATION_ID, getNotification(progress, message));
    }

    private Notification getNotification(int progress, String message) {
        Intent intent = new Intent(this, CommunityActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Game Download")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .setOngoing(true);

        if (progress > 0) {
            builder.setProgress(100, progress, false);
        }
        return builder.build();
    }

    private void showDownloadCompletedNotification(String title) {
        Intent intent = new Intent(this, CommunityActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder completedBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Download Concluído")
                .setContentText("O arquivo " + title + " foi baixado com sucesso")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        notificationManager.notify(COMPLETED_NOTIFICATION_ID, completedBuilder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "Download Service",
                    NotificationManager.IMPORTANCE_LOW);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static class DownloadTask {
        String url;
        String title;
        String gameId;

        DownloadTask(String url, String title, String gameId) {
            this.url = url;
            this.title = title;
            this.gameId = gameId;
        }
    }
}