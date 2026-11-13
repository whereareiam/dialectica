# Dialectica

Dialectica describes database tables once, in Java, and works with them on SQLite, H2,
PostgreSQL, MySQL and MariaDB. An entity is a record or class with a few annotations. From it,
Dialectica creates the table, renders that database's SQL, and reads and writes entities
through Jdbi. Schema writes skip whatever already exists, so the same code can run on every
start. Java 17 or newer is required.

## Installation

```kotlin
repositories {
    maven("https://registry.whereareiam.me/maven/packages")
}

dependencies {
    // The entry point and the dialects of the databases you support:
    implementation("me.whereareiam:dialectica:5.0.0")
    implementation("me.whereareiam:dialectica-dialect-sqlite:5.0.0")
    implementation("me.whereareiam:dialectica-dialect-postgresql:5.0.0")

    // Or the entry point with all five dialects:
    implementation("me.whereareiam:dialectica-bundle:5.0.0")

    // Jdbi access to entities, and the typed columns of your entities:
    implementation("me.whereareiam:dialectica-adapter-jdbi:5.0.0")
    annotationProcessor("me.whereareiam:dialectica-processor:5.0.0")

    // Your database's JDBC driver, and Jdbi when you use the adapter.
}
```

| Artifact | Contains |
|----------|----------|
| `dialectica-api` | The `Dialect` contract, the schema and entity models and the annotations |
| `dialectica-common` | What the dialects share, JDBC inspection and the annotation reader |
| `dialectica` | The entry point `Dialectica` and `SchemaWriter`; no dialect |
| `dialectica-dialect-sqlite`, `-h2`, `-postgresql`, `-mysql`, `-mariadb` | One dialect each; MariaDB builds on MySQL and brings it along |
| `dialectica-bundle` | `dialectica` and all five dialects as one dependency; no code of its own |
| `dialectica-adapter-jdbi` | `EntityAccess`, `DialecticaPlugin` and row mapping for Jdbi |
| `dialectica-processor` | The annotation processor that writes each entity's `<Entity>Columns` |

The code is the same either way. Dialects are found at runtime among the dialect modules on the
classpath. Opening a database whose dialect module is missing fails with an
`IllegalArgumentException` that lists the available dialects.

Each dialect module registers itself in `META-INF/services/me.whereareiam.dialectica.Dialect`. If
your build relocates Dialectica into another package, relocate and merge those service files too.

## Declaring entities

```java
import me.whereareiam.dialectica.entity.annotation.*;
import me.whereareiam.dialectica.schema.type.ReferentialAction;

@Table("identica_accounts")
@Index(name = "identica_accounts_username", columns = "username")
public record Account(
        @Id UUID uniqueId,
        @Length(64) String username,
        @Nullable Long createdAt,
        @Nullable Long lastSeenAt
) {}

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Table("identica_provider_links")
@Unique({"uniqueId", "providerId"})
public class ProviderLink {
    @Id @Length(64) private String providerId;
    @Id @Length(128) private String providerSubject;
    @References(entity = Account.class, onDelete = ReferentialAction.CASCADE)
    private UUID uniqueId;
    @Column("is_primary") @Default("false") private boolean primary;
    @Nullable private Long linkedAt;
}
```

Every non-static, non-transient field the type declares is a column, in declaration order.

