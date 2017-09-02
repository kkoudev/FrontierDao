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

import java.io.Closeable;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;


/**
 * データベース接続セッション管理クラス。
 *
 * @author Kou
 *
 */
public class FRDatabaseSession implements Closeable {


    /**
     * トランザクション開始モード : 遅延ロック (DEFERRED)<br>
     * <br>
     * 更新が行われるタイミングでロックを行う。<br>
     * トランザクション開始のタイミングではロックは行わない。<br>
     */
    public static final int         TRANSACTION_DEFERRED    = 0;

    /**
     * トランザクション開始モード : 共有ロック (IMMEDIATE)<br>
     * <br>
     * トランザクション開始のタイミングでロックを行う。<br>
     * 他プロセスから読み込みは可能だが、書き込みはロック待ちとなる。<br>
     */
    public static final int         TRANSACTION_IMMEDIATE   = TRANSACTION_DEFERRED + 1;

    /**
     * トランザクション開始モード : 排他ロック (EXCLUSIVE)<br>
     * <br>
     * トランザクション開始のタイミングでロックを行う。<br>
     * 他プロセスからの読み込みも書き込みもロック待ちとなる。<br>
     */
    public static final int         TRANSACTION_EXCLUSIVE   = TRANSACTION_IMMEDIATE + 1;


    /**
     * DEFERREDのSQL文
     */
    private static final String     SQL_TRANSACTION_DEFERRED    =
        "BEGIN DEFERRED";

    /**
     * IMMEDIATEのSQL文
     */
    private static final String     SQL_TRANSACTION_IMMEDIATE   =
        "BEGIN IMMEDIATE";

    /**
     * EXCLUSIVEのSQL文
     */
    private static final String     SQL_TRANSACTION_EXCLUSIVE   =
        "BEGIN EXCLUSIVE";

    /**
     * トランザクションコミットのSQL文
     */
    private static final String     SQL_COMMIT =
        "COMMIT";

    /**
     * トランザクションロールバックのSQL文
     */
    private static final String     SQL_ROLLBACK =
        "ROLLBACK";


    /**
     * エラー文言 : トランザクションが開始していない
     */
    private static final String     ERROR_TRANSACTION_BEGIN =
        "The transaction have not begin.";



    /**
     * アクセス先データベース
     */
    private final SQLiteDatabase            accessDatabase;

    /**
     * トランザクションカウント
     */
    private int                             transactionCount;

    /**
     * トランザクションが成功したかどうか
     */
    private boolean                         transactionSuccessful;

    /**
     * トランザクションを開始したスレッド
     */
    private Thread                          transactionThread;

    /**
     * トランザクション待ちオブジェクト
     */
    private final Object                    transactionLock         = new Object();

    /**
     * トランザクション待ち状態
     */
    private boolean                         transactionWait;




    /**
     * データベース接続セッションを作成する。
     *
     * @param database アクセスするデータベース
     */
    FRDatabaseSession(
            final SQLiteDatabase    database
            ) {

        accessDatabase = database;

    }


    /**
     * 指定された参照用SQLを実行する。
     *
     * @param sql 実行する参照用SQL
     * @return 実行結果へアクセスするためのカーソル
     */
    Cursor rawQuery(
            final String    sql
            ) {

        return accessDatabase.rawQuery(sql, null);

    }


    /**
     * 指定された書き込み用SQLを実行する。
     *
     * @param sql 実行する書き込み用SQL
     */
    void execSQL(
            final String    sql
            ) {

        execSQL(sql, true);

    }


    /**
     * 指定された書き込み用SQLを実行する。
     *
     * @param sql               実行する書き込み用SQL
     * @param autoTransaction   自動トランザクション処理を有効にするかどうか
     */
    void execSQL(
            final String    sql,
            final boolean   autoTransaction
            ) {

        // 自動トランザクション処理が有効の場合
        if (autoTransaction) {

            try {

                // トランザクションを開始する
                beginTransaction();

                // 書き込み用SQLを実行する
                accessDatabase.execSQL(sql);

                // トランザクション成功とする
                setTransactionSuccessful();

            } finally {

                // トランザクションを終了する
                endTransaction();

            }

        } else {

            // 書き込み用SQLを実行する
            accessDatabase.execSQL(sql);

        }

    }


