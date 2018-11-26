package dom.notescanner;

import org.opencv.core.Mat;
import org.opencv.core.Rect;

import java.util.ArrayList;

public class WordObject {
    private ArrayList<Mat> letters; //mat array of characters from wordMat
    private boolean isLineBreak;
    private ArrayList<Mat> wordMat;

    public WordObject(Mat newWord) {
        isLineBreak = false;
        letters = new ArrayList<>();
        wordMat = new ArrayList<>();
        wordMat.add(new Mat());
        newWord.copyTo(wordMat.get(0));

        segmentToLetters(0);
    }

    public ArrayList<Mat> getLetters() {
        return letters;
    }

    public void mergeText(Mat mergeWord) {
        wordMat.add(new Mat());     //add new matrix to arraylist of words
        mergeWord.copyTo(wordMat.get(wordMat.size()-1));    //copy mergeword to new matrix
        segmentToLetters(wordMat.size()-1);
    }

    public void setLineBreak() {
        isLineBreak = true;
    }

    public boolean getLineBreak() {
        return isLineBreak;
    }

    private void segmentToLetters(int wordIndex) {
        ArrayList<Rect> segBoxes = getSegmentationBoxes(wordMat.get(wordIndex));
        if(segBoxes == null) {
            letters.add(wordMat.get(wordIndex));       //null means no lines, the entire region must be a single letter
        } else {
            for (int i = 0; i < segBoxes.size(); i++) {                        //add submats pointers of letter regions to letters arraylist
                letters.add(wordMat.get(wordIndex).submat(segBoxes.get(i)));       //this is awkward because we need to refer to the wordMat pointer.
            }
        }

    }

    private ArrayList<Rect> getSegmentationBoxes(Mat src) { //TODO: implement segmentation
        Mat wordCopy = src.clone();
        int bias = (src.width() > src.cols()*1.5) ? 1 : 0;
        ArrayList<Rect> PSC = new ArrayList<>();
        ArrayList<Rect> Boxes = new ArrayList<>();
        //Rect scanRect =

        return null;
    }
}
