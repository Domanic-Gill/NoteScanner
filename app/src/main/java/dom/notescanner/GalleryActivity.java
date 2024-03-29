package dom.notescanner;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.ml.CvSVM;
import org.w3c.dom.Text;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import static org.opencv.core.Core.bitwise_not;
import static org.opencv.core.Core.countNonZero;
import static org.opencv.core.Core.line;

public class GalleryActivity extends AppCompatActivity {

    private static final String TAG = "GalleryActivity";
    ImageView imgPrevView;
    Button cancelButton, acceptButton;
    CheckBox linesCB, segCB, morphCB;
    TextView loadingTV;
    String sUri;
    Uri uri;
    boolean isCamera;
    Bitmap camPhoto;
    PreProcImgAsync a;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);
        imgPrevView = findViewById(R.id.image_view);
        cancelButton = findViewById(R.id.cancelButton);
        acceptButton = findViewById(R.id.acceptButton);
        linesCB = findViewById(R.id.CB_lines);
        segCB = findViewById(R.id.CB_segment);
        morphCB = findViewById(R.id.CB_morph);
        loadingTV = findViewById(R.id.loadingTV);

        linesCB.setClickable(false);
        segCB.setClickable(false);
        acceptButton.setClickable(false);
        morphCB.setClickable(false);

        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            sUri = bundle.getString("uri");
            isCamera = bundle.getBoolean("isCamImg");
            if (isCamera) {
                uri = Uri.parse(bundle.getString("uri"));
                camPhoto = BitmapFactory.decodeFile(uri.toString());
            } else {
                camPhoto = null;
                uri = Uri.parse(bundle.getString("uri"));
            }
            startPreProcessing();
        } else {
            Toast.makeText(GalleryActivity.this, "Failed to retrieve Image!", Toast.LENGTH_SHORT).show();
        }
    }

    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.acceptButton:
                Intent i = new Intent();
                i.putExtra("removeLines", linesCB.isChecked());
                i.putExtra("wideText",morphCB.isChecked());
                i.putExtra("isCamImg", isCamera);
                i.putExtra("uri", sUri);
                setResult(RESULT_OK, i);
                finish();
                break;
            case R.id.cancelButton:
                onBackPressed();
                break;
            case R.id.CB_lines:
                if (linesCB.isClickable())
                    startPreProcessing();
                break;
            case R.id.CB_segment:
                if (segCB.isClickable())
                    startPreProcessing();
                break;
            case R.id.CB_morph:
                if (morphCB.isClickable())
                    startPreProcessing();
                break;
        }
    }

    private void startPreProcessing() {
        linesCB.setClickable(false);
        acceptButton.setClickable(false);
        morphCB.setClickable(false);

        linesCB.setTextColor(getResources().getColor(R.color.colorTextGrey));
        segCB.setTextColor(getResources().getColor(R.color.colorTextGrey));
        morphCB.setTextColor(getResources().getColor(R.color.colorTextGrey));
        acceptButton.setTextColor(getResources().getColor(R.color.colorTextGrey));

        loadingTV.setText(getResources().getString(R.string.text_processing));
        if (isCamera) {
            a = new PreProcImgAsync(null, camPhoto, getApplicationContext(), this,
                    linesCB.isChecked(), segCB.isChecked(), morphCB.isChecked());
            a.execute();
            //new PreProcImgAsync(null, camPhoto, getApplicationContext(), this, removeLines).execute();
        } else {
            a =  new PreProcImgAsync(uri, null, getApplicationContext(), this,
                    linesCB.isChecked(), segCB.isChecked(), morphCB.isChecked());
            a.execute();
           // new PreProcImgAsync(uri, null, getApplicationContext(), this, removeLines).execute();
        }

    }

    @Override
    public void onBackPressed() {
        a.cancel(true);
        File file = new File(uri.toString());
        boolean deleted = file.delete();
        if (deleted) Log.d(TAG, "deleted = " + deleted);
        finish();
    }

    public void enableButtons() {
        linesCB.setClickable(true);
        linesCB.setTextColor(getResources().getColor(R.color.colorText));
        segCB.setClickable(true);
        segCB.setTextColor(getResources().getColor(R.color.colorText));
        morphCB.setClickable(true);
        morphCB.setTextColor(getResources().getColor(R.color.colorText));
        acceptButton.setClickable(true);
        acceptButton.setTextColor(getResources().getColor(R.color.colorText));
        loadingTV.setText(getResources().getString(R.string.text_processed));
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
        private static final String TAG = "GALACTIVITY-ASYNCTASK";  //debug tag
        private final WeakReference<GalleryActivity> weakActivity;  //weak reference to activity
        private final WeakReference<Context> mAppContext;   //weak reference to app, not needed but in main activity it is for writing to file without an activity
        private Uri mUri;   //URI of  the image
        Bitmap inputBitmap; //Bitmap of the Image
        Mat displayMat;   //final Matrix to display to view back in Gallery
        Boolean removeLines, showSegLines, isWideText;


        PreProcImgAsync(Uri uri, Bitmap cameraImage, final Context appContext, GalleryActivity myActivity, boolean removeLines, boolean segLines, boolean wideText) {
            mUri = uri;
            weakActivity = new WeakReference<>(myActivity);
            mAppContext = new WeakReference<>(appContext);
            this.removeLines = removeLines;
            this.showSegLines = segLines;
            inputBitmap = cameraImage;
            isWideText = wideText;
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
            if (inputBitmap == null) {
                appCon = mAppContext.get();
                if (appCon != null) {
                    try {
                        inputBitmap = MediaStore.Images.Media.getBitmap(appCon.getContentResolver(), mUri);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            Utils.bitmapToMat(inputBitmap, inMat);  //change image to OpenCV matrix
            OcrProcessor ocrProc = new OcrProcessor(inMat);

            Mat scaleMat = ocrProc.scaleMat(inMat);     //downscale any high resolution images
            inMat.release();                            //release original image from memory.

            publishProgress(scaleMat);                  //show downscaled image to ImageView

            Mat noiseMat = ocrProc.removeNoise(scaleMat, removeLines); //remove noise from Image

            Mat textRegionMat = noiseMat.clone();
            List<Rect> textRegions = ocrProc.getTextRegionsRects(textRegionMat, isWideText);    //retrieve regions of text
            Log.d(TAG, textRegions.size() + "TEXT REGIONS DETECTED");

            ArrayList<TextObject> words = ocrProc.generateTextObject(textRegions);

            for (int i = 0; i < words.size(); i++) {
                Rect wordRegion = words.get(i).getWord();
                words.get(i).setSegColumns(ocrProc.segmentToLetters(wordRegion, noiseMat));
            }

            Mat tmp = ocrProc.displayTextRegions2(noiseMat.clone(), words, showSegLines);
            displayMat = new Mat();
            displayMat = tmp.clone();       //display regions to matrix
            tmp.release();

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            GalleryActivity activity = weakActivity.get(); //get strong reference to Gallery activity
            boolean isRGB = displayMat.channels() > 1;

            //Don't update if activity not found;
            if (activity == null || activity.isFinishing() || activity.isDestroyed())
                return;

            activity.displayMat(displayMat, isRGB);
            activity.enableButtons();
            displayMat.release();

        }

        @Override
        protected void onProgressUpdate(Mat... values) {
            super.onProgressUpdate(values);
            GalleryActivity activity = weakActivity.get(); //get strong reference to Gallery activity

            // Don't update if activity not found
            if (activity == null || activity.isFinishing() || activity.isDestroyed())
                return;
            //race condition check, possibility that mat is released before progress is updated
            if (values[0].cols() != 0 && values[0].rows() != 0)
                activity.displayMat(values[0], true);
        }

        @Override
        protected void onCancelled() {
            //TODO: ocrproc signal global boolean cancelled, only for use in text regions
            super.onCancelled();
        }

        class SvmLoader implements Runnable {
            private OcrProcessor ocrProcessor;
            private Context appContext;
            CvSVM tsvm;

            public SvmLoader(OcrProcessor o, Context c) {
                ocrProcessor = o;
                appContext = c;
            }

            public void run() {
                tsvm = ocrProcessor.loadSVM(appContext);
            }

            public CvSVM getSvm() {
                return tsvm;
            }

        }
    }

}
