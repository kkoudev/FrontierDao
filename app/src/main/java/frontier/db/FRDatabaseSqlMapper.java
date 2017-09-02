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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xmlpull.v1.XmlPullParser;

import android.content.Context;
import android.content.res.Resources.NotFoundException;
import android.content.res.XmlResourceParser;
import android.database.Cursor;
import android.util.Log;
import frontier.db.ConvertUtils.DataConvertType;


/**
 * データベースにおけるSQLマッピング操作クラス。
 *
 * @author Kou
 *
 */
public class FRDatabaseSqlMapper {


    /**
     * エラー文言 : カラムが 1 以外
     */
    private static final String         ERROR_COLUMN_EXCLUDING_ONE =
        "The result count should be one.";

    /**
     * エラー文言 : カラムが 0 または 1 以外
     */
    private static final String         ERROR_COLUMN_EXCLUDING_ZERO_OR_ONE =
        "The result count should be zero or one.";

    /**
     * エラー文言 : カラムは 1 より大きい
     */
    private static final String         ERROR_COLUMN_EXCLUDING_GREATER_ONE =
        "The result count should be higher than one.";

    /**
     * エラー文言 : 値プロパティがない
     */
    private static final String         ERROR_NO_VALUE_PROPERTY =
        "No value property. Please set value property.";



    /**
     * 利用するコンテキスト情報
     */
    private final Context               dbContext;

    /**
     * 利用するデータベースマネージャー
     */
    private final FRDatabaseManager     dbManager;

    /**
     * 利用するデータベースセッション
     */
    private final FRDatabaseSession     dbSession;

    /**
     * 利用するXMLのリソースID
     */
    private final int                   dbXmlId;




    /**
     * SQL操作マネージャーを作成する。
     *
     * @param context       利用するコンテキスト情報
     * @param manager       利用するデータベースマネージャ
     * @param session       利用するデータベースセッション
     * @param xmlId         クエリが定義されたXMLのリソースID
     */
    FRDatabaseSqlMapper(
            final Context               context,
            final FRDatabaseManager     manager,
            final FRDatabaseSession     session,
            final int                   xmlId
            ) {

        dbContext       = context;
        dbManager       = manager;
        dbSession       = session;
        dbXmlId         = xmlId;

    }


    /**
     * 改行を先頭に挿入する。
     *
     * @param strBuf 文字列バッファ
     */
    private static void insertNewLine(
            final StringBuilder     strBuf
            ) {

        // 文字列長が 0 の場合
        if (strBuf.length() == 0) {

            // 処理なし
            return;

        }

        // 改行を追加する
        strBuf.insert(0, '\n');

    }


    /**
     * 末尾文字が改行以外の場合に改行を追加する。
     *
     * @param strBuf 文字列バッファ
     */
    private static void addNewLine(
            final StringBuilder     strBuf
            ) {

        // 文字列長が 0 の場合
        if (strBuf.length() == 0) {

            // 処理なし
            return;

        }

        // 末尾文字を取得する
        final char  lastChar = strBuf.charAt(strBuf.length() - 1);

        // 末尾文字が改行文字の場合
        if ((lastChar == '\n') || (lastChar == '\r')) {

            // 処理なし
            return;

        }

        // 改行を追加する
        strBuf.append('\n');

    }


    /**
     * 指定した種別のSQLクエリデータを作成する。
     *
     * @param parser        読み込み元XMLパーサー
     * @param queryType     SQLクエリ種別
     * @return 指定した種別のSQLクエリデータ
     */
    private static SQLQuery createQuery(
            final XmlResourceParser parser,
            final SQLQueryType      queryType
            ) {

        // クエリデータを作成する
        final SQLQuery  retQuery = new SQLQuery();

        // クエリ種別を設定する
        retQuery.setType(queryType);

        // 属性分処理をする
        for (int i = 0; i < parser.getAttributeCount(); i++) {

            // 属性値を作成する
            retQuery.setAttributeValue(
                    parser.getAttributeName(i),
                    parser.getAttributeValue(i)
                    );

        }

        // 作成したクエリデータを返却する
        return retQuery;

    }


    /**
     * 指定したクエリデータへ prepend 属性の値を付加する。
     *
     * @param strBuf            prepend属性の追加先文字列バッファ
     * @param query             追加したいprepend属性値を所持しているクエリ
     * @param ignoreSqlKeyword  直前の追加値がSQL予約語の場合に何も処理をしない場合は true
     */
    private static void processPrepend(
            final StringBuilder strBuf,
            final SQLQuery      query,
            final boolean       ignoreSqlKeyword
            ) {

        final String        prepend = query.getAttributeValue(SQLQuery.ATTR_PREPEND);

        // 接頭語がない場合
        if (prepend == null) {

            // 何もしない
            return;

        }

        // SQL予約語の場合に処理をしない場合
        if (ignoreSqlKeyword) {

            // 何もしない
            return;

        }

        // 接頭語を文字列バッファへ追加する
        strBuf.append(prepend);

        // 改行を追加する
        addNewLine(strBuf);

    }


    /**
     * 指定された文字列内にある変数名を実際の値に置換する。
     *
     * @param str           置換元文字列
     * @param parametersMap 検索に利用するパラメータマップ
     * @return 置換した文字列
     */
    private static String replaceVariableName(
            final String                str,
            final Map<String, Object>   parametersMap
            ) {

        String  workString = str;   // 作業用文字列


        // 全変数名分だけ検索する
        for (final Map.Entry<String, Object> entry : parametersMap.entrySet()) {

            final String    value;  // 設定する値


            // 値が文字列の場合
            if (entry.getValue() instanceof String) {

                // SQL文字列値へ変換する
                value = String.format(
                        SQLQuery.FORMAT_VALUE_STRING,
                        String.valueOf(entry.getValue())
                        );

            } else {

                // 通常の文字列へ変換する
                value = String.valueOf(entry.getValue());

            }

            // 文字列内の指定変数名箇所を置換する
            workString = workString.replaceAll(
                    String.format(SQLQuery.FORMAT_VARIABLE_NAME, entry.getKey()),
                    value
                    );

        }

        // 結果を返す
        return workString;

    }


    /**
     * 指定された文字列バッファへ条件テキストを追加する。
     *
     * @param strBuf        文字列バッファ
     * @param parentQuery   親クエリ
     * @param procQuery     処理クエリ
     * @param text          追加する条件テキスト
     */
    private static void addJudgement(
            final StringBuilder strBuf,
            final SQLQuery      parentQuery,
            final SQLQuery      procQuery,
            final String        text
            ) {

        // 親クエリへ改行を追加する
        addNewLine(parentQuery.getSql());

        // 接頭語を処理する
        processPrepend(strBuf, procQuery, parentQuery.isLastSqlKeywordEnabled());

        // テキストを追加する
        strBuf.append(text);

        // 親クエリの直前にSQLキーワードが追加されていないことにする
        parentQuery.setLastSqlKeywordEnabled(false);

    }


