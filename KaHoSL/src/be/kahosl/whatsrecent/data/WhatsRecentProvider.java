/*
 * Copyright (c) 2011, Lauren Darcey and Shane Conder
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are 
 * permitted provided that the following conditions are met:
 * 
 * * Redistributions of source code must retain the above copyright notice, this list of 
 *   conditions and the following disclaimer.
 *   
 * * Redistributions in binary form must reproduce the above copyright notice, this list 
 *   of conditions and the following disclaimer in the documentation and/or other 
 *   materials provided with the distribution.
 *   
 * * Neither the name of the <ORGANIZATION> nor the names of its contributors may be used
 *   to endorse or promote products derived from this software without specific prior 
 *   written permission.
 *   
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY 
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES 
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT 
 * SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, 
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED 
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR 
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, 
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF 
 * THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * 
 * <ORGANIZATION> = Mamlambo
 */
package be.kahosl.whatsrecent.data;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

public class WhatsRecentProvider extends ContentProvider {

    private WhatsRecentDatabase mDB;

    private static final String AUTHORITY = "be.kahosl.whatsrecent.data.WhatsRecentProvider";
    public static final int WHATSRECENT = 100;
    public static final int WHATSRECENT_ID = 110;

    private static final String WHATSRECENT_BASE_PATH = "whatsrecent";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY
            + "/" + WHATSRECENT_BASE_PATH);

    public static final String CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE
            + "/mt-whatsrecent";
    public static final String CONTENT_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE
            + "/mt-whatsrecent";

    private static final UriMatcher sURIMatcher = new UriMatcher(
            UriMatcher.NO_MATCH);

    private static final String DEBUG_TAG = "WhatsRecentProvider";
    static {
        sURIMatcher.addURI(AUTHORITY, WHATSRECENT_BASE_PATH, WHATSRECENT);
        sURIMatcher.addURI(AUTHORITY, WHATSRECENT_BASE_PATH + "/#", WHATSRECENT_ID);
    }

    @Override
    public boolean onCreate() {
        mDB = new WhatsRecentDatabase(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {

        SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
        queryBuilder.setTables(WhatsRecentDatabase.TABLE_WHATSRECENT);

        int uriType = sURIMatcher.match(uri);
        switch (uriType) {
        case WHATSRECENT_ID:
            queryBuilder.appendWhere(WhatsRecentDatabase.ID + "="
                    + uri.getLastPathSegment());
            break;
        case WHATSRECENT:
            // no filter
            break;
        default:
            throw new IllegalArgumentException("Unknown URI");
        }

        Cursor cursor = queryBuilder.query(mDB.getReadableDatabase(),
                projection, selection, selectionArgs, null, null, sortOrder);
        cursor.setNotificationUri(getContext().getContentResolver(), uri);
        return cursor;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        int uriType = sURIMatcher.match(uri);
        SQLiteDatabase sqlDB = mDB.getWritableDatabase();
        int rowsAffected = 0;
        switch (uriType) {
        case WHATSRECENT:
            rowsAffected = sqlDB.delete(WhatsRecentDatabase.TABLE_WHATSRECENT,
                    selection, selectionArgs);
            break;
        case WHATSRECENT_ID:
            String id = uri.getLastPathSegment();
            if (TextUtils.isEmpty(selection)) {
                rowsAffected = sqlDB.delete(WhatsRecentDatabase.TABLE_WHATSRECENT,
                        WhatsRecentDatabase.ID + "=" + id, null);
            } else {
                rowsAffected = sqlDB.delete(WhatsRecentDatabase.TABLE_WHATSRECENT,
                        selection + " and " + WhatsRecentDatabase.ID + "=" + id,
                        selectionArgs);
            }
            break;
        default:
            throw new IllegalArgumentException("Unknown or Invalid URI " + uri);
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return rowsAffected;
    }

    @Override
    public String getType(Uri uri) {
        int uriType = sURIMatcher.match(uri);
        switch (uriType) {
        case WHATSRECENT:
            return CONTENT_TYPE;
        case WHATSRECENT_ID:
            return CONTENT_ITEM_TYPE;
        default:
            return null;
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        int uriType = sURIMatcher.match(uri);
        if (uriType != WHATSRECENT) {
            throw new IllegalArgumentException("Invalid URI for insert");
        }
        SQLiteDatabase sqlDB = mDB.getWritableDatabase();
        try {
            long newID = sqlDB.insertOrThrow(WhatsRecentDatabase.TABLE_WHATSRECENT,
                    null, values);
            if (newID > 0) {
                Uri newUri = ContentUris.withAppendedId(uri, newID);
                getContext().getContentResolver().notifyChange(uri, null);
                return newUri;
            } else {
                throw new SQLException("Failed to insert row into " + uri);
            }
        } catch (SQLiteConstraintException e) {
            Log.i(DEBUG_TAG, "Ignoring constraint failure.");
        }
        return null;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) {
        int uriType = sURIMatcher.match(uri);
        SQLiteDatabase sqlDB = mDB.getWritableDatabase();

        int rowsAffected;

        switch (uriType) {
        case WHATSRECENT_ID:
            String id = uri.getLastPathSegment();
            StringBuilder modSelection = new StringBuilder(WhatsRecentDatabase.ID
                    + "=" + id);

            if (!TextUtils.isEmpty(selection)) {
                modSelection.append(" AND " + selection);
            }

            rowsAffected = sqlDB.update(WhatsRecentDatabase.TABLE_WHATSRECENT,
                    values, modSelection.toString(), null);
            break;
        case WHATSRECENT:
            rowsAffected = sqlDB.update(WhatsRecentDatabase.TABLE_WHATSRECENT,
                    values, selection, selectionArgs);
            break;
        default:
            throw new IllegalArgumentException("Unknown URI");
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return rowsAffected;
    }
}
