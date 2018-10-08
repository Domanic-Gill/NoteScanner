package dom.notescanner;

import android.graphics.Rect;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import com.github.clans.fab.FloatingActionButton;
import com.github.clans.fab.FloatingActionMenu;


//TODO - implement list view properly
public class MainActivity extends AppCompatActivity {
    //Navigation drawer
    private DrawerLayout drawer;

    //fab menu and respective buttons below
    private FloatingActionMenu floatingActionMenu;
    private FloatingActionButton fabAddPhoto, fabAddGallery, fabaddNote;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        floatingActionMenu = findViewById(R.id.floatingActionMenu);
        floatingActionMenu.setClosedOnTouchOutside(true);
        fabAddPhoto = findViewById(R.id.fabItem1);
        fabAddGallery = findViewById(R.id.fabItem2);
        fabaddNote = findViewById(R.id.fabItem3);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, toolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
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


    public void onClick (View v) {
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            floatingActionMenu.close(true);
        }
        switch (v.getId()) {
            case R.id.fabItem1:
                Toast.makeText(MainActivity.this,"photo add", Toast.LENGTH_SHORT).show();
            case R.id.fabItem2:
                Toast.makeText(MainActivity.this,"gallery add", Toast.LENGTH_SHORT).show();
            case R.id.fabItem3:
                Toast.makeText(MainActivity.this,"Normal add", Toast.LENGTH_SHORT).show();
        }
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event){

        //close fab menu if somewhere other than the fab buttons is pressed
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (floatingActionMenu.isOpened()) {
                Rect outRect = new Rect();
                floatingActionMenu.getGlobalVisibleRect(outRect);
                if(!outRect.contains((int)event.getRawX(), (int)event.getRawY())) {
                    floatingActionMenu.close(true);
                    super.dispatchTouchEvent(event);
                    return false;
                }
            }
        }
        return super.dispatchTouchEvent(event);
    }
}

