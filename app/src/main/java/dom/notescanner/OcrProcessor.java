package dom.notescanner;

import android.content.Context;
import android.util.Log;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.ml.CvSVM;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;

import static org.opencv.core.Core.FILLED;
import static org.opencv.core.Core.bitwise_not;
import static org.opencv.core.Core.countNonZero;
import static org.opencv.core.Core.line;
import static org.opencv.core.Core.log;

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

    /*find all the text regions in the input matrix using morphology and contouring*/
    List<Rect> getTextRegionsRects(Mat m) {

        List<Rect> textRegions = new ArrayList<>();

        Mat colourMat = new Mat();
        Imgproc.cvtColor(m, colourMat, Imgproc.COLOR_GRAY2BGR);

        Mat morphKern = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(3, 3));
        Imgproc.morphologyEx(m, m, Imgproc.MORPH_GRADIENT, morphKern);
        morphKern = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(9, 1)); //used to be morph rect
        Imgproc.morphologyEx(m, m, Imgproc.MORPH_CLOSE, morphKern);

        Mat mask = Mat.zeros(m.size(), CvType.CV_8UC1);
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();

        Imgproc.dilate(m, m, Mat.ones(new Size(2, 2), 0));
        Imgproc.findContours(m, contours, hierarchy, Imgproc.RETR_CCOMP, Imgproc.CHAIN_APPROX_SIMPLE, new Point(0, 0));
        Imgproc.erode(m, m, Mat.ones(new Size(2, 2), 0));
        for (int i = (int) hierarchy.get(0, 0)[0]; i >= 0; i = (int) hierarchy.get(0, i)[0]) {
            Log.d(TAG, "Processing rect  = " + i);
            Rect rect = Imgproc.boundingRect(contours.get(i));
            Mat maskRegion = new Mat(mask, rect);
            maskRegion.setTo(new Scalar(0, 0, 0), maskRegion);
            Imgproc.drawContours(mask, contours, i, new Scalar(255, 255, 255), FILLED);

            if (rect.height > 16 && rect.width > 12)   //Region is at least 16x12
                textRegions.add(rect);
        }
        Collections.sort(textRegions, new SortRectbyPos()); //sort regions top to bottom, left to right
        return textRegions;
    }

    /*Iterates through all text boundaries and conditionally insert the coloured boundary to the input matrix */
    Mat displayTextRegions(Mat dst, List<Rect> textRegions) {
        Mat colMat = dst.clone();   //colour version of input matrix
        Imgproc.cvtColor(dst, colMat, Imgproc.COLOR_GRAY2BGR);
        double minRectY = textRegions.get(0).tl().y;
        double maxRectY = textRegions.get(textRegions.size() - 1).br().y;

        for (int i = 0; i < textRegions.size(); i++) {
            Rect region = textRegions.get(i);
            if (region.tl().y < minRectY) minRectY = region.tl().y;
            if (region.br().y > maxRectY) maxRectY = region.br().y;
            Mat roi = dst.submat(region);
            bitwise_not(roi, roi);
            double blackPercent = (double) countNonZero(roi) / (region.width * region.height);
            Scalar colour;   //colour of rectangle
            int thickness = 2;   //thickness of rectangle lines

            if (blackPercent > 0.06) {   //Region must be at least 6% black
                colour = new Scalar(0, 255, 0);         //Make green
            } else {
                colour = new Scalar(255, 0, 0);         //Make red
            }

            Core.rectangle(colMat, region.tl(), region.br(), colour, thickness);
        }

        //Create cropped image of Region of Interest if there is space
        Point minRectPoint;
        Point maxRectPoint;
        if (maxRectY < colMat.height() - 10 && minRectY > 10) {
            minRectPoint = new Point(0, minRectY - 10);
            maxRectPoint = new Point((double) colMat.width(), maxRectY + 10);
        } else {
            minRectPoint = new Point(0, minRectY);
            maxRectPoint = new Point((double) colMat.width(), maxRectY);
        }
        Rect r = new Rect(minRectPoint, maxRectPoint);

        return new Mat(colMat, r);
    }

    public void checkWord2(List<Rect> r, Mat src) {
        double imgWidth = src.width();
        ArrayList<WordObject> wordList = new ArrayList<>();
        ListIterator<Rect> ri = r.listIterator();
        Rect prevRect = ri.next();
        Mat prevMat = src.submat(prevRect);
        wordList.add(new WordObject(prevMat));

        while (ri.hasNext()) {
            Rect curRect = ri.next();
            Mat curMat = src.submat(curRect);
            double prevRectBuffer = (prevRect.br().x+15 > imgWidth) ?  imgWidth-1 : prevRect.br().x+15;         //need to change this to subtracted difference of two rects
            if (curRect.tl().y > prevRect.br().y) {                 //if it is a wordMat on the next line
                wordList.get(wordList.size()-1).setLineBreak();
                wordList.add(new WordObject(curMat));
            } else if(curRect.tl().x <= prevRectBuffer) {           //if region is close to previous, merge the two
                wordList.get(wordList.size()-1).mergeText(curMat);
            } else {
                wordList.add(new WordObject(curMat));               //if region is far from previous, create separate word
            }
            prevRect = curRect.clone();
        }

        for (WordObject aWordList : wordList) Log.d(TAG, " " + aWordList.getLetters().size() + "| " + aWordList.getLineBreak());
    }

    /**
     * Returns an OpenCV SVM which is loaded from raw resources and
     * stored in a temporary file which is deleted after loading.
     * <p></p>
     * Alternatively if the temp file has not been deleted we use
     * it instead of rewriting a new temp file
     *
     * @param appContext application context to load file
     * @return Loaded CvSVM
     */
    CvSVM loadSVM(Context appContext) {
        if (appContext != null) {
            try {
                File svmModelDir = appContext.getDir("svmModelDir", Context.MODE_PRIVATE);
                File mSvmModel = new File(svmModelDir, "svmModel.yaml");
                if (!mSvmModel.exists()) {
                    InputStream is = appContext.getResources().openRawResource(R.raw.eclipse);
                    FileOutputStream os = new FileOutputStream(mSvmModel);

                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        os.write(buffer, 0, bytesRead);
                    }
                    is.close();
                    os.close();
                } else {
                    Log.d(TAG, "FILE ALREADY EXISTS. SKIPPING UNPACKING");
                }
                CvSVM mSvm = new CvSVM();
                long time = System.currentTimeMillis();
                mSvm.load(mSvmModel.getAbsolutePath());
                Log.d(TAG, "LOADING TIME =  " + (float) ((System.currentTimeMillis() - time) / 1000));
                Log.d(TAG, "SVM COUNT = " + mSvm.get_support_vector_count());
                //svmModelDir.delete();

                return mSvm;

            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "Failed to load SVM, exception thrown: " + e);
            }
        } else {
            Log.e(TAG, "Failed to load SVM, appContext is null");
        }
        return null;
    }

    /**
     * Takes an  {@link ArrayList<Rect>} which are text regions but
     * are out of order and sorts them. If they are on the same line
     * then the Rect with the lowest y (closest to the left) gets priority.
     * <p></p>
     * If they are not on the same line it defaults to the Rect with
     * the lowest y (closest to the top)
     * <p></p>
     * This results in the List being sorted from top to bottom, left to
     * right
     */
    static class SortRectbyPos implements Comparator<Rect> {
        @Override
        public int compare(Rect a, Rect b) {
            if ((a.tl().y <= b.tl().y && a.tl().y + a.height >= b.tl().y) ||
                    (b.tl().y <= a.tl().y && b.tl().y + b.height >= a.tl().y)) {
                return (a.tl().x < b.tl().x) ? -1 : 1;

            } else {
                return (a.tl().y < b.tl().y) ? -1 : 1;
            }
        }

    }


}