| Annotation | Meaning |
|------------|---------|
| `@Table("name")` | The entity's table; required |
| `@Id` | Part of the primary key; several `@Id` fields form a composite key, in field order |
| `@Column("name")` | The column's name; by default the field name in snake_case (`lastSeenAt` → `last_seen_at`) |
| `@Length(n)` | A `String` stored as `varchar(n)`; a `String` without it is unlimited `text` |
| `@Nullable` | The column accepts `NULL`; columns refuse it otherwise |
| `@Default("value")` | The value the column takes when an insert leaves it out, read by the field's type |
| `@References(entity = X.class, onDelete = ...)` | A foreign key to `X`'s primary key, which must be a single `@Id` field |
| `@References(table = "name", column = "name", onDelete = ...)` | A foreign key to a column of a table named directly |
| `@ForeignKey(fields = {"fieldA", "fieldB"}, entity = X.class, onDelete = ...)` | A foreign key over fields to `X`'s whole primary key, one field for each of `X`'s `@Id` fields in their order; repeatable |
| `@ForeignKey(fields = {"fieldA", "fieldB"}, table = "name", columns = {...}, onDelete = ...)` | A foreign key over fields to columns of a table named directly, in the order given; repeatable |
| `@Unique({"fieldA", "fieldB"})` | A unique constraint over fields; repeatable |
| `@Index(name = "...", columns = {...}, unique = ...)` | An index over fields; repeatable |

`@Unique`, `@Index` and `@ForeignKey` name fields, not columns. A foreign key to a composite key
names the referencing fields in the order of the referenced `@Id` fields, and each of them has the
Java type of the key field it references:

```java
@Table("identica_provider_profiles")
@ForeignKey(fields = {"providerId", "providerSubject"}, entity = ProviderLink.class,
        onDelete = ReferentialAction.CASCADE)
public record ProviderProfile(
        @Id @Length(64) String providerId,
        @Id @Length(128) String providerSubject,
        @Length(64) String providerUsername
) {}
```

A foreign key names either an entity or a table, never both. Name the table when its entity is
out of reach, for example when a plug-in's tables reference the core tables of the application it
extends. The referenced columns then have to form that table's primary key or a unique key, and
Dialectica cannot check their types:

```java
@Table("example_notes")
public record Note(
        @Id @Length(64) String id,
        @References(table = "identica_accounts", column = "unique_id",
                onDelete = ReferentialAction.CASCADE) UUID owner
) {}
```

`@Default` is written as text and read by the field's type: `true` or `false` for a `boolean`, a
decimal number in range for a `long` or an `int`, text for a `String`, at most its `@Length`, and a
36-character UUID for a `UUID`. Any other value is refused when the entity is read. A default lets
an `AddColumn` add a `NOT NULL` column to a table that already holds rows. MySQL accepts the default
of a `String` field without `@Length` only as an expression, which needs MySQL 8.0.13 or newer.

Any annotation named `Nullable` that is visible at runtime also counts, such as Jakarta's or
JSR-305's. JetBrains' `@Nullable` is not visible at runtime, so a field carrying only that
annotation is stored `NOT NULL`. Key fields and primitive fields cannot be nullable.

| Java type | Column type | SQLite | H2 | PostgreSQL | MySQL, MariaDB |
|-----------|-------------|--------|----|------------|----------------|
| `UUID` | `uuid()` | `TEXT` | `UUID` | `UUID` | `CHAR(36)` |
| `String` with `@Length(n)` | `varchar(n)` | `VARCHAR(n)` | `VARCHAR(n)` | `VARCHAR(n)` | `VARCHAR(n)` |
| `String` | `text()` | `TEXT` | `CLOB` | `TEXT` | `TEXT` |
| `long`, `Long` | `bigint()` | `INTEGER` | `BIGINT` | `BIGINT` | `BIGINT` |
| `int`, `Integer` | `integer()` | `INTEGER` | `INTEGER` | `INTEGER` | `INT` |
| `boolean`, `Boolean` | `bool()` | `INTEGER` | `BOOLEAN` | `BOOLEAN` | `BOOLEAN` |

Any other field type is refused, and so is any other mistake. `Dialectica.entity(...)` throws
an `IllegalArgumentException` that names the entity and the field.

Dialectica reads values through record accessors, getters or the fields themselves. It builds
records through their canonical constructor. It builds classes through a constructor that takes
every column field in order, as Lombok's `@AllArgsConstructor` does, or else through a
constructor without parameters followed by setters or field writes.

