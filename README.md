# FrontierDao

This library is O/R Mapper library like `MyBatis` for Android.<br>


## Build

### Creates jar

```bash
./gradlew makeJar
```

Output to `app/build/libs/frontierdao-X.X.X.jar`.

## Usage

Copy created jar file to your application projects "libs" directory.

### 1. Copy sqlite database file to assets directory.

### 2. Creates a sql statements xml file as resource.
[Example]<br>
<br>
`res/xml/sql_histories.xml`
```sql
<?xml version="1.0" encoding="utf-8"?>

<mapper namespace="Histories"> <!-- This name is table name -->

    <select id="getEntity">
        SELECT
            id                  AS historyId,
            historyTypeId,
            updateTime
        FROM
            Histories
        <dynamic prepend="WHERE">
            <isNotNull property="historyId">
                id = #historyId#
            </isNotNull>
            <isNotNull prepend="AND" property="date">
                date(updateTime) = date(#updateTime#)
            </isNotNull>
        </dynamic>
        <isNotNull property="limit">
            LIMIT #limit#
        </isNotNull>
        <isNotNull property="offset">
            OFFSET #offset#
        </isNotNull>
    </select>

    <insert id="insert">

        <!-- Specify a variable assignment destination -->
        <selectKey keyProperty="historyId" />

        INSERT INTO
            Histories (
                historyTypeId,
                updateTime
            ) VALUES (
                #historyTypeId#,
                #updateTime#
            )

    </insert>

    <update id="update">

        UPDATE
            Histories
        SET
            historyTypeId  = #historyTypeId#,
            updateTime     = #updateTime#
        WHERE
            id = #historyId#

    </update>

    <delete id="delete">

        DELETE FROM
            Histories
        WHERE
            id = #historyId#

    </delete>

</mapper>

```

### 3. Creates a dao class extended `FRDatabaseDao` class and a entity pojo class.

[Example]<br>
<br>
・`app/entity/HistoriesEntity.java`
```java
public class HistoriesEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer     historyId;

    private Integer     historyTypesId;

    private Date        updateTime;


    public Integer getHistoryId() {
        return historyId;
    }

    public void setHistoryId(final Integer historyId) {
        this.historyId = historyId;
    }

    public Integer getHistoryTypeId() {
        return historyTypesId;
    }

    public void setHistoryTypeId(final Integer historyTypesId) {
        this.historyTypesId = historyTypesId;
    }

    public Date getUpdateTime() {
        return updateTime == null ? null : (Date)updateTime.clone();
    }

    public void setUpdateTime(final Date updateTime) {
        this.updateTime = updateTime == null ? null : (Date)updateTime.clone();
    }

}
```

・`app/dao/HistoriesDao.java`

```java
@FRDatabaseMappingXml("sql_histories") // specify the sql xml file name (exclude .xml extension) in the "res/xml" directory.
public class HistoriesDao extends FRDatabaseDao {

    public List<HistoriesEntity> getAll() {

        return getSqlMapper().selectForList(
                "getEntity",
                HistoriesEntity.class
                );

    }

    public HistoriesEntity getEntity(
            final Integer historyId
            ) {

        return getSqlMapper().selectForObject(
                "getEntity",
                HistoriesEntity.class,
                new FRDatabaseParam("historyId", historyId)
                );

    }

    public HistoriesEntity getEntity(
            final Date    date
            ) {

        return getSqlMapper().selectForObject(
                "getEntity",
                HistoriesEntity.class,
                new FRDatabaseParam("updateTime", date)
                );

    }

    public void insert(
            final HistoriesEntity   entity
            ) {

        getSqlMapper().insert("insert", entity);

    }

    public void update(
            final HistoriesEntity   entity
            ) {

        getSqlMapper().update("update", entity);

    }

    public void delete(
            final Integer   id
            ) {

        getSqlMapper().delete(
                "delete",
                new FRDatabaseParam("historyId", id)
                );

    }

}
```

### 4. Set using database name and call created dao class method.

[Example]

・`app/activity/LaunchActivity.java`<br>
(This activity to be launched at the beginning of the application)

```java
final Resources res = context.getResources();
final String    dbFileName;

// Set a default database file name.
dbFileName = "application.db";

// Copy database file from assets.
FRDatabaseManager.copyDatabaseFromAssets(
    context.getApplicationContext(),
    dbFileName,
    dbFileName,
    false);

// Set using database file name.
FRDatabaseManager.setDefaultDatabaseName(context, dbFileName);
```

・`app/activity/HistoryActivity.java`

```java
// context is instance of activity context.
final HistoriesDao  dao = FRDatabaseManager.getInstance(context).getDao(
        context,
        HistoriesDao.class
        );

final List<HistoriesEntity> models = dao.getAll(); // get all records of Histories table.
```

## [Changelog](CHANGELOG.md)

## [License](LICENSE)
