package dom.notescanner;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.ml.CvSVM;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class GalleryActivity extends AppCompatActivity {

    private static final String TAG = "GalleryActivity";
    ImageView imgPrevView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);
        imgPrevView = findViewById(R.id.image_view);

        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            Uri uri = Uri.parse(bundle.getString("uri"));
            new PreProcImgAsync(uri, getApplicationContext(), this).execute();
        } else {
            Toast.makeText(GalleryActivity.this, "Failed to retrieve Image!", Toast.LENGTH_SHORT).show();
        }
    }

    //takes matrix and outputs it to ImageView
    public void displayMat(Mat m, boolean isRGB) {
        if (isRGB) {
            final Bitmap bmpRGB = Bitmap.createBitmap(m.cols(), m.rows(), Bitmap.Config.RGB_565);
            Utils.matToBitmap(m, bmpRGB);
            imgPrevView.setImageBitmap(bmpRGB);
            imgPrevView.invalidate();
        } else {
            Bitmap bmpGRAY = Bitmap.createBitmap(m.cols(), m.rows(), Bitmap.Config.ARGB_8888);
            Imgproc.cvtColor(m, m, Imgproc.COLOR_GRAY2RGBA, 4);
            Utils.matToBitmap(m, bmpGRAY);
            imgPrevView.setImageBitmap(bmpGRAY);
            imgPrevView.invalidate();
        }
    }

    static class PreProcImgAsync extends AsyncTask<String, Mat, Void>  //weak reference and strong app context needs to be passed
    {
        private static final String TAG = "GALACTIVITY-ASYNCTASK";      //galactic activity :^)
        private final WeakReference<GalleryActivity> weakActivity;  //weak reference to activity
        private final WeakReference<Context> mAppContext;   //weak reference to app, not needed but in main activity it is for writing to file without ab activity
        private Uri mUri;   //URI of  the image
        Bitmap inputBitmap; //Bitmap of the Image
        Mat displayMat;   //final Matrix to display to view back in Gallery


        PreProcImgAsync(Uri uri, final Context appContext, GalleryActivity myActivity) {
            mUri = uri;
            weakActivity = new WeakReference<>(myActivity);
            mAppContext = new WeakReference<>(appContext);
        }


        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Void doInBackground(String... params) {

            Mat inMat = new Mat();  //Image matrix
            Context appCon;         //Strong reference to application context;

            //get the bitmap from URI using app context
            appCon = mAppContext.get();
            if (appCon != null) {
                try {
                    inputBitmap = MediaStore.Images.Media.getBitmap(appCon.getContentResolver(), mUri);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                Log.e(TAG, "APP CONTEXT IS NULL");
            }

            Utils.bitmapToMat(inputBitmap, inMat);  //change image to OpenCV matrix
            OcrProcessor ocrProc = new OcrProcessor(inMat);

            Thread z = null;
            Runnable r = null;
            appCon = mAppContext.get();
            if (appCon != null) {
                r = new SvmLoader(ocrProc, appCon);
                z = new Thread(r);
                z.start();
            }
            ocrProc.scaleMat(inMat);    //downscale any high resolution images
            publishProgress(inMat);             //show downscaled image to ImageView

            Mat noiseMat = inMat.clone();
            ocrProc.removeNoise(noiseMat, true); //remove noise from Image

            Mat textRegionMat = noiseMat.clone();
            List<Rect> textRegions = ocrProc.getTextRegionsRects(textRegionMat);    //retrieve regions of text
            Log.d(TAG,textRegions.size() + "TEXT REGIONS DETECTED");
            displayMat = new Mat();
            displayMat = ocrProc.displayTextRegions(noiseMat, textRegions);         //display regions to matrix

            /*Rect rio = textRegions.get(55);
            Mat cropped = new Mat(noiseMat, rio);
            Mat sneak = new Mat();
            cropped.copyTo(sneak);
            Imgproc.resize(sneak, sneak, new Size(28,28));
            Imgproc.threshold(sneak, sneak, 128, 1, Imgproc.THRESH_BINARY);
            Imgproc.dilate(sneak, sneak, Mat.ones(new Size(2, 2), 0));
            sneak.convertTo(sneak, CvType.CV_32FC1);
            Mat fin = sneak.reshape(1,1);

            long time = System.currentTimeMillis();
            try {
                z.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            Log.d(TAG,"ASYNC WAITING TIME =  " + (float)((System.currentTimeMillis() - time) / 1000));
            CvSVM svm = ((SvmLoader) r).getSvm();


            Log.d(TAG, "ASYNC SVM = " + svm.get_support_vector_count());
            double f = svm.predict(fin);

            Log.d(TAG, "CROPPED " + sneak.rows() + "x" + sneak.cols() + "-" + sneak.isContinuous()+ "PREDICTION: " + f);
            System.out.println(sneak.dump());*/

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            Activity activity = weakActivity.get(); //get strong reference to Gallery activity

            //Don't update if activity not found;
            if (activity == null || activity.isFinishing() || activity.isDestroyed())
                return;

            ((GalleryActivity) activity).displayMat(displayMat, true);
        }

        @Override
        protected void onProgressUpdate(Mat... values) {
            super.onProgressUpdate(values);
            Activity activity = weakActivity.get(); //get strong reference to Gallery activity

            // Don't update if activity not found
            if (activity == null || activity.isFinishing() || activity.isDestroyed())
                return;

            ((GalleryActivity) activity).displayMat(values[0], true);
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
        }

        class SvmLoader implements Runnable{
            private OcrProcessor ocrProcessor;
            private Context appContext;
            CvSVM tsvm;

            public SvmLoader(OcrProcessor o, Context c){
                ocrProcessor = o;
                appContext = c;
            }

            public void run(){
                tsvm = ocrProcessor.loadSVM(appContext);
            }

            public CvSVM getSvm() {
                return tsvm;
            }

        }
    }

}
