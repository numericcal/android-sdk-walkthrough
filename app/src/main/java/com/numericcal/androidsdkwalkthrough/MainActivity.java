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

public class MainActivity extends AppCompatActivity {


    static final String TAG = "AndroidSdkWalkthrough";
    static final String DATA_SUFFIX = ".jpg";

    ////////////////////////////////////////////////////////////////////
    // create a netManager field

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


        // get a list of image file names in assets and load the corresponding images
        List<String> sampleImageNames = Utils.getAssetImageNames(getBaseContext(),
                                                                 DATA_SUFFIX);
        List<Bitmap> sampleImages = Utils.loadImages(getAssets(), sampleImageNames);

        ////////////////////////////////////////////////////////////////////
        // create a handle and extract the network receptive field

        Single<Pair<Integer, Integer>> hw = Single.just(new Pair<>(224,224));


        Observable<Integer> ticker = periodicIndexChange(sampleImages.size()).share();
        Observable<Bitmap> img = pickAndCrop(ticker, hw, sampleImages).share();

        showImage(img, camView);
        printImageFileName(ticker, sampleImageNames, fileText);

        ////////////////////////////////////////////////////////////////////
        // set up the inference chain

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

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy()");
        super.onDestroy();
    }

    ////////////////////////////////////////////////////////////////////
    // create a signal processing chain using DNN for inference


    ////////////////////////////////////////////////////////////////////
    // configure a dnn model and create a handle


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
                            Log.i(TAG, "Error received in showImage(): " + thr.toString());
                            thr.printStackTrace();
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
                }, (Throwable thr) -> {Log.i(TAG, "Error received in printImageFileName(): " + thr.toString());});
    }

}