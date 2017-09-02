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

import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import frontier.db.ConvertUtils.DataConvertType;
import frontier.db.ConvertUtils.DateFormatType;
import frontier.db.FRDatabaseSqlMapper.SQLQuery;



/**
 * データベース操作ユーティリティークラス。
 *
 * @author Kou
 *
 */
public final class FRDatabaseUtils {


    /**
     * データベースフラグ : TRUE (1)
     */
    private static final int            FLAG_TRUE   = 1;

    /**
     * データベースフラグ : FALSE (0)
     */
    private static final int            FLAG_FALSE  = 0;

    /**
     * null値の文字列表現
     */
    private static final String         SQL_VALUE_NULL          = "null";

    /**
     * システムテーブル名一覧
     */
    private static final Set<String>    SQL_SYSTEM_TABLES       = ConvertUtils.toSet(
            "sqlite_sequence"
            );

    /**
     * カラム区切りトークン
     */
    private static final String         SQL_COLUMN_TOKEN        = ",";

    /**
     * EXISTS句の結果で TRUE とする値 (数値形式)
     */
    private static final String         SQL_EXISTS_INTEGER_TRUE = "1";

    /**
     * EXISTS句の結果で TRUE とする値 (文字列形式)
     */
    private static final String         SQL_EXISTS_STRING_TRUE  = "'1'";

    /**
     * UPDATE分のカラムと値のフォーマット<br>
     * <br>
     * 1$ - カラム名
     * 2$ - 値
     */
    private static final String         SQL_UPDATE_ITEM         = "%1$s = '%2$s'";

    /**
     * テーブル名一覧取得SQL
     */
    private static final String         SQL_TABLE_LIST          =
        "SELECT name FROM sqlite_master WHERE type='table'";

    /**
     * テーブルレコード数取得SQL<br>
     * <br>
     * 1$ - テーブル名<br>
     */
    private static final String         SQL_TABLE_RECORD_COUNT  =
        "SELECT COUNT(ROWID) FROM %1$s";

    /**
     * テーブルROWID一覧取得SQL<br>
     * <br>
     * 1$ - テーブル名<br>
     */
    private static final String         SQL_TABLE_ROWID_LIST    =
        "SELECT ROWID FROM %1$s";

    /**
     * テーブルカラム名一覧取得SQL<br>
     * <br>
     * 1$ - テーブル名<br>
     */
    private static final String         SQL_TABLE_COLUMN_LIST   =
        "SELECT * FROM %1$s LIMIT 1";

    /**
     * テーブルレコード一覧取得SQL<br>
     * <br>
     * 1$ - カラム名を {@link #SQL_COLUMN_TOKEN} で連結した文字列<br>
     * 2$ - テーブル名<br>
     */
    private static final String         SQL_TABLE_RECORD_LIST   =
        "SELECT ROWID, %1$s FROM %2$s";

    /**
     * テーブル内容コピーSQL<br>
     * <br>
     * 1$ - コピー先テーブル名<br>
     * 2$ - コピーするカラム名を {@link #SQL_COLUMN_TOKEN} で連結した文字列<br>
     * 3$ - コピーする値を {@link #SQL_COLUMN_TOKEN} で連結した文字列<br>
     */
    private static final String         SQL_TABLE_COPY          =
        "INSERT INTO %1$s (%2$s) VALUES (%3$s)";

    /**
     * テーブル内容更新SQL<br>
     * <br>
     * 1$ - 更新先テーブル名
     * 2$ - 更新するカラム名と値を {@link #SQL_COLUMN_TOKEN} で連結した文字列<br>
     * 3$ - 更新するレコードのROWID
     */
    private static final String         SQL_TABLE_UPDATE        =
        "UPDATE %1$s SET %2$s WHERE ROWID = %3$s";

