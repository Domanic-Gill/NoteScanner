package dom.notescanner;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.net.Uri;
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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;

/*LOAD SVM ON THE FIRST NOTE TAKE, PASS BACK TO ACTIVITY
 * THEN KEEP THE REFERENCE AS LONG AS IT ISN'T NULL
 * ALWAYS PASS REFERENCE AND USE IF IT ISN'T NULL, ELSE LOAD AND RETURN
 * DOESN'T SOLVE ONCREATE,*/
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    public static final int GAL_IMAGE=42, CAM_IMAGE=43, CAM_REQUEST=44, GAL_ACT=45; //Request codes
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
                startActivityForResult(intent, GAL_ACT);
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
        if (requestCode == GAL_ACT && resultCode == RESULT_OK)    //update db if changes made
            queryProvider();

        if (requestCode == GAL_IMAGE) {     //returning from gallery, start intent with img
            if (data != null) {
                Uri uri = data.getData();
                Intent intent = new Intent(MainActivity.this, GalleryActivity.class);
                intent.putExtra("uri", uri.toString());
                intent.putExtra("isCamImg", false);
                startActivity(intent);
            }
        } else if (requestCode == CAM_IMAGE) {      //returning from camera, start intent with img
            if (resultCode == RESULT_CANCELED) {    //delete temp file if no image found.
                File emptyFile = new File(mCurrentPhotoPath);
                emptyFile.delete();
            } else {
                Intent camIntent = new Intent(MainActivity.this, GalleryActivity.class);
                camIntent.putExtra("isCamImg", true);
                camIntent.putExtra("uri", mCurrentPhotoPath);
                startActivity(camIntent);
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

                startActivityForResult(intent, 1);
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
}


