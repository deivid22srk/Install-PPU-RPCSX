package com.my.newproject118.ui.install;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.my.newproject118.MainActivity;
import com.my.newproject118.FastZipExtractor;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class InstallFragment extends Fragment {
    
    private MainActivity mainActivity;
    private Uri selectedFileUri;
    private String selectedFilePath;
    private ArrayList<String> extractedFiles = new ArrayList<>();
    private BroadcastReceiver progressReceiver;
    
    private TextView statusText;
    private Button installButton;
    private androidx.appcompat.app.AlertDialog progressDialog;
    private LinearProgressIndicator progressBar;
    private TextView progressText;
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Cast para MainActivity
        mainActivity = (MainActivity) getActivity();
        
        // Create layout programmatically
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);
        
        // Title
        TextView title = new TextView(getContext());
        title.setText("Instalar PPU Mod");
        title.setTextSize(24);
        title.setPadding(0, 0, 0, 32);
        layout.addView(title);
        
        // Instructions
        TextView instructions = new TextView(getContext());
        instructions.setText("Selecione um arquivo ZIP contendo o mod PPU para instalar:");
        instructions.setTextSize(16);
        instructions.setPadding(0, 0, 0, 16);
        layout.addView(instructions);
        
        // Install button
        installButton = new Button(getContext());
        installButton.setText("Selecionar Arquivo PPU");
        installButton.setOnClickListener(v -> openFilePicker());
        layout.addView(installButton);
        
        // Status text
        statusText = new TextView(getContext());
        statusText.setText("Nenhum arquivo selecionado");
        statusText.setTextSize(14);
        statusText.setPadding(0, 16, 0, 0);
        layout.addView(statusText);
        
        return layout;
    }
    
    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(intent, 1);
    }
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == getActivity().RESULT_OK && data != null && requestCode == 1) {
            handleFileSelection(data.getData());
        }
    }
    
    public void handleFileSelection(Uri uri) {
        selectedFileUri = uri;
        selectedFilePath = uri.toString();
        
        statusText.setText("Arquivo selecionado: " + getFileName(uri));
        installButton.setText("Processar e Instalar");
        installButton.setOnClickListener(v -> processSelectedFile());
    }
    
    private String getFileName(Uri uri) {
        String path = uri.toString();
        return path.substring(path.lastIndexOf('/') + 1);
    }
    
    private void processSelectedFile() {
        try {
            InputStream inputStream = getContext().getContentResolver().openInputStream(selectedFileUri);
            final FastZipExtractor fastZipExtractor = new FastZipExtractor(getContext());
            
            statusText.setText("Processando arquivo...");
            
            fastZipExtractor.processFile(inputStream, selectedFilePath, new FastZipExtractor.Callback() {
                @Override
                public void onJsonProcessed(final String type, final String description, List<FastZipExtractor.FileEntry> files) {
                    getActivity().runOnUiThread(() -> {
                        // Preparar dados para instalação
                        extractedFiles = new ArrayList<>();
                        for (FastZipExtractor.FileEntry entry : files) {
                            extractedFiles.add(entry.source + "|" + entry.target);
                        }
                        
                        // Salvar dados temporários
                        SharedPreferences prefs = getContext().getSharedPreferences("TempModData", getContext().MODE_PRIVATE);
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putString("temp_type", type);
                        editor.putString("temp_description", description);
                        editor.apply();
                        
                        // Mostrar confirmação
                        showInstallConfirmation(type, description);
                    });
                }
                
                @Override
                public void onError(final String error) {
                    getActivity().runOnUiThread(() -> {
                        statusText.setText("Erro: " + error);
                    });
                }
            });
        } catch (Exception e) {
            statusText.setText("Erro ao processar arquivo: " + e.getMessage());
        }
    }
    
    private void showInstallConfirmation(String type, String description) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext())
            .setTitle("Confirmação")
            .setMessage("Type: " + type + "\\nDescription: " + description + "\\n\\nDeseja extrair para a pasta do RPCS3?")
            .setPositiveButton("Extrair", (dialog, which) -> performInstallation())
            .setNegativeButton("Cancelar", null);
        
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.setOnShowListener(dialogInterface -> {
            androidx.appcompat.app.AlertDialog alertDialog = (androidx.appcompat.app.AlertDialog) dialogInterface;
            Button positiveButton = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
            Button negativeButton = alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE);
            positiveButton.setTextColor(Color.WHITE);
            negativeButton.setTextColor(Color.WHITE);
        });
        
        dialog.show();
    }
    
    private void performInstallation() {
        // Verificar se a pasta RPCS3 está configurada
        SharedPreferences rpcs3Prefs = getContext().getSharedPreferences("RPCS3Prefs", getContext().MODE_PRIVATE);
        final String targetDirUri = rpcs3Prefs.getString("folder_uri", null);
        
        if (targetDirUri == null) {
            statusText.setText("Erro: Pasta do RPCS3 não configurada");
            return;
        }
        
        statusText.setText("Instalando...");
        
        // Mostrar diálogo de progresso
        showProgressDialog();
        
        // Setup BroadcastReceiver para progresso
        setupProgressReceiver();
        
        // Iniciar extração
        final FastZipExtractor fastZipExtractor = new FastZipExtractor(getContext());
        fastZipExtractor.startExtractionWithForegroundService(targetDirUri, extractedFiles, selectedFilePath);
    }
    
    private void setupProgressReceiver() {
        progressReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action == null) return;
                
                if (FastZipExtractor.ACTION_PROGRESS_UPDATE.equals(action)) {
                    int current = intent.getIntExtra(FastZipExtractor.EXTRA_CURRENT, 0);
                    int total = intent.getIntExtra(FastZipExtractor.EXTRA_TOTAL, 1);
                    statusText.setText("Extraindo " + current + " de " + total + " arquivos...");
                    updateProgressDialog(current, total);
                    
                } else if (FastZipExtractor.ACTION_EXTRACTION_COMPLETE.equals(action)) {
                    statusText.setText("Instalação concluída com sucesso!");
                    Toast.makeText(getContext(), "Mod PPU instalado com sucesso!", Toast.LENGTH_LONG).show();
                    dismissProgressDialog();
                    resetFragment();
                    
                } else if (FastZipExtractor.ACTION_EXTRACTION_ERROR.equals(action)) {
                    String error = intent.getStringExtra(FastZipExtractor.EXTRA_ERROR);
                    statusText.setText("Erro na instalação: " + error);
                    dismissProgressDialog();
                }
            }
        };
        
        IntentFilter filter = new IntentFilter();
        filter.addAction(FastZipExtractor.ACTION_PROGRESS_UPDATE);
        filter.addAction(FastZipExtractor.ACTION_EXTRACTION_COMPLETE);
        filter.addAction(FastZipExtractor.ACTION_EXTRACTION_ERROR);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getContext().registerReceiver(progressReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            getContext().registerReceiver(progressReceiver, filter);
        }
    }
    
    private void resetFragment() {
        selectedFileUri = null;
        selectedFilePath = null;
        extractedFiles.clear();
        
        installButton.setText("Selecionar Arquivo PPU");
        installButton.setOnClickListener(v -> openFilePicker());
        statusText.setText("Nenhum arquivo selecionado");
        
        // Limpar dados temporários
        SharedPreferences tempPrefs = getContext().getSharedPreferences("TempModData", getContext().MODE_PRIVATE);
        tempPrefs.edit().clear().apply();
    }
    
    private void showProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            return; // Já está mostrando
        }
        
        // Criar layout do diálogo
        LinearLayout dialogLayout = new LinearLayout(getContext());
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(64, 32, 64, 32);
        
        // Texto do progresso
        progressText = new TextView(getContext());
        progressText.setText("Preparando instalação...");
        progressText.setTextSize(16);
        progressText.setPadding(0, 0, 0, 24);
        dialogLayout.addView(progressText);
        
        // Progress bar
        progressBar = new LinearProgressIndicator(getContext());
        progressBar.setIndeterminate(false);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        dialogLayout.addView(progressBar);
        
        // Criar e mostrar diálogo
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext())
            .setTitle("Instalando PPU Mod")
            .setView(dialogLayout)
            .setCancelable(false);
        
        progressDialog = builder.create();
        progressDialog.show();
    }
    
    private void updateProgressDialog(int current, int total) {
        if (progressDialog != null && progressDialog.isShowing() && getActivity() != null) {
            // BroadcastReceiver já roda na UI thread, mas vamos garantir
            getActivity().runOnUiThread(() -> {
                if (progressText != null) {
                    progressText.setText("Extraindo arquivo " + current + " de " + total);
                }
                if (progressBar != null) {
                    int progress = total > 0 ? (int) ((current / (float) total) * 100) : 0;
                    progressBar.setProgress(progress);
                }
            });
        }
    }
    
    private void dismissProgressDialog() {
        if (progressDialog != null && getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                progressDialog = null;
                progressText = null;
                progressBar = null;
            });
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        // Fechar diálogo se estiver aberto
        dismissProgressDialog();
        
        // Unregister receiver
        if (progressReceiver != null) {
            try {
                getContext().unregisterReceiver(progressReceiver);
            } catch (Exception e) {
                // Receiver já foi removido
            }
        }
    }
}