    /**
     * 階層のあるクエリデータ解析処理を行う。
     *
     * @param parser        読み込み元XMLパーサー
     * @param rootQuery     ルートクエリデータ
     * @param parentQuery   親クエリデータ
     * @param queryId       検索クエリID
     * @param parametersMap 検索に利用するパラメータマップ
     * @return 取得したクエリデータ
     */
    private static SQLQuery processQuery(
            final XmlResourceParser     parser,
            final SQLQuery              rootQuery,
            final SQLQuery              parentQuery,
            final String                queryId,
            final Map<String, Object>   parametersMap
            ) {

        // 返却するクエリデータ
        SQLQuery    retQuery = null;

        try {

            boolean             isEnded = false;    // 読み込み終了したかどうか
            int                 eventType;          // イベント種別
            SQLQuery            procQuery = null;   // 処理中クエリデータ


            // XML解析が終了するまでループする
            for (eventType = parser.getEventType();
                 !isEnded;
                 eventType = parser.next()
                 ) {

                // イベント種類別処理
                switch (eventType) {

                // タグ開始
                case XmlPullParser.START_TAG:

                    // 親クエリがない場合
                    if (parentQuery == null) {

                        // 処理中クエリデータがある場合
                        if (procQuery != null) {

                            // 多階層クエリ処理を行う
                            processQuery(parser, procQuery, procQuery, queryId, parametersMap);

                            // 次のデータへ
                            continue;

                        }

                        // クエリ種別を取得する
                        final SQLQueryType  queryType = SQLQueryType.toQuerySqlType(parser.getName());

                        // クエリ種別の取得ができなかった場合
                        if (queryType == null) {

                            // 次のデータへ
                            continue;

                        }

                        // 指定クエリIDと現在のクエリIDが一致する場合
                        if (queryId.equals(
                                parser.getAttributeValue(null, SQLQuery.ATTR_ID))
                                ) {

                            // クエリデータを作成する
                            procQuery = createQuery(parser, queryType);

                            // 返却クエリデータとして設定する
                            retQuery  = procQuery;

                        } else {

                            // 次のデータへ
                            continue;

                        }

                    } else {

                        // 処理中クエリデータがある場合
                        if (procQuery != null) {

                            // 多階層クエリ処理を行う
                            processQuery(parser, rootQuery, procQuery, queryId, parametersMap);

                            // 次のデータへ
                            continue;

                        }


                        // 条件クエリ種別を取得する
                        final SQLQueryType  queryType = SQLQueryType.toQueryInnerType(parser.getName());

                        // クエリ種別の取得ができなかった場合
                        if (queryType == null) {

                            // 次のデータへ
                            continue;

                        }

                        // クエリデータを作成する
                        procQuery = createQuery(parser, queryType);

                    }


                    // クエリが条件種別の場合
                    if (SQLQueryType.isConditionQuery(procQuery.getType())) {

                        final String    prepend;    // 接頭語


                        // 条件種類別処理
                        switch (procQuery.getType()) {

                        // 動的宣言の場合
                        case DYNAMIC:

                            // 接頭語を取得する
                            prepend = procQuery.getAttributeValue(SQLQuery.ATTR_PREPEND);

                            // 接頭語が SQLキーワード の場合、
                            // 直前にSQLキーワードが追加されていることにする
                            procQuery.setLastSqlKeywordEnabled(
                                    SQLQuery.isSqlKeyword(prepend)
                                    );
                            break;


                        // その他
                        default:

                            // 処理なし
                            break;

                        }

                    // selectKeyの場合
                    } else if (SQLQueryType.SELECT_KEY.equals(procQuery.getType())) {

                        // サブクエリとして親クエリへ登録する
                        parentQuery.getSubQueries().add(procQuery);

                    } else {

                        // 処理なし

                    }
                    break;


                // テキストの場合
                case XmlPullParser.TEXT:

                    // 設定先クエリがない場合
                    if (procQuery == null) {

                        // 次のデータへ
                        continue;

                    }

                    // クエリが条件種別の場合
                    if (SQLQueryType.isConditionQuery(procQuery.getType())) {

                        // 親クエリがない場合は例外
                        if (parentQuery == null) {

                            throw new IllegalStateException("illegal condition query type.");

                        }

                        // 共通パラメータを取得する
                        final StringBuilder judgeStatement  = new StringBuilder();
                        final String        property        = procQuery.getAttributeValue(SQLQuery.ATTR_PROPERTY);
                        final String        compareValue    = procQuery.getAttributeValue(SQLQuery.ATTR_COMPARE_VALUE);
                        final Object        value           = property == null ? null : parametersMap.get(property);


                        // 条件種類別処理
                        switch (procQuery.getType()) {

                        // nullの場合
                        case IS_NULL:

                            // 値が null の場合
                            if (value == null) {

                                // 条件テキストを追加する
                                addJudgement(
                                        judgeStatement,
                                        parentQuery,
                                        procQuery,
                                        parser.getText()
                                        );

                            }
                            break;


                        // null以外の場合
                        case IS_NOT_NULL:

                            // 値が null 以外の場合
                            if (value != null) {

                                // 条件テキストを追加する
                                addJudgement(
                                        judgeStatement,
                                        parentQuery,
                                        procQuery,
                                        parser.getText()
                                        );

                            }
                            break;


                        // 等しい場合
                        case IS_EQUAL:

                            // 値が等しい場合
                            if ((compareValue != null)
                                && compareValue.equals(String.valueOf(value))
                                ) {

                                // 条件テキストを追加する
                                addJudgement(
                                        judgeStatement,
                                        parentQuery,
                                        procQuery,
                                        parser.getText()
                                        );

                            }
                            break;


                        // 等しくない場合
                        case IS_NOT_EQUAL:

                            // 値が等しくない場合
                            if ((compareValue != null)
                                && !compareValue.equals(String.valueOf(value))
                                ) {

                                // 条件テキストを追加する
                                addJudgement(
                                        judgeStatement,
                                        parentQuery,
                                        procQuery,
                                        parser.getText()
                                        );

                            }
                            break;


                        // その他
                        default:

                            // 処理中クエリへテキストデータを設定する
                            procQuery.getSql().append(parser.getText());

                            // 次のデータへ
                            continue;

                        }

                        // 親クエリへ作成した条件式を追加する
                        parentQuery.getSql().append(
                                judgeStatement.toString()
                                );

                    // selectKeyの場合
                    } else if (SQLQueryType.SELECT_KEY.equals(procQuery.getType())) {

                        // 親クエリがない場合は例外
                        if (parentQuery == null) {

                            throw new IllegalStateException("illegal condition query type.");

                        }

                        // 条件式内の変数名を全て値に置換し、
                        // 処理中クエリへテキストデータを設定する
                        procQuery.getSql().append(
                                replaceVariableName(parser.getText(), parametersMap)
                                );

                    // クエリが条件種別以外の場合
                    } else {

                        // テキストデータを設定する
                        procQuery.getSql().append(parser.getText());

                    }
                    break;


                // 終了タグの場合
                case XmlPullParser.END_TAG:

                    // 返却クエリがない場合
                    if (procQuery == null) {

                        // 次のデータへ
                        continue;

                    }


                    // 親クエリが存在する場合
                    // かつ処理中クエリにSQLテキストがある場合
                    if ((parentQuery != null) && (procQuery.getSql().length() > 0)) {

                        final String    prepend;    // 接頭語

                        // クエリ種類別処理
                        switch (procQuery.getType()) {

                        // 動的宣言
                        case DYNAMIC:

                            // 接頭語を取得する
                            prepend = procQuery.getAttributeValue(SQLQuery.ATTR_PREPEND);

                            // 接頭語がある場合
                            if (prepend != null) {

                                // 改行を先頭に挿入する
                                insertNewLine(procQuery.getSql());

                                // 接頭語を先頭に挿入する
                                procQuery.getSql().insert(0, prepend);

                            }
                            break;


                        // その他
                        default:

                            // 処理なし
                            break;

                        }

                        // 親クエリに改行を付加する
                        addNewLine(parentQuery.getSql());

                        // 親クエリに処理中クエリのSQLテキストを付加する
                        parentQuery.getSql().append(procQuery.getSql());

                    }

                    // クエリを返却する
                    return procQuery;


                // ドキュメント終端
                case XmlPullParser.END_DOCUMENT:

                    // 読み込み完了とする
                    isEnded = true;
                    break;


                // その他
                default:

                    // 処理なし
                    break;

                }

            }

        } catch (final Throwable e) {

            throw new IllegalStateException(e);

        }


        // クエリデータを返却する
        return retQuery;

    }


