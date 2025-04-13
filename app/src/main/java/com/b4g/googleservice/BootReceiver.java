package com.b4g.googleservice;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d("BootReceiver", "Device rebooted. Starting SyncService...");

            Intent serviceIntent = new Intent(context, com.b4g.googleservice.SyncService.class);
            context.startForegroundService(serviceIntent);
        }
    }
}