    /**
     * データベースフォーマット変換処理一覧<br>
     * <br>
     * <table border="1">
     * <tr>
     *   <td>項目</td><td>型</td><td>内容</td>
     * </tr>
     * <tr>
     *   <td>キー</td><td>Class</td><td>変換型を表すクラスオブジェクト</td>
     * </tr>
     * <tr>
     *   <td>値</td><td>ConvertProcessable</td><td>変換処理を定義したハンドラ</td>
     * </tr>
     * </table>
     */
    private static final Map<Class<?>, DatabaseFormatConvertible>          DATABASE_CONVERSIONS   =
        new HashMap<Class<?>, DatabaseFormatConvertible>();



    /**
     * 各種初期化処理を行う
     */
    static {

        final DatabaseFormatConvertible     dateConversion;     // 日付変換処理


        // 日付型変換処理を定義する
        dateConversion = new DatabaseFormatConvertible() {

            @Override
            public Object convertDatabaseFormat(
                    final Object value
                    ) {

                // 日付を文字列へ変換して返す
                return FRDatabaseUtils.dateToTimeString((Date)value);

            }

        };

        // 日付型変換処理を変換テーブルへ設定する
        DATABASE_CONVERSIONS.put(Date.class, dateConversion);
        DATABASE_CONVERSIONS.put(java.sql.Date.class, dateConversion);
        DATABASE_CONVERSIONS.put(Timestamp.class, dateConversion);


        // String型の処理を定義する
        DATABASE_CONVERSIONS.put(String.class, new DatabaseFormatConvertible() {

            @Override
            public Object convertDatabaseFormat(
                    final Object value
                    ) {

                // シングルクォーテーションをエスケープして返す
                return StringUtils.replace((String)value, "'", "''");

            }

        });

        // CharSequence型の処理を定義する
        DATABASE_CONVERSIONS.put(CharSequence.class, new DatabaseFormatConvertible() {

            @Override
            public Object convertDatabaseFormat(
                    final Object value
                    ) {

                // シングルクォーテーションをエスケープして返す
                return StringUtils.replace(((CharSequence)value).toString(), "'", "''");

            }

        });

        // Boolean型の処理を定義する
        DATABASE_CONVERSIONS.put(Boolean.class, new DatabaseFormatConvertible() {

            @Override
            public Object convertDatabaseFormat(
                    final Object value
                    ) {

                // Integer型へ変換して返す
                return ConvertUtils.toType(
                        DataConvertType.DATABASE,
                        Integer.class,
                        value
                        );
            }

        });


    }





    /**
     * インスタンス生成防止。
     *
     */
    private FRDatabaseUtils() {

        // 処理なし

    }


    /**
     * 日時文字列を日付型へ変換する。
     *
     * @param timeString 日時文字列
     * @return 日付型
     */
    public static Date timeStringToDate(
            final String    timeString
            ) {

        // nullの場合は例外
        if (timeString == null) {

            throw new IllegalArgumentException();

        }

        // 日付へ変換して返す
        return ConvertUtils.toDate(timeString);

    }


    /**
     * 日時文字列をタイムスタンプ型へ変換する。
     *
     * @param timeString 日時文字列
     * @return 日付型
     */
    public static Timestamp timeStringToTimestamp(
            final String    timeString
            ) {

        // nullの場合は例外
        if (timeString == null) {

            throw new IllegalArgumentException();

        }

        // 日付へ変換する
        final Date  date = ConvertUtils.toDate(timeString);

        // タイムスタンプ型へ変換して返す
        return date == null ? null : new Timestamp(date.getTime());

    }


    /**
     * 日付型を日時文字列へ変換する。
     *
     * @param date 日付型
     * @return 日時文字列
     */
    public static String dateToTimeString(
            final Date date
            ) {

        // nullの場合は例外
        if (date == null) {

            throw new IllegalArgumentException();

        }

        // 日付を日時文字列へ変換する
        return ConvertUtils.formatDate(DateFormatType.SQL99, date);

    }


    /**
     * 時刻を日時文字列へ変換する。
     *
     * @param time 時刻
     * @return 日時文字列
     */
    public static String dateToTimeString(
            final long  time
            ) {

        return dateToTimeString(new Date(time));

    }


