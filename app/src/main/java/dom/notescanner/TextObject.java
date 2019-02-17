package dom.notescanner;

import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;

import java.util.ArrayList;

public class TextObject {
    private Rect word;              //Rectangles containing all words detected
    private ArrayList<MatOfPoint> segColumns;   //segmentation lines
    private boolean isLineBreak;                // haha nice

    public TextObject(Rect newWord) {
        isLineBreak = false;
        word = new Rect();
        segColumns = new ArrayList<>();
        word = newWord.clone();
    }

    public Rect getWord()  { return word; }
    public void setLineBreak() {
        isLineBreak = true;
    }
    public boolean getLineBreak() {
        return isLineBreak;
    }


    public void mergeWord(Rect r) {
        Rect mergeRegion = r.clone();
        Point tl, br;
        if (mergeRegion.tl().y < word.tl().y) {     //taller region gets priority
            tl = new Point(word.tl().x, mergeRegion.tl().y);
        } else {
            tl = new Point(word.tl().x, word.tl().y);
        }


        if (mergeRegion.br().y > word.br().y) {     //vice versa for the bottom of the region
            br = new Point(mergeRegion.br().x, mergeRegion.br().y);
        } else {
            br = new Point(mergeRegion.br().x, word.br().y);
        }

        Rect tmp = new Rect(tl, br);
        word = tmp.clone();


        System.out.println(word.tl().x + " " + word.br().x);

    }
}
