package dom.notescanner;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class HelperDB extends SQLiteOpenHelper {

    public HelperDB(Context context, String name, SQLiteDatabase.CursorFactory factory, int version) {
        super(context, name, factory, version);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE recipes (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "noteTitle VARCHAR(128), " +
                "noteBody VARCHAR(2048)" +
                ");");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS recipes;");
        onCreate(db);
    }
}

