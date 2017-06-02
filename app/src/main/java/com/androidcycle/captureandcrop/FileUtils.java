package com.androidcycle.captureandcrop;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class FileUtils {
    /**
     * 复制content uri指定的文件到另一个content uri指定的文件
     *
     * @param context Context
     * @param fromUri 源文件 Content Uri
     * @param toUri   目标文件Content Uri
     */
    public static void copyFileFromProviderToSelfProvider(Context context, Uri fromUri, Uri toUri) {
        InputStream inputStream = null;
        OutputStream outputStream = null;
        try {
            final ContentResolver contentResolver = context.getContentResolver();
            inputStream = contentResolver.openInputStream(fromUri);
            outputStream = contentResolver.openOutputStream(toUri);
            final byte[] buffer = new byte[8192];
            while (inputStream.read(buffer) != -1) {
                outputStream.write(buffer);
                outputStream.flush();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
