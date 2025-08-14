package com.my.newproject118;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class PS3Downloader extends AppCompatActivity {
    
    private static final String TAG = "PS3Downloader";
    private static final String CHANNEL_ID = "PS3_DOWNLOAD_CHANNEL";
    private static final String LOGIN_URL = "https://archive.org/account/login";
    private static final String SUCCESS_URL = "https://archive.org/";
    private static final String DOWNLOAD_PATH = "/storage/emulated/0/INSTALL PPU RPCSX/Download/";
    private static final String ACTION_DOWNLOAD_COMPLETE = "com.my.newproject118.DOWNLOAD_COMPLETE";
    private static final String ACTION_DOWNLOAD_UPDATE = "com.my.newproject118.DOWNLOAD_UPDATE";
    private static final String PREFS_NAME = "DownloadPrefs";
    private static final String PREF_LOGGED_IN = "isLoggedIn";
    
    private static final Map<String, String> METADATA_URLS = new HashMap<>();
    private static final Map<String, String> BASE_DOWNLOAD_URLS = new HashMap<>();
    private static final List<String> ORDERED_SOURCES = Arrays.asList(
        "ps3-redump-roms321com",
        "Part 1 (# - C)",
        "Downloading",
        "Part 2 (C - D)",
        "Part 3 (D - G)",
        "Part 4 (G - J)",
        "Part 6 (M - N)",
        "Part 7 (N - R)",
        "Part 8 (R - S)",
        "Part 9 (S - T)",
        "Part 10 (T)",
        "Part 11 (T - Z)"
    );
    
    static {
        METADATA_URLS.put("ps3-redump-roms321com", "https://archive.org/metadata/ps3-redump-roms321com");
        METADATA_URLS.put("Part 1 (# - C)", "https://archive.org/metadata/PS3_ALVRO_PART_1");
        METADATA_URLS.put("Part 2 (C - D)", "https://archive.org/metadata/PS3_ALVRO_PART_2");
        METADATA_URLS.put("Part 3 (D - G)", "https://archive.org/metadata/PS3_ALVRO_PART_3");
        METADATA_URLS.put("Part 4 (G - J)", "https://archive.org/metadata/PS3_ALVRO_PART_4");
        METADATA_URLS.put("Part 6 (M - N)", "https://archive.org/metadata/PS3_ALVRO_PART_6");
        METADATA_URLS.put("Part 7 (N - R)", "https://archive.org/metadata/PS3_ALVRO_PART_7");
        METADATA_URLS.put("Part 8 (R - S)", "https://archive.org/metadata/PS3_ALVRO_PART_8");
        METADATA_URLS.put("Part 9 (S - T)", "https://archive.org/metadata/PS3_ALVRO_PART_9");
        METADATA_URLS.put("Part 10 (T)", "https://archive.org/metadata/PS3_ALVRO_PART_10");
        METADATA_URLS.put("Part 11 (T - Z)", "https://archive.org/metadata/PS3_ALVRO_PART_11");
        METADATA_URLS.put("Downloading", "");

        BASE_DOWNLOAD_URLS.put("ps3-redump-roms321com", "https://archive.org/download/ps3-redump-roms321com/all/");
        BASE_DOWNLOAD_URLS.put("Part 1 (# - C)", "https://archive.org/download/PS3_ALVRO_PART_1/");
        BASE_DOWNLOAD_URLS.put("Part 2 (C - D)", "https://archive.org/download/PS3_ALVRO_PART_2/");
        BASE_DOWNLOAD_URLS.put("Part 3 (D - G)", "https://archive.org/download/PS3_ALVRO_PART_3/");
        BASE_DOWNLOAD_URLS.put("Part 4 (G - J)", "https://archive.org/download/PS3_ALVRO_PART_4/");
        BASE_DOWNLOAD_URLS.put("Part 6 (M - N)", "https://archive.org/download/PS3_ALVRO_PART_6/");
        BASE_DOWNLOAD_URLS.put("Part 7 (N - R)", "https://archive.org/download/PS3_ALVRO_PART_7/");
        BASE_DOWNLOAD_URLS.put("Part 8 (R - S)", "https://archive.org/download/PS3_ALVRO_PART_8/");
        BASE_DOWNLOAD_URLS.put("Part 9 (S - T)", "https://archive.org/download/PS3_ALVRO_PART_9/");
        BASE_DOWNLOAD_URLS.put("Part 10 (T)", "https://archive.org/download/PS3_ALVRO_PART_10/");
        BASE_DOWNLOAD_URLS.put("Part 11 (T - Z)", "https://archive.org/download/PS3_ALVRO_PART_11/");
        BASE_DOWNLOAD_URLS.put("Downloading", "");
    }

    private TextInputEditText searchInput;
    private RecyclerView titleList;
    private MaterialButton downloadButton;
    private LinearProgressIndicator progressBar;
    private Spinner sourceSpinner;
    private List<PS3Title> ps3Titles = new ArrayList<>();
    private List<DownloadItem> downloadingItems = new ArrayList<>();
    private TitleAdapter adapter;
    private OkHttpClient client = new OkHttpClient();
    private String selectedSource = "ps3-redump-roms321com";
    private Queue<PS3Title> downloadQueue = new LinkedList<>();
    private BroadcastReceiver downloadReceiver;
    private SharedPreferences prefs;
    private WebView loginWebView;
    private boolean isLoggedIn = false;
    private boolean isDownloading = false;

    public interface OnTitleSelectionListener {
        void onSelectionChanged();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        isLoggedIn = prefs.getBoolean(PREF_LOGGED_IN, false);
        String cookies = CookieManager.getInstance().getCookie(SUCCESS_URL);

        if (isLoggedIn && cookies != null && cookies.contains("logged-in")) {
            loadMainLayout();
        } else {
            loadLoginLayout();
        }
    }

    private void loadLoginLayout() {
        setContentView(R.layout.login_layout);
        loginWebView = findViewById(R.id.loginWebView);

        WebSettings settings = loginWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        loginWebView.setFocusable(true);
        loginWebView.setFocusableInTouchMode(true);

        loginWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (url.startsWith(SUCCESS_URL)) {
                    CookieManager.getInstance().flush();
                    String cookies = CookieManager.getInstance().getCookie(SUCCESS_URL);
                    if (cookies != null && cookies.contains("logged-in")) {
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putBoolean(PREF_LOGGED_IN, true);
                        editor.apply();
                        runOnUiThread(() -> {
                            Toast.makeText(PS3Downloader.this, "Login successful!", Toast.LENGTH_SHORT).show();
                            loadMainLayout();
                        });
                    }
                }
            }
        });

        loginWebView.loadUrl(LOGIN_URL);

        loginWebView.requestFocus();
        loginWebView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                loginWebView.evaluateJavascript(
                    "document.activeElement.tagName === 'INPUT' || document.activeElement.tagName === 'TEXTAREA'",
                    value -> {
                        if ("true".equals(value)) {
                            v.requestFocus();
                            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                            imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT);
                        }
                    });
            }
            return false;
        });
    }

    private void loadMainLayout() {
        setContentView(R.layout.ps3_download_layout);

        searchInput = findViewById(R.id.searchInput);
        titleList = findViewById(R.id.titleList);
        downloadButton = findViewById(R.id.downloadButton);
        progressBar = findViewById(R.id.progressBar);
        sourceSpinner = findViewById(R.id.sourceSpinner);

        titleList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TitleAdapter(ps3Titles, downloadingItems, this::onTitleSelectionChanged);
        titleList.setAdapter(adapter);

        loadDownloadingItems();

        File downloadDir = new File(DOWNLOAD_PATH);
        if (!downloadDir.exists()) {
            downloadDir.mkdirs();
        }

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, 
            android.R.layout.simple_spinner_item, ORDERED_SOURCES);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sourceSpinner.setAdapter(spinnerAdapter);
        sourceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedSource = (String) parent.getItemAtPosition(position);
                if ("Downloading".equals(selectedSource)) {
                    adapter.setModeDownloading(true);
                    adapter.notifyDataSetChanged();
                    downloadButton.setVisibility(View.GONE);
                    searchInput.setVisibility(View.GONE);
                } else {
                    adapter.setModeDownloading(false);
                    loadTitlesAsync();
                    downloadButton.setVisibility(View.VISIBLE);
                    searchInput.setVisibility(View.VISIBLE);
                    searchInput.setText("");
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        downloadButton.setOnClickListener(v -> startDownloadQueue());

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterTitles(s.toString());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        createNotificationChannel();
        setupDownloadReceiver();
        loadTitlesAsync();
    }

    private void loadDownloadingItems() {
        downloadingItems.clear();
        Set<String> keys = prefs.getStringSet("downloading_urls", new HashSet<>());
        for (String url : keys) {
            String fileName = prefs.getString(url + "_fileName", "");
            String size = prefs.getString(url + "_size", "0 B");
            long downloaded = prefs.getLong(url + "_downloaded", 0);
            long totalSize = prefs.getLong(url + "_totalSize", 0);
            boolean paused = prefs.getBoolean(url + "_paused", true);
            boolean completed = prefs.getBoolean(url + "_completed", false);
            downloadingItems.add(new DownloadItem(url, fileName, size, downloaded, totalSize, paused, completed));
        }
    }

    private void saveDownloadState(DownloadItem item) {
        SharedPreferences.Editor editor = prefs.edit();
        Set<String> keys = new HashSet<>(prefs.getStringSet("downloading_urls", new HashSet<>()));
        keys.add(item.url);
        editor.putStringSet("downloading_urls", keys);
        editor.putString(item.url + "_fileName", item.fileName);
        editor.putString(item.url + "_size", item.size);
        editor.putLong(item.url + "_downloaded", item.downloaded);
        editor.putLong(item.url + "_totalSize", item.totalSize);
        editor.putBoolean(item.url + "_paused", item.paused);
        editor.putBoolean(item.url + "_completed", item.completed);
        editor.apply();
    }

    private void removeDownloadState(String url) {
        SharedPreferences.Editor editor = prefs.edit();
        Set<String> keys = new HashSet<>(prefs.getStringSet("downloading_urls", new HashSet<>()));
        keys.remove(url);
        editor.putStringSet("downloading_urls", keys);
        editor.remove(url + "_fileName");
        editor.remove(url + "_size");
        editor.remove(url + "_downloaded");
        editor.remove(url + "_totalSize");
        editor.remove(url + "_paused");
        editor.remove(url + "_completed");
        editor.apply();
    }

    private void setupDownloadReceiver() {
        downloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
                    String url = intent.getStringExtra("url");
                    for (DownloadItem item : downloadingItems) {
                        if (item.url.equals(url)) {
                            item.completed = true;
                            item.paused = true;
                            saveDownloadState(item);
                            break;
                        }
                    }
                    isDownloading = false;
                    startNextDownload();
                    adapter.notifyDataSetChanged();
                } else if (ACTION_DOWNLOAD_UPDATE.equals(intent.getAction())) {
                    String url = intent.getStringExtra("url");
                    long downloaded = intent.getLongExtra("downloaded", 0);
                    for (DownloadItem item : downloadingItems) {
                        if (item.url.equals(url)) {
                            item.downloaded = downloaded;
                            saveDownloadState(item);
                            adapter.notifyDataSetChanged();
                            break;
                        }
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_DOWNLOAD_COMPLETE);
        filter.addAction(ACTION_DOWNLOAD_UPDATE);
        registerReceiver(downloadReceiver, filter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (downloadReceiver != null) {
            unregisterReceiver(downloadReceiver);
        }
    }

    private void loadTitlesAsync() {
        new Thread(() -> {
            try {
                String metadataUrl = METADATA_URLS.get(selectedSource);
                Request request = new Request.Builder().url(metadataUrl).build();
                Response response = client.newCall(request).execute();
                if (!response.isSuccessful()) {
                    throw new IOException("Unexpected code " + response.code());
                }
                String jsonString = response.body().string();

                List<PS3Title> titles = parseTitlesFromJson(jsonString);
                runOnUiThread(() -> {
                    ps3Titles.clear();
                    ps3Titles.addAll(titles);
                    adapter.updateTitles(titles);
                    if (titles.isEmpty()) {
                        Toast.makeText(this, "No titles found for " + selectedSource, Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (IOException e) {
                runOnUiThread(() -> Toast.makeText(this, "Error loading titles: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private List<PS3Title> parseTitlesFromJson(String jsonString) {
        List<PS3Title> titles = new ArrayList<>();
        try {
            JSONObject jsonObject = new JSONObject(jsonString);
            JSONArray filesArray = jsonObject.getJSONArray("files");
            String baseDownloadUrl = BASE_DOWNLOAD_URLS.get(selectedSource);
            boolean isAll = "ps3-redump-roms321com".equals(selectedSource);

            for (int i = 0; i < filesArray.length(); i++) {
                JSONObject file = filesArray.getJSONObject(i);
                String name = file.optString("name", null);
                String size = file.optString("size", null);
                String format = file.optString("format", null);

                if (name != null && size != null && format != null && ("7z".equalsIgnoreCase(format) || "rar".equalsIgnoreCase(format))) {
                    String fileName = isAll ? name.substring(4) : name;
                    if (isAll && !name.startsWith("all/")) continue;
                    if (!isAll && name.contains("/")) continue;

                    String encodedFileName = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");
                    String downloadLink = baseDownloadUrl + encodedFileName;
                    String sizeFormatted = formatSize(Long.parseLong(size));
                    titles.add(new PS3Title(fileName, downloadLink, sizeFormatted));
                }
            }
        } catch (Exception e) {
            runOnUiThread(() -> Toast.makeText(this, "Error parsing JSON: " + e.getMessage(), Toast.LENGTH_LONG).show());
        }
        return titles;
    }

    private String formatSize(long bytes) {
        if (bytes >= 1024 * 1024 * 1024) {
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        } else if (bytes >= 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        } else if (bytes >= 1024) {
            return String.format("%.2f KB", bytes / (1024.0));
        } else {
            return bytes + " B";
        }
    }

    private void filterTitles(String search) {
        List<PS3Title> filtered = new ArrayList<>();
        String[] searches = search.toLowerCase().trim().split("\\s+");
        
        for (PS3Title title : ps3Titles) {
            boolean matches = true;
            String titleLower = title.getTitle().toLowerCase();
            for (String s : searches) {
                if (!titleLower.contains(s)) {
                    matches = false;
                    break;
                }
            }
            if (matches) filtered.add(title);
        }
        
        adapter.updateTitles(filtered);
    }

    private void onTitleSelectionChanged() {
        downloadButton.setEnabled(adapter.getSelectedTitles().size() > 0);
    }

    private void startDownloadQueue() {
        downloadQueue.clear();
        downloadQueue.addAll(adapter.getSelectedTitles());
        if (!downloadQueue.isEmpty()) {
            progressBar.setVisibility(View.VISIBLE);
            downloadButton.setEnabled(false);
            startNextDownload();
        }
    }

    private void startNextDownload() {
        if (isDownloading || downloadQueue.isEmpty()) return;
        PS3Title nextTitle = downloadQueue.poll();
        if (nextTitle != null) {
            downloadTitle(nextTitle);
        } else {
            progressBar.setVisibility(View.GONE);
            downloadButton.setEnabled(adapter.getSelectedTitles().size() > 0);
            if (downloadingItems.isEmpty()) {
                Toast.makeText(this, "All downloads completed!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void downloadTitle(PS3Title title) {
        String sanitizedTitle = title.getTitle().replaceAll("[^a-zA-Z0-9\\-_\\. ]", "").trim();
        if (sanitizedTitle.length() > 50) sanitizedTitle = sanitizedTitle.substring(0, 50);
        String fullUrl = title.getLink();
        
        String fileName = sanitizedTitle.endsWith(".rar") || sanitizedTitle.endsWith(".7z") ? 
            sanitizedTitle : sanitizedTitle + (fullUrl.endsWith(".rar") ? ".rar" : ".7z");
        
        long totalSize = parseFileSize(title.getSize());

        DownloadItem existingItem = null;
        for (DownloadItem item : downloadingItems) {
            if (item.url.equals(fullUrl)) {
                existingItem = item;
                break;
            }
        }
        if (existingItem == null) {
            DownloadItem item = new DownloadItem(fullUrl, fileName, title.getSize(), 0, totalSize, true, false);
            downloadingItems.add(item);
            saveDownloadState(item);
            adapter.notifyDataSetChanged();
        }

        Intent intent = new Intent(this, DownloadService.class);
        intent.putExtra("url", fullUrl);
        intent.putExtra("fileName", fileName);
        intent.putExtra("size", title.getSize());
        intent.setAction("RESUME");
        isDownloading = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private long parseFileSize(String sizeStr) {
        if (sizeStr == null) return 0;
        sizeStr = sizeStr.trim().toUpperCase().replace(",", ".");
        try {
            if (sizeStr.endsWith("GB")) {
                return (long) (Double.parseDouble(sizeStr.replace("GB", "")) * 1024 * 1024 * 1024);
            } else if (sizeStr.endsWith("MB")) {
                return (long) (Double.parseDouble(sizeStr.replace("MB", "")) * 1024 * 1024);
            } else if (sizeStr.endsWith("KB")) {
                return (long) (Double.parseDouble(sizeStr.replace("KB", "")) * 1024);
            }
            return 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "PS3 Download Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    public static class DownloadService extends Service {
        private static final int NOTIFICATION_ID = 1;
        private static final int COMPLETED_NOTIFICATION_ID_BASE = 1000;
        private static final int BUFFER_SIZE = 64 * 1024;
        private static final long UPDATE_INTERVAL_MS = 1000;
        private OkHttpClient client;
        private NotificationManager notificationManager;
        private NotificationCompat.Builder builder;
        private volatile boolean isPaused = false;
        private volatile boolean isCancelled = false;
        private Handler handler = new Handler();
        private String currentUrl;
        private String currentFileName;
        private String currentFileSizeStr;
        private long currentTotalSize;
        private DownloadPartTask currentTask;
        private int completedNotificationIdCounter = 0;
        private long lastUpdateTime = 0;
        private volatile long totalDownloaded = 0;
        private long bytesInLastSecond = 0;
        private Queue<DownloadRequest> downloadQueue = new ConcurrentLinkedQueue<>();

        private static class DownloadRequest {
            String url;
            String fileName;
            String size;

            DownloadRequest(String url, String fileName, String size) {
                this.url = url;
                this.fileName = fileName;
                this.size = size;
            }
        }

        @Override
        public void onCreate() {
            super.onCreate();
            notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    String cookies = CookieManager.getInstance().getCookie(SUCCESS_URL);
                    Request authenticated = original.newBuilder()
                        .header("Cookie", cookies != null ? cookies : "")
                        .build();
                    return chain.proceed(authenticated);
                })
                .build();
            builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setOngoing(true);
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            if (intent == null) return START_NOT_STICKY;

            String action = intent.getAction();
            String url = intent.getStringExtra("url");
            String fileName = intent.getStringExtra("fileName");
            String fileSizeStr = intent.getStringExtra("size");

            Intent notificationIntent = new Intent(this, PS3Downloader.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));

            builder.setContentIntent(pendingIntent);

            if ("PAUSE".equals(action)) {
                pauseDownload();
                return START_NOT_STICKY;
            } else if ("RESUME".equals(action)) {
                downloadQueue.offer(new DownloadRequest(url, fileName, fileSizeStr));
                new StartDownloadTask().execute();
                return START_NOT_STICKY;
            } else if ("CANCEL".equals(action)) {
                new CancelDownloadTask().execute(); // Run cancel in background
                return START_NOT_STICKY;
            }

            downloadQueue.offer(new DownloadRequest(url, fileName, fileSizeStr));
            new StartDownloadTask().execute();

            return START_STICKY;
        }

        private class StartDownloadTask extends AsyncTask<Void, Void, Void> {
            @Override
            protected Void doInBackground(Void... params) {
                startNextDownload();
                return null;
            }
        }

        private class CancelDownloadTask extends AsyncTask<Void, Void, Void> {
            @Override
            protected Void doInBackground(Void... params) {
                cancelDownload();
                return null;
            }
        }

        private void startNextDownload() {
            DownloadRequest request = downloadQueue.poll();
            if (request == null) {
                stopForeground(true);
                stopSelf();
                return;
            }

            currentUrl = request.url;
            currentFileName = request.fileName;
            currentFileSizeStr = request.size;
            currentTotalSize = parseFileSize(currentFileSizeStr);

            builder.setContentTitle("Downloading " + currentFileName)
                .setContentText("Preparing download...");
            startForeground(NOTIFICATION_ID, builder.build());
            startDownload(currentUrl, currentFileName, currentTotalSize);
        }

        private void startDownload(String url, String fileName, long totalSize) {
            long serverSize = getContentLength(url);
            if (serverSize > 0 && serverSize != totalSize) {
                totalSize = serverSize;
                currentTotalSize = totalSize;
            }
            if (totalSize <= 0) {
                notifyFailure("Unable to determine file size");
                return;
            }

            File file = new File(Environment.getExternalStoragePublicDirectory("PS3"), fileName);
            totalDownloaded = file.exists() ? file.length() : 0;
            bytesInLastSecond = 0;
            lastUpdateTime = System.currentTimeMillis();

            currentTask = new DownloadPartTask(url, file, totalSize);
            currentTask.execute();

            handler.removeCallbacks(updateProgressRunnable);
            handler.post(updateProgressRunnable);
        }

        private long getContentLength(String url) {
            try {
                Request request = new Request.Builder()
                    .url(url)
                    .head()
                    .addHeader("User-Agent", "Mozilla/5.0")
                    .build();
                Response response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    String contentLength = response.header("Content-Length");
                    return contentLength != null ? Long.parseLong(contentLength) : -1;
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to get content length: " + e.getMessage());
            }
            return -1;
        }

        private final Runnable updateProgressRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isCancelled && currentTask != null && currentTask.getStatus() != AsyncTask.Status.FINISHED) {
                    long currentTime = System.currentTimeMillis();
                    long timeElapsed = currentTime - lastUpdateTime;

                    if (timeElapsed >= UPDATE_INTERVAL_MS) {
                        int progress = (int) ((totalDownloaded * 100) / currentTotalSize);
                        double speedKBs = (bytesInLastSecond / 1024.0) / (timeElapsed / 1000.0);
                        double speedMBs = speedKBs / 1024.0;

                        String speedText = speedMBs >= 1.0 ?
                            String.format("%.2f MB/s (%.2f KB/s)", speedMBs, speedKBs) :
                            String.format("%.2f KB/s", speedKBs);

                        builder.setContentText("Size: " + formatSize(totalDownloaded) + "/" + formatSize(currentTotalSize) +
                            " | Speed: " + speedText)
                            .setProgress(100, progress, false);
                        notificationManager.notify(NOTIFICATION_ID, builder.build());

                        Intent updateIntent = new Intent(ACTION_DOWNLOAD_UPDATE);
                        updateIntent.putExtra("url", currentUrl);
                        updateIntent.putExtra("downloaded", totalDownloaded);
                        sendBroadcast(updateIntent);

                        bytesInLastSecond = 0; // Reset after update
                        lastUpdateTime = currentTime;
                    }
                    handler.postDelayed(this, UPDATE_INTERVAL_MS);
                }
            }
        };

        private class DownloadPartTask extends AsyncTask<Void, Void, Boolean> {
            private final String url;
            private final File file;
            private final long totalSize;

            DownloadPartTask(String url, File file, long totalSize) {
                this.url = url;
                this.file = file;
                this.totalSize = totalSize;
            }

            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    Request request = new Request.Builder()
                        .url(url)
                        .addHeader("User-Agent", "Mozilla/5.0")
                        .addHeader("Range", "bytes=" + totalDownloaded + "-" + (totalSize - 1))
                        .build();
                    Response response = client.newCall(request).execute();
                    if (!response.isSuccessful()) {
                        Log.e(TAG, "Download failed: HTTP " + response.code());
                        return false;
                    }
                    try (InputStream inputStream = response.body().byteStream();
                         RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
                        raf.seek(totalDownloaded);
                        byte[] buffer = new byte[BUFFER_SIZE];
                        long downloaded = totalDownloaded;

                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1 && !isCancelled) {
                            while (isPaused) {
                                Thread.sleep(500);
                                if (isCancelled) break;
                            }
                            if (isCancelled) break;
                            synchronized (file) {
                                raf.write(buffer, 0, bytesRead);
                                downloaded += bytesRead;
                                synchronized (DownloadService.this) {
                                    totalDownloaded = downloaded;
                                    bytesInLastSecond += bytesRead;
                                }
                            }
                        }
                        return !isCancelled && downloaded >= totalSize;
                    }
                } catch (IOException | InterruptedException e) {
                    Log.e(TAG, "Download error: " + e.getMessage());
                    return false;
                }
            }

            @Override
            protected void onPostExecute(Boolean success) {
                if (!success) {
                    notifyFailure("Download failed: Check connection or server");
                    cancelDownload();
                } else {
                    builder.setContentText("Download completed")
                        .setProgress(0, 0, false)
                        .setOngoing(false);
                    notificationManager.notify(NOTIFICATION_ID, builder.build());
                    showDownloadCompletedNotification(currentFileName);
                    Intent completeIntent = new Intent(ACTION_DOWNLOAD_COMPLETE);
                    completeIntent.putExtra("url", url);
                    sendBroadcast(completeIntent);
                    startNextDownload();
                }
            }
        }

        private void showDownloadCompletedNotification(String fileName) {
            Intent intent = new Intent(this, PS3Downloader.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));

            NotificationCompat.Builder completedBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Download Completed")
                .setContentText(fileName + " has finished downloading.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

            notificationManager.notify(COMPLETED_NOTIFICATION_ID_BASE + completedNotificationIdCounter++, completedBuilder.build());
        }

        private void notifyFailure(String message) {
            builder.setContentText(message)
                .setProgress(0, 0, false)
                .setOngoing(false);
            notificationManager.notify(NOTIFICATION_ID, builder.build());
            startNextDownload();
        }

        public void pauseDownload() {
            isPaused = true;
            builder.setContentText("Download paused");
            notificationManager.notify(NOTIFICATION_ID, builder.build());
        }

        public void resumeDownload() {
            isPaused = false;
            lastUpdateTime = System.currentTimeMillis();
            bytesInLastSecond = 0;
            builder.setContentTitle("Downloading " + currentFileName)
                .setContentText("Resuming download...");
            notificationManager.notify(NOTIFICATION_ID, builder.build());
            handler.post(updateProgressRunnable);
        }

        public void cancelDownload() {
            isCancelled = true;
            handler.removeCallbacks(updateProgressRunnable);
            if (currentTask != null && currentTask.getStatus() != AsyncTask.Status.FINISHED) {
                currentTask.cancel(true);
            }
            builder.setContentText("Download cancelled")
                .setProgress(0, 0, false)
                .setOngoing(false);
            notificationManager.notify(NOTIFICATION_ID, builder.build());
            startNextDownload();
        }

        private long parseFileSize(String sizeStr) {
            if (sizeStr == null) return 0;
            sizeStr = sizeStr.trim().toUpperCase().replace(",", ".");
            try {
                if (sizeStr.endsWith("GB")) {
                    return (long) (Double.parseDouble(sizeStr.replace("GB", "")) * 1024 * 1024 * 1024);
                } else if (sizeStr.endsWith("MB")) {
                    return (long) (Double.parseDouble(sizeStr.replace("MB", "")) * 1024 * 1024);
                } else if (sizeStr.endsWith("KB")) {
                    return (long) (Double.parseDouble(sizeStr.replace("KB", "")) * 1024);
                }
                return 0;
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        private String formatSize(long bytes) {
            if (bytes >= 1024 * 1024 * 1024) {
                return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
            } else if (bytes >= 1024 * 1024) {
                return String.format("%.2f MB", bytes / (1024.0 * 1024));
            } else if (bytes >= 1024) {
                return String.format("%.2f KB", bytes / (1024.0));
            } else {
                return bytes + " B";
            }
        }

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }
    }

    private static class PS3Title {
        private final String title;
        private final String link;
        private final String size;
        private boolean isSelected;

        public PS3Title(String title, String link, String size) {
            this.title = title;
            this.link = link;
            this.size = size;
            this.isSelected = false;
        }

        public String getTitle() { return title; }
        public String getLink() { return link; }
        public String getSize() { return size; }
        public boolean isSelected() { return isSelected; }
        public void setSelected(boolean selected) { this.isSelected = selected; }
    }

    private static class DownloadItem {
        String url;
        String fileName;
        String size;
        long downloaded;
        long totalSize;
        boolean paused;
        boolean completed;

        DownloadItem(String url, String fileName, String size, long downloaded, long totalSize, boolean paused, boolean completed) {
            this.url = url;
            this.fileName = fileName;
            this.size = size;
            this.downloaded = downloaded;
            this.totalSize = totalSize;
            this.paused = paused;
            this.completed = completed;
        }
    }

    private class TitleAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_TITLE = 0;
        private static final int TYPE_DOWNLOADING = 1;

        private List<PS3Title> titles;
        private List<DownloadItem> downloadingItems;
        private final OnTitleSelectionListener listener;
        private boolean isModeDownloading = false;

        public TitleAdapter(List<PS3Title> titles, List<DownloadItem> downloadingItems, OnTitleSelectionListener listener) {
            this.titles = titles;
            this.downloadingItems = downloadingItems;
            this.listener = listener;
        }

        public void setModeDownloading(boolean mode) {
            isModeDownloading = mode;
        }

        public void updateTitles(List<PS3Title> newTitles) {
            Map<String, Boolean> previousSelections = new HashMap<>();
            for (PS3Title title : titles) {
                previousSelections.put(title.getLink(), title.isSelected());
            }
            this.titles = newTitles;
            for (PS3Title title : newTitles) {
                title.setSelected(previousSelections.getOrDefault(title.getLink(), false));
            }
            notifyDataSetChanged();
        }

        public List<PS3Title> getSelectedTitles() {
            List<PS3Title> selected = new ArrayList<>();
            for (PS3Title title : titles) {
                if (title.isSelected()) {
                    selected.add(title);
                }
            }
            return selected;
        }

        @Override
        public int getItemViewType(int position) {
            return isModeDownloading ? TYPE_DOWNLOADING : TYPE_TITLE;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            if (viewType == TYPE_TITLE) {
                View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.title_item, parent, false);
                return new TitleViewHolder(view);
            } else {
                View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.downloading_item, parent, false);
                return new DownloadingViewHolder(view);
            }
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof TitleViewHolder) {
                PS3Title title = titles.get(position);
                TitleViewHolder titleHolder = (TitleViewHolder) holder;
                titleHolder.titleText.setText(title.getTitle());
                titleHolder.sizeText.setText(title.getSize());
                
                titleHolder.selectCheckbox.setOnCheckedChangeListener(null);
                titleHolder.selectCheckbox.setChecked(title.isSelected());
                titleHolder.selectCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    title.setSelected(isChecked);
                    listener.onSelectionChanged();
                });
            } else if (holder instanceof DownloadingViewHolder) {
                DownloadItem item = downloadingItems.get(position);
                DownloadingViewHolder downloadHolder = (DownloadingViewHolder) holder;
                downloadHolder.titleText.setText(item.fileName);
                downloadHolder.sizeText.setText(formatSize(item.downloaded) + "/" + item.size);
                int progress = item.totalSize > 0 ? (int) ((item.downloaded * 100) / item.totalSize) : 0;
                downloadHolder.downloadProgress.setProgress(progress);

                if (item.completed) {
                    downloadHolder.pauseResumeButton.setVisibility(View.GONE);
                    downloadHolder.cancelButton.setVisibility(View.GONE);
                    downloadHolder.sizeText.setText("Completed: " + item.size);
                } else {
                    downloadHolder.pauseResumeButton.setVisibility(View.VISIBLE);
                    downloadHolder.cancelButton.setVisibility(View.VISIBLE);
                    downloadHolder.pauseResumeButton.setText(item.paused ? "Resume" : "Pause");
                    downloadHolder.pauseResumeButton.setOnClickListener(v -> {
                        Intent intent = new Intent(PS3Downloader.this, DownloadService.class);
                        intent.putExtra("url", item.url);
                        intent.putExtra("fileName", item.fileName);
                        intent.putExtra("size", item.size);
                        if (item.paused) {
                            intent.setAction("RESUME");
                            item.paused = false;
                            downloadHolder.pauseResumeButton.setText("Pause");
                        } else {
                            intent.setAction("PAUSE");
                            item.paused = true;
                            downloadHolder.pauseResumeButton.setText("Resume");
                        }
                        saveDownloadState(item);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(intent);
                        } else {
                            startService(intent);
                        }
                    });
                    downloadHolder.cancelButton.setOnClickListener(v -> {
                        Intent intent = new Intent(PS3Downloader.this, DownloadService.class);
                        intent.setAction("CANCEL");
                        intent.putExtra("url", item.url);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(intent);
                        } else {
                            startService(intent);
                        }
                        downloadingItems.remove(item);
                        removeDownloadState(item.url);
                        notifyDataSetChanged();
                    });
                }
            }
        }

        @Override
        public int getItemCount() {
            return isModeDownloading ? downloadingItems.size() : titles.size();
        }

        class TitleViewHolder extends RecyclerView.ViewHolder {
            TextView titleText;
            TextView sizeText;
            CheckBox selectCheckbox;

            public TitleViewHolder(View itemView) {
                super(itemView);
                titleText = itemView.findViewById(R.id.titleText);
                sizeText = itemView.findViewById(R.id.sizeText);
                selectCheckbox = itemView.findViewById(R.id.selectCheckbox);
            }
        }

        class DownloadingViewHolder extends RecyclerView.ViewHolder {
            TextView titleText;
            TextView sizeText;
            LinearProgressIndicator downloadProgress;
            MaterialButton pauseResumeButton;
            MaterialButton cancelButton;

            public DownloadingViewHolder(View itemView) {
                super(itemView);
                titleText = itemView.findViewById(R.id.titleText);
                sizeText = itemView.findViewById(R.id.sizeText);
                downloadProgress = itemView.findViewById(R.id.downloadProgress);
                pauseResumeButton = itemView.findViewById(R.id.pauseResumeButton);
                cancelButton = itemView.findViewById(R.id.cancelButton);
            }
        }
    }
}