package com.my.newproject118.ui.games;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.my.newproject118.R;
import com.my.newproject118.RPCS3Helper;

import java.io.InputStream;
import java.util.List;

public class GamesAdapter extends RecyclerView.Adapter<GamesAdapter.GameViewHolder> {

    private List<GamesFragment.GameItem> games;
    private OnGameBackupListener backupListener;

    public interface OnGameBackupListener {
        void onGameBackup(GamesFragment.GameItem game);
    }

    public GamesAdapter(List<GamesFragment.GameItem> games, OnGameBackupListener backupListener) {
        this.games = games;
        this.backupListener = backupListener;
    }

    @NonNull
    @Override
    public GameViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_game_card, parent, false);
        return new GameViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GameViewHolder holder, int position) {
        GamesFragment.GameItem game = games.get(position);
        
        // Set game info
        holder.gameTitle.setText(game.title);
        holder.gameId.setText("ID: " + game.id + " • " + RPCS3Helper.getRegionFromGameId(game.id));
        holder.appVersion.setText("App: " + game.appVer);
        holder.gameVersion.setText("Ver: " + game.version);
        
        // Set game image
        if (game.iconUri != null) {
            try {
                InputStream inputStream = holder.itemView.getContext().getContentResolver().openInputStream(game.iconUri);
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                holder.gameImage.setImageBitmap(bitmap);
                inputStream.close();
            } catch (Exception e) {
                setPlaceholderImage(holder.gameImage, game.id);
            }
        } else {
            setPlaceholderImage(holder.gameImage, game.id);
        }
        
        // Check PPU status
        boolean hasPPUs = RPCS3Helper.hasGamePPUs(holder.itemView.getContext(), game.id);
        if (hasPPUs) {
            holder.ppuStatus.setVisibility(View.VISIBLE);
            holder.ppuStatus.setText("PPU OK");
            holder.ppuStatus.setChipIconResource(com.my.newproject118.R.drawable.ic_check);
        } else {
            holder.ppuStatus.setVisibility(View.GONE);
        }
        
        // Set backup button listener
        holder.backupButton.setOnClickListener(v -> {
            if (backupListener != null) {
                backupListener.onGameBackup(game);
            }
        });
    }
    
    private void setPlaceholderImage(ImageView imageView, String gameId) {
        // Use game ID hash to get consistent placeholder colors
        int colorIndex = gameId.hashCode() % 6;
        int[] colors = {
            android.graphics.Color.parseColor("#354479"),
            android.graphics.Color.parseColor("#34343A"),
            android.graphics.Color.parseColor("#1A1B21"),
            android.graphics.Color.parseColor("#4A5568"),
            android.graphics.Color.parseColor("#2D3748"),
            android.graphics.Color.parseColor("#1A202C")
        };
        
        imageView.setImageDrawable(null);
        imageView.setBackgroundColor(colors[Math.abs(colorIndex)]);
    }

    @Override
    public int getItemCount() {
        return games.size();
    }
    
    public void updateList(List<GamesFragment.GameItem> newGames) {
        Log.d("GamesAdapter", "updateList: Updating with " + newGames.size() + " games");
        this.games = newGames;
        notifyDataSetChanged();
        Log.d("GamesAdapter", "updateList: notifyDataSetChanged called");
    }

    static class GameViewHolder extends RecyclerView.ViewHolder {
        ImageView gameImage;
        TextView gameTitle, gameId, appVersion, gameVersion;
        Chip ppuStatus;
        MaterialButton backupButton;

        public GameViewHolder(@NonNull View itemView) {
            super(itemView);
            gameImage = itemView.findViewById(R.id.game_image);
            gameTitle = itemView.findViewById(R.id.game_title);
            gameId = itemView.findViewById(R.id.game_id);
            appVersion = itemView.findViewById(R.id.app_version);
            gameVersion = itemView.findViewById(R.id.game_version);
            ppuStatus = itemView.findViewById(R.id.ppu_status);
            backupButton = itemView.findViewById(R.id.backup_button);
        }
    }
}