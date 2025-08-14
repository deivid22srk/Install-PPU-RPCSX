package com.my.newproject118;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.AnimatorListenerAdapter;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

public class SplashActivity extends AppCompatActivity {
    
    private static final String PREFS_NAME = "RPCS3Prefs";
    private static final String KEY_FOLDER_URI = "folder_uri";
    private Button nextButton;
    private Button selectFolderButton;
    private TextView welcomeText;
    private TextView subtitleText;
    private TextView setupText;
    private TextView instructionText;
    private TextView folderPathText;
    private ConstraintLayout mainLayout;
    private boolean isSetupShown = false;
    private Uri selectedFolderUri = null; // Armazena temporariamente até salvar
    
    private final ActivityResultLauncher<Intent> folderPickerLauncher = 
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Uri folderUri = result.getData().getData();
                if (folderUri != null) {
                    selectedFolderUri = folderUri;
                    folderPathText.setText("Pasta selecionada: " + folderUri.toString());
                    folderPathText.setVisibility(View.VISIBLE);
                    nextButton.setEnabled(true);
                }
            }
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        
        nextButton = findViewById(R.id.next_button);
        selectFolderButton = findViewById(R.id.select_folder_button);
        welcomeText = findViewById(R.id.welcome_text);
        subtitleText = findViewById(R.id.subtitle_text);
        setupText = findViewById(R.id.setup_text);
        instructionText = findViewById(R.id.instruction_text);
        folderPathText = findViewById(R.id.folder_path_text);
        mainLayout = findViewById(R.id.main_layout);
        
        // Verifica se já existe uma pasta salva (SharedPreferences global)
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedUri = prefs.getString(KEY_FOLDER_URI, null);
        
        if (savedUri != null) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        
        nextButton.setOnClickListener(v -> {
            if (isSetupShown && selectedFolderUri != null) {
                // Salva na SharedPreferences e vai para MainActivity
                SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
                editor.putString(KEY_FOLDER_URI, selectedFolderUri.toString());
                editor.apply();
                
                // Concede permissão persistente
                getContentResolver().takePersistableUriPermission(
                    selectedFolderUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                );
                
                startActivity(new Intent(this, MainActivity.class));
                finish();
            } else if (!isSetupShown) {
                // Mostra o layout de configuração com animação
                ObjectAnimator animator = ObjectAnimator.ofFloat(
                    mainLayout,
                    "translationX",
                    0f,
                    -1000f
                );
                animator.setDuration(500);
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        welcomeText.setVisibility(View.GONE);
                        subtitleText.setVisibility(View.GONE);
                        setupText.setVisibility(View.VISIBLE);
                        instructionText.setVisibility(View.VISIBLE);
                        selectFolderButton.setVisibility(View.VISIBLE);
                        mainLayout.setTranslationX(0f);
                        isSetupShown = true;
                    }
                });
                animator.start();
            }
        });
        
        selectFolderButton.setOnClickListener(v -> {
            openFolderPicker();
        });
    }
    
    private void openFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION |
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        );
        folderPickerLauncher.launch(intent);
    }
}