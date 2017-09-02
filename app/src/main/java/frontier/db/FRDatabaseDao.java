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


/**
 * データベースDAOの抽象クラス。
 *
 * @author Kou
 *
 */
public abstract class FRDatabaseDao {


    /**
     * 使用するSQLマッパー
     */
    private FRDatabaseSqlMapper     sqlMapper;



    /**
     * 使用するSQLマッパーを設定する。
     *
     * @param mapper 使用するSQLマッパー
     */
    final void setSqlMapper(
            final FRDatabaseSqlMapper   mapper
            ) {

        sqlMapper = mapper;

    }


    /**
     * 設定されているSQLマッパーを取得する。
     *
     * @return 設定されているSQLマッパー
     */
    protected final FRDatabaseSqlMapper getSqlMapper() {

        // SQLマッパーが定義されていない場合は例外
        if (sqlMapper == null) {

            throw new IllegalStateException(
                    "No sql mapper. Please create a dao by the 'FRDatabaseManager.getDao' method"
                    );

        }

        // SQLマッパーを返す
        return sqlMapper;

    }


}
