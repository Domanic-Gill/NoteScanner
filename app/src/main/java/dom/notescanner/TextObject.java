package dom.notescanner;

import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;

import java.util.ArrayList;
import java.util.List;

public class TextObject {
    private Rect word;              //Rectangles containing all words detected



    private List<Integer> segColumns;   //segmentation lines
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
    public void setSegColumns(List<Integer> segColumns) {
        this.segColumns = segColumns;
    }
    public List<Integer> getSegColumns() {
        return segColumns;
    }

    public void mergeWord(Rect r) {
        Rect mergeRegion = r.clone();
        Point tl, br;
        double tlX, tlY, brX, brY;

        tlX = (mergeRegion.tl().x <= word.tl().x) ? mergeRegion.tl().x : word.tl().x;
        tlY = (mergeRegion.tl().y <= word.tl().y) ? mergeRegion.tl().y : word.tl().y;
        brX = (mergeRegion.br().x >= word.br().x) ? mergeRegion.br().x : word.br().x;
        brY = (mergeRegion.br().y >= word.br().y) ? mergeRegion.br().y : word.br().y;

        /*if (mergeRegion.tl().y < word.tl().y) {     //taller region gets priority
            tl = new Point(word.tl().x, mergeRegion.tl().y);
        } else {
            tl = new Point(word.tl().x, word.tl().y);
        }


        if (mergeRegion.br().y > word.br().y) {     //vice versa for the bottom of the region
            br = new Point(mergeRegion.br().x, mergeRegion.br().y);
        } else {
            br = new Point(mergeRegion.br().x, word.br().y);
        }*/

        Rect tmp = new Rect(new Point(tlX, tlY), new Point(brX, brY));
        //Rect tmp = new Rect(tl, br);
        word = tmp.clone();


        System.out.println(word.tl().x + " " + word.br().x);

    }
}
