package dom.notescanner;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.ml.CvSVM;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;

import static java.security.AccessController.getContext;

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
            new PreProcImgAsync(this, uri).execute();
        } else {
            Toast.makeText(GalleryActivity.this,"Failed to retrieve Image!", Toast.LENGTH_SHORT).show();
        }

    }

    //takes matrix and outputs it to view
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

    private class PreProcImgAsync extends AsyncTask<String, Mat, Void>
    {
        private Uri mUri;   //URI of  the image
        private final Context mContext;
        Bitmap inputBitmap; //Bitmap of the Image
        Mat finalMat;   //final Matrix to display to view back in Gallery

        PreProcImgAsync(Context c, Uri uri) {
            mContext = c;
            mUri = uri;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Void doInBackground(String... params) {

            Mat inMat = new Mat();  //Image matrix

            try {   //get the bitmap from URI
                inputBitmap = MediaStore.Images.Media.getBitmap(mContext.getContentResolver(), mUri);    //do with strong private reference to application context
            } catch (IOException e) {
                e.printStackTrace();
            }

            //change image to OpenCV matrix
            Utils.bitmapToMat(inputBitmap, inMat);

            OcrProcessor ocrProc = new OcrProcessor(inMat);
            inMat = ocrProc.scaleMat(inMat);    //downscale any high resolution images
            publishProgress(inMat);             //show downscaled image to ImageView

            finalMat = ocrProc.removeNoise(inMat,true); //remove noise from Image

            return null;
        }
        @Override
        protected void onPostExecute(Void result) {
            displayMat(finalMat, false);
        }

        @Override
        protected void onProgressUpdate(Mat... values) {
            super.onProgressUpdate(values);
            displayMat(values[0], true);
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
        }
    }

    //static class GalProcImgAsync extends AsyncTask

}
