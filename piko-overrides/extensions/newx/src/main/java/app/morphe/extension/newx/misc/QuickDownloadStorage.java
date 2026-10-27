package app.morphe.extension.newx.misc;

import android.app.DownloadManager;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import app.morphe.extension.newx.settings.NewXLogger;

/** Public native destinations need no synthetic SAF URI or ungranted tree permission. */
final class QuickDownloadStorage {
    private QuickDownloadStorage() {}
    static boolean enqueue(Context context, String url, String mimeType, String filename) {
        try {
            DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager == null) return false;
            boolean video = mimeType.startsWith("video/");
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url))
                    .setTitle(filename).setMimeType(mimeType)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(video ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_PICTURES,
                            "Twitter/" + DownloadFileName.limitFileName(filename));
            manager.enqueue(request);
            return true;
        } catch (RuntimeException failure) {
            NewXLogger.printException(() -> "Could not enqueue a native-folder download", failure);
            return false;
        }
    }

    static void saveMerged(Context context, Bitmap bitmap, Bitmap.CompressFormat format,
                           String mimeType, String filename) throws IOException {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, DownloadFileName.limitFileName(filename));
            values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Twitter/");
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri uri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IOException("Could not create native merged image");
            try {
                try (OutputStream out = context.getContentResolver().openOutputStream(uri, "w")) {
                    if (out == null || !bitmap.compress(format, 95, out)) throw new IOException("Could not save merged image");
                }
                values.clear();
                values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                context.getContentResolver().update(uri, values, null, null);
            } catch (IOException | RuntimeException failure) {
                context.getContentResolver().delete(uri, null, null);
                throw failure;
            }
        } else {
            File directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Twitter");
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Could not create Pictures/Twitter");
            File target = new File(directory, DownloadFileName.limitFileName(filename));
            int suffix = 0;
            while (!target.createNewFile()) {
                target = new File(directory, InlineDownloadButton.withResolutionSuffix(filename, String.valueOf(++suffix)));
            }
            try (OutputStream out = new FileOutputStream(target)) {
                if (!bitmap.compress(format, 95, out)) throw new IOException("Could not save merged image");
            } catch (IOException | RuntimeException failure) {
                target.delete();
                throw failure;
            }
            MediaScannerConnection.scanFile(context, new String[]{target.getAbsolutePath()}, new String[]{mimeType}, null);
        }
    }
}