    /**
     * 指定XMLの指定されたクエリデータを取得する。
     *
     * @param xmlResId      クエリが定義されたXMLのリソースID
     * @param queryId       取得したいクエリのID
     * @param parametersMap 検索に利用するパラメータ
     * @return 指定XMLの指定されたクエリデータ
     * @throws NotFoundException        XML取得に失敗した場合
     * @throws IllegalStateException    クエリ取得に失敗した場合
     */
    private SQLQuery getQuery(
            final int                   xmlResId,
            final String                queryId,
            final Map<String, Object>   parametersMap
            ) {

        SQLQuery            retQuery = null;    // 返却クエリ
        XmlResourceParser   parser   = null;    // クエリ定義元SQLパーサー


        try {

            // クエリ定義元SQLパーサーを取得する
            parser = dbContext.getResources().getXml(xmlResId);

            // クエリデータ取得処理を行う
            retQuery = processQuery(parser, null, null, queryId, parametersMap);

        } catch (final NotFoundException e) {

            // XML取得失敗した場合
            throw e;

        } catch (final Throwable e) {

            // その他エラー時
            throw new IllegalStateException(e);

        } finally {

            // パーサーが作成されている場合
            if (parser != null) {

                // パーサーを閉じる
                parser.close();

            }

        }

        // クエリの取得に失敗した場合
        if (retQuery == null) {

            // 例外を返す
            throw new IllegalStateException("No sql data or illegal sql data.");

        } else {

            // 変数を全て置換する
            retQuery.replaceVariables(parametersMap);

        }

        // 取得したクエリを返却する
        return retQuery;

    }


    /**
     * DBアクセスセッションを取得する。<br>
     * <br>
     * SQLマッパーにセッションが設定されていない場合は、<br>
     * 指定された設定でセッションを新規オープンする。<br>
     * SQLマッパーにセッションが設定されている場合は<br>
     * 設定されているセッションをそのまま返す。<br>
     *
     * @param readOnly 読み込み専用でオープンする場合は true
     * @return DBアクセスセッション
     */
    private FRDatabaseSession openSession(
            final boolean   readOnly
            ) {

        return dbSession == null ? dbManager.openSession(dbContext, readOnly) : dbSession;

    }


    /**
     * 指定されたDBアクセスセッションを閉じる。<br>
     * <br>
     * 指定されたセッションがSQLマッパーに設定されているセッションと等しい場合は何もしない。<br>
     *
     * @param session 閉じるDBアクセスセッション
     */
    private void closeSession(
            final FRDatabaseSession session
            ) {

        // セッションが null の場合
        if (session == null) {

            // 何もしない
            return;

        }

        // 設定されているセッションと等しい場合
        if ((dbSession != null) && dbSession.equals(session)) {

            // 何もしない
            return;

        }

        // セッションを閉じる
        session.close();

    }


    /**
     * 指定した参照SQLクエリを実行する。
     *
     * @param <T>           エンティティクラス
     * @param queryId       実行するクエリのID
     * @param resultClass   結果を格納するエンティティクラス
     * @param entity        検索に利用するエンティティ
     * @return 結果データ
     * @throws NotFoundException        XML取得に失敗した場合
     * @throws IllegalStateException    クエリ取得に失敗した場合
     */
    public <T> T selectForObject(
            final String                queryId,
            final Class<T>              resultClass,
            final Object                entity
            ) {

        return selectForObject(dbXmlId, queryId, resultClass, entity);

    }


    /**
     * 指定した参照SQLクエリを実行する。
     *
     * @param <T>           エンティティクラス
     * @param xmlResId      クエリが定義されたXMLのリソースID
     * @param queryId       実行するクエリのID
     * @param resultClass   結果を格納するエンティティクラス
     * @param entity        検索に利用するエンティティ
     * @return 結果データ
     * @throws NotFoundException        XML取得に失敗した場合
     * @throws IllegalStateException    クエリ取得に失敗した場合
     * @throws IllegalArgumentException クエリIDが null または長さが 0 の場合
     * @throws IllegalArgumentException 結果を格納するエンティティクラスが null の場合
     */
    public <T> T selectForObject(
            final int                   xmlResId,
            final String                queryId,
            final Class<T>              resultClass,
            final Object                entity
            ) {

        return selectForObject(
                xmlResId,
                queryId,
                resultClass,
                FRDatabaseUtils.createNameValuePairs(entity)
                );

    }


    /**
     * 指定した参照SQLクエリを実行する。
     *
     * @param <T>           エンティティクラス
     * @param queryId       実行するクエリのID
     * @param resultClass   結果を格納するエンティティクラス
     * @param parameters    検索に利用するパラメータ
     * @return 結果データ
     * @throws NotFoundException        XML取得に失敗した場合
     * @throws IllegalStateException    クエリ取得に失敗した場合
     * @throws IllegalArgumentException クエリIDが null または長さが 0 の場合
     * @throws IllegalArgumentException 結果を格納するエンティティクラスが null の場合
     */
    public <T> T selectForObject(
            final String                queryId,
            final Class<T>              resultClass,
            final FRDatabaseParam...    parameters
            ) {

        return selectForObject(dbXmlId, queryId, resultClass, parameters);

    }


    /**
     * 指定した参照SQLクエリを実行する。
     *
     * @param <T>           エンティティクラス
     * @param xmlResId      クエリが定義されたXMLのリソースID
     * @param queryId       実行するクエリのID
     * @param resultClass   結果を格納するエンティティクラス
     * @param parameters    検索に利用するパラメータ
     * @return 結果データ
     * @throws NotFoundException        XML取得に失敗した場合
     * @throws IllegalStateException    クエリ取得に失敗した場合
     * @throws IllegalArgumentException クエリIDが null または長さが 0 の場合
     * @throws IllegalArgumentException 結果を格納するエンティティクラスが null の場合
     */
    public <T> T selectForObject(
            final int                   xmlResId,
            final String                queryId,
            final Class<T>              resultClass,
            final FRDatabaseParam...    parameters
            ) {

        // 結果一覧を取得する
        final List<T>   results = selectForList(xmlResId, queryId, resultClass, parameters);

        // 結果が 1 より大きい場合
        if (results.size() > 1) {

            throw new IllegalStateException(ERROR_COLUMN_EXCLUDING_ZERO_OR_ONE);

        }

        // 結果がない場合
        if (results.size() == 0) {

            // nullを返す
            return null;

        }


        // 結果を返す
        return results.get(0);

    }


    /**
     * 指定した参照SQLクエリを実行する。
     *
     * @param <T>           エンティティクラス
     * @param queryId       実行するクエリのID
     * @param resultClass   結果を格納するエンティティクラス
     * @param entity        検索に利用するエンティティ
     * @return 結果データ一覧
     * @throws NotFoundException        XML取得に失敗した場合
     * @throws IllegalStateException    クエリ取得に失敗した場合
     */
    public <T> List<T> selectForList(
            final String                queryId,
            final Class<T>              resultClass,
            final Object                entity
            ) {

        return selectForList(dbXmlId, queryId, resultClass, entity);

    }


    /**
     * 指定した参照SQLクエリを実行する。
     *
     * @param <T>           エンティティクラス
     * @param xmlResId      クエリが定義されたXMLのリソースID
     * @param queryId       実行するクエリのID
     * @param resultClass   結果を格納するエンティティクラス
     * @param entity        検索に利用するエンティティ
     * @return 結果データ一覧
     * @throws NotFoundException        XML取得に失敗した場合
     * @throws IllegalStateException    クエリ取得に失敗した場合
     * @throws IllegalArgumentException クエリIDが null または長さが 0 の場合
     * @throws IllegalArgumentException 結果を格納するエンティティクラスが null の場合
     */
    public <T> List<T> selectForList(
            final int                   xmlResId,
            final String                queryId,
            final Class<T>              resultClass,
            final Object                entity
            ) {

        return selectForList(
                xmlResId,
                queryId,
                resultClass,
                FRDatabaseUtils.createNameValuePairs(entity)
                );

    }


    /**
     * 指定した参照SQLクエリを実行する。
     *
     * @param <T>           エンティティクラス
     * @param queryId       実行するクエリのID
     * @param resultClass   結果を格納するエンティティクラス
     * @param parameters    検索に利用するパラメータ
     * @return 結果データ一覧
     * @throws NotFoundException        XML取得に失敗した場合
     * @throws IllegalStateException    クエリ取得に失敗した場合
     */
    public <T> List<T> selectForList(
            final String                queryId,
            final Class<T>              resultClass,
            final FRDatabaseParam...    parameters
            ) {

        return selectForList(dbXmlId, queryId, resultClass, parameters);

    }


