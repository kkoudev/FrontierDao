/*
 * Copyright (C) 2017 kkoudev.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package frontier.db;

import android.database.Cursor;


/**
 * データベースのレコードデータ。
 *
 * @author Kou
 *
 */
public class FRDatabaseRecord {


    /**
     * 元となるカーソルデータ
     */
    private final Cursor        dbCursor;



    /**
     * データベースのレコードデータを初期化する。
     *
     * @param cursor 元となるカーソルデータ
     */
    FRDatabaseRecord(
            final Cursor    cursor
            ) {

        dbCursor = cursor;

    }


    /**
     * バイナリ形式でデータを取得する。
     *
     * @param columnIndex データ取得カラム位置
     * @return 指定カラム位置のデータ
     */
    public byte[] getBlob(
            final int columnIndex
            ) {

        return dbCursor.getBlob(columnIndex);

    }


    /**
     * バイナリ形式でデータを取得する。
     *
     * @param columnName データ取得カラム名
     * @return 指定カラム位置のデータ
     */
    public byte[] getBlob(
            final String columnName
            ) {

        return dbCursor.getBlob(dbCursor.getColumnIndex(columnName));

    }


    /**
     * String形式でデータを取得する。
     *
     * @param columnIndex データ取得カラム位置
     * @return 指定カラム位置のデータ
     */
    public String getString(
            final int columnIndex
            ) {

        return dbCursor.getString(columnIndex);

    }


    /**
     * String形式でデータを取得する。
     *
     * @param columnName データ取得カラム名
     * @return 指定カラム位置のデータ
     */
    public String getString(
            final String columnName
            ) {

        return dbCursor.getString(dbCursor.getColumnIndex(columnName));

    }


    /**
     * boolean形式でデータを取得する。
     *
     * @param columnIndex データ取得カラム位置
     * @return 指定カラム位置のデータ
     */
    public boolean getBoolean(
            final int columnIndex
            ) {

        return FRDatabaseUtils.toBoolean(dbCursor.getString(columnIndex));

    }


    /**
     * boolean形式でデータを取得する。
     *
     * @param columnName データ取得カラム名
     * @return 指定カラム位置のデータ
     */
    public boolean getBoolean(
            final String columnName
            ) {

        return FRDatabaseUtils.toBoolean(dbCursor.getString(dbCursor.getColumnIndex(columnName)));

    }


    /**
     * short形式でデータを取得する。
     *
     * @param columnIndex データ取得カラム位置
     * @return 指定カラム位置のデータ
     */
    public short getShort(
            final int columnIndex
            ) {

        return dbCursor.getShort(columnIndex);

    }


    /**
     * short形式でデータを取得する。
     *
     * @param columnName データ取得カラム名
     * @return 指定カラム位置のデータ
     */
    public short getShort(
            final String columnName
            ) {

        return dbCursor.getShort(dbCursor.getColumnIndex(columnName));

    }


    /**
     * int形式でデータを取得する。
     *
     * @param columnIndex データ取得カラム位置
     * @return 指定カラム位置のデータ
     */
    public int getInt(
            final int columnIndex
            ) {

        return dbCursor.getInt(columnIndex);

    }


    /**
     * int形式でデータを取得する。
     *
     * @param columnName データ取得カラム名
     * @return 指定カラム位置のデータ
     */
    public int getInt(
            final String columnName
            ) {

        return dbCursor.getInt(dbCursor.getColumnIndex(columnName));

    }


    /**
     * long形式でデータを取得する。
     *
     * @param columnIndex データ取得カラム位置
     * @return 指定カラム位置のデータ
     */
    public long getLong(
            final int columnIndex
            ) {

        return dbCursor.getLong(columnIndex);

    }


    /**
     * long形式でデータを取得する。
     *
     * @param columnName データ取得カラム名
     * @return 指定カラム位置のデータ
     */
    public long getLong(
            final String columnName
            ) {

        return dbCursor.getLong(dbCursor.getColumnIndex(columnName));

    }


    /**
     * float形式でデータを取得する。
     *
     * @param columnIndex データ取得カラム位置
     * @return 指定カラム位置のデータ
     */
    public float getFloat(
            final int columnIndex
            ) {

        return dbCursor.getFloat(columnIndex);

    }


    /**
     * float形式でデータを取得する。
     *
     * @param columnName データ取得カラム名
     * @return 指定カラム位置のデータ
     */
    public float getFloat(
            final String columnName
            ) {

        return dbCursor.getFloat(dbCursor.getColumnIndex(columnName));

    }


    /**
     * double形式でデータを取得する。
     *
     * @param columnIndex データ取得カラム位置
     * @return 指定カラム位置のデータ
     */
    public double getDouble(
            final int columnIndex
            ) {

        return dbCursor.getDouble(columnIndex);

    }


    /**
     * double形式でデータを取得する。
     *
     * @param columnName データ取得カラム名
     * @return 指定カラム位置のデータ
     */
    public double getDouble(
            final String columnName
            ) {

        return dbCursor.getDouble(dbCursor.getColumnIndex(columnName));

    }


    /**
     * 指定カラム位置のデータが null データかどうかを取得する。
     *
     * @param columnIndex nullかどうかを判定するデータのカラム位置
     * @return データが null の場合は true
     */
    public boolean isNull(
            final int columnIndex
            ) {

        return dbCursor.isNull(columnIndex);

    }


    /**
     * 指定カラム位置のデータが null データかどうかを取得する。
     *
     * @param columnName nullかどうかを判定するデータのカラム名
     * @return データが null の場合は true
     */
    public boolean isNull(
            final String columnName
            ) {

        return dbCursor.isNull(dbCursor.getColumnIndex(columnName));

    }


    /**
     * 指定カラム名のデータが存在するかどうかを取得する。
     *
     * @param columnName 存在するかどうかを判定するカラム名
     * @return 指定カラムの値が存在する場合は true
     */
    public boolean exists(
            final String columnName
            ) {

        try {

            // カラムが存在するかどうか取得する
            dbCursor.getColumnIndexOrThrow(columnName);

            // 存在したこととする
            return true;

        } catch (final IllegalArgumentException e) {

            // 存在しない
            return false;

        }

    }


}