`Dialectica.entity(Account.class)` returns an `EntityTable<Account>`. Its `table()` is the
schema-level `TableDefinition`, and its `columns()` are typed `EntityColumn<Account, V>`s that read values from
an entity. The rest of Dialectica only works with this model. A table can also be written by hand
by extending `EntityTable`: give the constructor typed columns and the keys, and implement
`create(Row)`. The `EntityTable` Javadoc has an example.

MySQL, MariaDB and H2 cannot use a `text()` column in a key or an index, so use `@Length` there.
A unique constraint or index accepts any number of rows with `NULL` in its columns, on every
database. Index names share one namespace per schema on PostgreSQL, H2 and SQLite, so prefix them
with the table's name. A lowercase snake_case name that no supported database reserves is written
without quotes. Any other name is quoted, and hand-written queries have to quote it the same way;
`dialect.identifier(name)` writes it as Dialectica does.

## Creating the schema

```java
try (Connection connection = dataSource.getConnection()) {
    Dialectica.on(connection).create(Schema.of(
            Dialectica.entity(ProviderLink.class).table(),
            Dialectica.entity(Account.class).table()
    ));
}
```

`Dialectica.on` picks the dialect from the connection's metadata. `create` makes every table that
does not exist yet, after the tables of the same schema it references, followed by its indexes.
Running it again changes nothing. A foreign key may also name a table of another owner, such as a
core table referenced by an add-on's schema; that table has to exist first.

Tables can also be declared without an entity:

```java
TableDefinition accounts = TableDefinition.builder("identica_accounts")
        .column(ColumnDefinition.builder("unique_id", ColumnType.uuid()).notNull().build())
        .column(ColumnDefinition.builder("username", ColumnType.varchar(64)).notNull().build())
        .column(ColumnDefinition.builder("is_locked", ColumnType.bool()).notNull().defaultValue(false).build())
        .primaryKey("unique_id")
        .index(IndexDefinition.plain("identica_accounts_username", "username"))
        .build();
```

`TableDefinition`, `ColumnDefinition`, `IndexDefinition` and `ForeignKeyDefinition` are the schema
model in `me.whereareiam.dialectica.schema`; the annotations `@Table`, `@Column`, `@Index` and
`@ForeignKey` describe an entity and are read into it.
`ForeignKeyDefinition.builder(columns...).references(table, columns...).onDelete(...)` and
`TableDefinition.Builder.unique(...)` declare the other keys. A primary key column must be `notNull()`.
`defaultValue` takes text, a number or a boolean. MySQL accepts a text column's default only as an
expression, which needs MySQL 8.0.13 or newer.

## Changing a schema

An installed schema grows through changes, applied in order:

```java
Dialectica.on(connection).apply(
        new AddColumn("identica_accounts", ColumnDefinition.builder("last_seen_at", ColumnType.bigint()).build()),
        new CreateIndex("identica_accounts", IndexDefinition.plain("identica_accounts_last_seen_at", "last_seen_at")),
        new DropColumn("identica_accounts", "created_at"),
        new RenameTable("identica_cracked_accounts", "identica_credential_accounts")
);
```

Before each change the writer reads the database's metadata and skips the change when it has
already been made:

| Change | Skipped when |
|--------|--------------|
| `CreateTable` | the table exists; its missing indexes are still created |
| `DropTable` | the table does not exist |
| `RenameTable` | no table has the old name |
| `AddColumn` | the column exists |
| `DropColumn` | the column does not exist |
| `CreateIndex` | an index of that name exists on the table |
| `DropIndex` | no index of that name exists on the table |

MySQL, MariaDB and H2 commit every DDL statement on its own, and MySQL has no `IF NOT EXISTS` for
columns and indexes. When a run stops halfway, the next run therefore carries on where it stopped
instead of failing on what the first run already did.

`create` does not compare an existing table with its model. When a newer model declares a new
column, add it with an `AddColumn`.

Some changes would need SQLite to rebuild the table, which Dialectica does not do. They throw an
`UnsupportedChangeException` that names the dialect and the change:

- adding a `notNull()` column without a default;
- dropping a column that belongs to a key or an index. Dropping any other column needs SQLite
  3.35 or newer.

On the other databases, a `notNull()` column added to a table that already holds rows also needs a
default.

## Transactions and concurrent instances

The writer runs its statements on the connection as it is and leaves transactions to the caller.
On PostgreSQL, a transaction around `create` or `apply` makes the whole write succeed or fail
together.

The writer takes no lock. Instances that share a database and start at the same moment, such as
the proxies of a network, have to run their schema writes one after another. One way is to run
them in a [Strata](https://github.com/whereareiam/strata) migration, which holds Strata's lock.

## Reading and writing entities with Jdbi

```java
Jdbi jdbi = Jdbi.create(dataSource);
jdbi.installPlugin(new SqlObjectPlugin());
jdbi.installPlugin(new DialecticaPlugin());

EntityAccess<Account> accounts = EntityAccess.of(jdbi, Dialectica.entity(Account.class));
accounts.insert(account);
boolean created = accounts.insertIfAbsent(account); // false when a row holds one of its keys
accounts.upsert(account);
accounts.replace(account);                         // true when a row had the key
Optional<Account> found = accounts.find(uniqueId);
accounts.delete(uniqueId);                         // true when a row had the key

List<Account> recent = accounts.query(AccountColumns.LAST_SEEN_AT.atLeast(since))
        .orderBy(AccountColumns.LAST_SEEN_AT.descending())
        .limit(20)
        .list();
int removed = accounts.delete(AccountColumns.LAST_SEEN_AT.below(cutoff));

EntityAccess<ProviderLink> links = EntityAccess.of(jdbi, Dialectica.entity(ProviderLink.class));
links.find("premium", subject);                    // composite key, in key order
links.upsert(link, ProviderLinkColumns.IS_PRIMARY, ProviderLinkColumns.LAST_SEEN_AT);
```

`DialecticaPlugin` reads the dialect from a connection when it is installed, or takes one:
`new DialecticaPlugin(dialect)`. It binds and maps `UUID` and `Boolean` values the way the
database stores them, so a UUID is text on SQLite and `CHAR(36)` on MySQL. This also applies to
hand-written queries. `find`, `update(key...)`, `delete(key...)`, `upsert` and `replace` need a
primary key; queries, updates and deletes by a condition do not. A key is given as the values of
its columns in key order and checked against their types when the call runs.

### Typed columns

`dialectica-processor`, added to the compiler as an `annotationProcessor`, writes a class
`<Entity>Columns` next to every `@Table` entity, with one `EntityColumn` constant per column, named
after the column in upper snake case:

```java
// written for Account: AccountColumns.UNIQUE_ID, USERNAME, CREATED_AT, LAST_SEEN_AT
public static final EntityColumn<Account, Long> LAST_SEEN_AT = ...;
```

Code that knows a column's name only at runtime looks it up with
`accounts.column("last_seen_at", Long.class)`. The processor reports a field whose type has no
column type, and two columns whose constants would share a name, as compile errors.

### Conditions

A condition is a `Where<Entity>` value built from the columns, which queries, updates and deletes
take. It can be kept in a field and used again:

```java
Where<StoreEntry> expired = StoreEntryColumns.STORE_NAME.is(store)
        .and(StoreEntryColumns.EXPIRES_AT.atMost(now));
```

| Method | SQL |
|--------|-----|
| `is(value)`, `isNot(value)` | `column = ?`, `column <> ?` |
| `isNull()`, `isNotNull()` | `column IS NULL`, `column IS NOT NULL` |
| `in(values...)` | `column IN (?, ...)`, at least one value |
| `below`, `above`, `atMost`, `atLeast` | `<`, `>`, `<=`, `>=` |
| `and(other)`, `or(other)`, `not()` | `AND`, `OR`, `NOT (...)` |

`below`, `above`, `atMost`, `atLeast`, `ascending()` and `descending()` need a column with an
order: numbers, and text, which compares by the database's collation, so MySQL and MariaDB ignore
case by default while the others do not. UUID and boolean columns have none. Conditions compare as
SQL does: a column that is `NULL` meets no comparison with a value, `isNot` included, and neither
does its negation; `isNull` and `isNotNull` test for it. A value has to have the column's Java type,
so a `bigint()` column takes `0L`, not `0`.

### Queries

`query(where)` reads the entities whose rows meet a condition. It can order them, skip some and
limit how many it reads, and runs on one of its terminal methods:

```java
EntityQuery<StoreEntry> stored = entries.query(StoreEntryColumns.STORE_NAME.is(store))
        .orderBy(StoreEntryColumns.ENTRY_KEY.ascending());
List<StoreEntry> page = stored.offset(40).limit(20).list();
Optional<StoreEntry> first = stored.first();
long count = stored.count();                       // order, limit and offset do not apply
boolean any = stored.exists();
stored.stream(stream -> stream.forEach(cache::put)); // reads rows as the stream is pulled
```

A query is an immutable value, so `orderBy`, `limit` and `offset` return a changed copy. A page read
with an offset needs an order to be stable. The stream of `stream` is open only while its consumer
runs.

| Database | Limit and offset |
|----------|------------------|
| PostgreSQL, H2 | `OFFSET n ROWS FETCH FIRST m ROWS ONLY` |
| MySQL, MariaDB, SQLite | `LIMIT m OFFSET n` |

### Transactions and batches

Each call runs on its own handle of the `Jdbi`. `on(handle)` binds an access to a handle, so that
several calls share its transaction:

```java
jdbi.useTransaction(handle -> {
    EntityAccess<StoreEntry> bound = entries.on(handle);
    bound.delete(StoreEntryColumns.STORE_NAME.is(store));
    bound.insertAll(replacement);
});
```

`insertAll` and `upsertAll` write many entities as batches, in one transaction: on the access's own
handle, or inside the transaction the bound handle has open. Either every entity is written or none.

### Inserting if absent

`insertIfAbsent` inserts the entity unless a row already holds its primary key or one of its unique
keys, and leaves that row as it is. It returns whether it inserted. Any other failure, such as a
`NULL` in a `NOT NULL` column or a missing referenced row, still throws. Of several instances that
insert the same key at once, exactly one gets `true`:

```java
if (credentials.insertIfAbsent(new CredentialAccount(uniqueId, username, hash))) {
    // this instance created the account
}
```

| Database | Insert if absent |
|----------|------------------|
| PostgreSQL, SQLite | `INSERT ... ON CONFLICT DO NOTHING` |
| MySQL, MariaDB, H2 | `INSERT`, whose duplicate key error reads as `false` |

MySQL's `INSERT IGNORE` would also skip or alter rows for other errors, and its
`ON DUPLICATE KEY UPDATE` reports a kept row like an inserted one, so neither is used. A failed insert
does not end a transaction on MySQL, MariaDB or H2. `dialect.isKeyConflict(exception)` tells
whether an exception reports a taken key; on SQLite it reads the driver's extended result code,
because the plain code is the same for every refused constraint.

### Upserting

`upsert(entity)` inserts the entity or, when a row has its primary key, updates every column of that
row outside the key. `upsert(entity, columns...)` updates only the columns it names and leaves the
others as the row holds them. The primary key decides whether the row exists. A row that holds
another unique key of the entity, such as a second link of the same account to the same provider,
fails the upsert with a `KeyConflictException` on every database and stays as it is. Of several
instances that write the same key at once, each one succeeds, and the row ends with the values of
the last.

| Database | Table whose only unique key is the primary key | Table with other unique keys |
|----------|-----------------------------------------------|------------------------------|
| PostgreSQL, SQLite | `INSERT ... ON CONFLICT (key) DO UPDATE SET column = excluded.column` | `INSERT ... ON CONFLICT DO NOTHING`, then `UPDATE ... WHERE key` |
| MySQL 8.0.19+ | `INSERT ... AS excluded ON DUPLICATE KEY UPDATE column = excluded.column` | `INSERT`, then `UPDATE ... WHERE key` on a duplicate key |
| MariaDB | `INSERT ... ON DUPLICATE KEY UPDATE column = VALUES(column)` | `INSERT`, then `UPDATE ... WHERE key` on a duplicate key |
| H2 | `INSERT`, then `UPDATE ... WHERE key` on a duplicate key | the same |

The second form exists because PostgreSQL's `ON CONFLICT (key)` lets a concurrent insert fail on
another unique key, and MySQL's and MariaDB's `ON DUPLICATE KEY UPDATE` would update the row that
holds whichever key is taken. When its update matches no row, the insert met another unique key,
which is the `KeyConflictException`; its cause is the database's error where there is one. MySQL
and MariaDB connections that set `useAffectedRows=true` report an update that keeps the row's
values as matching no row, so there an upsert of a table with other unique keys that changes
nothing also throws it.

### Updating some columns

`replace` writes every column of the entity. `update` sets only the columns it names, in one
`UPDATE` statement, without reading the row first: `update(key...)` of the row with a primary key,
`update(where)` of every row that meets a condition. `execute()` returns the number of rows it
updated.

```java
accounts.update(uniqueId).set(AccountColumns.LAST_SEEN_AT, now).execute();
sessions.update(SessionColumns.EXPIRES_AT.atMost(now)).set(SessionColumns.CLOSED, true).execute();
```

`when(where)` lets the update change a row only while its current values meet a condition as
well, so a value can be claimed once across instances that share the database:

```java
boolean used = recoveryCodes.update(uniqueId, code)
        .set(RecoveryCodeColumns.USED_AT, now)
        .when(RecoveryCodeColumns.USED_AT.is(0L))
        .execute() > 0;                            // true for exactly one instance
```

An ordered guard lets a counter only move forward, again in one statement, so of several instances
that offer the same value only one gets it accepted:

```java
boolean accepted = enrollments.update(uniqueId, method)
        .set(EnrollmentColumns.ACCEPTED_COUNTER, step)
        .when(EnrollmentColumns.ACCEPTED_COUNTER.below(step))
        .execute() > 0;                            // false once step or a later one was accepted
```

An update that would give a row a unique key another row holds fails with a
`KeyConflictException`, as an upsert does, so a caller can tell a taken value from any other failure:

```java
try {
    usernames.update(uniqueId).set(UsernameColumns.USERNAME_KEY, key).execute();
} catch (KeyConflictException taken) {
    // another row holds the key
}
```

A key column cannot be set, and a `NULL` only goes into a nullable column. MySQL and MariaDB
report the rows an update matched, unless the connection sets `useAffectedRows=true`. With that
option, an update that writes the values the row already holds counts no row.

### Hand-written queries

Register an entity's mapper on a SqlObject interface, and select its columns by name; no aliases
are needed:

```java
@RegisterDialecticaMapper(Account.class)
public interface AccountQueries {
    @SqlQuery("SELECT * FROM identica_accounts WHERE LOWER(username) = LOWER(:username)")
    List<Account> findByUsername(@Bind("username") String username);

    @SqlUpdate("DELETE FROM identica_account_reservations WHERE expires_at <= :now")
    void deleteExpired(@Bind("now") long now);
}
```

Result columns are matched to the entity's columns regardless of case. A column the query leaves
out reads as `null`. `EntityRowMapper` is the same mapper, for registering by hand.

## Rendering without writing

Each dialect renders SQL without connecting to the database:

```java
Dialect dialect = Dialect.of(connection.getMetaData());
List<String> ddl = dialect.schemaRenderer().render(new CreateTable(accounts));
Upsert upsert = dialect.statementRenderer().upsert(accounts, List.of("username", "is_locked"));
SqlStatement lock = dialect.statementRenderer().update(accounts, List.of("is_locked"),
        new Condition.All(List.of(
                new Condition.Comparing("unique_id", Comparison.EQUALS),
                new Condition.Comparing("is_locked", Comparison.EQUALS))));
SqlStatement page = dialect.statementRenderer().select(accounts,
        Selection.of(new Condition.Comparing("last_seen_at", Comparison.AT_LEAST))
                .withOrderings(List.of(new Ordering("last_seen_at", Ordering.Direction.DESCENDING)))
                .withLimit(20));
```

The renderers, `SqlStatement`, `Upsert`, `Condition`, `Selection` and `Ordering` are in
`me.whereareiam.dialectica.dialect`. An `Upsert` is either `Upsert.OnConflict`, one statement, or
`Upsert.InsertThenUpdate`, an insert and the update to run when it inserts no row or fails with a
key conflict. `insertIfAbsent` renders the insert that skips or fails on a taken key. `replace`
renders the update of every column outside the key. `select`, `count`, `exists`, `update` and
`delete` reach the rows that meet a `Condition`: a `Comparing` of a column by a `Comparison`, an
`In`, or `All`, `Any` and `Not` of other conditions; a `Where` is one together with its values.
Schema statements are plain DDL without `IF [NOT] EXISTS`. Entity statements use a positional `?`
for each column that `SqlStatement.getParameters()` names, in the order of the condition's
comparisons, and `dialect.bind(...)` binds a value the way the database stores it.

## Writing a dialect

A dialect for another database implements `me.whereareiam.dialectica.Dialect`, has a public
constructor without parameters, and lists itself in
`META-INF/services/me.whereareiam.dialectica.Dialect`. `Dialect.of` then picks it for a connection
its `accepts` method recognizes, usually by product name; exactly one dialect may accept a
database.

The simplest way is to depend on `dialectica-common` and extend what the shipped dialects share:
`me.whereareiam.dialectica.common.AnsiDialect` and the renderers in
`me.whereareiam.dialectica.common.dialect`.

```java
public final class ExampleDialect extends AnsiDialect {
    private final SchemaRenderer schemaRenderer = new ExampleSchemaRenderer(this);   // extends AnsiSchemaRenderer
    private final StatementRenderer statementRenderer = new ExampleStatementRenderer(this); // extends AnsiStatementRenderer

    @Override public String name() { return "Example"; }
    @Override public boolean accepts(DatabaseMetaData metaData) throws SQLException {
        return "Example".equalsIgnoreCase(metaData.getDatabaseProductName());
    }
    @Override public SchemaRenderer schemaRenderer() { return schemaRenderer; }
    @Override public StatementRenderer statementRenderer() { return statementRenderer; }
}
```

The schema renderer supplies the column types, and the statement renderer supplies the upsert and
the insert if absent from the forms `AnsiStatementRenderer` renders. A select's limit and offset
render as the ANSI `OFFSET ... FETCH FIRST`, unless the renderer overrides `range`. The dialect's `isKeyConflict`
recognises the error a plain insert fails with on a taken key.
Override whatever else the database writes differently, as the MariaDB dialect does on top of
MySQL's.

## Migrations

Installation upgrades belong to [Strata](https://github.com/whereareiam/strata). When an upgrade
is a schema change, apply the Dialectica change from a Strata migration, on the migration's
connection.

## Building and publishing

```sh
./gradlew test build
./gradlew -PintegrationTests=true test
```

The first command runs the rendering and reader tests, plus the SQLite and H2 tests in process.
The second also runs the PostgreSQL, MariaDB and MySQL 8 tests in containers, which requires
Docker. Set `DOCKER_HOST` when using a non-default socket.

Pull requests run the first command. Development builds run only through the manual workflow.
Releases run the complete test suite, publish public packages through the shared DevOps OIDC
action, and attach binary, source and Javadoc JARs.

Release Drafter follows `dev`. Pull request titles follow `Area: Description` and carry one of the
`feature`, `change`, `bug`, `dependencies` or `skip-changelog` labels, with `major` for breaking
changes; see `AGENTS.md`.
