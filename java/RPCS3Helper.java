package com.my.newproject118;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import androidx.documentfile.provider.DocumentFile;

/**
 * Helper class for RPCS3 folder operations and path validation
 */
public class RPCS3Helper {
    
    private static final String TAG = "RPCS3Helper";
    private static final String PREFS_NAME = "RPCS3Prefs";
    private static final String PREF_FOLDER_URI = "folder_uri";
    
    /**
     * Get the configured RPCS3 folder URI
     */
    public static Uri getRPCS3FolderUri(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedUri = prefs.getString(PREF_FOLDER_URI, null);
        return savedUri != null ? Uri.parse(savedUri) : null;
    }
    
    /**
     * Validate if the RPCS3 folder structure is correct
     */
    public static boolean validateRPCS3Folder(Context context, Uri folderUri) {
        if (folderUri == null) {
            Log.w(TAG, "Folder URI is null");
            return false;
        }
        
        DocumentFile rootFolder = DocumentFile.fromTreeUri(context, folderUri);
        if (rootFolder == null || !rootFolder.exists()) {
            Log.w(TAG, "Root folder does not exist: " + folderUri);
            return false;
        }
        
        // Check for config folder
        DocumentFile configFolder = findSubFolder(rootFolder, "config");
        if (configFolder == null) {
            Log.w(TAG, "Config folder not found");
            return false;
        }
        
        // Check for dev_hdd0 folder
        DocumentFile devHdd0Folder = findSubFolder(configFolder, "dev_hdd0");
        if (devHdd0Folder == null) {
            Log.w(TAG, "dev_hdd0 folder not found");
            return false;
        }
        
        // Check for game folder
        DocumentFile gameFolder = findSubFolder(devHdd0Folder, "game");
        if (gameFolder == null) {
            Log.w(TAG, "game folder not found");
            return false;
        }
        
        Log.d(TAG, "RPCS3 folder structure validated successfully");
        return true;
    }
    
    /**
     * Get the games folder path
     */
    public static DocumentFile getGamesFolder(Context context) {
        Uri folderUri = getRPCS3FolderUri(context);
        if (folderUri == null) return null;
        
        DocumentFile rootFolder = DocumentFile.fromTreeUri(context, folderUri);
        if (rootFolder == null) return null;
        
        DocumentFile configFolder = findSubFolder(rootFolder, "config");
        if (configFolder == null) return null;
        
        DocumentFile devHdd0Folder = findSubFolder(configFolder, "dev_hdd0");
        if (devHdd0Folder == null) return null;
        
        return findSubFolder(devHdd0Folder, "game");
    }
    
    /**
     * Get the cache folder path for PPU backups
     */
    public static DocumentFile getCacheFolder(Context context) {
        Uri folderUri = getRPCS3FolderUri(context);
        if (folderUri == null) return null;
        
        DocumentFile rootFolder = DocumentFile.fromTreeUri(context, folderUri);
        if (rootFolder == null) return null;
        
        DocumentFile cacheFolder = findSubFolder(rootFolder, "cache");
        if (cacheFolder == null) return null;
        
        return findSubFolder(cacheFolder, "cache");
    }
    
    /**
     * Get the PPU cache folder for a specific game
     */
    public static DocumentFile getGamePPUFolder(Context context, String gameId) {
        DocumentFile cacheFolder = getCacheFolder(context);
        if (cacheFolder == null) return null;
        
        return findSubFolder(cacheFolder, gameId);
    }
    
    /**
     * Check if a game has PPU cache files
     */
    public static boolean hasGamePPUs(Context context, String gameId) {
        DocumentFile ppuFolder = getGamePPUFolder(context, gameId);
        if (ppuFolder == null || !ppuFolder.exists()) return false;
        
        DocumentFile[] files = ppuFolder.listFiles();
        return files != null && files.length > 0;
    }
    
    /**
     * Count total PPU files for a game
     */
    public static int countGamePPUFiles(Context context, String gameId) {
        DocumentFile ppuFolder = getGamePPUFolder(context, gameId);
        if (ppuFolder == null) return 0;
        
        return countFilesRecursive(ppuFolder);
    }
    
    /**
     * Find a subfolder by name (case insensitive)
     */
    public static DocumentFile findSubFolder(DocumentFile parent, String folderName) {
        if (parent == null) return null;
        
        DocumentFile[] files = parent.listFiles();
        if (files == null) return null;
        
        for (DocumentFile file : files) {
            if (file.isDirectory() && file.getName() != null && 
                file.getName().equalsIgnoreCase(folderName)) {
                return file;
            }
        }
        
        return null;
    }
    
    /**
     * Count files recursively in a folder
     */
    private static int countFilesRecursive(DocumentFile folder) {
        if (folder == null || !folder.exists()) return 0;
        
        int count = 0;
        DocumentFile[] files = folder.listFiles();
        if (files != null) {
            for (DocumentFile file : files) {
                if (file.isDirectory()) {
                    count += countFilesRecursive(file);
                } else {
                    count++;
                }
            }
        }
        
        return count;
    }
    
    /**
     * Get region from game ID
     */
    public static String getRegionFromGameId(String gameId) {
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
    
    /**
     * Check if RPCS3 is properly configured
     */
    public static boolean isRPCS3Configured(Context context) {
        Uri folderUri = getRPCS3FolderUri(context);
        return folderUri != null && validateRPCS3Folder(context, folderUri);
    }
}