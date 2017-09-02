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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import frontier.db.annotation.FRDatabaseMappingXml;


/**
 * データベースアクセスマネージャー。
 *
 * @author Kou
 *
 */
public final class FRDatabaseManager {


    /**
     * デフォルト接続先DBファイル名プリファレンスキー
     */
    private static final String     PREF_KEY_DB_DEFAULT_NAME        = "PREF_KEY_DB_DEFAULT_NAME";

    /**
     * 最適化コマンド
     */
    private static final String     SQL_OPTIMIZE                    = "VACUUM;";

    /**
     * XMLリソースID取得用クラス名フォーマット<br>
     * <br>
     * 1$ - AndroidManifestに記載されたアプリケーションパッケージ名
     */
    private static final String     XML_RESOURCE_CLASS_NAME_FORMAT  = "%s.R$xml";

    /**
     * データベースアクセスマネージャーのキャッシュ
     */
    private static final Map<String, WeakReference<FRDatabaseManager>>  DB_MANAGER_CACHES =
        new WeakHashMap<String, WeakReference<FRDatabaseManager>>();

    /**
     * デフォルト接続先DBファイル名
     */
    private static String           dbDefaultName;

    /**
     * 接続先DBファイル名
     */
    private String                  dbName;




    /**
     * インスタンス生成防止。
     *
     */
    private FRDatabaseManager() {

        // 処理なし

    }
    /**
     * 指定した入力ストリームの内容を指定した出力ストリームへコピーする。
     *
     * @param in        コピー元入力ストリーム
     * @param out       コピー先出力ストリーム
     * @param closing   処理完了またはエラー発生時に入力ストリームと出力ストリームをクローズするかどうか
     * @return 処理が成功したかどうか
     */
    private static boolean copyStream(
            final InputStream   in,
            final OutputStream  out,
            final boolean       closing
            ) {

        // ストリームが null の場合
        if ((in == null) || (out == null)) {

            throw new IllegalArgumentException();

        }


        final int               bufferSize = 8192;
        final byte[]            readBuffer = new byte[bufferSize];      // 読み込みバッファ
        int                     readLength;                             // 読み込み長さ

        try {

            // 読み込みバッファへデータを読み込む
            readLength = in.read(readBuffer);

            // 読み込みバイト数がバッファ長と等しければ繰り返し
            while (readLength != -1) {

                // 読み込んだデータを書き込む
                out.write(readBuffer, 0, readLength);

                // 次のデータを読み込む
                readLength = in.read(readBuffer);

            }

            // バッファをフラッシュする
            out.flush();

        } catch (final IOException e) {

            e.printStackTrace();

            // 読み込みエラーが発生した場合は失敗
            return false;

        } finally {

            // 処理完了またはエラー発生時にクローズする場合
            if (closing) {

                try {

                    // ストリームをクローズする
                    in.close();
                    out.close();

                } catch (final IOException e) {

                    e.printStackTrace();

                }

            }

        }


        // 処理成功
        return true;

    }


    /**
     * 指定されたデータベースファイルをアセッツからコピーする。
     *
     * @param context       利用するコンテキスト情報
     * @param dbName        データベースファイル名
     * @param overwrite     上書きコピーする場合は true
     * @return コピーに成功した場合は true。失敗した場合は false
     */
    public static boolean copyDatabaseFromAssets(
            final Context       context,
            final String        dbName,
            final boolean       overwrite
            ) {

        return copyDatabaseFromAssets(context, dbName, dbName, overwrite);

    }