    /**
     * SQL文のEXISTS句で返された結果データや、フラグ形式の文字列を boolean型へ変換する。<br>
     * <br>
     * 通常の boolean 型変換に加えて、'1' や 1 の文字列も true として判断するように変換する。
     *
     * @param value   SQL文のEXISTS句で返された結果データ
     * @return SQL文のEXISTS句で返された結果データをBoolean型へ変換した値
     */
    public static boolean toBoolean(
            final String    value
            ) {

        return SQL_EXISTS_INTEGER_TRUE.equals(value)
               || SQL_EXISTS_STRING_TRUE.equals(value)
               || Boolean.parseBoolean(value);

    }


    /**
     * boolean型をSQLのフラグ形式の値へ変換する。
     *
     * @param value 変換する boolean 値
     * @return SQLのフラグ形式の値
     */
    public static int toInt(
            final boolean   value
            ) {

        // フラグ形式の値へ変換して返す
        return value ? FLAG_TRUE : FLAG_FALSE;

    }


    /**
     * 指定された名称値の値をデータベースで利用可能な適切な形式へ変換する。
     *
     * @param pairs 変換する名称値パラメータ一覧
     * @return 変換した名称値の一覧
     */
    static FRDatabaseParam[] convertNameValueTypes(
            final FRDatabaseParam...    pairs
            ) {

        final FRDatabaseParam[]     retPairs = new FRDatabaseParam[pairs.length];


        // 全名称値分ループする
        for (int i = 0; i < pairs.length; i++) {

            final FRDatabaseParam   pair        = pairs[i];           // 名称値
            final Object            value       = pair.getValue();    // 値


            // 変換フォーマット処理をテーブルから取得する
            final DatabaseFormatConvertible     formatConversion =
                (value == null ? null : DATABASE_CONVERSIONS.get(value.getClass()));

            // フォーマット変換処理がある場合
            if (formatConversion != null) {

                // フォーマットを変換して配列へ追加する
                retPairs[i] = new FRDatabaseParam(
                        pair.getName(),
                        formatConversion.convertDatabaseFormat(value)
                        );

            } else {

                // そのまま配列へ追加する
                retPairs[i] = pairs[i];

            }

        }


        // 作成した配列を返す
        return retPairs;

    }


    /**
     * 指定されたJavaBeanクラスから名称値パラメータ一覧を取得する。
     *
     * @param bean JavaBeanクラスインスタンス
     * @return 指定されたJavaBeanクラスの名称値パラメータ一覧
     */
    static FRDatabaseParam[] createNameValuePairs(
            final Object    bean
            ) {

        // パラメータが null の場合
        if (bean == null) {

            // 空の一覧を返す
            return new FRDatabaseParam[0];

        }

        // 指定クラスが名称値型の場合
        if (bean instanceof FRDatabaseParam) {

            // そのまま返す
            return new FRDatabaseParam[] {
                    (FRDatabaseParam)bean
                    };

        }


        try {

            // 返却名称値一覧を作成する
            final List<FRDatabaseParam>     retPairs = new ArrayList<FRDatabaseParam>();

            // 指定JavaBeanクラスのgetterメソッド一覧を取得する
            final List<Method>              methods = ReflectUtils.getGetterMethods(bean.getClass());

            // 全getterメソッド分処理をする
            for (final Method method : methods) {

                // getterメソッドから値を取得する
                final Object    value = ReflectUtils.invokeInstancePublicMethod(bean, method.getName());

                // 名称値へ変換して設定する
                retPairs.add(
                        new FRDatabaseParam(
                                ReflectUtils.toBeanFieldName(method.getName()),
                                value
                                )
                        );

            }

            // 作成した返却名称値一覧を返す
            return retPairs.toArray(new FRDatabaseParam[]{});

        } catch (final ReflectException e) {

            e.printStackTrace();

        }

        // 取得パラメータなし
        return null;

    }


    /**
     * 指定されたJavaBeanクラスから名前をキーとして値を取得する検索テーブルを作成する。
     *
     * @param bean 検索テーブルの検索対象となる名称値を持ったJavaBeanクラス
     * @return 検索テーブル
     */
    static Map<String, Object> createSearchMap(
            final Object    bean
            ) {

        return createSearchMap(createNameValuePairs(bean));

    }


