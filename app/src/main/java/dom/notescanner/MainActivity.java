package dom.notescanner;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Rect;
import android.net.Uri;
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

import com.github.clans.fab.FloatingActionButton;
import com.github.clans.fab.FloatingActionMenu;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.OpenCVLoader;


//TODO - implement list view properly
public class MainActivity extends AppCompatActivity {
    boolean openCVLoaded;
    private static final String TAG = "MainActivity";
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
    //Navigation drawer
    private DrawerLayout drawer;
    private int lastNoteID = 0; //id of the final note of the ListView
    public static final int PICK_IMAGE = 100;

    //fab menu and respective buttons below
    private FloatingActionMenu floatingActionMenu;

    SimpleCursorAdapter simpleCursorAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_13, this, mCallBack);

        //find and set fab buttons
        floatingActionMenu = findViewById(R.id.floatingActionMenu);
        floatingActionMenu.setClosedOnTouchOutside(true);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, toolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        queryProvider();
    }

    public void onClick (View v) {
        switch (v.getId()) {
            case R.id.fabItem1:
                Toast.makeText(MainActivity.this,"photo add", Toast.LENGTH_SHORT).show();
                break;
            case R.id.fabItem2:
                Intent imageIntent = new Intent();
                imageIntent.setType("image/*");
                imageIntent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(Intent.createChooser(imageIntent, "Select Picture"), PICK_IMAGE);
                break;
            case R.id.fabItem3:
                queryProvider();
                Intent intent = new Intent(MainActivity.this, NoteActivity.class);
                Bundle bundle = new Bundle();
                bundle.putBoolean("isNewNote", true);
                System.out.println("--------------------MAIN noteID = " + lastNoteID);
                bundle.putInt("noteID", lastNoteID+1);
                intent.putExtras(bundle);
                startActivityForResult(intent, 1);
                break;
        }
    }

    /*Deals with response codes from other activities */
    protected void onActivityResult(int requestCode,int resultCode, Intent data) {
        if (requestCode == 1 && resultCode == RESULT_OK)    //update db if changes made
            queryProvider();

        if (requestCode == PICK_IMAGE) {
            if (data != null) {
                Uri uri = data.getData();
                Intent intent = new Intent(MainActivity.this, GalleryActivity.class);
                intent.putExtra("uri", uri.toString());
                startActivity(intent);
            }
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

        final ListView noteListview = findViewById(R.id.lv_notes);
        noteListview.setAdapter(simpleCursorAdapter);
        noteListview.setOnItemClickListener(new AdapterView.OnItemClickListener(){

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Cursor cursor = (Cursor) noteListview.getItemAtPosition(position);
                Intent intent = new Intent(MainActivity.this,NoteActivity.class);

                int noteID = cursor.getInt(0);
                String noteTitle = cursor.getString(1);
                String noteBody = cursor.getString(2);

                Bundle bundle = new Bundle();   //bundle values for displaying in noteActivity
                bundle.putBoolean("isNewNote",false);
                bundle.putInt("noteID", noteID);
                bundle.putString("noteTitle",noteTitle);
                bundle.putString("noteBody",noteBody);
                intent.putExtras(bundle);

                startActivityForResult(intent, 1);
            }
        });

        //use list view to get last item and get its id, for usage in creating new note
        int x = noteListview.getAdapter().getCount();
        if (x != 0) {
            Cursor c = (Cursor) noteListview.getItemAtPosition(x - 1);
            lastNoteID = c.getInt(0);
        }
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event){
        if (event.getAction() == MotionEvent.ACTION_DOWN) {

            //close fab menu if we click on anything other than its buttons
            if (floatingActionMenu.isOpened()) {
                Rect clickRect = new Rect();
                floatingActionMenu.getGlobalVisibleRect(clickRect);
                if(!clickRect.contains((int)event.getRawX(), (int)event.getRawY())) {
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
        } else {
            super.onBackPressed();
        }

    }
    @Override
    public void onResume(){
        super.onResume();
        floatingActionMenu.close(false);
    }
}


