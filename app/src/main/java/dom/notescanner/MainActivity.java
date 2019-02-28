package dom.notescanner;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;

import com.github.clans.fab.FloatingActionMenu;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.ml.CvSVM;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/*LOAD SVM ON THE FIRST NOTE TAKE, PASS BACK TO ACTIVITY
 * THEN KEEP THE REFERENCE AS LONG AS IT ISN'T NULL
 * ALWAYS PASS REFERENCE AND USE IF IT ISN'T NULL, ELSE LOAD AND RETURN
 * DOESN'T SOLVE ONCREATE,*/
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    public static final int GAL_IMAGE=42, CAM_IMAGE=43, CAM_REQUEST=44, NOTE_ACT=45, ICR_START = 46; //Request codes
    private int lastNoteID = 0;                     //id of the final note of the ListView
    private String mCurrentPhotoPath;               //file path of new temporary file
    boolean openCVLoaded;

    private FloatingActionMenu floatingActionMenu;  //bottom right fab menu
    private DrawerLayout drawer;                    //navigation drawer
    SimpleCursorAdapter simpleCursorAdapter;        //adapter for ListView

    BaseLoaderCallback mCallBack = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case BaseLoaderCallback.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");
                    openCVLoaded = true;
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
        setContentView(R.layout.activity_main);

        //load openCV asynchronously using callback
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_13, this, mCallBack);

        //find and set fab buttons
        floatingActionMenu = findViewById(R.id.floatingActionMenu);
        floatingActionMenu.setClosedOnTouchOutside(true);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        //Add drawer
        drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, toolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        //Update ListView
        queryProvider();
    }

    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.fabItem1:         //Starts camera for img to process
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.CAMERA},
                            CAM_REQUEST);
                } else { startCameraIntent(); }
                break;
            case R.id.fabItem2:         //Starts gallery for image to process
                Intent imageIntent = new Intent();
                imageIntent.setType("image/*");
                imageIntent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(Intent.createChooser(imageIntent, "Select Picture"), GAL_IMAGE);
                break;
            case R.id.fabItem3:         //Starts new note without img to process
                queryProvider();
                Intent intent = new Intent(MainActivity.this, NoteActivity.class);
                Bundle bundle = new Bundle();
                bundle.putBoolean("isNewNote", true);
                bundle.putInt("noteID", lastNoteID + 1);
                intent.putExtras(bundle);
                startActivityForResult(intent, NOTE_ACT);
                break;
        }
    }

    private void startCameraIntent() {
        Intent camIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (camIntent.resolveActivity(getPackageManager()) != null) {   //check if there is a camera
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException e) {
                e.printStackTrace();
                Log.d(TAG, "ERROR CREATING FILE DIRECTORY FOR PHOTO");
            }

            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this, "dom.notescanner.fileprovider", photoFile);
                camIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(camIntent, CAM_IMAGE);
            }
        }
    }

    /*Creates temporary image file and updates path string with its path*/
    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(imageFileName,".jpg", storageDir);
        mCurrentPhotoPath = image.getAbsolutePath();    // Save file
        return image;
    }

    /*Deals with response codes from other activities */
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == NOTE_ACT && resultCode == RESULT_OK)     //update db if changes made
            queryProvider();

        if (requestCode == GAL_IMAGE) {     //returning from gallery, start intent with img
            if (data != null) {
                Uri uri = data.getData();
                Intent intent = new Intent(MainActivity.this, GalleryActivity.class);
                intent.putExtra("uri", uri.toString());
                intent.putExtra("isCamImg", false);
                startActivityForResult(intent, ICR_START);
            }
        } else if (requestCode == CAM_IMAGE) {      //returning from camera, start intent with img
            if (resultCode == RESULT_CANCELED) {    //delete temp file if no image found.
                File emptyFile = new File(mCurrentPhotoPath);
                emptyFile.delete();
            } else {
                Intent camIntent = new Intent(MainActivity.this, GalleryActivity.class);
                camIntent.putExtra("isCamImg", true);
                camIntent.putExtra("uri", mCurrentPhotoPath);
                startActivityForResult(camIntent, ICR_START);
            }
        } else if (requestCode == ICR_START && resultCode == RESULT_OK) {   //start ICR pipeline on gallery accept
            Bundle b = data.getExtras();
            Uri uri = null;
            Bitmap camPhoto = null;
            Boolean isCamImg = b.getBoolean("isCamImg"),
                    remLines = b.getBoolean("removeLines"),
                    morphWord = b.getBoolean("wideText");
            System.out.println(" HI " + remLines + "hi" + morphWord);
            if (b != null) {
                if (isCamImg) {
                    uri = Uri.parse(b.getString("uri"));
                    camPhoto = BitmapFactory.decodeFile(uri.toString());
                } else {
                    camPhoto = null;
                    uri = Uri.parse(b.getString("uri"));
                }
            }
            ContentValues newNote = new ContentValues();
            newNote.put(ConProviderContract.NOTE_TITLE, "Processing new ICR note...");
            newNote.put(ConProviderContract.NOTE_BODY, "Processing...");
            getContentResolver().insert(ConProviderContract.NOTES_URI, newNote);

            queryProvider();

            if (isCamImg) {
                new IcrAsync(null, camPhoto, getApplicationContext(), this, remLines, morphWord, lastNoteID).execute();
            } else {
                new IcrAsync(uri, null, getApplicationContext(), this, remLines, morphWord, lastNoteID).execute();
            }

        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case CAM_REQUEST:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(MainActivity.this,
                            "Camera permission granted!",
                            Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this,
                            "Camera permission denied!",
                            Toast.LENGTH_SHORT).show();
                }
                break;
            // other 'case' lines to check for other
            // permissions this app might request.
        }
    }

    /*querying content provider and providing a clickable list of recipes*/
    public void queryProvider() {

        //set query values for cursor
        String[] projection = new String[]{
                ConProviderContract._ID,
                ConProviderContract.NOTE_TITLE,
                ConProviderContract.NOTE_BODY
        };

        String displayCols[] = new String[]{  //display only note title and body
                ConProviderContract.NOTE_TITLE,
                ConProviderContract.NOTE_BODY,
        };

        int[] colResIds = new int[]{   //get id's from layout
                R.id.note_title,
                R.id.note_body,
        };

        Cursor cursor = getContentResolver().query(ConProviderContract.NOTES_URI, projection, null, null, null);
        simpleCursorAdapter = new SimpleCursorAdapter(this, R.layout.adapter_view_layout, cursor, displayCols, colResIds, 0);

        final ListView noteListView = findViewById(R.id.lv_notes);
        noteListView.setAdapter(simpleCursorAdapter);
        noteListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Cursor cursor = (Cursor) noteListView.getItemAtPosition(position);
                Intent intent = new Intent(MainActivity.this, NoteActivity.class);

                int noteID = cursor.getInt(0);
                String noteTitle = cursor.getString(1);
                String noteBody = cursor.getString(2);

                Bundle bundle = new Bundle();   //bundle values for displaying in noteActivity
                bundle.putBoolean("isNewNote", false);
                bundle.putInt("noteID", noteID);
                bundle.putString("noteTitle", noteTitle);
                bundle.putString("noteBody", noteBody);
                intent.putExtras(bundle);

                startActivityForResult(intent, NOTE_ACT);
            }
        });

        //use list view to get last item and get its id, for usage in creating new note
        int x = noteListView.getAdapter().getCount();
        if (x != 0) {
            Cursor c = (Cursor) noteListView.getItemAtPosition(x - 1);
            lastNoteID = c.getInt(0);
        }
    }


    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {

            //close fab menu if we click on anything other than its buttons
            if (floatingActionMenu.isOpened()) {
                Rect clickRect = new Rect();
                floatingActionMenu.getGlobalVisibleRect(clickRect);
                if (!clickRect.contains((int) event.getRawX(), (int) event.getRawY())) {
                    floatingActionMenu.close(true);
                    return super.dispatchTouchEvent(event);
                }
            }
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public void onBackPressed() {
        //close drawer if it is open
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else super.onBackPressed();
    }

    @Override
    public void onResume() {
        super.onResume();
        floatingActionMenu.close(false);
    }

    static class IcrAsync extends AsyncTask<String, Mat, Void>  //weak reference and strong app context needs to be passed
    {
        private static final String TAG = "MAIN-ASYNC";  //debug tag
        private final WeakReference<MainActivity> weakActivity;  //weak reference to activity
        private final WeakReference<Context> mAppContext;   //weak reference to app, not needed but in main activity it is for writing to file without an activity
        private Uri mUri;   //URI of  the image
        private Bitmap inputBitmap; //Bitmap of the Image

        private Boolean removeLines, morphWord;
        private StringBuilder finalText;
        private int noteID;


        IcrAsync(Uri uri, Bitmap cameraImage, final Context appContext, MainActivity myActivity, boolean removeLines, boolean morphWord, int newNoteID) {
            mUri = uri;
            weakActivity = new WeakReference<>(myActivity);
            mAppContext = new WeakReference<>(appContext);
            this.removeLines = removeLines;
            this.morphWord = morphWord;
            inputBitmap = cameraImage;
            noteID = newNoteID;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Void doInBackground(String... params) {

            System.out.println("HI IM AN MAIN ASYNC");

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

            Thread z = null;
            SvmLoader r = null;
            appCon = mAppContext.get();
            if (appCon != null) {
                r = new SvmLoader(ocrProc, appCon);
                z = new Thread(r);
                z.start();
            }

            Mat scaleMat = ocrProc.scaleMat(inMat);     //downscale any high resolution images
            inMat.release();                            //release original image from memory.

            Mat noiseMat = ocrProc.removeNoise(scaleMat, removeLines); //remove noise from Image

            Mat textRegionMat = noiseMat.clone();
            List<org.opencv.core.Rect> textRegions = ocrProc.getTextRegionsRects(textRegionMat, morphWord);    //retrieve regions of text
            Log.d(TAG, textRegions.size() + "TEXT REGIONS DETECTED");

            ArrayList<TextObject> words = ocrProc.generateTextObject(textRegions);

            for (int i = 0; i < words.size(); i++) {
                org.opencv.core.Rect wordRegion = words.get(i).getWord();
                words.get(i).setSegColumns(ocrProc.segmentToLetters(wordRegion, noiseMat));
            }

            char[] lexicon = {'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
                    'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', '0', '1',
                    '2', '3', '4', '5', '6', '7', '8', '9'};

            int lSegLine, rSegLine;
            finalText = new StringBuilder();

            try {
                z.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            CvSVM svm = r.getSvm();

            for (int i = 0; i < words.size(); i++) {            //cycle through each word
                org.opencv.core.Rect wordRegion = words.get(i).getWord();
                List<Integer> segmentLines = words.get(i).getSegColumns();

                for (int j = 0; j < segmentLines.size(); j++) {     //cycle through each character
                    lSegLine = segmentLines.get(j);
                    rSegLine = j + 1 == segmentLines.size() ? (int) wordRegion.br().x : segmentLines.get(j + 1);

                    org.opencv.core.Rect charRegion = new org.opencv.core.Rect(new Point(lSegLine, wordRegion.tl().y), new Point(rSegLine, wordRegion.br().y));
                    Mat charMatSub = noiseMat.submat(charRegion);   //THERE IS SOMETHING WRONG HERE, ERROR ONCE BUT IT WILL COME BACK
                    Mat charMat = new Mat();
                    charMatSub.copyTo(charMat);
                    Mat charMatVector = ocrProc.preProcessLetter(charMat);
                    Mat svmInput = charMatVector.reshape(1,1);
                    double prediction = svm.predict(svmInput);
                    finalText.append(lexicon[(int) prediction - 1]);
                }

                finalText.append(words.get(i).getLineBreak() ? '\n' : ' ');
            }

            if (appCon != null) {
                ContentValues newNote = new ContentValues();
                newNote.put(ConProviderContract.NOTE_TITLE, "New ICR note " + noteID);
                newNote.put(ConProviderContract.NOTE_BODY, finalText.toString());
                appCon.getContentResolver().update(ConProviderContract.NOTES_URI, newNote,
                        ConProviderContract._ID + "=?",
                        new String[]{String.valueOf(noteID)});
            }

            System.out.println("--------\n" + finalText.toString());

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            MainActivity activity = weakActivity.get(); //get strong reference to activity

            if (activity == null || activity.isFinishing() || activity.isDestroyed())   //Don't update if activity not found;
                return;

            activity.queryProvider();       //update list view

        }

        @Override
        protected void onProgressUpdate(Mat... values) {
            super.onProgressUpdate(values);
        }

        @Override
        protected void onCancelled() {
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