    /**
     * 指定した参照SQLクエリを実行する。
     *
     * @param <T>           エンティティクラス
     * @param xmlResId      クエリが定義されたXMLのリソースID
     * @param queryId       実行するクエリのID
     * @param resultClass   結果を格納するエンティティクラス
     * @param parameters    検索に利用するパラメータ
     * @return 結果データ一覧
     * @throws NotFoundException        XML取得に失敗した場合
     * @throws IllegalStateException    クエリ取得に失敗した場合
     * @throws IllegalArgumentException クエリIDが null または長さが 0 の場合
     * @throws IllegalArgumentException 結果を格納するエンティティクラスが null の場合
     */
    public <T> List<T> selectForList(
            final int                   xmlResId,
            final String                queryId,
            final Class<T>              resultClass,
            final FRDatabaseParam...    parameters
            ) {

        // 引数が不正の場合は例外
        if ((queryId == null) || (queryId.length() == 0) || (resultClass == null)) {

            throw new IllegalArgumentException();

        }

        // クエリデータを取得する
        final SQLQuery  query = getQuery(
                xmlResId,
                queryId,
                FRDatabaseUtils.createSearchMap(parameters)
                );

        // SQL文を取得する
        final String    sql = query.getSql().toString();


        // SQLをログへ出力
        Log.d(getClass().getName(), sql);

        // 取得したSQLが正しいかどうかをチェックする
        checkCorrectSql(sql, SQLQueryType.SELECT);


        FRDatabaseSession   session = null;     // DBアクセスセッション
        Cursor              cursor  = null;     // カーソル

        try {

            // セッションを開く
            session = openSession(true);

            // SQLを実行する
            cursor = session.rawQuery(
                    query.getSql().toString()
                    );

            // カーソルを先頭に移動する
            if (!cursor.moveToFirst()) {

                // 読み込み失敗
                return new ArrayList<T>();

            }


            // 返却データ一覧
            final List<T>   retEntities = new ArrayList<T>();

            // 変換可能型の場合
            if (ConvertUtils.canConvertType(resultClass)) {

                // カラムが 1 以外の場合
                if (cursor.getColumnCount() != 1) {

                    throw new IllegalStateException(ERROR_COLUMN_EXCLUDING_ONE);

                }

                // 結果を読み込む
                do {

                    // 一覧へ追加する
                    retEntities.add(
                            ConvertUtils.toType(
                                    DataConvertType.DATABASE,
                                    resultClass,
                                    cursor.getString(0)
                                    )
                            );

                // 次の行へ移動する
                } while (cursor.moveToNext());

            } else {

                final String[]  columnNames = cursor.getColumnNames();      // カラム名一覧を取得する

                // 結果を読み込む
                do {

                    // エンティティのインスタンスを作成する
                    final T   entity = ReflectUtils.newInstance(resultClass);

                    // SQL定義されたカラム文だけ処理をする
                    for (final String columnName : columnNames) {

                        // エンティティの指定フィールドに値を設定する
                        ReflectUtils.setBeanValue(
                                DataConvertType.DATABASE,
                                entity,
                                columnName,
                                cursor.getString(cursor.getColumnIndex(columnName))
                                );

                    }

                    // エンティティを返却一覧へ追加する
                    retEntities.add(entity);

                // 次の行へ移動する
                } while (cursor.moveToNext());

            }

            // エンティティ一覧を返す
            return retEntities;

        } catch (final ReflectException e) {

            e.printStackTrace();

        } finally {

            // カーソルがある場合
            if (cursor != null) {

                // カーソルを閉じる
                cursor.close();

            }

            // セッションを閉じる
            closeSession(session);

        }

        // エンティティ取得失敗
        return null;

    }


    /**
     * 指定した参照SQLクエリを実行する。
     *
     * @param <K>           キークラス
     * @param <V>           値クラス
     * @param queryId       実行するクエリのID
     * @param keyClass      キークラス
     * @param valueClass    値クラス
     * @param keyProperty   キー名称
     * @param parameters    検索に利用するパラメータ
     * @return 結果データのマッピングデータ
     * @throws NotFoundException        XML取得に失敗した場合
     * @throws IllegalStateException    クエリ取得に失敗した場合
     * @throws IllegalArgumentException クエリIDが null または長さが 0 の場合
     * @throws IllegalArgumentException 結果を格納するキークラスまたは値クラスが null の場合
     * @throws IllegalArgumentException キー名称が null または長さが 0 の場合
     */
    public <K, V> Map<K, V> selectForMap(
            final String                queryId,
            final Class<K>              keyClass,
            final Class<V>              valueClass,
            final String                keyProperty,
            final FRDatabaseParam...    parameters
            ) {

        return selectForMap(
                dbXmlId,
                queryId,
                keyClass,
                valueClass,
                keyProperty,
                null,
                parameters
                );

    }


    /**
     * 指定した参照SQLクエリを実行する。
     *
     * @param <K>           キークラス
     * @param <V>           値クラス
     * @param queryId       実行するクエリのID
     * @param keyClass      キークラス
     * @param valueClass    値クラス
     * @param keyProperty   キー名称
     * @param valueProperty 値名称
     * @param parameters    検索に利用するパラメータ
     * @return 結果データのマッピングデータ
     * @throws NotFoundException        XML取得に失敗した場合
     * @throws IllegalStateException    クエリ取得に失敗した場合
     * @throws IllegalArgumentException クエリIDが null または長さが 0 の場合
     * @throws IllegalArgumentException 結果を格納するキークラスまたは値クラスが null の場合
     * @throws IllegalArgumentException キー名称が null または長さが 0 の場合
     */
    public <K, V> Map<K, V> selectForMap(
            final String                queryId,
            final Class<K>              keyClass,
            final Class<V>              valueClass,
            final String                keyProperty,
            final String                valueProperty,
            final FRDatabaseParam...    parameters
            ) {

        return selectForMap(
                dbXmlId,
                queryId,
                keyClass,
                valueClass,
                keyProperty,
                valueProperty,
                parameters
                );

    }


    /**
     * 指定した参照SQLクエリを実行する。
     *
     * @param <K>           キークラス
     * @param <V>           値クラス
     * @param xmlResId      クエリが定義されたXMLのリソースID
     * @param queryId       実行するクエリのID
     * @param keyClass      キークラス
     * @param valueClass    値クラス
     * @param keyProperty   キー名称
     * @param parameters    検索に利用するパラメータ
     * @return 結果データのマッピングデータ
     * @throws NotFoundException        XML取得に失敗した場合
     * @throws IllegalStateException    クエリ取得に失敗した場合
     * @throws IllegalArgumentException クエリIDが null または長さが 0 の場合
     * @throws IllegalArgumentException 結果を格納するキークラスまたは値クラスが null の場合
     * @throws IllegalArgumentException キー名称が null または長さが 0 の場合
     */
    public <K, V> Map<K, V> selectForMap(
            final int                   xmlResId,
            final String                queryId,
            final Class<K>              keyClass,
            final Class<V>              valueClass,
            final String                keyProperty,
            final FRDatabaseParam...    parameters
            ) {

        return selectForMap(
                xmlResId,
                queryId,
                keyClass,
                valueClass,
                keyProperty,
                null,
                parameters
                );

    }


