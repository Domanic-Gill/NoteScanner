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
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import static org.opencv.core.Core.FILLED;
import static org.opencv.core.Core.bitwise_not;
import static org.opencv.core.Core.countNonZero;
import static org.opencv.core.Core.line;
import static org.opencv.core.Core.log;

class OcrProcessor {    //TODO: Fix to be not package private, localise all methods
    private static final String TAG = "OCRPROC";

    private boolean isCancelled = false;
    private static final int MAXRES = 2000;     //highest amount of rows/cols an image can be
    private float aspectRatio;                  //Aspect Ratio of image
    private int sourceCols, sourceRows;         //original resolution of image
    private float scaleFactor;                  //factor that image is downscaled from
    private boolean isDownscaled = false;

    /*PARAMETERS*/
    private static final int MORPHSHIFT_LOW = 5, MORPHSHIFT_MED = 12, MORPHSHIFT_HIGH = 15; //used to be 6 12 18
    private static final int[] NOISERED_LOW = {11, 2}, NOISERED_MED = {15, 5}, NOISERED_HIGH = {23, 7};

    OcrProcessor(Mat m) {
        sourceCols = m.cols();
        sourceRows = m.rows();
        aspectRatio = (float) m.rows() / (float) m.cols();
        if (aspectRatio < 1) aspectRatio = (float) m.cols() / (float) m.rows();

        if (sourceCols > sourceRows && sourceCols > MAXRES) {
            scaleFactor = (float) MAXRES / (float) sourceCols;
        } else if (sourceRows > sourceCols && sourceRows > MAXRES) {
            scaleFactor = (float) MAXRES / (float) sourceRows;
        } else scaleFactor = 0;

        Log.d(TAG, "sf = " + scaleFactor + "| sc = " + sourceCols + "| sr = " + sourceRows + "| ar =" + aspectRatio);
    }

    /* Downscales High resolution images for easier pre-processing */
    Mat scaleMat(Mat src) {   //TODO: Create proper scaling, using factor dividing
        Mat m = src.clone();


        if (scaleFactor > 0) {
            Size s = new Size(sourceCols * scaleFactor, sourceRows * scaleFactor);
            Imgproc.resize(m, m, s, 0, 0, Imgproc.INTER_AREA);
            isDownscaled = true;
        }

        if (sourceRows < 1000 && sourceCols < 1000) {
            Imgproc.pyrUp(m, m);
        }
        return m;
    }

    /*Use thresholding to convert noisy image to clean binary image */
    Mat removeNoise(Mat src, boolean removeLines) {   //TODO: need global scale variable/source resolution + isdownscaled to determine binary thresh + lineremoval values
        Mat m = src.clone();
        //threshold image to remove noise and invert image for line removal
        Imgproc.cvtColor(m, m, Imgproc.COLOR_BGR2GRAY); //change to greyscale
        Imgproc.adaptiveThreshold(m, m, 255,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                NOISERED_HIGH[0], NOISERED_HIGH[1]); //default 11 2 now 15 5, sharper on 23 7
        //Imgproc.GaussianBlur(m, m, new Size(5,5), 0);
        Imgproc.threshold(m, m, 0, 255, Imgproc.THRESH_OTSU);

        //LINE REMOVAL
        if (removeLines) {
            Core.bitwise_not(m, m); //invert the matrix
            Mat hor = m.clone();
            Mat ver = m.clone();

            //30 sharper on 16:9, 30 on 4:3 is too low so we double it
            int hs = (isDownscaled) ? hor.cols() / 60 : hor.cols() / 30;
            //hs = sourceCols / 30;
            Mat horStruct = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(hs, 1));
            Imgproc.erode(hor, hor, horStruct, new Point(-1, -1), 1);
            Imgproc.dilate(hor, hor, horStruct, new Point(-1, -1), 1);
            Core.subtract(m, hor, m);   //subtract mask of horizontal lines from original mat

            //now remove vertical lines (column bar, etc)
            int vs = (isDownscaled) ? hor.rows() / 30 : hor.rows() / 15;
            //vs = sourceCols / 30;
            Mat verStruct = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(1, vs));
            Imgproc.erode(ver, ver, verStruct, new Point(-1, -1), 1);
            Imgproc.dilate(ver, ver, verStruct, new Point(-1, -1), 1);
            Core.subtract(m, ver, m); //subtract mask of vertical lines from original mat