    /**
     * 指定されたデータベースファイルをアセッツから別名でコピーする。
     *
     * @param context       利用するコンテキスト情報
     * @param dbName        データベースファイル名
     * @param newDBName     コピー後のデータベースファイル名
     * @param overwrite     上書きコピーする場合は true
     * @return コピーに成功した場合は true。失敗した場合は false
     */
    public static boolean copyDatabaseFromAssets(
            final Context       context,
            final String        dbName,
            final String        newDBName,
            final boolean       overwrite
            ) {

        // 引数が不正の場合は例外
        if ((context == null)
            || (dbName == null)
            || (dbName.length() == 0)
            || (newDBName == null)
            || (newDBName.length() == 0)
            ) {

            throw new IllegalArgumentException();

        }


        // DBファイルを作成する
        final File      dbFile = context.getDatabasePath(newDBName);

        // ファイルが既に存在する場合
        if (dbFile.exists()) {

            // ファイルを上書きする場合
            if (overwrite) {

                // ファイル削除に失敗した場合
                if (!dbFile.delete()) {

                    // コピー失敗
                    return false;

                }

            } else {

                // 既にファイルがあるので成功とする
                return true;

            }

        }

        // ディレクトリの作成に失敗した場合
        // かつディレクトリが存在しない場合
        if (!dbFile.getParentFile().mkdirs()
            && !dbFile.getParentFile().exists()
            ) {

            // 接続失敗
            return false;

        }


        try {

            // ファイルをコピーして結果を返す
            return copyStream(
                    context.getAssets().open(dbName),
                    new FileOutputStream(dbFile),
                    true
                    );

        } catch (final IOException e) {

            e.printStackTrace();

            // 失敗
            return false;

        }

    }


    /**
     * デフォルト接続先のデータベースファイル名を設定する。
     *
     * @param context   利用するコンテキスト
     * @param name      デフォルト接続先のデータベースファイル名
     */
    public static void setDefaultDatabaseName(
            final Context   context,
            final String    name
            ) {

        // 引数が null の場合は例外
        if (context == null) {

            throw new IllegalArgumentException();

        }

        // プリファレンスを取得する
        final SharedPreferences         pref = context.getSharedPreferences(
                PREF_KEY_DB_DEFAULT_NAME,
                Context.MODE_PRIVATE
                );
        final SharedPreferences.Editor  edit = pref.edit();

        // ファイル名をプリファレンスに書きこむ
        edit.putString(PREF_KEY_DB_DEFAULT_NAME, name);
        edit.commit();

        // DB名をキャッシュする
        dbDefaultName = name;

    }


    /**
     * デフォルトデータベースへのアクセスマネージャーを取得する。
     *
     * @param context   利用するコンテキスト
     * @return デフォルトデータベースへのアクセスマネージャー
     * @throws IllegalArgumentException 利用するコンテキストが null の場合
     */
    public static FRDatabaseManager getInstance(
            final Context   context
            ) {

        // 引数が null の場合は例外
        if (context == null) {

            throw new IllegalArgumentException();

        }


        String    defaultName = dbDefaultName;    // デフォルト名

        // デフォルト名が null の場合
        if (defaultName == null) {

            // プリファレンスを取得する
            final SharedPreferences         pref = context.getSharedPreferences(
                    PREF_KEY_DB_DEFAULT_NAME,
                    Context.MODE_PRIVATE
                    );

            // プリファレンスの値を読み込む
            defaultName = pref.getString(PREF_KEY_DB_DEFAULT_NAME, null);

        }

        // 指定されたDBのインスタンスを返す
        return getInstance(defaultName);

    }


