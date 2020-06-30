package com.tessocr;

import android.app.ProgressDialog;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class OcrProcessHelper {
    private static final String TAG = OcrProcessHelper.class.getSimpleName();
    private TessBaseAPI mTessOCR;
    private ProgressDialog progressDialog;
    private OcrResultCallback ocrResultCallback;
    private String ocrText;
    private static final String DATA_PATH = Environment.getExternalStorageDirectory().toString() + "/TesseractSample/";
    private static final String TESSDATA = "tessdata";

    private void copyTessDataFiles(@NonNull String path, @NonNull AppCompatActivity appCompatActivity) {
        AssetManager assetManager = appCompatActivity.getAssets();
        String[] files = null;
        try {
            files = assetManager.list("");
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (files == null) {
            return;
        }

        for (String filename : files) {
            InputStream in;
            OutputStream out;
            try {
                in = assetManager.open(filename);
                File outFile = new File(path, filename);
                out = new FileOutputStream(outFile);
                copyFile(in, out);
                in.close();
                out.flush();
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        dismissProgressDialog();
    }

    private void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    private void prepareDirectory(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                Log.e(TAG, "ERROR: Creation of directory " + path +
                        " failed, check does Android Manifest have permission to write to external storage.");
            }
        } else {
            Log.i(TAG, "Created directory " + path);
        }
    }

    public void prepareTesseract(@NonNull AppCompatActivity appCompatActivity) {
        showProgressDialog(appCompatActivity, "Coping Required Files...");
        try {
            prepareDirectory(DATA_PATH + TESSDATA);
            copyTessDataFiles(DATA_PATH + TESSDATA, appCompatActivity);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public OcrProcessHelper(@NonNull AppCompatActivity appCompatActivity, @NonNull OcrResultCallback ocrResultCallback) {
        this.ocrResultCallback = ocrResultCallback;
    }

    public void processBitmapImage(@NonNull final AppCompatActivity appCompatActivity, @NonNull final Bitmap bitmap) {
        showProgressDialog(appCompatActivity, "Doing OCR...");
        new Thread(new Runnable() {
            public void run() {
                Bitmap grayscaleImg = setGrayscale(bitmap);
                Bitmap noiceRemovedImage = removeNoise(grayscaleImg);
                ocrText = extractText(noiceRemovedImage);
                appCompatActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog.dismiss();
                        ocrResultCallback.onOcrResult(ocrText);
                    }
                });
            }
        }).start();
    }

    // SetGrayscale
    private Bitmap setGrayscale(Bitmap img) {
        Bitmap bmap = img.copy(img.getConfig(), true);
        int c;
        for (int i = 0; i < bmap.getWidth(); i++) {
            for (int j = 0; j < bmap.getHeight(); j++) {
                c = bmap.getPixel(i, j);
                byte gray = (byte) (.299 * Color.red(c) + .587 * Color.green(c) + .114 * Color.blue(c));
                bmap.setPixel(i, j, Color.argb(255, gray, gray, gray));
            }
        }
        return bmap;
    }

    // RemoveNoise
    private Bitmap removeNoise(Bitmap bmap) {
        for (int x = 0; x < bmap.getWidth(); x++) {
            for (int y = 0; y < bmap.getHeight(); y++) {
                int pixel = bmap.getPixel(x, y);
                if (Color.red(pixel) < 162 && Color.green(pixel) < 162 && Color.blue(pixel) < 162) {
                    bmap.setPixel(x, y, Color.BLACK);
                }
            }
        }
        for (int x = 0; x < bmap.getWidth(); x++) {
            for (int y = 0; y < bmap.getHeight(); y++) {
                int pixel = bmap.getPixel(x, y);
                if (Color.red(pixel) > 162 && Color.green(pixel) > 162 && Color.blue(pixel) > 162) {
                    bmap.setPixel(x, y, Color.WHITE);
                }
            }
        }
        return bmap;
    }

    private String extractText(Bitmap bitmap) {
        try {
            mTessOCR = new TessBaseAPI();
            mTessOCR.setDebug(true);
        } catch (Exception e) {
            e.printStackTrace();
            if (mTessOCR == null) {
                Log.e(TAG, "TessBaseAPI is null. TessFactory not returning tess object.");
            }
        }
        mTessOCR.init(DATA_PATH, "eng");
        Log.d(TAG, "Training file loaded");
        mTessOCR.setImage(bitmap);
        String extractedText = "Empty/Null Result";
        try {
            extractedText = mTessOCR.getUTF8Text();
        } catch (Exception e) {
            Log.e(TAG, "Error in recognizing text.");
        }
        mTessOCR.end();
        return extractedText;
    }

    private void showProgressDialog(@NonNull AppCompatActivity appCompatActivity, String msg) {
        if (progressDialog == null) {
            progressDialog = ProgressDialog.show(appCompatActivity, "Processing", msg, true);
        } else {
            progressDialog.setMessage(msg);
            progressDialog.show();
        }
    }

    private void dismissProgressDialog() {
        if (progressDialog != null) {
            progressDialog.dismiss();
        }
    }

    public interface OcrResultCallback {
        void onOcrResult(String text);
    }
}
