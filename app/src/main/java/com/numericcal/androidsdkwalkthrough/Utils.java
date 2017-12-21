package com.numericcal.androidsdkwalkthrough;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.util.Pair;

import com.numericcal.dnn.Manager;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Trivial helpers.
 */

public class Utils {
    private static final String TAG = "Utils";

    public static Pair<Integer, Float> argMaxPositive(float[] arr) {
        Integer i = 0;
        Integer mIdx = 0;
        float mVal = 0.0f;

        for (float x : arr) {
            if (x > mVal) {
                mVal = x;
                mIdx = i;
            }
            i += 1;
        }
        return new Pair<>(mIdx, mVal);
    }

    static int red(int pix) { return (pix >> 16) & 0xFF; }
    static int green(int pix) { return (pix >> 8) & 0xFF; }
    static int blue(int pix) { return pix & 0xFF; }

    public static void ibuff2fbuff(int[] ibuff, int mean, float std, float[] fbuff) {
        for (int i = 0; i < ibuff.length; ++i) {
            int val = ibuff[i];
            fbuff[i * 3 + 0] = (red(val) - mean) / std;
            fbuff[i * 3 + 1] = (green(val) - mean) / std;
            fbuff[i * 3 + 2] = (blue(val) - mean) / std;
        }
    }

    public static List<String> getAssetImageNames(Context ctx, String suffix) {
        try {
            List <String> sampleImages = new ArrayList<>();
            for (String fname: ctx.getAssets().list("")) {
                if (fname.endsWith(suffix)) {
                    sampleImages.add(fname);
                }
            }
            Log.i(TAG, "Working with examples: " + sampleImages);
            return sampleImages;
        } catch (IOException ioex) {
            Log.i(TAG, "No exaple (.jpg) files.");
            throw new RuntimeException("Nothing to do");
        }
    }

    public static List<Bitmap> loadImages(AssetManager ast, List<String> names) {
        List<Bitmap> imgs = new ArrayList<>();
        for (String name: names) {
            try (InputStream ins = ast.open(name)) {
                imgs.add(BitmapFactory.decodeStream(ins));
            } catch (IOException ioex) {
                // just drop non-existing files
                Log.i(TAG, "Missing: " + name);
            }
        }
        return imgs;
    }

    public static Integer maxInt(Integer x, Integer y) {
        if (x > y) return x; else return y;
    }

    public static float[] pixToFloatBuff(Bitmap bm, Integer width, Integer height, Integer mean, float std) {
        int size = height * width;

        int[] ibuff = new int[size];
        float[] fbuff = new float[3 * size]; // rgb, each a float

        Bitmap bmScaled = Bitmap.createScaledBitmap(bm, width, height, true);

        bmScaled.getPixels(ibuff, 0, width, bmScaled.getWidth()/2 - width/2, bmScaled.getHeight()/2 - height/2, width, height);
        ibuff2fbuff(ibuff, mean, std, fbuff);

        return fbuff;
    }

    public static Bitmap centerCrop(Bitmap bm, Integer viewWidth, Integer viewHeight) {
        return Bitmap.createBitmap(bm, // this will throw if out of bounds
                bm.getWidth()/2 - viewWidth/2,
                bm.getHeight()/2 - viewHeight/2,
                viewWidth, viewHeight);
    }
}