    /**
     * 指定されたデータベースへのアクセスマネージャーを取得する。
     *
     * @param databaseName  接続先データベースファイル名
     * @return 指定されたデータベースへのアクセスマネージャー
     * @throws IllegalArgumentException 接続先データベースファイル名が null または 空文字 の場合
     */
    public static FRDatabaseManager getInstance(
            final String    databaseName
            ) {

        // 接続先データベースファイル名が不正の場合
        if ((databaseName == null) || (databaseName.length() == 0)) {

            throw new IllegalArgumentException();

        }


        // DBマネージャーキャッシュテーブルでロックする
        synchronized (DB_MANAGER_CACHES) {

            FRDatabaseManager   manager;      // DBマネージャー


            // 指定されたDB名のマネージャーがキャッシュに存在する場合
            if (DB_MANAGER_CACHES.containsKey(databaseName)) {

                // DBマネージャーを返す
                manager = DB_MANAGER_CACHES.get(databaseName).get();

                // DBマネージャーがまだキャッシュされている場合
                if (manager != null) {

                    // DBマネージャーを返す
                    return manager;

                }

            }

            // DBマネージャーを新規作成する
            manager = new FRDatabaseManager();

            // DBファイル名を設定する
            manager.dbName = databaseName;

            // DBマネージャーをキャッシュテーブルへ追加する
            DB_MANAGER_CACHES.put(
                    databaseName,
                    new WeakReference<FRDatabaseManager>(manager)
                    );


            // 生成したインスタンスを返却する
            return manager;

        }

    }


    /**
     * 指定されたDAOクラスインスタンスを取得する。
     *
     * @param <T>           DAOクラス
     * @param context       利用するコンテキスト情報
     * @param daoClass      生成したいDAOクラス
     * @return 指定されたDAOクラスインスタンス
     */
    public <T> T getDao(
            final Context                           context,
            final Class<? extends FRDatabaseDao>    daoClass
            ) {

        return ConvertUtils.<T>cast(getDao(context, daoClass, null));

    }


    /**
     * 指定されたDAOクラスインスタンスを取得する。
     *
     * @param <T>           DAOクラス
     * @param context       利用するコンテキスト情報
     * @param daoClass      生成したいDAOクラス
     * @param session       DAOクラスでDBアクセスするセッション情報
     * @return 指定されたDAOクラスインスタンス。作成に失敗した場合は null
     */
    public <T> T getDao(
            final Context                           context,
            final Class<? extends FRDatabaseDao>    daoClass,
            final FRDatabaseSession                 session
            ) {

        try {

            // 指定されたDAOを作成する
            final T retDao = ConvertUtils.<T>cast(ReflectUtils.newInstance(daoClass));

            // 利用するXMLリソースIDが定義されたアノテーションを取得する
            final FRDatabaseMappingXml     annotationXml =
                    ReflectUtils.getClassAnnotation(daoClass, FRDatabaseMappingXml.class);

            // アノテーションがない場合
            if (annotationXml == null) {

                // nullを返す
                return null;

            }


            // マニフェストからパッケージ名を取得する
            final String    packageName =
                    context.getPackageManager().getPackageInfo(
                            context.getPackageName(),
                            PackageManager.GET_META_DATA).packageName;

            // XMLリソースIDを取得する
            final Object    xmlResId = ReflectUtils.getStaticPublicFieldValue(
                    Class.forName(String.format(XML_RESOURCE_CLASS_NAME_FORMAT, packageName)),
                    annotationXml.value()
                    );

            // リソースIDが不正の場合は例外
            if (!(xmlResId instanceof Integer)) {

                throw new IllegalStateException();

            }

            // SQLマッパーを作成してDAOへ設定する
            ((FRDatabaseDao)retDao).setSqlMapper(
                    new FRDatabaseSqlMapper(
                            context,
                            this,
                            session,
                            (Integer)xmlResId
                            )
                    );

            // 作成したDAOを返す
            return retDao;

        } catch (final Throwable e) {

            e.printStackTrace();

            // DAO作成失敗
            return null;

        }

    }


    /**
     * データベースを最適化する。<br>
     * <br>
     * データベースの無駄な領域を最適化する。<br>
     * 本メソッドは時間が掛かる場合があるため<br>
     * UIスレッドとは別のスレッドで実行すること。<br>
     *
     * @param context 利用するコンテキスト情報
     * @return 最適化が成功した場合は true
     */
    public boolean optimize(
            final Context   context
            ) {

        FRDatabaseSession   session = null;     // セッション

        try {

            // セッションを開く
            session = openSession(context, false);

            // DB最適化コマンドを実行する
            session.execSQL(SQL_OPTIMIZE, false);

            // 最適化成功
            return true;

        } catch (final Throwable e) {

            e.printStackTrace();

            // 失敗
            return false;

        } finally {

            // セッションがある場合
            if (session != null) {

                // セッションを閉じる
                session.close();

            }

        }

    }


