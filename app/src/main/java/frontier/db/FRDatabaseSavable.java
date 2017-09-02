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

import java.util.HashSet;
import java.util.Set;




/**
 * データベース保存処理定義用抽象クラス。
 *
 * @author Kou
 *
 */
public abstract class FRDatabaseSavable {


    /**
     * 保存可能結果 : 保存しない
     */
    public static final int         RESULT_SKIP     = 0;

    /**
     * 保存可能結果 : 追加保存<br>
     * <br>
     * 同じROWIDのレコードが新規DBに存在する場合は、新規レコードとして追加する
     */
    public static final int         RESULT_INSERT   = RESULT_SKIP + 1;

    /**
     * 保存可能結果 : 更新保存<br>
     * <br>
     * 同じROWIDのレコードが新規DBに存在する場合は、レコード内容を上書きする。
     */
    public static final int         RESULT_UPDATE   = RESULT_INSERT + 1;




    /**
     * 保存非対称テーブル名セット
     */
    private final Set<String>                                           ignoreTables    =
        new HashSet<String>();

    /**
     * 保存非対称カラムマップ
     */
    private final TwoKeysConcurrentHashMap<String, String, Object>      ignoreColumns   =
        new TwoKeysConcurrentHashMap<String, String, Object>();



    /**
     * データベース保存処理定義クラスを初期化する。
     *
     * @param argIgnoreTables   保存非対象テーブル名一覧
     * @param argIgnoreParams   保存非対象カラムパラメータ。key がテーブル名で value がカラム名となる。
     */
    public FRDatabaseSavable(
            final String[]              argIgnoreTables,
            final FRDatabaseParam[]     argIgnoreParams
            ) {

        // 保存非対称テーブル名一覧が指定されている場合
        if (argIgnoreTables != null) {

            // テーブル名を追加する
            for (final String ignoreTable : argIgnoreTables) {

                // nullの場合
                if (ignoreTable == null) {

                    // 次のテーブル名へ
                    continue;

                }

                // テーブル名を追加する
                ignoreTables.add(ignoreTable);

            }

        }

        // 保存非対称カラムパラメータがある場合
        if (argIgnoreParams != null) {

            // パラメータ分ループする
            for (final FRDatabaseParam param : argIgnoreParams) {

                // パラメータが null の場合
                if (param == null) {

                    // 次のパラメータへ
                    continue;

                }

                // パラメータを追加する
                ignoreColumns.put(
                        param.getName(),
                        ConvertUtils.toString(param.getValue()),
                        true
                        );

            }

        }

    }


    /**
     * データベースのテーブル保存判定処理を行う。
     *
     * @param tableName     データベースのテーブル名
     * @param oldVersion    古いデータベースのバージョン
     * @param newVersion    新しいデータベースのバージョン
     * @return 保存可能かどうか
     */
    boolean canSaveTableInner(
            final String            tableName,
            final int               oldVersion,
            final int               newVersion
            ) {

        // テーブル名が保存非対称テーブルセットに存在する場合
        if (ignoreTables.contains(tableName)) {

            // テーブルスキップ
            return false;

        }

        // 保存可能かどうかを判断して返す
        return canSaveTable(tableName, oldVersion, newVersion);

    }


    /**
     * データベース保存判定処理を行う。
     *
     * @param tableName     データベースのテーブル名
     * @param columnName    データベースのカラム名
     * @param columnValue   データベースのカラム値
     * @param oldVersion    古いデータベースのバージョン
     * @param newVersion    新しいデータベースのバージョン
     * @return 保存可能かどうか
     */
    boolean canSaveColumnInner(
            final String            tableName,
            final String            columnName,
            final String            columnValue,
            final int               oldVersion,
            final int               newVersion
            ) {

        // 指定カラムが除外指定テーブル内に存在する場合
        if (ignoreColumns.containsKey(tableName, columnName)) {

            // カラムスキップ
            return false;

        }

        // 保存可能かどうかを判断して返す
        return canSaveColumn(tableName, columnName, columnValue, oldVersion, newVersion);

    }


    /**
     * 保存対象テーブルかどうかを判断して返す。<br>
     * <br>
     * オーバーライドして保存判定処理を定義する。<br>
     *
     * @param tableName     データベースのテーブル名
     * @param oldVersion    古いデータベースのバージョン
     * @param newVersion    新しいデータベースのバージョン
     * @return 保存可能かどうか
     */
    protected boolean canSaveTable(
            final String            tableName,
            final int               oldVersion,
            final int               newVersion
            ) {

        // 保存対象を返す
        return true;

    }


    /**
     * 保存対象レコードかどうかを判断して返す。<br>
     * <br>
     * オーバーライドして保存判定処理を定義する。<br>
     *
     * @param tableName     データベースのテーブル名
     * @param record        データベースのレコード値
     * @param oldVersion    古いデータベースのバージョン
     * @param newVersion    新しいデータベースのバージョン
     * @return 保存可能結果。デフォルトでは更新保存可能 ({@link #RESULT_UPDATE}) を返す
     */
    protected int canSaveRecord(
            final String            tableName,
            final FRDatabaseRecord  record,
            final int               oldVersion,
            final int               newVersion
            ) {

        // 更新保存可能を返す
        return RESULT_UPDATE;

    }


    /**
     * 保存対象カラムかどうかを判断して返す。<br>
     * <br>
     * オーバーライドして保存判定処理を定義する。<br>
     *
     * @param tableName     データベースのテーブル名
     * @param columnName    データベースのカラム名
     * @param columnValue   データベースのカラム値
     * @param oldVersion    古いデータベースのバージョン
     * @param newVersion    新しいデータベースのバージョン
     * @return 保存可能かどうか
     */
    protected boolean canSaveColumn(
            final String            tableName,
            final String            columnName,
            final String            columnValue,
            final int               oldVersion,
            final int               newVersion
            ) {

        // 保存対象を返す
        return true;

    }


}
