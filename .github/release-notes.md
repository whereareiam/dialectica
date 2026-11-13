---
version: 5.0.0
---

# Dialectica 5.0.0 - Queries and Conditions

Entities can now be queried, updated and deleted by conditions on any column, not only by their primary key: conditions are `Where` values built from typed columns, which a new annotation processor writes for every entity, and queries order, page, count and stream their rows. Calls can share a transaction, and many entities can be inserted or upserted as one batch.

## Upgrading

Add the processor next to the adapter, `annotationProcessor("me.whereareiam:dialectica-processor:5.0.0")`; it writes `<Entity>Columns` next to each `@Table` entity, with constants such as `StoreEntryColumns.STORE_NAME`. Columns are no longer named by strings in entity access: use those constants, or `access.column("store_name", String.class)` for a name known only at runtime. The schema side, entity annotations and stored tables are unchanged.

| 4.x | 5.0 |
| --- | --- |
| `access.upsert(entity, "is_primary", "last_seen_at")` | `access.upsert(entity, LinkColumns.IS_PRIMARY, LinkColumns.LAST_SEEN_AT)` |
| `access.update(key).set("last_seen_at", now)` | `access.update(key).set(AccountColumns.LAST_SEEN_AT, now)` |
| `.when("used_at", 0L)`, `.when(column, 0L)` | `.when(CodeColumns.USED_AT.is(0L))` |
| `.when("used_at", null)` | `.when(CodeColumns.USED_AT.isNull())` |
| `.whenBelow(...)`, `.whenAbove(...)`, `.whenAtMost(...)`, `.whenAtLeast(...)` | `.when(column.below(v))`, `.when(column.above(v))`, `.when(column.atMost(v))`, `.when(column.atLeast(v))` |
| several `when…` calls | several `.when(...)` calls, or one `.when(a.and(b))` |
| `boolean changed = update.execute()` | `boolean changed = update.execute() > 0`; `execute()` returns the number of updated rows |
| `(EntityColumn<T, Long>) access.table().column("expires_at")` | `access.column("expires_at", Long.class)`, or the generated constant |
| hand-written `SELECT` of rows by a column | `access.query(where).list()`, with `orderBy`, `limit`, `offset`, `first()`, `count()`, `exists()` and `stream(consumer)` |
| hand-written `DELETE` or `UPDATE` of rows by a column | `access.delete(where)`, `access.update(where).set(...).execute()`, each returning the number of rows |
| a loop of `insert` or `upsert` calls | `access.insertAll(entities)`, `access.upsertAll(entities)` or `upsertAll(entities, columns...)`, in one transaction |
| `jdbi.useTransaction(handle -> …)` around hand-written SQL | `jdbi.useTransaction(handle -> access.on(handle)…)` |

`EntityAccess.of`, `insert`, `insertIfAbsent`, `upsert(entity)`, `upsert(entity, EntityColumn...)`, `replace`, `find(key...)` and `delete(key...)` keep their form; keys are still the values of the key columns in key order. Ordered comparisons now apply to text columns as well as numbers, by the database's collation.

A third-party dialect's `StatementRenderer` changes:

| 4.x | 5.0 |
| --- | --- |
| `new Condition(column, comparison)` | `new Condition.Comparing(column, comparison)`; `Condition` is a sealed interface with `Comparing`, `In`, `All`, `Any` and `Not` |
| `update(table, columns, List<Condition>)` | `update(table, columns, Condition)`; an update by key is an `All` of the key's `EQUALS` comparisons and the guards |
| `select(table)` | `select(table, Selection.of(condition))`, with orderings, a limit and an offset |
| `delete(table)` | `delete(table, condition)` |
| | `count(table, condition)` and `exists(table, condition)` are new |

`AnsiStatementRenderer` implements them all and renders limits and offsets as the ANSI `OFFSET n ROWS FETCH FIRST m ROWS ONLY`; override `range(limit, offset)` for a database that writes them otherwise, as MySQL, MariaDB and SQLite do with `LIMIT m OFFSET n`. `Comparison` gains `NOT_EQUALS` and `IS_NOT_NULL`, and `Comparison.orders(type)` tells which column types ordered comparisons accept.
