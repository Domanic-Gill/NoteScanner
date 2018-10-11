package dom.notescanner;

import android.content.ContentValues;
import android.support.v4.app.NavUtils;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;
import android.support.v7.widget.Toolbar;

import java.util.Objects;

//TODO - don't allow to save 0 in both text fields
public class NoteActivity extends AppCompatActivity {
    private String startTitleText = "";
    private String startBodyText = "";
    private boolean isNewNote = false;
    private int noteID;

    EditText noteTitleBox;
    EditText noteBodyBox;
    Toolbar noteToolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note);

        noteBodyBox = findViewById(R.id.note_body);
        noteTitleBox = findViewById(R.id.note_title);

        //set toolbar to our custom toolbar with visible back button
        noteToolbar = findViewById(R.id.note_toolbar);
        setSupportActionBar(noteToolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        //set local variables from MainActivity bundle
        Bundle bundle = getIntent().getExtras();
        assert bundle != null;
        isNewNote = Objects.requireNonNull(bundle).getBoolean("isNewNote");
        noteID = bundle.getInt("noteID");

        //set text to EditTexts if this is a note that has been selected
        if (!isNewNote) {
            startTitleText = bundle.getString("noteTitle");
            startBodyText = bundle.getString("noteBody");
            noteTitleBox.setText(startTitleText);
            noteBodyBox.setText(startBodyText);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_notes, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        String noteTitle = noteTitleBox.getText().toString();
        String noteBody = noteBodyBox.getText().toString();
        boolean textChanged = !noteTitle.equals(startTitleText) || !noteBody.equals(startBodyText);

        switch (item.getItemId()) {
            case android.R.id.home:     //pressing the back button on the toolbar
                setResult(MainActivity.RESULT_OK);
                if (!textChanged) {
                    NavUtils.navigateUpFromSameTask(this);  //return to previous activity
                } else {
                    ContentValues newNote = new ContentValues();
                    newNote.put(ConProviderContract.NOTE_TITLE, noteTitle);
                    newNote.put(ConProviderContract.NOTE_BODY, noteBody);
                    if (isNewNote) {   //if note started empty and text changed
                        getContentResolver().insert(ConProviderContract.NOTES_URI, newNote);
                    } else {        //else update if text has changed
                        getContentResolver().update(ConProviderContract.NOTES_URI, newNote,
                                ConProviderContract._ID + "=?",
                                new String[]{String.valueOf(noteID)});
                    }
                    NavUtils.navigateUpFromSameTask(this);  //return to previous activity
                }
                return true;

            case R.id.action_save:
                if (!textChanged) {
                    Toast.makeText(getApplicationContext(), "No changes to save", Toast.LENGTH_SHORT).show();
                } else {
                    ContentValues newNote = new ContentValues();
                    newNote.put(ConProviderContract.NOTE_TITLE, noteTitle);
                    newNote.put(ConProviderContract.NOTE_BODY, noteBody);
                    newNote.put(ConProviderContract._ID, noteID);   //save id for rewriting to same id. May have consequences?

                    if (isNewNote) {   //if note started empty and text changed
                        getContentResolver().insert(ConProviderContract.NOTES_URI, newNote);
                        Toast.makeText(getApplicationContext(), "Changes saved", Toast.LENGTH_SHORT).show();
                        isNewNote = false;  //so we don't insert more entries on a new note
                    } else {        //else update if text has changed
                        getContentResolver().update(ConProviderContract.NOTES_URI, newNote,
                                ConProviderContract._ID + "=?",
                                new String[]{String.valueOf(noteID)});
                        Toast.makeText(getApplicationContext(), "New changes saved", Toast.LENGTH_SHORT).show();
                    }
                    setResult(MainActivity.RESULT_OK);
                }
                return true;

            case R.id.action_delete:
                //if start values are 0 then we haven't saved yet, should not delete
                if (startTitleText.length() == 0 && startBodyText.length() == 0)
                    Toast.makeText(getApplicationContext(), "Nothing to delete", Toast.LENGTH_SHORT).show();
                else {
                    getContentResolver().delete(ConProviderContract.NOTES_URI,
                            ConProviderContract._ID + "=?",
                            new String[]{String.valueOf(noteID)});
                    Toast.makeText(this, "Note deleted", Toast.LENGTH_SHORT).show();
                    setResult(MainActivity.RESULT_OK);
                    finish();
                }
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

}