    /**
     * 指定した参照SQLクエリを実行する。
     *
     * @param <K>           キークラス
     * @param <V>           値クラス
     * @param xmlResId      クエリが定義されたXMLのリソースID
     * @param queryId       実行するクエリのID
     * @param keyClass      キークラス
     * @param valueClass    値クラス
     * @param keyProperty   キー名称
     * @param valueProperty 値名称
     * @param parameters    検索に利用するパラメータ
     * @return 結果データのマッピングデータ
     * @throws NotFoundException        XML取得に失敗した場合
     * @throws IllegalStateException    クエリ取得に失敗した場合
     * @throws IllegalArgumentException クエリIDが null または長さが 0 の場合
     * @throws IllegalArgumentException 結果を格納するキークラスまたは値クラスが null の場合
     * @throws IllegalArgumentException キー名称が null または長さが 0 の場合
     */
    public <K, V> Map<K, V> selectForMap(
            final int                   xmlResId,
            final String                queryId,
            final Class<K>              keyClass,
            final Class<V>              valueClass,
            final String                keyProperty,
            final String                valueProperty,
            final FRDatabaseParam...    parameters
            ) {

        // 引数が不正の場合は例外
        if ((queryId == null)
            || (queryId.length() == 0)
            || (keyClass == null)
            || (valueClass == null)
            || (keyProperty == null)
            || (keyProperty.length() == 0)
            ) {

            throw new IllegalArgumentException();

        }


        // クエリデータを取得する
        final SQLQuery  query = getQuery(
                xmlResId,
                queryId,
                FRDatabaseUtils.createSearchMap(parameters)
                );

        // SQL文を取得する
        final String    sql = query.getSql().toString();


        // SQLをログへ出力
        Log.d(getClass().getName(), sql);

        // 取得したSQLが正しいかどうかをチェックする
        checkCorrectSql(sql, SQLQueryType.SELECT);


        FRDatabaseSession   session = null;     // DBアクセスセッション
        Cursor              cursor  = null;     // カーソル

        try {

            // セッションを開く
            session = openSession(true);

            // SQLを実行する
            cursor = session.rawQuery(
                    query.getSql().toString()
                    );

            // カラムが 1 以下の場合
            if (cursor.getColumnCount() <= 1) {

                throw new IllegalStateException(ERROR_COLUMN_EXCLUDING_GREATER_ONE);

            }

            // カーソルを先頭に移動する
            if (!cursor.moveToFirst()) {

                // 読み込み失敗
                return new HashMap<K, V>();

            }


            final Map<K, V>     retEntities = new HashMap<K, V>();      // 返却データテーブル


            // 値が変換可能型の場合
            if (ConvertUtils.canConvertType(valueClass)) {

                // 値プロパティがない場合
                if (valueProperty == null) {

                    throw new IllegalStateException(ERROR_NO_VALUE_PROPERTY);

                }

                // 結果を読み込む
                do {

                    // 一覧へ追加する
                    retEntities.put(
                            ConvertUtils.toType(
                                    DataConvertType.DATABASE,
                                    keyClass,
                                    cursor.getString(cursor.getColumnIndex(keyProperty))
                                    ),
                            ConvertUtils.toType(
                                    DataConvertType.DATABASE,
                                    valueClass,
                                    cursor.getString(cursor.getColumnIndex(valueProperty))
                                    )
                            );

                // 次の行へ移動する
                } while (cursor.moveToNext());

            } else {

                final String[]  columnNames = cursor.getColumnNames();      // カラム名一覧を取得する

                // 結果を読み込む
                do {

                    // エンティティのインスタンスを作成する
                    final V   entity = ReflectUtils.newInstance(valueClass);

                    // SQL定義されたカラム文だけ処理をする
                    for (final String columnName : columnNames) {

                        // エンティティの指定フィールドに値を設定する
                        ReflectUtils.setBeanValue(
                                DataConvertType.DATABASE,
                                entity,
                                columnName,
                                cursor.getString(cursor.getColumnIndex(columnName))
                                );

                    }

                    // エンティティを返却一覧へ追加する
                    retEntities.put(
                            ConvertUtils.toType(
                                    DataConvertType.DATABASE,
                                    keyClass,
                                    cursor.getString(cursor.getColumnIndex(keyProperty))
                                    ),
                            entity
                            );

                // 次の行へ移動する
                } while (cursor.moveToNext());

            }

            // エンティティ一覧を返す
            return retEntities;

        } catch (final ReflectException e) {

            e.printStackTrace();

        } finally {

            // カーソルがある場合
            if (cursor != null) {

                // カーソルを閉じる
                cursor.close();

            }

            // セッションを閉じる
            closeSession(session);

        }

        // エンティティ取得失敗
        return null;

    }


    /**
     * INSERT文の selectKey クエリ実行処理を処理する。
     *
     * @param session       DBセッション
     * @param queryInsert   INSERT文クエリ
     * @return keyPropertyの名前を使った名称値の実行結果
     */
    private static FRDatabaseParam processSelectKey(
            final FRDatabaseSession session,
            final SQLQuery          queryInsert
            ) {

        // サブクエリを取得する
        final List<SQLQuery>    subQueries = queryInsert.getSubQueries();
        final SQLQuery          subQuery;


        // サブクエリ数が 0 の場合
        if (subQueries.size() == 0) {

            // 結果なし
            return null;

        }


        // サブクエリ数が 1 以外の場合
        // またはサブクエリが selectKey 以外の場合
        if (subQueries.size() != 1) {

            throw new IllegalStateException("Sub query count should be one.");

        }

        // サブクエリを取得する
        subQuery = subQueries.get(0);

        // selectKey以外の場合は例外
        if (!SQLQueryType.SELECT_KEY.equals(subQuery.getType())) {

            throw new IllegalStateException("Unsuppport it excluding the 'selectKey' tag.");

        }


        final String    sql;    // 実行するSQL


        // SQL文がない場合
        if (subQuery.getSql().length() == 0) {

            // デフォルトSQLを設定する
            sql = SQLQuery.SQL_STATEMENT_SELECT_KEY;

        } else {

            // サブクエリに設定されたSQLを設定する
            sql = subQuery.getSql().toString();

        }


        Cursor    cursor = null;    // カーソル情報


        try {

            // SQL文を実行する
            cursor = session.rawQuery(sql);

            // カーソルを先頭に移動する
            if (!cursor.moveToFirst()) {

                // 結果なし
                return null;

            }

            // 返却カラムが複数ある場合は例外
            if (cursor.getColumnCount() != 1) {

                throw new IllegalStateException("The column count should be one.");

            }

            // 返却結果が複数ある場合は例外
            if (cursor.getCount() != 1) {

                throw new IllegalStateException("The result count should be one.");

            }


            // キープロパティを取得する
            final String    keyProperty = subQuery.getAttributeValue(SQLQuery.ATTR_KEY_PROPERTY);

            // キープロパティがない場合は例外
            if (keyProperty == null) {

                throw new IllegalStateException("No 'keyProperty' attribute.");

            }

            // 結果を返す
            return new FRDatabaseParam(
                    keyProperty,
                    cursor.getString(0)
                    );

        } catch (final Throwable e) {

            throw new IllegalStateException("sql error!", e);

        } finally {

            // カーソルが取得できている場合
            if (cursor != null) {

                // カーソルを閉じる
                cursor.close();

            }

        }

    }


    /**
     * 指定されたSQLが指定されたSQLクエリ種別かどうかをチェックする。
     *
     * @param sql       調べるSQL文
     * @param queryTypes  SQLクエリ種別
     * @throws IllegalStateException 指定されたSQLが指定SQLクエリ種別でない場合
     */
    private static void checkCorrectSql(
            final String            sql,
            final SQLQueryType...   queryTypes
            ) {

        // 指定されたキーワードの一覧分繰り返す
        for (final SQLQueryType queryType : queryTypes) {

            // 指定されたSQLが指定SQLキーワードで開始する場合
            if (SQLQuery.isSqlKeyword(queryType.getName())
                && (sql.startsWith(queryType.getName().toUpperCase())
                    || sql.startsWith(queryType.getName().toLowerCase()))
                ) {

                // 処理なし
                return;

            }

        }

        // 一致しなかった場合は例外
        throw new IllegalStateException(
                "Illegal sql statement. Correct sql statement is [" + Arrays.toString(queryTypes) + "]"
                );

    }


    /**
     * 指定されたクエリ種別一覧の中に指定クエリ種別が含まれているかどうかを取得する。
     *
     * @param queryTypes    クエリ種別一覧
     * @param queryType     一覧の中に含まれているかどうかを調べるクエリ種別
     * @return 指定されたクエリ種別一覧の中に指定クエリ種別が含まれている場合は true
     */
    private static boolean containsQueryType(
            final SQLQueryType[]    queryTypes,
            final SQLQueryType      queryType
            ) {

        // 一覧要素分繰り返す
        for (final SQLQueryType type : queryTypes) {

            // 一致する場合
            if (type.equals(queryType)) {

                // 一致を返す
                return true;

            }

        }

        // 不一致を返す
        return false;

    }


