package dom.notescanner;

import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;

public class GalleryActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    ImageView imageView;
    Bitmap inputBitmap, resultBitmap;
    BaseLoaderCallback mCallBack = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case BaseLoaderCallback.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");
                    break;
                }
                default: {
                    super.onManagerConnected(status);
                    break;
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);
        imageView = findViewById(R.id.image_view);
        Bundle bundle = getIntent().getExtras();
        Uri uri = Uri.parse(bundle.getString("uri"));
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_13, this, mCallBack); // Start OpenCV

        //get the bitmap from URI
        try {
             inputBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), uri);
        } catch (IOException e) {
            e.printStackTrace();
        }

        //change image to bitmap
        Mat inputMat = new Mat();
        Utils.bitmapToMat(inputBitmap, inputMat);

        //rescale matrix
        Size size = new Size(1800, 1800);
        Mat scaleMat = new Mat();
        Imgproc.resize(inputMat, scaleMat, size);

        //Noise reduction
        Imgproc.cvtColor(scaleMat, scaleMat, Imgproc.COLOR_BGR2GRAY);       //change to greyscale
       // Imgproc.GaussianBlur(scaleMat,scaleMat,new Size(3,3), 0);
        //Imgproc.threshold(scaleMat,scaleMat, 0, 255, Imgproc.THRESH_OTSU);
        Imgproc.adaptiveThreshold(scaleMat,scaleMat, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,Imgproc.THRESH_BINARY,15,4); //originally blocksize = 11, c = 2
        Imgproc.threshold(scaleMat,scaleMat, 0, 255, Imgproc.THRESH_BINARY+Imgproc.THRESH_OTSU);
       // Imgproc.adaptiveThreshold(scaleMat, scaleMat, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, 15, 40);  //originally c=40

       // Imgproc.erode(scaleMat, scaleMat, Mat.ones(new Size(2,2), 0));
        //Imgproc.erode(scaleMat, scaleMat, Mat.ones(new Size(3,3), 0));
        // Imgproc.dilate(scaleMat, scaleMat, Mat.ones(new Size(3,3), 0));
        Bitmap bmpOut = Bitmap.createBitmap(scaleMat.cols(), scaleMat.rows(), Bitmap.Config.ARGB_8888);
        Imgproc.cvtColor(scaleMat, scaleMat, Imgproc.COLOR_GRAY2RGBA,4);
        Utils.matToBitmap(scaleMat, bmpOut);
        imageView.setImageBitmap(bmpOut);

    }

}
