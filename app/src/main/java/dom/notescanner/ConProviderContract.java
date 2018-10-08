package dom.notescanner;

import android.net.Uri;

public class ConProviderContract {
    public static final String AUTHORITY = "dom.notescanner.ConProviderDB";
    public static final Uri NOTES_URI = Uri.parse("content://"+AUTHORITY+"/notes");
    public static final String _ID = "_id";
    public static final String NOTE_TITLE = "noteTitle";
    public static final String NOTE_BODY = "noteBody";
}