    /**
     * 指定された更新登録用SQLクエリを実行する。
     *
     * @param xmlResId      クエリが定義されたXMLのリソースID
     * @param queryId       実行するクエリのID
     * @param queryType     実行するクエリ種別
     * @param parameters    更新登録に利用するパラメータ
     * @return 追加したデータの行番号または主キーを返す selectKey 指定がある場合はその値。それ以外の場合は null (INSERT文でのみ有効)
     * @throws IllegalStateException 指定されたSQLが指定SQLクエリ種別でない場合
     */
    private FRDatabaseParam processUpdatingQuery(
            final int                   xmlResId,
            final String                queryId,
            final SQLQueryType          queryType,
            final FRDatabaseParam...    parameters
            ) {

        return processUpdatingQuery(xmlResId, queryId, new SQLQueryType[] {queryType}, parameters);

    }


    /**
     * 指定された更新登録用SQLクエリを実行する。
     *
     * @param xmlResId      クエリが定義されたXMLのリソースID
     * @param queryId       実行するクエリのID
     * @param queryTypes    実行するクエリ種別
     * @param parameters    更新登録に利用するパラメータ
     * @return 追加したデータの行番号または主キーを返す selectKey 指定がある場合はその値。それ以外の場合は null (INSERT文でのみ有効)
     */
    private FRDatabaseParam processUpdatingQuery(
            final int                   xmlResId,
            final String                queryId,
            final SQLQueryType[]        queryTypes,
            final FRDatabaseParam...    parameters
            ) {

        // 引数が不正の場合は例外
        if ((queryId == null) || (queryId.length() == 0)) {

            throw new IllegalArgumentException();

        }


        // クエリデータを取得する
        final SQLQuery  query = getQuery(
                xmlResId,
                queryId,
                FRDatabaseUtils.createSearchMap(parameters)
                );

        // SQL文を取得する
        final String    sql = query.getSql().toString();


        // SQLをログへ出力
        Log.d(getClass().getName(), sql);

        // 取得したSQLが正しいかどうかをチェックする
        checkCorrectSql(sql, queryTypes);


        FRDatabaseSession   session     = null;     // DBアクセスセッション
        FRDatabaseParam     selectKey   = null;     // 返却する行番号または主キー

        try {

            // セッションを開く
            session = openSession(false);

            // SQLを実行する
            session.execSQL(sql);

            // INSERT文の場合
            if (containsQueryType(queryTypes, SQLQueryType.INSERT)) {

                // selectKeyタグがあれば処理をする
                selectKey = processSelectKey(session, query);

            }

        } catch (final Throwable e) {

            throw new IllegalStateException(e);

        } finally {

            // セッションを閉じる
            closeSession(session);

        }

        // 取得した行番号または主キーを返す
        return selectKey;

    }


    /**
     * 指定した追加登録SQLクエリを実行する。
     *
     * @param queryId       実行するクエリのID
     * @param entity        追加登録に利用するエンティティ
     * @return 追加したデータの行番号または主キーを返す selectKey 指定がある場合はその値。それ以外の場合は null
     * @throws NotFoundException        XML取得に失敗した場合
     * @throws IllegalStateException    クエリ取得に失敗した場合
     */
    public String insert(
            final String                queryId,
            final Object                entity
            ) {

        return insert(dbXmlId, queryId, entity);

    }


    /**
     * 指定した追加登録SQLクエリを実行する。
     *
     * @param xmlResId      クエリが定義されたXMLのリソースID
     * @param queryId       実行するクエリのID
     * @param entity        追加登録に利用するエンティティ
     * @return 追加したデータの行番号または主キーを返す selectKey 指定がある場合はその値。それ以外の場合は null
     * @throws NotFoundException        XML取得に失敗した場合
     * @throws IllegalStateException    クエリ取得に失敗した場合
     */
    public String insert(
            final int                   xmlResId,
            final String                queryId,
            final Object                entity
            ) {

        // 追加登録SQLを実行する
        final FRDatabaseParam   selectKey = processUpdatingQuery(
                xmlResId,
                queryId,
                SQLQueryType.INSERT,
                FRDatabaseUtils.createNameValuePairs(entity)
                );

        // selectKeyタグが存在した場合
        if (selectKey != null) {

            try {

                // 指定されたエンティティのキープロパティへ値を設定する
                ReflectUtils.setBeanValue(
                        DataConvertType.DATABASE,
                        entity,
                        selectKey.getName(),
                        selectKey.getValue()
                        );

            } catch (final ReflectException e) {

                e.printStackTrace();

            }

            // 行番号または主キーを返す
            return String.valueOf(selectKey.getValue());

        }

        // 返却データなし
        return null;

    }


    /**
     * 指定した追加登録SQLクエリを実行する。
     *
     * @param queryId       実行するクエリのID
     * @param parameters    追加登録に利用するパラメータ
     * @return 追加したデータの行番号または主キーを返す selectKey 指定がある場合はその値。それ以外の場合は null
     * @throws NotFoundException        XML取得に失敗した場合
     * @throws IllegalStateException    クエリ取得に失敗した場合
     */
    public String insert(
            final String                queryId,
            final FRDatabaseParam...    parameters
            ) {

        return insert(dbXmlId, queryId, parameters);

    }


    /**
     * 指定した追加登録SQLクエリを実行する。
     *
     * @param xmlResId      クエリが定義されたXMLのリソースID
     * @param queryId       実行するクエリのID
     * @param parameters    追加登録に利用するパラメータ
     * @return 追加したデータの行番号または主キーを返す selectKey 指定がある場合はその値。それ以外の場合は null
     * @throws NotFoundException        XML取得に失敗した場合
     * @throws IllegalStateException    クエリ取得に失敗した場合
     */
    public String insert(
            final int                   xmlResId,
            final String                queryId,
            final FRDatabaseParam...    parameters
            ) {

        // 追加登録SQLを実行する
        final FRDatabaseParam   selectKey = processUpdatingQuery(
                xmlResId,
                queryId,
                SQLQueryType.INSERT,
                parameters
                );

        // selectKeyタグが存在した場合
        if (selectKey != null) {

            // 行番号または主キーを返す
            return String.valueOf(selectKey.getValue());

        }

        // 返却データなし
        return null;

    }


    /**
     * 指定した更新SQLクエリを実行する。
     *
     * @param queryId       実行するクエリのID
     * @param entity        更新に利用するエンティティ
     * @throws NotFoundException        XML取得に失敗した場合
     * @throws IllegalStateException    クエリ取得に失敗した場合
     */
    public void update(
            final String                queryId,
            final Object                entity
            ) {

        update(dbXmlId, queryId, entity);

    }


    /**
     * 指定した更新SQLクエリを実行する。
     *
     * @param xmlResId      クエリが定義されたXMLのリソースID
     * @param queryId       実行するクエリのID
     * @param entity        更新に利用するエンティティ
     * @throws NotFoundException        XML取得に失敗した場合
     * @throws IllegalStateException    クエリ取得に失敗した場合
     */
    public void update(
            final int                   xmlResId,
            final String                queryId,
            final Object                entity
            ) {

        // 更新SQLを実行する
        processUpdatingQuery(
                xmlResId,
                queryId,
                SQLQueryType.UPDATE,
                FRDatabaseUtils.createNameValuePairs(entity)
                );

    }


    /**
     * 指定した更新SQLクエリを実行する。
     *
     * @param queryId       実行するクエリのID
     * @param parameters    更新に利用するパラメータ
     * @throws NotFoundException        XML取得に失敗した場合
     * @throws IllegalStateException    クエリ取得に失敗した場合
     */
    public void update(
            final String                queryId,
            final FRDatabaseParam...    parameters
            ) {

        update(dbXmlId, queryId, parameters);

    }