    /**
     * データベースの更新が必要かどうかを取得する。<br>
     * <br>
     * アプリが保持しているデータベースを<br>
     * アセッツ内にある同名のデータベースと比較し、<br>
     * アプリが保持しているデータベースより新しいバージョンであれば更新処理を行う。<br>
     * <br>
     * 更新元データベースファイル名は設定DBファイル名と同じものを利用する。<br>
     *
     * @param context   利用するコンテキスト情報
     * @return データベースの更新に成功した場合は true。DB更新が不要または失敗した場合は false
     */
    public boolean needsUpdate(
            final Context       context
            ) {

        return needsUpdate(context, dbName);

    }


    /**
     * データベースの更新が必要かどうかを取得する。<br>
     * <br>
     * アプリが保持しているデータベースを<br>
     * アセッツ内にある同名のデータベースと比較し、<br>
     * アプリが保持しているデータベースより新しいバージョンであれば更新処理を行う。<br>
     * <br>
     * 更新元データベースファイル名は指定ファイルを利用する。<br>
     *
     * @param context   利用するコンテキスト情報
     * @param fileName  更新に利用するDBファイル
     * @return データベースの更新に成功した場合は true。DB更新が不要または失敗した場合は false
     */
    public boolean needsUpdate(
            final Context       context,
            final String        fileName
            ) {

        // 引数が不正の場合は例外
        if (context == null) {

            throw new IllegalArgumentException();

        }


        // 作業用DBファイル名を作成する
        final String    tempDBName = System.currentTimeMillis() + fileName;

        // アセッツから新しいDBファイルをアプリ内へコピーする
        if (!copyDatabaseFromAssets(context, fileName, tempDBName, false)) {

            // 更新失敗
            return false;

        }


        // 新旧データベースファイルを作成する
        final File      oldDBFile = context.getDatabasePath(dbName);
        final File      newDBFile = context.getDatabasePath(tempDBName);

        // 更新が必要かどうかを取得する
        final boolean   retResult = FRDatabaseUtils.needsUpdate(
                oldDBFile.getAbsolutePath(),
                newDBFile.getAbsolutePath()
                );

        // コピーしていた新しいデータベースファイルを削除する
        if (!newDBFile.delete()) {

            // 失敗
            return false;

        }


        // 結果を返す
        return retResult;

    }


    /**
     * データベースの更新を行う。<br>
     * <br>
     * 設定されているデータベースを<br>
     * アセッツ内にある同名のデータベースと比較し、<br>
     * バージョンが異なっていれば更新処理を行う。<br>
     * <br>
     * 更新元データベースファイル名は設定DBファイル名と同じものを利用する。<br>
     *
     * @param context   利用するコンテキスト情報
     * @return データベースの更新に成功した場合は true
     */
    public boolean update(
            final Context       context
            ) {

        return update(context, dbName);

    }


    /**
     * データベースの更新を行う。<br>
     * <br>
     * 設定されているデータベースを<br>
     * アセッツ内にある同名のデータベースと比較し、<br>
     * バージョンが異なっていれば更新処理を行う。<br>
     * <br>
     * 更新元データベースファイル名は設定DBファイル名と同じものを利用する。<br>
     *
     * @param context   利用するコンテキスト情報
     * @param updatable 更新判定処理ハンドラ
     * @return データベースの更新に成功した場合は true
     */
    public boolean update(
            final Context               context,
            final FRDatabaseSavable   updatable
            ) {

        return update(context, dbName, updatable);

    }