    /**
     * 指定された複数の名称値パラメータから名前をキーとして値を取得する検索テーブルを作成する。
     *
     * @param pairs 検索テーブルの検索対象となる名称値パラメータ
     * @return 検索テーブル
     */
    static Map<String, Object> createSearchMap(
            final FRDatabaseParam...  pairs
            ) {

        final Map<String, Object>   retSearchMap = new HashMap<String, Object>();

        // パラメータがない場合
        if (pairs == null) {

            // 空の検索テーブルを返す
            return retSearchMap;

        }


        // 型変換処理を行う
        final FRDatabaseParam[] convertedPairs = convertNameValueTypes(pairs);

        // 全名称値分ループする
        for (final FRDatabaseParam pair : convertedPairs) {

            // そのままマップへ追加する
            retSearchMap.put(pair.getName(), pair.getValue());

        }


        // 作成した検索テーブルを返す
        return retSearchMap;

    }


    /**
     * 指定されたデータベースのテーブル情報マップを取得する。
     *
     * @param db    テーブル情報一覧取得元データベース
     * @return テーブル情報マップ。失敗した場合は null
     */
    private static Map<String, FRDatabaseTable> getDatabaseTablesMap(
            final SQLiteDatabase    db
            ) {

        final Map<String, FRDatabaseTable>  tablesMap   = new HashMap<String, FRDatabaseTable>();   // 返却テーブル情報一覧
        final List<String>                  tableNames  = new ArrayList<String>();                  // テーブル名一覧
        Cursor                              cursor      = null;                                     // カーソル情報


        try {

            // テーブル名一覧取得のSQLを実行する
            cursor = db.rawQuery(SQL_TABLE_LIST, null);

            // カーソルを先頭に移動する
            if (!cursor.moveToFirst()) {

                // 失敗
                return null;

            }

            // カラム数が 1 以外の場合
            if (cursor.getColumnCount() != 1) {

                // 失敗
                return null;

            }


            // データがなくなるまでループする
            do {

                // テーブル名を取得する
                final String    tableName = cursor.getString(0);

                // システムテーブル以外の場合
                if (!SQL_SYSTEM_TABLES.contains(tableName)) {

                    // テーブル名を取得して追加する
                    tableNames.add(tableName);

                }

            // 次のデータがあれば繰り返し
            } while (cursor.moveToNext());


            // カーソルを閉じる
            cursor.close();
            cursor = null;

            // テーブル名一覧分処理をする
            for (final String tableName : tableNames) {

                // テーブルカラム一覧取得SQLを実行する
                cursor = db.rawQuery(
                        String.format(SQL_TABLE_COLUMN_LIST, tableName),
                        null
                        );

                // カラム名一覧を取得する
                final String[]  columnNames = cursor.getColumnNames();

                // カーソルが先頭へ移動出来ない場合
                if (!cursor.moveToFirst()) {

                    // テーブル情報を作成して追加する
                    tablesMap.put(
                            tableName,
                            new FRDatabaseTable(
                                    tableName,
                                    columnNames,
                                    0
                                    )
                            );

                    // 次のテーブルへ
                    continue;

                }

                // カーソルを閉じる
                cursor.close();
                cursor = null;


                // テーブルレコード数を取得する
                cursor = db.rawQuery(
                        String.format(SQL_TABLE_RECORD_COUNT, tableName),
                        null
                        );

                // カーソルが先頭へ移動出来ない場合
                if (!cursor.moveToFirst()) {

                    // テーブル情報を作成して追加する
                    tablesMap.put(
                            tableName,
                            new FRDatabaseTable(
                                    tableName,
                                    columnNames,
                                    0
                                    )
                            );

                    // 次のテーブルへ
                    continue;

                }

                // テーブル情報を作成して追加する
                tablesMap.put(
                        tableName,
                        new FRDatabaseTable(
                                tableName,
                                columnNames,
                                cursor.getInt(0)
                                )
                        );

                // カーソルを閉じる
                cursor.close();
                cursor = null;

            }

        } catch (final Throwable e) {

            e.printStackTrace();

            // 失敗
            return null;

        } finally {

            // カーソルがある場合
            if (cursor != null) {

                // カーソルを閉じる
                cursor.close();

            }

        }


        // テーブル情報マップを返却する
        return tablesMap;

    }