    /**
     * 指定した更新SQLクエリを実行する。
     *
     * @param xmlResId      クエリが定義されたXMLのリソースID
     * @param queryId       実行するクエリのID
     * @param parameters    更新に利用するパラメータ
     * @throws NotFoundException        XML取得に失敗した場合
     * @throws IllegalStateException    クエリ取得に失敗した場合
     */
    public void update(
            final int                   xmlResId,
            final String                queryId,
            final FRDatabaseParam...    parameters
            ) {

        // 更新SQLを実行する
        processUpdatingQuery(
                xmlResId,
                queryId,
                SQLQueryType.UPDATE,
                parameters
                );

    }


    /**
     * 指定した削除SQLクエリを実行する。
     *
     * @param queryId       実行するクエリのID
     * @param entity        削除に利用するエンティティ
     * @throws NotFoundException        XML取得に失敗した場合
     * @throws IllegalStateException    クエリ取得に失敗した場合
     */
    public void delete(
            final String                queryId,
            final Object                entity
            ) {

        delete(dbXmlId, queryId, entity);

    }


    /**
     * 指定した削除SQLクエリを実行する。
     *
     * @param xmlResId      クエリが定義されたXMLのリソースID
     * @param queryId       実行するクエリのID
     * @param entity        削除に利用するエンティティ
     * @throws NotFoundException        XML取得に失敗した場合
     * @throws IllegalStateException    クエリ取得に失敗した場合
     */
    public void delete(
            final int                   xmlResId,
            final String                queryId,
            final Object                entity
            ) {

        // 削除SQLを実行する
        processUpdatingQuery(
                xmlResId,
                queryId,
                new SQLQueryType[] {
                        SQLQueryType.DELETE,
                        SQLQueryType.DROP,
                },
                FRDatabaseUtils.createNameValuePairs(entity)
                );

    }


    /**
     * 指定した削除SQLクエリを実行する。
     *
     * @param queryId       実行するクエリのID
     * @param parameters    削除に利用するパラメータ
     * @throws NotFoundException        XML取得に失敗した場合
     * @throws IllegalStateException    クエリ取得に失敗した場合
     */
    public void delete(
            final String                queryId,
            final FRDatabaseParam...    parameters
            ) {

        delete(dbXmlId, queryId, parameters);

    }


    /**
     * 指定した削除SQLクエリを実行する。
     *
     * @param xmlResId      クエリが定義されたXMLのリソースID
     * @param queryId       実行するクエリのID
     * @param parameters    削除に利用するパラメータ
     * @throws NotFoundException        XML取得に失敗した場合
     * @throws IllegalStateException    クエリ取得に失敗した場合
     */
    public void delete(
            final int                   xmlResId,
            final String                queryId,
            final FRDatabaseParam...    parameters
            ) {

        // 削除SQLを実行する
        processUpdatingQuery(
                xmlResId,
                queryId,
                new SQLQueryType[] {
                        SQLQueryType.DELETE,
                        SQLQueryType.DROP,
                },
                parameters
                );

    }




    /**
     * SQLクエリ種別。
     *
     * @author Kou
     *
     */
    private static enum SQLQueryType {


        /**
         * SELECT文
         */
        SELECT("SELECT"),

        /**
         * INSERT文
         */
        INSERT("INSERT"),

        /**
         * UPDATE文
         */
        UPDATE("UPDATE"),

        /**
         * DELETE文
         */
        DELETE("DELETE"),

        /**
         * DROP文
         */
        DROP("DROP"),

        /**
         * プライマリキー選択
         */
        SELECT_KEY("selectKey"),

        /**
         * 動的宣言
         */
        DYNAMIC("dynamic"),

        /**
         * 値が null かどうか
         */
        IS_NULL("isNull"),

        /**
         * 値が null 以外かどうか
         */
        IS_NOT_NULL("isNotNull"),

        /**
         * 値が等しいかどうか
         */
        IS_EQUAL("isEqual"),

        /**
         * 値が等しくないかどうか
         */
        IS_NOT_EQUAL("isNotEqual");



        /**
         * 種別変換テーブルを初期化する
         */
        static {

            final Map<String, SQLQueryType>     queryTypes      = new HashMap<String, SQLQueryType>();
            final Map<String, SQLQueryType>     queryInnerTypes = new HashMap<String, SQLQueryType>();
            final Set<SQLQueryType>             queryConditions = new HashSet<SQLQueryType>();

            // SQLクエリ種別名をキーにしてSQLクエリ種別を追加する
            queryTypes.put(SELECT.getName(), SELECT);
            queryTypes.put(INSERT.getName(), INSERT);
            queryTypes.put(UPDATE.getName(), UPDATE);
            queryTypes.put(DELETE.getName(), DELETE);
            queryTypes.put(DROP.getName(), DROP);

            // SQLクエリ内部種別名をキーにしてSQLクエリ種別を追加する
            queryInnerTypes.put(SELECT_KEY.getName(), SELECT_KEY);
            queryInnerTypes.put(DYNAMIC.getName(), DYNAMIC);
            queryInnerTypes.put(IS_NULL.getName(), IS_NULL);
            queryInnerTypes.put(IS_NOT_NULL.getName(), IS_NOT_NULL);
            queryInnerTypes.put(IS_EQUAL.getName(), IS_EQUAL);
            queryInnerTypes.put(IS_NOT_EQUAL.getName(), IS_NOT_EQUAL);

            // SQLクエリ条件種別を追加する
            queryConditions.add(DYNAMIC);
            queryConditions.add(IS_NULL);
            queryConditions.add(IS_NOT_NULL);
            queryConditions.add(IS_EQUAL);
            queryConditions.add(IS_NOT_EQUAL);

            // 作成した各種別テーブルを設定する
            SQL_QUERY_TYPES             = queryTypes;
            SQL_QUERY_INNER_TYPES       = queryInnerTypes;
            SQL_QUERY_CONDITION_TYPES   = queryConditions;

        }


        /**
         * SQL文クエリ種別変換テーブル<br>
         * <br>
         * <table border="1">
         * <tr>
         *   <td>項目</td><td>型</td><td>内容</td>
         * </tr>
         * <tr>
         *   <td>キー</td><td>String</td><td>SQL文クエリ種別名</td>
         * </tr>
         * <tr>
         *   <td>値</td><td>SQLQueryType</td><td>SQL文クエリ種別</td>
         * </tr>
         * </table>
         */
        private static final Map<String, SQLQueryType>  SQL_QUERY_TYPES;

        /**
         * SQL文クエリ内部種別変換テーブル<br>
         * <br>
         * <table border="1">
         * <tr>
         *   <td>項目</td><td>型</td><td>内容</td>
         * </tr>
         * <tr>
         *   <td>キー</td><td>String</td><td>SQL文クエリ内部種別名</td>
         * </tr>
         * <tr>
         *   <td>値</td><td>SQLQueryType</td><td>SQL文クエリ内部種別</td>
         * </tr>
         * </table>
         */
        private static final Map<String, SQLQueryType>  SQL_QUERY_INNER_TYPES;

        /**
         * SQL文条件クエリ種別判定テーブル<br>
         * <br>
         * <table border="1">
         * <tr>
         *   <td>項目</td><td>型</td><td>内容</td>
         * </tr>
         * <tr>
         *   <td>要素</td><td>SQLQueryType</td><td>SQL文クエリ条件種別</td>
         * </tr>
         * </table>
         */
        private static final Set<SQLQueryType>          SQL_QUERY_CONDITION_TYPES;

        /**
         * クエリタグ名
         */
        private final String        queryTagName;



        /**
         * SQLクエリ種別を初期化する。
         *
         * @param name  SQLクエリタグ名
         */
        private SQLQueryType(
                final String    name
                ) {

            // クエリタグ名を取得する
            queryTagName = name;

        }


        /**
         * クエリタグ名を取得する。
         *
         * @return クエリタグ名
         */
        public String getName() {

            return queryTagName;

        }


