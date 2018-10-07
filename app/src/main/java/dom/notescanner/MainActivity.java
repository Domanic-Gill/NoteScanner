package dom.notescanner;

import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Toast;

import com.github.clans.fab.FloatingActionButton;
import com.github.clans.fab.FloatingActionMenu;

public class MainActivity extends AppCompatActivity {
    //Navigation drawer
    private DrawerLayout drawer;        //navigation draw

    //fab menu and respective buttons below
    private FloatingActionMenu floatingActionMenu;
    private FloatingActionButton fabAddPhoto, fabAddGallery, fabaddNote;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        floatingActionMenu = findViewById(R.id.floatingActionMenu);
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
        switch (v.getId()) {
            case R.id.fabItem1:
                Toast.makeText(MainActivity.this,"photo add", Toast.LENGTH_SHORT).show();
            case R.id.fabItem2:
                Toast.makeText(MainActivity.this,"gallery add", Toast.LENGTH_SHORT).show();
            case R.id.fabItem3:
                Toast.makeText(MainActivity.this,"Normal add", Toast.LENGTH_SHORT).show();
        }
    }
}