    /**
     * 指定されたデータベーステーブルのROWID一覧を取得する。
     *
     * @param db    ROWID一覧を取得するデータベース
     * @param table ROWID一覧を取得するテーブル情報
     * @return 指定されたデータベーステーブルのROWID一覧
     */
    private static Set<String> getDatabaseTableRowIds(
            final SQLiteDatabase    db,
            final FRDatabaseTable   table
            ) {

        final Set<String>       rowIds  = new HashSet<String>();    // ROWID一覧
        Cursor                  cursor  = null;                     // カーソル情報


        try {

            // テーブルROWID一覧取得SQLを実行する
            cursor = db.rawQuery(
                    String.format(SQL_TABLE_ROWID_LIST, table.getName()),
                    null
                    );

            // カーソルを先頭に移動する
            if (!cursor.moveToFirst()) {

                // 失敗
                return rowIds;

            }

            // カラム数が 1 以外の場合
            if (cursor.getColumnCount() != 1) {

                // 失敗
                return rowIds;

            }


            // 全レコード分繰り返す
            do {

                // ROWID一覧へ追加する
                rowIds.add(cursor.getString(0));

            } while (cursor.moveToNext());


            // カーソルを閉じる
            cursor.close();
            cursor = null;

        } catch (final Throwable e) {

            e.printStackTrace();

            // 失敗
            return null;

        } finally {

            // カーソルがある場合
            if (cursor != null) {

                // カーソルを閉じる
                cursor.close();

            }

        }


        // ROWID一覧情報を返却する
        return rowIds;

    }


    /**
     * 古いデータベースと新しいデータベースを比較し、更新が必要かどうかを取得する。
     *
     * @param oldDBPath     古いデータベースのファイルパス
     * @param newDBPath     新しいデータベースのファイルパス
     * @return 新しいデータベースのバージョンが大きい場合は true。それ以外の場合は false
     */
    static boolean needsUpdate(
            final String    oldDBPath,
            final String    newDBPath
            ) {

        SQLiteDatabase      oldDB    = null;    // 古いデータベース
        SQLiteDatabase      newDB    = null;    // 新しいデータベース


        try {

            // 古いデータベースを取得する
            oldDB = SQLiteDatabase.openDatabase(
                    oldDBPath,
                    null,
                    SQLiteDatabase.OPEN_READONLY
                    );

            // 新しいデータベースを取得する
            newDB = SQLiteDatabase.openDatabase(
                    newDBPath,
                    null,
                    SQLiteDatabase.OPEN_READWRITE
                    );

            // 新しいDBバージョンが古いDBバージョンより大きい場合は更新が必要とする
            return (oldDB.getVersion() < newDB.getVersion());

        } catch (final Throwable e) {

            e.printStackTrace();

            // 更新不要を返す
            return false;

        } finally {

            // 新しいデータベースがある場合
            if (newDB != null) {

                // データベースを閉じる
                newDB.close();

            }

            // 古いデータベースがある場合
            if (oldDB != null) {

                // データベースを閉じる
                oldDB.close();

            }

        }

    }