    /**
     * セッションを閉じる。
     *
     */
    public void close() {

        // トランザクションをロックする
        synchronized (transactionLock) {

            // トランザクションが開始されている場合
            if (transactionCount > 0) {

                // トランザクションを終了する
                endTransaction();

            }

        }

        // セッションを閉じる
        accessDatabase.close();

    }


    /**
     * トランザクションを開始する。<br>
     * <br>
     * {@link #TRANSACTION_IMMEDIATE} モードでトランザクションを開始する。
     */
    public void beginTransaction() {

        beginTransaction(TRANSACTION_IMMEDIATE);

    }


    /**
     * 指定したトランザクション開始モードでトランザクションを開始する。
     *
     * @param mode トランザクション開始モード
     */
    public void beginTransaction(
            final int   mode
            ) {

        final String    sql;    // 実行するSQL


        // トランザクション開始モード別処理
        switch (mode) {

        // 遅延ロック
        case TRANSACTION_DEFERRED:

            // 遅延ロックSQLを設定する
            sql = SQL_TRANSACTION_DEFERRED;
            break;


        // 共有ロック
        case TRANSACTION_IMMEDIATE:

            // 共有ロックSQLを設定する
            sql = SQL_TRANSACTION_IMMEDIATE;
            break;


        // 排他ロック
        case TRANSACTION_EXCLUSIVE:

            // 排他ロックSQLを設定する
            sql = SQL_TRANSACTION_EXCLUSIVE;
            break;


        // その他 (エラー)
        default:

            throw new IllegalArgumentException("Illegal transaction mode. [mode = " + mode + "]");

        }


        // トランザクション処理メインループ
        while (true) {

            // トランザクションをロックする
            synchronized (transactionLock) {

                // トランザクションカウントが 0 の場合
                if (transactionCount == 0) {

                    // トランザクションカウントをインクリメントする
                    transactionCount++;

                    // トランザクション開始スレッドを設定する
                    transactionThread = Thread.currentThread();

                    // トランザクションを開始する
                    accessDatabase.execSQL(sql);

                    // 処理終了
                    break;

                } else {

                    // トランザクションカウントをインクリメントする
                    transactionCount++;

                    // カレントスレッドがトランザクション開始スレッドと等しい場合
                    if (Thread.currentThread() == transactionThread) {

                        // 入れ子トランザクションにはAndroidのSQLiteは非対応のため
                        //
                        break;

                    }


                    try {

                        // 待ち状態を有効にする
                        transactionWait = true;

                        // 待ち状態が解除されるまで繰り返し
                        while (!transactionWait) {

                            // 待ち状態へ
                            transactionLock.wait();

                        }

                    } catch (final InterruptedException e) {

                        e.printStackTrace();

                    }

                }

            }

        }

    }


    /**
     * トランザクション成功を設定する。
     *
     */
    public void setTransactionSuccessful() {

        // トランザクションをロックする
        synchronized (transactionLock) {

            // トランザクションが開始されていなければ例外
            if (transactionCount == 0) {

                throw new IllegalStateException(ERROR_TRANSACTION_BEGIN);

            }

            // トランザクションが最上位階層の場合
            if (transactionCount == 1) {

                // 成功を設定する
                transactionSuccessful = true;

            }

        }

    }


    /**
     * トランザクションを終了する。
     *
     */
    public void endTransaction() {

        // トランザクションをロックする
        synchronized (transactionLock) {

            // トランザクションが開始されていなければ例外
            if (transactionCount == 0) {

                throw new IllegalStateException(ERROR_TRANSACTION_BEGIN);

            }

            // トランザクションカウントをデクリメントする
            transactionCount--;

            // トランザクションカウントが 0 になる場合
            if (transactionCount == 0) {

                // トランザクションが成功している場合
                if (transactionSuccessful) {

                    // 変更内容をコミットする
                    accessDatabase.execSQL(SQL_COMMIT);

                } else {

                    // 変更内容をロールバックする
                    accessDatabase.execSQL(SQL_ROLLBACK);

                }

                // トランザクション開始スレッドと成功状態をクリアする
                transactionThread     = null;
                transactionSuccessful = false;

                // トランザクション待ち状態を無効にする
                transactionWait = false;

                // 待ち状態を解除する
                transactionLock.notifyAll();

            }

        }

    }


}
