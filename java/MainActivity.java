package com.my.newproject118;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

// Import dos fragments
import com.my.newproject118.ui.home.HomeFragment;
import com.my.newproject118.ui.games.GamesFragmentSafe;
import com.my.newproject118.ui.install.InstallFragment;
import com.my.newproject118.ui.community.CommunityFragment;
import com.my.newproject118.ui.settings.SettingsFragment;

import java.io.File;

public class MainActivity extends AppCompatActivity {
    
    private Toolbar toolbar;
    private BottomNavigationView bottomNavigation;
    private FloatingActionButton fab;
    private FragmentManager fragmentManager;
    
    // Fragment management
    private HomeFragment homeFragment;
    private GamesFragmentSafe gamesFragment;
    private InstallFragment installFragment;
    private CommunityFragment communityFragment;
    private SettingsFragment settingsFragment;
    private Fragment activeFragment;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            setContentView(R.layout.activity_main);
            initializeViews();
            setupFragments();
            setupNavigation();
            // TEMPORARIAMENTE DESATIVADO PARA TESTE DO SISTEMA PPU
            // checkStoragePermissions();
            
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error in onCreate: " + e.getMessage(), e);
            Toast.makeText(this, "Erro ao carregar: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private void initializeViews() {
        // Find views
        toolbar = findViewById(R.id.toolbar);
        bottomNavigation = findViewById(R.id.bottom_navigation);
        fab = findViewById(R.id.fab);
        
        // Setup toolbar
        setSupportActionBar(toolbar);
        toolbar.setTitle("Games");
    }
    
    private void setupFragments() {
        // Initialize FragmentManager
        fragmentManager = getSupportFragmentManager();
        
        // Initialize fragments
        homeFragment = new HomeFragment();
        gamesFragment = new GamesFragmentSafe();
        installFragment = new InstallFragment();
        communityFragment = new CommunityFragment();
        settingsFragment = new SettingsFragment();
        
        // Add all fragments to container
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.add(R.id.fragment_container, homeFragment, "HOME");
        transaction.add(R.id.fragment_container, gamesFragment, "GAMES");
        transaction.add(R.id.fragment_container, installFragment, "INSTALL");
        transaction.add(R.id.fragment_container, communityFragment, "COMMUNITY");
        transaction.add(R.id.fragment_container, settingsFragment, "SETTINGS");
        
        // Hide all fragments except games (RPCS3 interface como nas imagens)
        transaction.hide(homeFragment);
        transaction.hide(installFragment);
        transaction.hide(communityFragment);
        transaction.hide(settingsFragment);
        transaction.commit();
        
        // Set games as active fragment (RPCS3 interface como nas imagens)
        activeFragment = gamesFragment;
    }
    
    private void setupNavigation() {
        // Always show FAB
        fab.show();
        fab.setOnClickListener(v -> {
            // Navigate to install fragment when FAB is clicked
            bottomNavigation.setSelectedItemId(R.id.nav_install);
        });
        
        // Setup bottom navigation
        bottomNavigation.setOnItemSelectedListener(this::onNavigationItemSelected);
        
        // Set default selection to Games (RPCS3 interface)
        bottomNavigation.setSelectedItemId(R.id.nav_games);
    }
    
    private boolean onNavigationItemSelected(MenuItem item) {
        Fragment selectedFragment = null;
        String title = "";
        
        int itemId = item.getItemId();
        if (itemId == R.id.nav_home) {
            selectedFragment = homeFragment;
            title = "Home";
        } else if (itemId == R.id.nav_games) {
            selectedFragment = gamesFragment;
            title = "Games";
        } else if (itemId == R.id.nav_install) {
            selectedFragment = installFragment;
            title = "Install PPU";
        } else if (itemId == R.id.nav_community) {
            selectedFragment = communityFragment;
            title = "Community";
        } else if (itemId == R.id.nav_settings) {
            selectedFragment = settingsFragment;
            title = "Settings";
        }
        
        if (selectedFragment != null && selectedFragment != activeFragment) {
            FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
            fragmentTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
            fragmentTransaction.hide(activeFragment);
            fragmentTransaction.show(selectedFragment);
            fragmentTransaction.commit();
            
            activeFragment = selectedFragment;
            toolbar.setTitle(title);
        }
        
        return true;
    }
    
    private void checkStoragePermissions() {
        if (ContextCompat.checkSelfPermission(this, 
                Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
            
            MaterialAlertDialogBuilder dialogBuilder = new MaterialAlertDialogBuilder(this)
                .setTitle("Permissão Necessária")
                .setMessage("Este aplicativo precisa da permissão de armazenamento para funcionar corretamente.")
                .setPositiveButton("Conceder", (dialog, which) -> {
                    ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                    
                    new android.os.Handler().postDelayed(() -> {
                        if (ContextCompat.checkSelfPermission(MainActivity.this, 
                                Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                                == PackageManager.PERMISSION_GRANTED) {
                            createBackupFolder();
                            Toast.makeText(MainActivity.this, "Permissão concedida!", Toast.LENGTH_SHORT).show();
                        } else {
                            showPermissionDeniedDialog();
                        }
                    }, 1000);
                })
                .setNegativeButton("Sair", (dialog, which) -> finish())
                .setCancelable(false);
            
            androidx.appcompat.app.AlertDialog dialog = dialogBuilder.show();
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(Color.WHITE);
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(Color.WHITE);
            
        } else {
            createBackupFolder();
        }
    }
    
    private void createBackupFolder() {
        File folder = new File("/storage/emulated/0/INSTALL PPU RPCS3/BACKUP/");
        if (!folder.exists()) {
            folder.mkdirs();
        }
    }
    
    private void showPermissionDeniedDialog() {
        new MaterialAlertDialogBuilder(this)
            .setTitle("Permissão Recusada")
            .setMessage("O aplicativo precisa da permissão de armazenamento para funcionar.")
            .setPositiveButton("OK", (dialog, which) -> finish())
            .setCancelable(false)
            .show()
            .getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(Color.WHITE);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (resultCode == RESULT_OK && data != null) {
            if (activeFragment instanceof InstallFragment && requestCode == 1) {
                ((InstallFragment) activeFragment).handleFileSelection(data.getData());
            }
        }
    }
    
    public void launchFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(intent, 1);
    }
    
    @Override
    public void onBackPressed() {
        if (activeFragment != gamesFragment) {
            bottomNavigation.setSelectedItemId(R.id.nav_games);
        } else {
            super.onBackPressed();
        }
    }
}