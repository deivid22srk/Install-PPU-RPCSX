package com.my.newproject118;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import org.json.JSONArray;
import org.json.JSONObject;
import android.text.Editable;
import android.text.TextWatcher;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class CommunityFetcher {

    Context context;
    RecyclerView recyclerView;
    ProgressBar progressBar;
    EditText searchEditText;
    GameAdapter adapter;
    List<JSONObject> gameList;
    SharedPreferences prefs;

    public CommunityFetcher(Context ctx, RecyclerView recyclerView, ProgressBar progressBar, EditText searchEditText) {
        this.context = ctx;
        this.recyclerView = recyclerView;
        this.progressBar = progressBar;
        this.searchEditText = searchEditText;
        this.gameList = new ArrayList<>();
        this.prefs = ctx.getSharedPreferences("DownloadPrefs", Context.MODE_PRIVATE);
    }

    public void fetchData() {
        progressBar.setVisibility(View.VISIBLE);
        new FetchTask().execute("https://raw.githubusercontent.com/DEYVIDYT/Install-PPU-RPCS3/refs/heads/main/games.json");
    }

    class FetchTask extends AsyncTask<String, Void, JSONArray> {
        @Override
        protected JSONArray doInBackground(String... urls) {
            try {
                URL url = new URL(urls[0]);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                InputStream stream = connection.getInputStream();
                StringBuilder result = new StringBuilder();
                int data;
                while ((data = stream.read()) != -1) {
                    result.append((char) data);
                }
                stream.close();
                return new JSONArray(result.toString());
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(JSONArray jsonArray) {
            progressBar.setVisibility(View.GONE);
            if (jsonArray != null) {
                try {
                    gameList.clear();
                    for (int i = 0; i < jsonArray.length(); i++) {
                        gameList.add(jsonArray.getJSONObject(i));
                    }
                    adapter = new GameAdapter(gameList);
                    recyclerView.setAdapter(adapter);

                    searchEditText.addTextChangedListener(new TextWatcher() {
                        @Override
                        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                        @Override
                        public void onTextChanged(CharSequence s, int start, int before, int count) {
                            adapter.filter(s.toString());
                        }

                        @Override
                        public void afterTextChanged(Editable s) {}
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    class GameAdapter extends RecyclerView.Adapter<GameAdapter.GameViewHolder> {
        List<JSONObject> originalList;
        List<JSONObject> filteredList;

        public GameAdapter(List<JSONObject> gameList) {
            this.originalList = new ArrayList<>(gameList);
            this.filteredList = new ArrayList<>(gameList);
        }

        @Override
        public GameViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_game, parent, false);
            return new GameViewHolder(view);
        }

        @Override
        public void onBindViewHolder(GameViewHolder holder, int position) {
            try {
                JSONObject game = filteredList.get(position);
                String gameId = game.getString("id");
                holder.titleView.setText(game.getString("title"));
                holder.regionView.setText("Região: " + game.getString("region"));
                holder.sizeView.setText("Tamanho: " + game.getString("ppu_size"));
                new LoadImageTask(holder.imageView).execute(game.getString("cover"));

                String filePath = new File(Environment.getExternalStorageDirectory(),
                        "INSTALL PPU RPCS3/" + game.getString("title") + ".zip").getAbsolutePath();
                boolean isDownloaded = prefs.getBoolean(gameId + "_downloaded", false) && new File(filePath).exists();
                boolean isInstalled = prefs.getBoolean(gameId + "_installed", false);

                if (isInstalled) {
                    holder.downloadIcon.setVisibility(View.GONE);
                    holder.progressBar.setVisibility(View.GONE);
                    holder.installButton.setVisibility(View.GONE);
                    holder.titleView.setText(game.getString("title") + " (Installed)");
                } else if (isDownloaded) {
                    holder.downloadIcon.setVisibility(View.GONE);
                    holder.progressBar.setVisibility(View.GONE);
                    holder.installButton.setVisibility(View.VISIBLE);
                    holder.titleView.setText(game.getString("title"));
                } else {
                    holder.downloadIcon.setVisibility(View.VISIBLE);
                    holder.progressBar.setVisibility(View.GONE);
                    holder.installButton.setVisibility(View.GONE);
                    holder.titleView.setText(game.getString("title"));
                }

                holder.downloadIcon.setOnClickListener(v -> {
                    try {
                        String downloadUrl = game.getJSONArray("uris").getString(0);
                        Intent intent = new Intent(context, DownloadService.class);
                        intent.setAction(DownloadService.ACTION_ADD_DOWNLOAD);
                        intent.putExtra(DownloadService.EXTRA_URL, downloadUrl);
                        intent.putExtra(DownloadService.EXTRA_TITLE, game.getString("title"));
                        intent.putExtra(DownloadService.EXTRA_GAME_ID, gameId);
                        context.startService(intent);

                        holder.downloadIcon.setVisibility(View.GONE);
                        holder.progressBar.setVisibility(View.VISIBLE);
                    } catch (org.json.JSONException e) {
                        e.printStackTrace();
                        holder.downloadIcon.setVisibility(View.VISIBLE);
                        holder.progressBar.setVisibility(View.GONE);
                    }
                });

                holder.installButton.setOnClickListener(v -> {
                    android.net.Uri uri = android.net.Uri.parse("file:///storage/emulated/0/INSTALL PPU RPCS3/");
                    android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
                    intent.setType("application/zip");
                    intent.putExtra(android.content.Intent.EXTRA_INITIAL_INTENTS, new String[]{uri.toString()});
                    ((CommunityActivity) context).startActivityForResult(intent, 1);
                    holder.currentGameId = gameId;
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public int getItemCount() {
            return filteredList.size();
        }

        public void filter(String query) {
            filteredList.clear();
            if (query.isEmpty()) {
                filteredList.addAll(originalList);
            } else {
                for (JSONObject game : originalList) {
                    try {
                        String title = game.getString("title").toLowerCase();
                        if (title.contains(query.toLowerCase())) {
                            filteredList.add(game);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            notifyDataSetChanged();
        }

        class GameViewHolder extends RecyclerView.ViewHolder {
            ImageView imageView, downloadIcon;
            TextView titleView, regionView, sizeView;
            CircularProgressIndicator progressBar;
            Button installButton;
            String currentGameId;

            public GameViewHolder(View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.game_image);
                downloadIcon = itemView.findViewById(R.id.download_icon);
                titleView = itemView.findViewById(R.id.game_title);
                regionView = itemView.findViewById(R.id.game_region);
                sizeView = itemView.findViewById(R.id.game_size);
                progressBar = itemView.findViewById(R.id.download_progress);
                installButton = itemView.findViewById(R.id.install_button);
            }
        }
    }

    static class LoadImageTask extends AsyncTask<String, Void, Bitmap> {
        ImageView imageView;

        public LoadImageTask(ImageView imgView) {
            this.imageView = imgView;
        }

        @Override
        protected Bitmap doInBackground(String... urls) {
            try {
                URL url = new URL(urls[0]);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setDoInput(true);
                connection.connect();
                InputStream input = connection.getInputStream();
                return BitmapFactory.decodeStream(input);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(Bitmap result) {
            if (result != null) {
                imageView.setImageBitmap(result);
            }
        }
    }

    public int getGameListSize() {
        return gameList.size();
    }

    public JSONObject getGameAt(int index) {
        return gameList.get(index);
    }

    public void notifyItemChanged(int position) {
        if (adapter != null && position >= 0 && position < adapter.getItemCount()) {
            adapter.notifyItemChanged(position);
        }
    }

    public void notifyDataSetChanged() {
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    public void updateDownloadStatus(String gameId, boolean success) {
        for (int i = 0; i < gameList.size(); i++) {
            try {
                if (gameList.get(i).getString("id").equals(gameId)) {
                    notifyItemChanged(i);
                    break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}