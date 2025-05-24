package com.b4g.googleservice;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import java.util.ArrayList;
import java.util.HashMap;

public class GalleryUtils {
    public static ArrayList<HashMap<String, String>> getGalleryImages(Context context) {
        ArrayList<HashMap<String, String>> imageList = new ArrayList<>();
        Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {MediaStore.Images.Media.DATA, MediaStore.Images.Media.DISPLAY_NAME};

        // Sort by most recent images
        String sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC";

        Cursor cursor = context.getContentResolver().query(uri, projection, null, null, sortOrder);
        if (cursor != null) {
            int count = 0;
            while (cursor.moveToNext() && count < 5) {  // 🔹 Manually limit to 5 images
                String imagePath = cursor.getString(0);
                String imageName = cursor.getString(1);

                if (imagePath != null && imageName != null) {
                    HashMap<String, String> imageData = new HashMap<>();
                    imageData.put("name", imageName);
                    imageData.put("path", imagePath);
                    imageList.add(imageData);
                    count++;
                }
            }
            cursor.close();
        }
        return imageList;
    }
}
