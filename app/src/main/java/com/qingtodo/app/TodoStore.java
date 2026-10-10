package com.qingtodo.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

/** Local-only storage. Every mutation is committed before its UI is updated. */
public final class TodoStore extends SQLiteOpenHelper {
    public static final class Item {
        public long id;
        public String title;
        public String note;
        public boolean done;
        public long created;

        Item(long id, String title, String note, boolean done, long created) {
            this.id = id;
            this.title = title;
            this.note = note;
            this.done = done;
            this.created = created;
        }
    }

    public TodoStore(Context context) { super(context, "qingtodo.db", null, 2); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, note TEXT NOT NULL DEFAULT '', done INTEGER NOT NULL DEFAULT 0, created INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE templates (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, note TEXT NOT NULL DEFAULT '', created INTEGER NOT NULL)");
        createUndoTable(db);
        insertSeed(db, "学习 30 分钟", "专心完成一个小目标");
        insertSeed(db, "运动 20 分钟", "出门走走，或者做一组训练");
        insertSeed(db, "整理房间", "让桌面和房间清爽一点");
    }

    private void insertSeed(SQLiteDatabase db, String title, String note) {
        ContentValues values = values(title, note);
        values.put("created", System.currentTimeMillis());
        db.insertOrThrow("templates", null, values);
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) createUndoTable(db);
    }

    private void createUndoTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE completed_undo (id INTEGER PRIMARY KEY, title TEXT NOT NULL, note TEXT NOT NULL, done INTEGER NOT NULL, created INTEGER NOT NULL)");
    }

    public static String cleanText(String value) {
        if (value == null) return "";
        int start = 0, end = value.length();
        while (start < end) {
            int cp = value.codePointAt(start);
            if (!Character.isWhitespace(cp) && !Character.isSpaceChar(cp)) break;
            start += Character.charCount(cp);
        }
        while (end > start) {
            int cp = value.codePointBefore(end);
            if (!Character.isWhitespace(cp) && !Character.isSpaceChar(cp)) break;
            end -= Character.charCount(cp);
        }
        return value.substring(start, end);
    }

    public static boolean hasTitleContent(String value) {
        if (value == null) return false;
        for (int offset = 0; offset < value.length();) {
            int cp = value.codePointAt(offset);
            int type = Character.getType(cp);
            if (!Character.isWhitespace(cp) && !Character.isSpaceChar(cp)
                    && type != Character.FORMAT && type != Character.CONTROL
                    && type != Character.NON_SPACING_MARK && type != Character.COMBINING_SPACING_MARK
                    && type != Character.ENCLOSING_MARK) return true;
            offset += Character.charCount(cp);
        }
        return false;
    }

    private static ContentValues values(String title, String note) {
        String cleanTitle = cleanText(title);
        String cleanNote = cleanText(note);
        if (!hasTitleContent(cleanTitle)) throw new IllegalArgumentException("请输入事项名称");
        if (cleanTitle.length() > 120) throw new IllegalArgumentException("名称最多 120 个字");
        if (cleanNote.length() > 2000) throw new IllegalArgumentException("备注最多 2000 个字");
        ContentValues values = new ContentValues();
        values.put("title", cleanTitle);
        values.put("note", cleanNote);
        return values;
    }

    public List<Item> tasks() { return items("tasks", "done ASC, created DESC, id DESC"); }
    public List<Item> templates() { return items("templates", "id ASC"); }

    private List<Item> items(String table, String order) {
        List<Item> items = new ArrayList<>();
        String[] cols = table.equals("tasks") ? new String[]{"id", "title", "note", "done", "created"} : new String[]{"id", "title", "note", "created"};
        try (Cursor cursor = getReadableDatabase().query(table, cols, null, null, null, null, order)) {
            while (cursor.moveToNext()) {
                items.add(new Item(cursor.getLong(0), cursor.getString(1), cursor.getString(2), table.equals("tasks") && cursor.getInt(3) == 1, cursor.getLong(table.equals("tasks") ? 4 : 3)));
            }
        }
        return items;
    }

    public long addTask(String title, String note) {
        ContentValues v = values(title, note);
        v.put("done", 0);
        v.put("created", System.currentTimeMillis());
        return getWritableDatabase().insertOrThrow("tasks", null, v);
    }

    public long addTemplate(String title, String note) {
        ContentValues v = values(title, note);
        v.put("created", System.currentTimeMillis());
        return getWritableDatabase().insertOrThrow("templates", null, v);
    }

    public void updateTask(long id, String title, String note) { update("tasks", id, values(title, note)); }
    public void updateTemplate(long id, String title, String note) { update("templates", id, values(title, note)); }
    private void update(String table, long id, ContentValues v) { getWritableDatabase().update(table, v, "id=?", new String[]{Long.toString(id)}); }
    public void setDone(long id, boolean done) {
        ContentValues v = new ContentValues();
        v.put("done", done ? 1 : 0);
        update("tasks", id, v);
    }
    public void deleteTask(long id) { delete("tasks", id); }
    public void deleteTemplate(long id) { delete("templates", id); }
    private void delete(String table, long id) { getWritableDatabase().delete(table, "id=?", new String[]{Long.toString(id)}); }

    /** A SQLite snapshot keeps large batches out of Activity saved-state Bundles. */
    public int clearCompleted() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            int count = (int) DatabaseUtils.queryNumEntries(db, "tasks", "done=1");
            if (count > 0) {
                db.delete("completed_undo", null, null);
                db.execSQL("INSERT INTO completed_undo (id,title,note,done,created) SELECT id,title,note,done,created FROM tasks WHERE done=1");
                db.delete("tasks", "done=1", null);
            }
            db.setTransactionSuccessful();
            return count;
        } finally { db.endTransaction(); }
    }

    public int completedUndoCount() { return (int) DatabaseUtils.queryNumEntries(getReadableDatabase(), "completed_undo"); }
    public void discardCompletedUndo() { getWritableDatabase().delete("completed_undo", null, null); }
    public void restoreCompleted() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.execSQL("INSERT INTO tasks (id,title,note,done,created) SELECT id,title,note,done,created FROM completed_undo");
            db.delete("completed_undo", null, null);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public void restoreTask(Item item) {
        ContentValues v = snapshot(item);
        v.put("id", item.id);
        v.put("done", item.done ? 1 : 0);
        v.put("created", item.created);
        getWritableDatabase().insertOrThrow("tasks", null, v);
    }

    public void restoreTemplate(Item item) {
        ContentValues v = snapshot(item);
        v.put("id", item.id);
        v.put("created", item.created);
        getWritableDatabase().insertOrThrow("templates", null, v);
    }

    private static ContentValues snapshot(Item item) {
        // Undo restores the stored snapshot exactly, including legacy whitespace.
        ContentValues v = new ContentValues();
        v.put("title", item.title);
        v.put("note", item.note);
        return v;
    }
}
