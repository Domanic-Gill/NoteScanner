package dom.notescanner;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;


public class ConProvider extends ContentProvider {
    private HelperDB helperDB;

    private static final UriMatcher uriMatcher;
    static {
        uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        uriMatcher.addURI(ConProviderContract.AUTHORITY, "notes", 1);
    }
    @Override
    public boolean onCreate() {
        this.helperDB = new HelperDB(this.getContext(), "database", null, 1);
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        SQLiteDatabase db = helperDB.getWritableDatabase();

        switch(uriMatcher.match(uri))
        {
            case 1:
                return db.query("notes",projection,selection,selectionArgs,null,null, sortOrder);
            case 3:
                String q3 = "SELECT * FROM notes";
                return db.rawQuery(q3, selectionArgs);
        }
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        if (uri.getLastPathSegment() == null){
            return "vnd.android.cursor.dir/MyProvider.data.text";
        } else {
            return "vnd.android.cursor.item/MyProvider.data.text";
        }
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues contentValues) {
        SQLiteDatabase db = helperDB.getWritableDatabase();
        db.insert("notes", null, contentValues);
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        SQLiteDatabase db = helperDB.getWritableDatabase();
        db.delete("notes",selection,selectionArgs);
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues contentValues, @Nullable String selection, @Nullable String[] selectionArgs) {
        SQLiteDatabase db = helperDB.getWritableDatabase();
        db.update("notes",contentValues,selection,selectionArgs);
        return 0;
    }
}


