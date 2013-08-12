/*
 * Copyright (c) 2013, The Linux Foundation. All rights reserved.
 *
 * Not a Contribution.
 *
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.dialer.calllog;

import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.CharArrayBuffer;
import android.database.ContentObserver;
import android.database.CrossProcessCursor;
import android.database.CursorWindow;
import android.database.DataSetObserver;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CallLog.Calls;
import android.util.Log;

import java.util.ArrayList;

public class RecentCallLogProvider extends ContentProvider {
    String TAG = "RecentCallLogProvider";

    public static String AUTHORITY = "com.android.dialer.calllog.RecentCallLogProvider";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/dictionary");

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        Log.d(TAG, "RecentCallLogProvider query selectionArgs[0]: " + selectionArgs[0]);
        String select = "(" + Calls.NUMBER + " like '%" + selectionArgs[0] + "%'  or  "
                + Calls.CACHED_NAME
                + " like '%" + selectionArgs[0] + "%' )";
        Cursor cursor = getContext().getContentResolver().query(
                Calls.CONTENT_URI,
                new String[] {
                        "_id", Calls.NUMBER, Calls.CACHED_NAME
                },
                select,
                null,
                null);
        if(null == cursor){
            return null;
        }

        return new SuggestionsCursor(cursor, selectionArgs[0]);
    }

    private class SuggestionsCursor implements CrossProcessCursor {
        Cursor mDatabaseCursor;
        int mColumnCount;
        int mCurrentRow;
        ArrayList<Row> mRows = new ArrayList<Row>();

        public SuggestionsCursor(Cursor cursor, String query) {
            mDatabaseCursor = cursor;
            mColumnCount = cursor.getColumnCount();
            try {
                computeRows();
            } catch (SQLiteException ex) {
                mRows.clear();
            }
        }

        public int getCount() {
            return mRows.size();
        }

        public int getType(int columnIndex) {
            return -1;
        }

        private class Row {
            public Row(int row, String name, String number) {
                mName = name;
                mNumber = number;
                mRowNumber = row;
            }

            String mName;
            String mNumber;
            int mRowNumber;

            public String getLine1() {
                if (mName != null && !mName.equals("")) {
                    return mName;
                } else {
                    return mNumber;
                }
            }

            public String getLine2() {
                if (mName != null && !mName.equals("")) {
                    return mNumber;
                } else {
                    return null;
                }
            }
        }

        private void computeRows() {

            int nameColumn = mDatabaseCursor.getColumnIndex("name");
            int numberColumn = mDatabaseCursor.getColumnIndex("number");

            int count = mDatabaseCursor.getCount();
            Log.d("SuggestionsProvider", "computeRows count:" + count);
            for (int i = 0; i < count; i++) {
                mDatabaseCursor.moveToPosition(i);
                String name = mDatabaseCursor.getString(nameColumn);
                String number = mDatabaseCursor.getString(numberColumn);
                mRows.add(new Row(i, name, number));
            }
        }

        public void fillWindow(int position, CursorWindow window) {
            int count = getCount();
            if (position < 0 || position > count - 1) {
                return;
            }
            window.acquireReference();
            try {
                int oldpos = getPosition();
                int pos = position;
                window.clear();
                window.setStartPosition(position);
                int columnNum = getColumnCount();
                window.setNumColumns(columnNum);
                while (moveToPosition(pos) && window.allocRow()) {
                    for (int i = 0; i < columnNum; i++) {
                        String field = getString(i);
                        if (field != null) {
                            if (!window.putString(field, pos, i)) {
                                window.freeLastRow();
                                break;
                            }
                        } else {
                            if (!window.putNull(pos, i)) {
                                window.freeLastRow();
                                break;
                            }
                        }
                    }
                    ++pos;
                }
                moveToPosition(oldpos);
            } catch (IllegalStateException e) {
            } finally {
                window.releaseReference();
            }
        }

        public CursorWindow getWindow() {
            Log.d("SuggestionsProvider", "getWindow ");
            CursorWindow window = new CursorWindow(false);

            fillWindow(0, window);
            return window;
        }

        public boolean onMove(int oldPosition, int newPosition) {
            return ((CrossProcessCursor) mDatabaseCursor).onMove(oldPosition, newPosition);
        }

        private String[] mVirtualColumns = new String[] {
                SearchManager.SUGGEST_COLUMN_INTENT_DATA,
                SearchManager.SUGGEST_COLUMN_INTENT_ACTION,
                SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA,
                SearchManager.SUGGEST_COLUMN_TEXT_1,
                SearchManager.SUGGEST_COLUMN_TEXT_2
        };

        private final int INTENT_DATA_COLUMN = 0;
        private final int INTENT_ACTION_COLUMN = 1;
        private final int INTENT_EXTRA_DATA_COLUMN = 2;
        private final int INTENT_TEXT_COLUMN = 3;
        private final int INTENT_TEXT2_COLUMN = 4;

        public int getColumnCount() {
            return mColumnCount + mVirtualColumns.length;
        }

        public int getColumnIndex(String columnName) {
            for (int i = 0; i < mVirtualColumns.length; i++) {
                if (mVirtualColumns[i].equals(columnName)) {
                    return mColumnCount + i;
                }
            }
            return mDatabaseCursor.getColumnIndex(columnName);
        }

        public String[] getColumnNames() {
            String[] x = mDatabaseCursor.getColumnNames();
            String[] y = new String[x.length + mVirtualColumns.length];

            for (int i = 0; i < x.length; i++) {
                y[i] = x[i];
            }

            for (int i = 0; i < mVirtualColumns.length; i++) {
                y[x.length + i] = mVirtualColumns[i];
            }

            return y;
        }

        public boolean moveToPosition(int position) {
            Log.d("SuggestionsProvider", "moveToPosition " + position);
            if (position >= 0 && position < mRows.size()) {
                mCurrentRow = position;
                mDatabaseCursor.moveToPosition(mRows.get(position).mRowNumber);
                return true;
            } else {
                return false;
            }
        }

        public boolean move(int offset) {
            return moveToPosition(mCurrentRow + offset);
        }

        public boolean moveToFirst() {
            return moveToPosition(0);
        }

        public boolean moveToLast() {
            return moveToPosition(mRows.size() - 1);
        }

        public boolean moveToNext() {
            return moveToPosition(mCurrentRow + 1);
        }

        public boolean moveToPrevious() {
            return moveToPosition(mCurrentRow - 1);
        }

        public String getString(int column) {
            Log.d(TAG, "RecentCallLogProvider getString column" + column);
            if (column < mColumnCount) {
                return mDatabaseCursor.getString(column);
            }

            Row row = mRows.get(mCurrentRow);
            switch (column - mColumnCount) {
                case INTENT_DATA_COLUMN:
                    Uri uri = Calls.CONTENT_URI.buildUpon()
                            .appendQueryParameter("id", mDatabaseCursor.getString(0)).build();
                    return uri.toString();
                case INTENT_ACTION_COLUMN:
                    return Intent.ACTION_SEARCH;
                case INTENT_EXTRA_DATA_COLUMN:
                    return getString(getColumnIndex(SearchManager.SUGGEST_COLUMN_TEXT_1));
                case INTENT_TEXT_COLUMN:
                    return row.getLine1();
                case INTENT_TEXT2_COLUMN:
                    return row.getLine2();
                default:
                    return null;
            }
        }

        public void close() {
            mDatabaseCursor.close();
        }

        public void copyStringToBuffer(int columnIndex, CharArrayBuffer buffer) {
            mDatabaseCursor.copyStringToBuffer(columnIndex, buffer);
        }

        public void deactivate() {
            mDatabaseCursor.deactivate();
        }


        public byte[] getBlob(int columnIndex) {
            return null;
        }

        public int getColumnIndexOrThrow(String columnName)
                throws IllegalArgumentException {
            return 0;
        }

        public String getColumnName(int columnIndex) {
            return null;
        }

        public double getDouble(int columnIndex) {
            return 0;
        }

        public Bundle getExtras() {
            return Bundle.EMPTY;
        }

        public float getFloat(int columnIndex) {
            return 0;
        }

        public int getInt(int columnIndex) {
            return 0;
        }

        public long getLong(int columnIndex) {
            return 0;
        }

        public int getPosition() {
            return mCurrentRow;
        }

        public short getShort(int columnIndex) {
            return 0;
        }

        public boolean getWantsAllOnMoveCalls() {
            return false;
        }

        public boolean isAfterLast() {
            return mCurrentRow >= mRows.size();
        }

        public boolean isBeforeFirst() {
            return mCurrentRow < 0;
        }

        public boolean isClosed() {
            return mDatabaseCursor.isClosed();
        }

        public boolean isFirst() {
            return mCurrentRow == 0;
        }

        public boolean isLast() {
            return mCurrentRow == mRows.size() - 1;
        }

        public boolean isNull(int columnIndex) {
            return false;
        }

        public void registerContentObserver(ContentObserver observer) {
            mDatabaseCursor.registerContentObserver(observer);
        }

        public void registerDataSetObserver(DataSetObserver observer) {
            mDatabaseCursor.registerDataSetObserver(observer);
        }

        public boolean requery() {
            return false;
        }

        public Bundle respond(Bundle extras) {
            return mDatabaseCursor.respond(extras);
        }

        public void setNotificationUri(ContentResolver cr, Uri uri) {
            mDatabaseCursor.setNotificationUri(cr, uri);
        }


        public void unregisterContentObserver(ContentObserver observer) {
            mDatabaseCursor.unregisterContentObserver(observer);
        }

        public void unregisterDataSetObserver(DataSetObserver observer) {
            mDatabaseCursor.unregisterDataSetObserver(observer);
        }
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}
