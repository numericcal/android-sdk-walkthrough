package com.numericcal.androidsdkwalkthrough;

import android.graphics.Bitmap;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.widget.ImageView;
import android.widget.TextView;

import com.uber.autodispose.AutoDispose;
import com.uber.autodispose.android.lifecycle.AndroidLifecycleScopeProvider;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

////////////////////////////////////////////////////////////////////
// add the numericcal Edge SDK imports below
import com.numericcal.dnn.Config;
import com.numericcal.dnn.Manager;
import com.numericcal.dnn.Handle;

public class MainActivity extends AppCompatActivity {


    static String TAG = "AndroidSdkWalkthrough";
    static String DATA_SUFFIX = ".jpg";

    ////////////////////////////////////////////////////////////////////
    // create a netManager field
    Manager.Dnn netManager = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.i(TAG, "onCreate()");
    }

    @Override
    protected void onResume() {
        super.onResume();
        ImageView camView = findViewById(R.id.camView);
        TextView fileText = findViewById(R.id.fileText);
        TextView labelText = findViewById(R.id.labelText);
        TextView labelTextExtraA = findViewById(R.id.labelTextExtraA);
        TextView labelTextExtraB = findViewById(R.id.labelTextExtraB);
        Log.i(TAG, "onResume()");

        ////////////////////////////////////////////////////////////////////
        // initialize netManager field
        //
        // try not to grab this reference from the Manager directly in other places;
        // while Manager is implemented as Singleton due to the constraints that Android
        // imposes, it is better to propagate netManager reference explicitly
        netManager = Manager.create(getApplicationContext());

        // get a list of image file names in assets and load the corresponding images
        List<String> sampleImageNames = Utils.getAssetImageNames(getBaseContext(),
                                                                 DATA_SUFFIX);
        List<Bitmap> sampleImages = Utils.loadImages(getAssets(), sampleImageNames);

        ////////////////////////////////////////////////////////////////////
        // create a handle and extract the network receptive field
        Single<Handle.Rx> dnn =
                configAndCreateDnnHandle("mobilenet_1.0_224").cache();
        //Single<Pair<Integer, Integer>> hw = Single.just(new Pair<>(224,224));
        Single<Pair<Integer, Integer>> hw = dnn
                .map(handle -> new Pair<>(
                        handle.info.inputShape().get(1),
                        handle.info.inputShape().get(2)));

        Observable<Integer> ticker = periodicIndexChange(sampleImages.size()).share();
        Observable<Bitmap> img = pickAndCrop(ticker, hw, sampleImages).share();

        showImage(img, camView);
        printImageFileName(ticker, sampleImageNames, fileText);

        ////////////////////////////////////////////////////////////////////
        // set up the inference chain
        runInferenceAndLabel(dnn, img, labelText);

        ////////////////////////////////////////////////////////////////////
        // set up the extra inference chains

    }

    @Override
    protected void onPause() {
        Log.i(TAG, "onPause()");

        ////////////////////////////////////////////////////////////////////
        // release the netManager to release resources
        //
        // we should not release the manager if we're only changing configuration;
        // otherwise it will spend time re-loading the networks in onResume
        if (!isChangingConfigurations() && netManager != null) {
            Log.i(TAG, "seems to be going in background ...");
            netManager.release();
            netManager = null;
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy()");
        super.onDestroy();
    }

    ////////////////////////////////////////////////////////////////////
    // create a signal processing chain using DNN for inference
    private void runInferenceAndLabel(Single<Handle.Rx> dnn,
                                      Observable<Bitmap> img,
                                      TextView labelText) {
        final int MEAN = 128;
        final float STD = 128.0f;

        dnn
                .flatMapObservable(hdl -> {
                    int height = hdl.info.inputShape().get(1);
                    int width = hdl.info.inputShape().get(2);
                    return img
                            .observeOn(Schedulers.computation())
                            .map(bm -> Utils.pixToFloatBuff(bm, width, height, MEAN, STD))
                            .compose(hdl.runInference()) // <== all it takes to run inference
                            .map(Utils::argMaxPositive)
                            .map(miv ->  "[" + hdl.info.modelId() + "] Inference label: " + hdl.info.labels().get(miv.first));
                })
                .observeOn(AndroidSchedulers.mainThread())
                .doOnDispose(() -> Log.i(TAG, "AutoDispose of the label ticker ..."))
                .as(AutoDispose.autoDisposable(AndroidLifecycleScopeProvider.from(this)))
                .subscribe(labelText::setText,
                        thr -> {Log.e(TAG, "Error received in runInferenceAndLabel(): " + thr.toString());});
    }

    ////////////////////////////////////////////////////////////////////
    // configure a dnn model and create a handle
    private Single<Handle.Rx> configAndCreateDnnHandle(String modelId) {

        Config.Model netCfg = Config
                .model(modelId)
                .downloadUpdates(false)    // check for model updates (disabled in alpha)
                .reportPerformance(false); // upload profiling results (ditto)

        return netManager.createHandle(netCfg);
    }

    /*
     * Everything below is GUI related.
     */
    private Observable<Integer> periodicIndexChange(Integer len) {
        Integer INTERVAL = 30;
        Integer MULTIPLIER = 100;
        Random rand = new Random();

        return Observable
                .interval(INTERVAL, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.io())
                .filter(i -> (i % MULTIPLIER) == 1)
                .map(x -> rand.nextInt(len));
    }

    private Observable<Bitmap> pickAndCrop(Observable<Integer> ticker,
                                           Single<Pair<Integer,Integer>> hw,
                                           List<Bitmap> images) {
        return hw
                .flatMapObservable(pair -> ticker
                        .observeOn(Schedulers.computation())
                        .map(images::get)
                        .map(bm -> Bitmap.createScaledBitmap(bm, pair.first, pair.second, true)));

    }

    private void showImage(Observable<Bitmap> bm, ImageView camView) {
        bm
                .observeOn(AndroidSchedulers.mainThread())
                .doOnDispose(() -> Log.i(TAG, "AutoDispose of the bitmap ticker ..."))
                .as(AutoDispose.autoDisposable(AndroidLifecycleScopeProvider.from(this)))
                .subscribe(camView::setImageBitmap,
                        (Throwable thr) -> {
                            Log.e(TAG, "Error received in showImage(): " + thr.toString());
                        });
    }

    private void printImageFileName(Observable<Integer> ticker,
                                    List<String> imageNames,
                                    TextView fileNameView) {
        ticker
                .observeOn(AndroidSchedulers.mainThread())
                .doOnDispose(() -> Log.i(TAG, "AutoDispose of the filename ticker ..."))
                .as(AutoDispose.autoDisposable(AndroidLifecycleScopeProvider.from(this)))
                .subscribe((Integer i) -> {
                    fileNameView.setText("Filename: " + imageNames.get(i));
                }, (Throwable thr) -> {Log.e(TAG, "Error received in printImageFileName(): " + thr.toString());});
    }
}