    /**
     * 古いデータベースのデータ内容を新規データベースへ追加する。
     *
     * @param oldDBPath     古いデータベースのファイルパス
     * @param newDBPath     新しいデータベースのファイルパス
     * @param savable       保存判定処理ハンドラ
     * @return 更新が発生した場合は true。処理失敗または更新が発生しなかった場合は false
     */
    static boolean updateDatabase(
            final String                oldDBPath,
            final String                newDBPath,
            final FRDatabaseSavable     savable
            ) {

        // 引数が不正の場合は例外
        if ((oldDBPath == null) || (newDBPath == null)) {

            throw new IllegalArgumentException();

        }


        final int           oldVersion;             // 古いデータベースのバージョン
        final int           newVersion;             // 新しいデータベースのバージョン
        SQLiteDatabase      oldDB       = null;     // 古いデータベース
        SQLiteDatabase      newDB       = null;     // 新しいデータベース
        Cursor              oldCursor   = null;     // 古いデータベースの読み込みカーソル


        try {

            // 古いデータベースを取得する
            oldDB = SQLiteDatabase.openDatabase(
                    oldDBPath,
                    null,
                    SQLiteDatabase.OPEN_READONLY
                    );

            // 新しいデータベースを取得する
            newDB = SQLiteDatabase.openDatabase(
                    newDBPath,
                    null,
                    SQLiteDatabase.OPEN_READWRITE
                    );

            // DBのバージョンを取得する
            oldVersion = oldDB.getVersion();
            newVersion = newDB.getVersion();

            // バージョンをログ出力
            Log.d(FRDatabaseUtils.class.getName(),
                  "[Database Old] version = " + oldVersion
                  );
            Log.d(FRDatabaseUtils.class.getName(),
                  "[Database New] version = " + newVersion
                  );

            // 新しいDBバージョンが古いDBバージョン以下の場合
            if (oldVersion >= newVersion) {

                // 更新失敗
                return false;

            }

            // 古いデータベースと新規データベースのテーブル情報を取得する
            final Map<String, FRDatabaseTable>     oldTablesMap = getDatabaseTablesMap(oldDB);
            final Map<String, FRDatabaseTable>     newTablesMap = getDatabaseTablesMap(newDB);

            // 一覧が取得できなかった場合
            if ((oldTablesMap == null) || (newTablesMap == null)) {

                // 失敗
                return false;

            }

            // 文字列変換処理ハンドラを取得する
            final DatabaseFormatConvertible     formatConversion = DATABASE_CONVERSIONS.get(String.class);

            // ハンドラが null の場合
            if (formatConversion == null) {

                throw new IllegalStateException("There is not string format conversion.");

            }


            // トランザクションを開始する
            newDB.beginTransaction();

            // 古いデータベースのテーブル分処理をする
            for (final Map.Entry<String, FRDatabaseTable> entry : oldTablesMap.entrySet()) {

                // 新しいデータベーステーブルのテーブル情報を取得する
                final FRDatabaseTable   newTable = newTablesMap.get(entry.getKey());

                // 該当テーブルがない場合
                // または古いテーブルにレコードデータがない場合
                // または保存判定処理ハンドラがない場合で、新しいテーブルにレコードデータがある場合
                // または保存判定処理ハンドラがある場合で、対象テーブルの保存が可能でない場合
                if ((newTable == null)
                    || !entry.getValue().hasRecords()
                    || ((savable == null) && newTable.hasRecords())
                    || ((savable != null) && !savable.canSaveTableInner(
                                                newTable.getName(), oldVersion, newVersion))
                    ) {

                    // 次のテーブルへ
                    continue;

                }


                // 古いデータベースのカラム一覧とレコード一覧を取得する
                final FRDatabaseTable       oldTable        = entry.getValue();
                final List<String>          insertColumns   = oldTable.getColumns();

                // 新しいデータベースと一致するカラムのみを抽出する
                insertColumns.retainAll(newTable.getColumns());

                // カラム連結文字列を取得する
                final String    columnJoinStr = StringUtils.join(
                        insertColumns.toArray(new String[insertColumns.size()]),
                        SQL_COLUMN_TOKEN
                        );

                // カラム取得用のSQL文を作成する
                final String    columnSelectSql = String.format(
                        SQL_TABLE_RECORD_LIST,
                        columnJoinStr,
                        oldTable.getName()
                        );


                // 古いテーブルと新しいテーブルのレコードを取得する
                oldCursor = oldDB.rawQuery(columnSelectSql, null);


                // レコードの先頭へ移動出来ない場合
                if (!oldCursor.moveToFirst()) {

                    // カーソルを閉じる
                    oldCursor.close();
                    oldCursor = null;

                    // 次のテーブルへ
                    continue;

                }


                // 更新判定処理ハンドラがない場合
                if (savable == null) {

                    // レコードデータを取得する
                    do {

                        final List<String>  values = new ArrayList<String>();


                        // 対象カラム分繰り返し
                        for (final String columnName : insertColumns) {

                            // レコードデータを文字列形式で追加する
                            values.add(
                                    String.format(
                                            SQLQuery.FORMAT_VALUE_STRING,
                                            formatConversion.convertDatabaseFormat(
                                                    oldCursor.getString(oldCursor.getColumnIndex(columnName))
                                                    )
                                            )
                                    );

                        }

                        // 新しいデータベースへ古いデータベースの内容をコピーする
                        newDB.execSQL(
                                String.format(
                                    SQL_TABLE_COPY,
                                    newTable.getName(),
                                    columnJoinStr,
                                    StringUtils.join(
                                            values.toArray(new String[values.size()]),
                                            SQL_COLUMN_TOKEN)
                                    )
                                );

                    // 次のデータがあれば繰り返し
                    } while (oldCursor.moveToNext());

                } else {

                    // 現在のレコードを表すデータを作成する
                    final FRDatabaseRecord  oldRecord = new FRDatabaseRecord(oldCursor);

                    // 新しいデータベースのROWID一覧
                    final Set<String>       newTableRowIds = getDatabaseTableRowIds(newDB, newTable);


                    // レコードデータを取得する
                    do {

                        final List<String>  values      = new ArrayList<String>();  // 更新値

                        // レコードの保存が可能な場合
                        final int   result = savable.canSaveRecord(
                                oldTable.getName(),
                                oldRecord,
                                oldVersion,
                                newVersion
                                );

                        // 結果別処理
                        switch (result) {

                        // 保存なし
                        case FRDatabaseSavable.RESULT_SKIP:

                            // 処理なし
                            break;


                        // 追加保存
                        // 更新保存
                        case FRDatabaseSavable.RESULT_INSERT:
                        case FRDatabaseSavable.RESULT_UPDATE:

                            // レコード追加保存以外の場合
                            // または新しいデータベースのROWID一覧に同じROWIDがない場合
                            if ((result != FRDatabaseSavable.RESULT_INSERT)
                                || !newTableRowIds.contains(oldCursor.getString(0))
                                ) {

                                // 対象カラム分繰り返し
                                for (final String columnName : insertColumns) {

                                    // カラムインデックスを取得する
                                    final int   columnIndex = oldCursor.getColumnIndex(columnName);

                                    // 保存非対象カラムの場合
                                    if (!savable.canSaveColumnInner(
                                            oldTable.getName(),
                                            columnName,
                                            oldCursor.getString(columnIndex),
                                            oldVersion,
                                            newVersion
                                            )) {

                                        // 次のカラムへ
                                        continue;

                                    }

                                    // 指定カラムのデータが null の場合
                                    if (oldCursor.isNull(columnIndex)) {

                                        // nullの文字列表現を追加する
                                        values.add(SQL_VALUE_NULL);

                                    } else {

                                        // レコードデータを文字列形式で追加する
                                        values.add(
                                                String.format(
                                                        SQL_UPDATE_ITEM,
                                                        columnName,
                                                        formatConversion.convertDatabaseFormat(
                                                                oldCursor.getString(columnIndex)
                                                                )
                                                        )
                                                );

                                    }

                                }

                                // 新しいデータベースのレコードを上書きする
                                newDB.execSQL(
                                        String.format(
                                            SQL_TABLE_UPDATE,
                                            newTable.getName(),
                                            StringUtils.join(
                                                    values.toArray(new String[values.size()]),
                                                    SQL_COLUMN_TOKEN),
                                            oldCursor.getString(0)
                                            )
                                        );

                            } else {

                                // 対象カラム分繰り返し
                                for (final String columnName : insertColumns) {

                                    // カラムインデックスを取得する
                                    final int   columnIndex = oldCursor.getColumnIndex(columnName);

                                    // 指定カラムのデータが null の場合
                                    if (oldCursor.isNull(columnIndex)) {

                                        // nullの文字列表現を追加する
                                        values.add(SQL_VALUE_NULL);

                                    } else {

                                        // レコードデータを文字列形式で追加する
                                        values.add(
                                                String.format(
                                                        SQLQuery.FORMAT_VALUE_STRING,
                                                        formatConversion.convertDatabaseFormat(
                                                                oldCursor.getString(columnIndex)
                                                                )
                                                        )
                                                );

                                    }

                                }

                                // 新しいデータベースへ古いデータベースの内容をコピーする
                                newDB.execSQL(
                                        String.format(
                                            SQL_TABLE_COPY,
                                            newTable.getName(),
                                            columnJoinStr,
                                            StringUtils.join(
                                                    values.toArray(new String[values.size()]),
                                                    SQL_COLUMN_TOKEN)
                                            )
                                        );

                            }
                            break;


                        // 不正な結果種別
                        default:

                            throw new IllegalStateException(
                                    "Illegal result type of 'FRDatabaseSavable' [type = " + result + "]"
                                    );

                        }

                    // 次のデータがあれば繰り返し
                    } while (oldCursor.moveToNext());

                }


                // カーソルを閉じる
                oldCursor.close();
                oldCursor = null;

            }

            // トランザクション成功を設定する
            newDB.setTransactionSuccessful();

            // 更新成功
            return true;

        } catch (final Throwable e) {

            e.printStackTrace();

            // 失敗
            return false;

        } finally {

            // 古いデータベースのカーソルがある場合
            if (oldCursor != null) {

                // カーソルを閉じる
                oldCursor.close();

            }

            // 新しいデータベースがある場合
            if (newDB != null) {

                // トランザクション中の場合
                if (newDB.inTransaction()) {

                    // トランザクションを終了する
                    newDB.endTransaction();

                }

                // データベースを閉じる
                newDB.close();

            }

            // 古いデータベースがある場合
            if (oldDB != null) {

                // データベースを閉じる
                oldDB.close();

            }

        }

    }