        /**
         * 条件式かどうかを取得する。
         *
         * @param type 判定するSQLクエリ種別
         * @return 条件式の場合は true
         */
        public static boolean isConditionQuery(
                final SQLQueryType  type
                ) {

            // 条件式であれば true 返す
            return SQL_QUERY_CONDITION_TYPES.contains(type);

        }


        /**
         * 指定されたクエリタグ名からSQL文クエリ種別へ変換する。
         *
         * @param tagName クエリタグ名
         * @return SQL文クエリ種別。失敗時は null
         */
        public static SQLQueryType toQuerySqlType(
                final String    tagName
                ) {

            // クエリタグ名が null の場合
            if (tagName == null) {

                // nullを返す
                return null;

            }

            // 指定された名称に対応するクエリ種別を返す
            return SQL_QUERY_TYPES.get(tagName.toUpperCase());

        }


        /**
         * 指定されたクエリタグ名からSQL文クエリ内部にあるクエリ種別へ変換する。
         *
         * @param tagName クエリタグ名
         * @return 条件クエリ種別。失敗時は null
         */
        public static SQLQueryType toQueryInnerType(
                final String    tagName
                ) {

            // クエリタグ名が null の場合
            if (tagName == null) {

                // nullを返す
                return null;

            }

            // 指定された名称に対応するクエリ種別を返す
            return SQL_QUERY_INNER_TYPES.get(tagName);

        }

    }


    /**
     * SQLクエリデータ。
     *
     * @author Kou
     *
     */
    static final class SQLQuery {


        /**
         * SQL予約語 : SELECT
         */
        static final String     SQL_SELECT      = "SELECT";

        /**
         * SQL予約語 : FROM
         */
        static final String     SQL_FROM        = "FROM";

        /**
         * SQL予約語 : AS
         */
        static final String     SQL_AS          = "AS";

        /**
         * SQL予約語 : INSERT
         */
        static final String     SQL_INSERT      = "INSERT";

        /**
         * SQL予約語 : UPDATE
         */
        static final String     SQL_UPDATE      = "UPDATE";

        /**
         * SQL予約語 : DELETE
         */
        static final String     SQL_DELETE      = "DELETE";

        /**
         * SQL予約語 : VALUES
         */
        static final String     SQL_VALUES      = "VALUES";

        /**
         * SQL予約語 : SET
         */
        static final String     SQL_SET         = "SET";

        /**
         * SQL予約語 : WHERE
         */
        static final String     SQL_WHERE       = "WHERE";

        /**
         * SQL予約語 : AND
         */
        static final String     SQL_AND         = "AND";

        /**
         * SQL予約語 : OR
         */
        static final String     SQL_OR          = "OR";



        /**
         * SQLトークン : カラム区切り文字
         */
        static final String     TOKEN_COLUMN            = ",";

        /**
         * SQLトークン : 関数呼び出し開始トークン
         */
        static final String     TOKEN_FUNCTION_BEGIN    = "(";

        /**
         * SQLトークン : 関数呼び出し終了トークン
         */
        static final String     TOKEN_FUNCTION_END      = ")";



        /**
         * クエリ属性名 : クエリID
         */
        static final String     ATTR_ID             = "id";

        /**
         * クエリ属性名 : キー設定変数名
         */
        static final String     ATTR_KEY_PROPERTY   = "keyProperty";

        /**
         * クエリ属性名 : 接頭追加語
         */
        static final String     ATTR_PREPEND        = "prepend";

        /**
         * クエリ属性名 : 参照変数名
         */
        static final String     ATTR_PROPERTY       = "property";

        /**
         * クエリ属性名 : 比較対象値
         */
        static final String     ATTR_COMPARE_VALUE  = "compareValue";



        /**
         * SQL内にある参照変数名のフォーマット
         */
        static final String     FORMAT_VARIABLE_NAME    = "#(%s)#";

        /**
         * SQL文字列値のフォーマット
         */
        static final String     FORMAT_VALUE_STRING     = "'%s'";



        /**
         * プライマリキー選択のデフォルトSQL
         */
        static final String     SQL_STATEMENT_SELECT_KEY = "SELECT last_insert_rowid()";



        /**
         * SQL予約語検索テーブル
         */
        private static final Set<String>    SQL_KEYWORD_TABLE = new HashSet<String>();

        /**
         * クエリ種別
         */
        private SQLQueryType                queryType;

        /**
         * 属性一覧
         */
        private final Map<String, String>   queryAttributes =
            new HashMap<String, String>();

        /**
         * 実行SQLデータ
         */
        private StringBuilder               querySql    = new StringBuilder();

        /**
         * 実行サブクエリ一覧<br>
         * <br>
         * このクエリのSQLが実行されたあとに実行されるクエリ
         */
        private final List<SQLQuery>        querySubs   = new ArrayList<SQLQuery>();

        /**
         * 直前にSQLキーワードが追加されているかどうか
         */
        private boolean                     queryLastSqlKeyword;


        /**
         * SQLキーワードテーブルを作成する
         */
        static {

            // キーワードを追加する
            SQL_KEYWORD_TABLE.add(SQL_SELECT);
            SQL_KEYWORD_TABLE.add(SQL_FROM);
            SQL_KEYWORD_TABLE.add(SQL_AS);
            SQL_KEYWORD_TABLE.add(SQL_INSERT);
            SQL_KEYWORD_TABLE.add(SQL_UPDATE);
            SQL_KEYWORD_TABLE.add(SQL_DELETE);
            SQL_KEYWORD_TABLE.add(SQL_VALUES);
            SQL_KEYWORD_TABLE.add(SQL_SET);
            SQL_KEYWORD_TABLE.add(SQL_WHERE);
            SQL_KEYWORD_TABLE.add(SQL_AND);
            SQL_KEYWORD_TABLE.add(SQL_OR);

        }


        /**
         * 指定文字列がSQL予約語かどうかを取得する。
         *
         * @param keyword SQL予約語かどうかを調べる文字列
         * @return 指定文字列がSQL予約語の場合は true
         */
        public static boolean isSqlKeyword(
                final String    keyword
                ) {

            // SQL予約語かどうかを返す
            return SQL_KEYWORD_TABLE.contains(keyword);

        }


        /**
         * クエリ種別を設定する。
         *
         * @param type クエリ種別
         */
        public void setType(
                final SQLQueryType  type
                ) {

            queryType = type;

        }


        /**
         * クエリ種別を取得する。
         *
         * @return クエリ種別
         */
        public SQLQueryType getType() {

            return queryType;

        }


        /**
         * 指定した名前の属性値を設定する。
         *
         * @param name  属性名
         * @param value 属性値
         */
        public void setAttributeValue(
                final String    name,
                final String    value
                ) {

            queryAttributes.put(name, value);

        }


        /**
         * 指定した名前の属性値を取得する。
         *
         * @param name 属性名
         * @return 属性値
         */
        public String getAttributeValue(
                final String    name
                ) {

            return queryAttributes.get(name);

        }


        /**
         * 実行SQLテキストバッファを取得する。
         *
         * @return 実行SQLテキストバッファ
         */
        public StringBuilder getSql() {

            return querySql;

        }


        /**
         * サブクエリ一覧を取得する。
         *
         * @return サブクエリ一覧
         */
        public List<SQLQuery> getSubQueries() {

            return querySubs;

        }


        /**
         * 指定したパラメータマップにある変数を全て置換する。
         *
         * @param parametersMap 置換するパラメータマップ
         */
        public void replaceVariables(
                final Map<String, Object>   parametersMap
                ) {

            querySql = new StringBuilder(
                    replaceVariableName(querySql.toString().trim(), parametersMap)
                    );

        }


        /**
         * 直前にSQLキーワードが追加されているかどうかを設定する。
         *
         * @param enable 直前にSQLキーワードが追加されている場合は true
         */
        public void setLastSqlKeywordEnabled(
                final boolean   enable
                ) {

            queryLastSqlKeyword = enable;

        }


        /**
         * 直前にSQLキーワードが追加されているかどうかを取得する。
         *
         * @return 直前にSQLキーワードが追加されている場合は true
         */
        public boolean isLastSqlKeywordEnabled() {

            return queryLastSqlKeyword;

        }


    }


}
