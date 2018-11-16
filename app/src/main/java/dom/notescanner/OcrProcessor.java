package dom.notescanner;

import android.util.Log;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

import static org.opencv.core.Core.FILLED;
import static org.opencv.core.Core.countNonZero;
import static org.opencv.core.Core.line;

class OcrProcessor {
    private static final String TAG = "OCRPROC";
    private float aspectRatio;  //Aspect Ratio of image
    private static final float AR_1610 = (float) 1.6;        //aspect ratio 16:10
    private static final float AR_43 = (float) 1.33;          //aspect ratio 4:3

    OcrProcessor(Mat m) {
        aspectRatio = (float) m.rows() / (float) m.cols();
        if (aspectRatio < 1) {
            aspectRatio = (float) m.cols() / (float) m.rows();
        }
    }

    /* Downscales High resolution images to 1080 for easier pre-processing */
    Mat scaleMat(Mat m) {
        int scaleRows, scaleCols;

        if (m.rows() > m.cols() && m.rows() > 1920) {   //if image portrait
            if (aspectRatio > 1.7) {                //if image 16:9
                scaleRows = m.rows() - 1920;
                scaleCols = m.cols() - 1080;
            } else {                                //if image 4:3
                scaleRows = m.rows() - 1856;
                scaleCols = m.cols() - 1392;
            }
            Size size = new Size(m.cols() - scaleCols,
                    m.rows() - scaleRows);
            Imgproc.resize(m, m, size);
        } else if (m.rows() < m.cols() && m.cols() > 1920) {    //if image landscape
            if (aspectRatio > 1.7) {                //if image 16:9
                scaleRows = m.rows() - 1080;
                scaleCols = m.cols() - 1920;
            } else {                                //if image 4:3
                scaleRows = m.rows() - 1392;
                scaleCols = m.cols() - 1856;
            }
            Size size = new Size(m.cols() - scaleCols,
                    m.rows() - scaleRows);
            Imgproc.resize(m, m, size);
        }
        return m;
    }

    /*Use thresholding to convert noisy image to clean binary image */
    Mat removeNoise(Mat m, boolean removeLines) {
        //threshold image to remove noise and invert image for line removal
        Imgproc.cvtColor(m, m, Imgproc.COLOR_BGR2GRAY); //change to greyscale
        Imgproc.adaptiveThreshold(m, m, 255,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY, 15, 5); //originally blocksize = 11, c = 2
        // Imgproc.erode(inputMatrix, inputMatrix, Mat.ones(new Size(2,2), 0));
        //Imgproc.erode(inputMatrix, inputMatrix, Mat.ones(new Size(3,3), 0));
        // Imgproc.dilate(inputMatrix, inputMatrix, Mat.ones(new Size(3,3), 0));

        //LINE REMOVAL
        if (removeLines) {
            Core.bitwise_not(m, m); //invert the matrix
            Mat hor = m.clone();
            Mat ver = m.clone();

            //30 sharper on 16:9, 30 on 4:3 is too low so we double it
            int hs = (aspectRatio >= AR_1610) ? hor.cols() / 30 : hor.cols() / 60;
            Mat horStruct = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(hs, 1));
            Imgproc.erode(hor, hor, horStruct, new Point(-1, -1), 1);
            Imgproc.dilate(hor, hor, horStruct, new Point(-1, -1), 1);
            Core.subtract(m, hor, m);   //subtract mask of horizontal lines from original mat

            //now remove vertical lines (column bar, etc)
            int vs = (aspectRatio >= AR_1610) ? hor.rows() / 30 : hor.rows() / 60;
            Mat verStruct = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(1, vs));
            Imgproc.erode(ver, ver, verStruct, new Point(-1, -1), 1);
            Imgproc.dilate(ver, ver, verStruct, new Point(-1, -1), 1);
            Core.subtract(m, ver, m); //subtract mask of vertical lines from original mat

            Core.bitwise_not(m, m); //return image back to white background and black text
        }

        return m;
    }

    Mat getTextRegions(Mat m) {
        Mat colourMat = new Mat();
        Imgproc.cvtColor(m, colourMat, Imgproc.COLOR_GRAY2BGR);

        Mat morphKern = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(3, 3));
        Imgproc.morphologyEx(m, m, Imgproc.MORPH_GRADIENT, morphKern);
        morphKern = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(9, 1)); //used to be morph rect
        Imgproc.morphologyEx(m, m, Imgproc.MORPH_CLOSE, morphKern);

        Mat mask = Mat.zeros(m.size(), CvType.CV_8UC1);
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();

        Imgproc.dilate(m, m, Mat.ones(new Size(2,2), 0));
        Imgproc.findContours(m, contours, hierarchy, Imgproc.RETR_CCOMP, Imgproc.CHAIN_APPROX_SIMPLE, new Point(0, 0));
        Imgproc.erode(m, m, Mat.ones(new Size(2,2), 0));

        for (int i = (int) hierarchy.get(0, 0)[0]; i >= 0; i = (int) hierarchy.get(0, i)[0]) {
            Log.d(TAG, "entering with i = " + i);
            Rect rect = Imgproc.boundingRect(contours.get(i));
            Mat maskRegion = new Mat(mask, rect);
            maskRegion.setTo(new Scalar(0, 0, 0), maskRegion);
            Imgproc.drawContours(mask, contours, i, new Scalar(255, 255, 255), FILLED);
            double r = (double) countNonZero(maskRegion) / (rect.width * rect.height);

            Scalar colour;   //colour of rectangle
            int thickness = 2;   //thickness of rectangle lines

            if (r > 0.15 && rect.height > 8 && rect.width > 8) {   //15% black and at least 8x8
                colour = new Scalar(0, 255, 0);
            } else {
                colour = new Scalar(255, 0, 0);
            }

            Point tl = rect.tl();
            Point br = rect.br();

            if (rect.height > 16 && rect.width > 8)
                Core.rectangle(colourMat, tl, br, colour, thickness);

        }

        return colourMat;
    }

    void loadSVM() {

    }
}