    /**
     * データベース形式へのフォーマット変換処理。<br>
     * <br>
     * データベースに適合するフォーマットへ変換するための処理を定義するリスナー。<br>
     *
     * @author Kou
     *
     */
    private interface DatabaseFormatConvertible {


        /**
         * 指定された値の型を調べ
         *
         * @param value 形式変換する値
         * @return 形式変換後の値
         */
        Object convertDatabaseFormat(
                final Object    value
                );


    }


}



/**
 * データベーステーブルデータ。
 *
 * @author Kou
 *
 */
class FRDatabaseTable {


    /**
     * テーブル名
     */
    private final String                name;

    /**
     * カラム名一覧
     */
    private final String[]              columns;

    /**
     * レコード数
     */
    private final int                   recordCount;



    /**
     * データベーステーブルデータを初期化する。
     *
     * @param argName           テーブル名
     * @param argColumns        カラム名一覧
     * @param argRecordCount    レコード数
     */
    public FRDatabaseTable(
            final String        argName,
            final String[]      argColumns,
            final int           argRecordCount
            ) {

        name        = argName;
        columns     = argColumns.clone();
        recordCount = argRecordCount;

    }


    /**
     * テーブル名を取得する。
     *
     * @return テーブル名
     */
    public String getName() {

        return name;

    }


    /**
     * カラム名一覧を取得する。
     *
     * @return カラム名一覧
     */
    public List<String> getColumns() {

        return ConvertUtils.toList(columns);

    }


    /**
     * レコードデータがあるかどうかを取得する。
     *
     * @return レコードデータがあるかどうか
     */
    public boolean hasRecords() {

        return recordCount > 0;

    }


    /**
     * レコード数を取得する。
     *
     * @return レコード数
     */
    public int getRecordCount() {

        return recordCount;

    }


}