    /**
     * データベースの更新を行う。<br>
     * <br>
     * 設定されているデータベースを<br>
     * アセッツ内にある同名のデータベースと比較し、<br>
     * バージョンが異なっていれば更新処理を行う。<br>
     * <br>
     * 更新元データベースファイル名は指定ファイルを利用する。<br>
     *
     * @param context   利用するコンテキスト情報
     * @param fileName  更新に利用するDBファイル
     * @return データベースの更新に成功した場合は true。DB更新が不要または失敗した場合は false
     */
    public boolean update(
            final Context       context,
            final String        fileName
            ) {

        return update(context, fileName, null);

    }


    /**
     * データベースの更新を行う。<br>
     * <br>
     * 設定されているデータベースを<br>
     * アセッツ内にある同名のデータベースと比較し、<br>
     * バージョンが異なっていれば更新処理を行う。<br>
     * <br>
     * 更新元データベースファイル名は指定ファイルを利用する。<br>
     *
     * @param context   利用するコンテキスト情報
     * @param fileName  更新に利用するDBファイル
     * @param savable   保存判定処理ハンドラ
     * @return データベースの更新に成功した場合は true。DB更新が不要または失敗した場合は false
     */
    public boolean update(
            final Context               context,
            final String                fileName,
            final FRDatabaseSavable     savable
            ) {

        // 引数が不正の場合は例外
        if (context == null) {

            throw new IllegalArgumentException();

        }


        // 作業用DBファイル名を作成する
        final String    tempDBName = System.currentTimeMillis() + fileName;

        // アセッツから新しいDBファイルをアプリ内へコピーする
        if (!copyDatabaseFromAssets(context, fileName, tempDBName, false)) {

            // 更新失敗
            return false;

        }


        // 新旧データベースファイルを作成する
        final File      oldDBFile = context.getDatabasePath(dbName);
        final File      newDBFile = context.getDatabasePath(tempDBName);

        // DB更新処理を行う
        final boolean   result = FRDatabaseUtils.updateDatabase(
                oldDBFile.getAbsolutePath(),
                newDBFile.getAbsolutePath(),
                savable
                );

        // 成功した場合
        if (result) {

            // 古いデータベースファイルを削除する
            if (!oldDBFile.delete()) {

                // 失敗
                return false;

            }

            // 新しいデータベースを既存名称に変更する
            if (!newDBFile.renameTo(oldDBFile)) {

                // 失敗
                return false;

            }

        } else {

            // コピーしていた新しいデータベースファイルを削除する
            if (!newDBFile.delete()) {

                // 失敗
                return false;

            }

        }


        // 結果を返す
        return result;

    }


    /**
     * DBへの接続を開始する。
     *
     * @param context       利用するコンテキスト情報
     * @param readOnly      読み込み専用で接続するかどうか
     * @return DBアクセスセッションのインスタンス
     * @throws IllegalArgumentException コンテキスト情報が null の場合
     */
    public FRDatabaseSession openSession(
            final Context       context,
            final boolean       readOnly
            ) {

        // 引数が不正の場合は例外
        if (context == null) {

            throw new IllegalArgumentException();

        }


        // 読み込み専用の場合
        if (readOnly) {

            // 読み込み専用のDBアクセスセッションを返す
            return new FRDatabaseSession(
                    SQLiteDatabase.openDatabase(
                            context.getDatabasePath(dbName).getAbsolutePath(),
                            null,
                            SQLiteDatabase.OPEN_READONLY
                            )
                    );

        } else {

            // 読み書き可能なDBアクセスセッションを返す
            return new FRDatabaseSession(
                    SQLiteDatabase.openDatabase(
                            context.getDatabasePath(dbName).getAbsolutePath(),
                            null,
                            SQLiteDatabase.OPEN_READWRITE
                            )
                    );

        }

    }


}
