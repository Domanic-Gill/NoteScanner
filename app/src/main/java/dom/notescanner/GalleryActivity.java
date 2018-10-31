package dom.notescanner;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;

public class GalleryActivity extends AppCompatActivity {
    private static final String TAG = "GalleryActivity";
    private static final float AR_1610 = (float) 1.6;        //aspect ratio 16:10
    private static final float AR_43 = (float) 1.33;          //aspect ratio 4:3
    private
    ImageView imageView;
    Bitmap inputBitmap;

    int scaleRows, scaleCols;
    float aspectRatio;
    boolean openCVLoaded = false;

    BaseLoaderCallback mCallBack = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case BaseLoaderCallback.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");
                    openCVLoaded =  true;
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
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_13, this, mCallBack);

        //get the image
        Bundle bundle = getIntent().getExtras();
        Uri uri = Uri.parse(bundle.getString("uri"));
        try {   //get the bitmap from URI
            inputBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), uri);
        } catch (IOException e) {
            e.printStackTrace();
        }

        //change image to bitmap and set aspect ratio
        Mat inMat = new Mat();
        Utils.bitmapToMat(inputBitmap, inMat);
        aspectRatio = (float) inMat.rows() / (float) inMat.cols();
        if (aspectRatio < 1) {
            aspectRatio = (float) inMat.cols() / (float) inMat.rows();
        }

        //Scale image to 1080 for easier pre-processing
        //if image is HD+ portrait image then rescale to 1080p, else for landscape
        if (inMat.rows() > inMat.cols() && inMat.rows() > 1920) {   //if image portrait
            if (aspectRatio > 1.7) {                //if image 16:9
                scaleRows = inMat.rows() - 1920;
                scaleCols = inMat.cols() - 1080;
            } else {                                //if image 4:3
                scaleRows = inMat.rows() - 1856;
                scaleCols = inMat.cols() - 1392;
            }
            Size size = new Size(inMat.cols() - scaleCols,
                    inMat.rows() - scaleRows);
            Imgproc.resize(inMat, inMat, size);
        } else if (inMat.rows() < inMat.cols() && inMat.cols() > 1920) {    //if image landscape
            if (aspectRatio > 1.7) {                //if image 16:9
                scaleRows = inMat.rows() - 1080;
                scaleCols = inMat.cols() - 1920;
            } else {                                //if image 4:3
                scaleRows = inMat.rows() - 1392;
                scaleCols = inMat.cols() - 1856;
            }
            Size size = new Size(inMat.cols() - scaleCols,
                    inMat.rows() - scaleRows);
            Imgproc.resize(inMat, inMat, size);
        }
        Bitmap bmpOut = Bitmap.createBitmap(inMat.cols(), inMat.rows(), Bitmap.Config.ARGB_8888);
        Imgproc.cvtColor(inMat, inMat, Imgproc.CV_RGBA2mRGBA, 4);
        Utils.matToBitmap(inMat, bmpOut);
        imageView.setImageBitmap(bmpOut);
        imageView.invalidate();
        //SystemClock.sleep(2000);
        /*------------------------------------NOISE REDUCTION------------------------------------*/
        //threshold image to remove noise and invert image for line removal
        Imgproc.cvtColor(inMat, inMat, Imgproc.COLOR_BGR2GRAY); //change to greyscale
        Imgproc.adaptiveThreshold(inMat, inMat, 255,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY, 15, 5); //originally blocksize = 11, c = 2
        // Imgproc.erode(inputMatrix, inputMatrix, Mat.ones(new Size(2,2), 0));
        //Imgproc.erode(inputMatrix, inputMatrix, Mat.ones(new Size(3,3), 0));
        // Imgproc.dilate(inputMatrix, inputMatrix, Mat.ones(new Size(3,3), 0));

        /*-------------------------------------LINE REMOVAL-------------------------------------*/
        Core.bitwise_not(inMat, inMat); //invert the matrix
        Mat hor = inMat.clone();
        Mat ver = inMat.clone();

        //30 sharper on 16:9, 30 on 4:3 is too low so we double it
        int hs = (aspectRatio >= AR_1610) ? hor.cols() / 30 : hor.cols() / 60;
        System.out.println("HS = " + hs);
        Mat horStruct = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(hs, 1));
        Imgproc.erode(hor, hor, horStruct, new Point(-1, -1), 1);
        Imgproc.dilate(hor, hor, horStruct, new Point(-1, -1), 1);
        Core.subtract(inMat, hor, inMat);

        //now remove vertical lines (column bar, etc)
        int vs = (aspectRatio >= AR_1610) ? hor.rows() / 30 : hor.rows() / 60;
        Mat verStruct = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(1,vs));
        Imgproc.erode(ver, ver, verStruct, new Point (-1, -1), 1);
        Imgproc.dilate(ver, ver, verStruct, new Point (-1, -1), 1);
        Core.subtract(inMat, ver, inMat);

        Core.bitwise_not(inMat, inMat);
        Imgproc.adaptiveThreshold(inMat, inMat, 255,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY, 11, 2); //originally blocksize = 11, c = 2


        displayMat(inMat, imageView);
    }

    public void displayMat(Mat m, ImageView v) {
        Bitmap bmpOut = Bitmap.createBitmap(m.cols(), m.rows(), Bitmap.Config.ARGB_8888);
        Imgproc.cvtColor(m, m, Imgproc.COLOR_GRAY2RGBA, 4);
        Utils.matToBitmap(m, bmpOut);
        v.setImageBitmap(bmpOut);
        v.invalidate();
    }

}