            Core.bitwise_not(m, m); //return image back to white background and black text
        }
        return m;
    }

    /*find all the text regions in the input matrix using morphology and contouring*/
    List<Rect> getTextRegionsRects(Mat m) { //TODO: pass bool iscancelled to stop rect loop

        List<Rect> textRegions = new ArrayList<>();

        Mat morphKern = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(3, 3));
        Imgproc.morphologyEx(m, m, Imgproc.MORPH_GRADIENT, morphKern);
        morphKern = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(MORPHSHIFT_HIGH, 1)); //TODO: higher res = lower width,
        Imgproc.morphologyEx(m, m, Imgproc.MORPH_CLOSE, morphKern);


        Mat mask = Mat.zeros(m.size(), CvType.CV_8UC1);
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();

        Imgproc.dilate(m, m, Mat.ones(new Size(2, 2), 0));
        Imgproc.findContours(m, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE, new Point(0, 0));
        Imgproc.erode(m, m, Mat.ones(new Size(2, 2), 0));
        Log.d(TAG, hierarchy.cols() + " Potential text regions found. Processing....");
        double time = System.currentTimeMillis();

        for (int i = (int) hierarchy.get(0, 0)[0]; i >= 0; i = (int) hierarchy.get(0, i)[0]) {  //TODO: null detection needed and cancel detection in here
            Rect rect = Imgproc.boundingRect(contours.get(i));
            Mat maskRegion = new Mat(mask, rect);
            maskRegion.setTo(new Scalar(0, 0, 0), maskRegion);
            Imgproc.drawContours(mask, contours, i, new Scalar(255, 255, 255), FILLED);     //stop inner rects from acceptance by filling

            if (rect.height > 16 && rect.width > 12)            //Check not to accept very small regions
                textRegions.add(rect);
        }

        Collections.sort(textRegions, new Comparator<Rect>() {
            @Override
            public int compare(Rect a, Rect b) {    //Sort regions by height
                return Integer.compare(a.height, b.height);
            }
        });

        int medianPos = (textRegions.size() - 1) / 2;
        int medianHeight = textRegions.get(medianPos).height;   //Get median position of height sorted regions

        for (int i = medianPos; i < textRegions.size(); i++) {
            if (textRegions.get(i).height > medianHeight * 2) {
                textRegions.remove(i);
                if (i >= 1) i--;
            }
        }

        Collections.sort(textRegions, new SortRectByPos()); //sort regions top to bottom, left to right

        Log.d(TAG, "Time to process text regions = " + (float) ((System.currentTimeMillis() - time) / 1000));

        return textRegions; //TODO: Fix case when this is null
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
            Scalar colour;          //colour of rectangle
            int thickness = 2;      //thickness of rectangle lines that are drawn on the preview

            //make region green if at least 6% black, else red
            colour = blackPercent > 0.06 ? new Scalar(0, 255, 0) : new Scalar(255, 0, 0);
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

    /*Iterates through all text boundaries and conditionally insert the coloured boundary to the input matrix */
    Mat displayTextRegions2(Mat dst, List<TextObject> words) {
        Mat colMat = dst.clone();   //colour version of input matrix
        Imgproc.cvtColor(dst, colMat, Imgproc.COLOR_GRAY2BGR);
        double minRectY = words.get(0).getWord().tl().y;
        double maxRectY = words.get(words.size() - 1).getWord().br().y;

        for (int i = 0; i < words.size(); i++) {
            Rect region = words.get(i).getWord();
            if (region.tl().y < minRectY) minRectY = region.tl().y;
            if (region.br().y > maxRectY) maxRectY = region.br().y;
            Mat roi = dst.submat(region);
            bitwise_not(roi, roi);
            double blackPercent = (double) countNonZero(roi) / (region.width * region.height);
            Scalar colour;          //colour of rectangle
            int thickness = 2;      //thickness of rectangle lines that are drawn on the preview

            //make region green if at least 6% black, else red
            colour = blackPercent > 0.06 ? new Scalar(0, 255, 0) : new Scalar(255, 0, 0);
            Core.rectangle(colMat, region.tl(), region.br(), colour, thickness);

            for (int j : words.get(i).getSegColumns()) {
                Core.line(colMat, new Point(j, region.tl().y), new Point(j, region.br().y), new Scalar(0, 0, 255), 3);
            }
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

    ArrayList<TextObject> generateTextObject(List<Rect> wordRegions) {
        ArrayList<TextObject> wordList = new ArrayList<>();
        ListIterator<Rect> wrI = wordRegions.listIterator();
        Rect prevRect = wrI.next();
        wordList.add(new TextObject(prevRect));

        while (wrI.hasNext()) {
            Rect curRect = wrI.next();
            double prevRectBuffer = prevRect.br().x + 15;         //need to change this to subtracted difference of two rects

            if (curRect.tl().y > prevRect.br().y) {                 //if it is a wordMat on the next line
                wordList.get(wordList.size() - 1).setLineBreak();
                wordList.add(new TextObject(curRect));
            } else if (curRect.tl().x <= prevRectBuffer) {           //if region is close to previous, merge the two
                wordList.get(wordList.size() - 1).mergeWord(curRect);
            } else {
                wordList.add(new TextObject(curRect));               //if region is far from previous, create separate word
            }
            prevRect = curRect.clone();
        }

        /*System.out.println(wordList.size() + " WORDS DETECTED");

        StringBuilder s = new StringBuilder();
        int count = 1;
        int i = 0;
        s.append("\n" + "Line 1: ");
        for (TextObject aWordList : wordList) {
            String textStructure;
            if (aWordList.getLineBreak()) {
                textStructure = "(" + i + ")" + "1" + "\nLine " + ++count + ": ";
            } else {
                textStructure = "(" + i + ")" + "1 " + " ";
            }
            s.append(textStructure);
            i++;
        }

        Log.i(TAG, " " + s)*/
        return wordList;
    }

    public List<Integer> segmentToLetters(Rect word, Mat src) {

        Mat tmp = src.submat(word);
        Mat wordMat = tmp.clone();
        Core.bitwise_not(wordMat, wordMat);         //invert word matrix to view non zero values
        List<Integer> potWS = new ArrayList<>();    //integer list of all potential word segments

        for (int i = 0; i < wordMat.cols(); i++) {
            Mat column = wordMat.col(i);
            int nonzero = countNonZero(column);
            float percent = ((float) nonzero / (float) wordMat.rows()) * 100;

            if (percent < (float) 5) {
                potWS.add(i);
            }
        }

        /*Potential word segment lines that are close together are merged here*/
        int sI = 0, cI;
        for (int x = 1; x < potWS.size() - 1; x++) {
            if (x == 1) {
                sI = potWS.get(x - 1);
            }
            cI = potWS.get(x);
            if (cI - sI <= 7 && cI - sI > 1) {
                for (int j = 1; j < (cI - sI); j++) {
                    potWS.add(j + sI);
                }
            }
            sI = cI;
        }

        Collections.sort(potWS);

        /*Sort lines into rectangles for further analysis, exclude thin lines */
        List<Rect> potWsRects = new ArrayList<>();
        int colSize = 1;
        int startIndex = 0, endIndex;
        for (int x = 1; x < potWS.size() - 1; x++) {
            if (x == 1) {
                startIndex = potWS.get(x - 1);
            }

            endIndex = potWS.get(x);

            if (endIndex - startIndex == 1) {
                colSize++;
            } else {
                if (colSize > 1) {
                    Point tl = new Point(word.tl().x + startIndex - colSize, word.tl().y);
                    Point br = new Point(word.tl().x + startIndex, word.br().y);
                    potWsRects.add(new Rect(tl, br));
                }
                colSize = 0;
            }
            startIndex = endIndex;
        }

        List<Integer> segLines = new ArrayList<>();

        /*Thin the segmentation columns and remove faulty segmentation columns*/
        for (int x = 0; x < potWsRects.size(); x++) {
            Rect column = potWsRects.get(x);
            segLines.add((int) (column.tl().x) + column.width / 2);
        }

        //for (int j = 0; j < potWsRects.size(); j++) {
        //    Core.rectangle(src, potWsRects.get(j).tl(), potWsRects.get(j).br(), new Scalar(0,0,255), FILLED);
        //}

        //  for (int i : segLines){
        //    Core.line(src, new Point(i, aword.getWord().tl().y), new Point(i, aword.getWord().br().y), new Scalar(0, 0, 255));
        // }
        return segLines;
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
    static class SortRectByPos implements Comparator<Rect> {
        @Override
        public int compare(Rect a, Rect b) {    // div4 compensates for letters barely on the same line
            return (a.tl().y <= b.tl().y && a.tl().y + a.height > b.tl().y + (b.height / 4)) ||
                    (b.tl().y <= a.tl().y && b.tl().y + b.height > a.tl().y + (a.height / 4)) ? Double.compare(a.tl().x, b.tl().x) : Double.compare(a.tl().y, b.tl().y);
        }
    }


}
