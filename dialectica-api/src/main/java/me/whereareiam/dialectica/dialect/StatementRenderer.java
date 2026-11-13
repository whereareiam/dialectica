package me.whereareiam.dialectica.dialect;

import me.whereareiam.dialectica.schema.TableDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Renders the statements that read and write rows of a table in one dialect, naming tables and
 * columns exactly as the dialect's {@link SchemaRenderer} does.
 * <p>
 * Inserts and upserts write one row from every column's value. Selects, counts, updates and
 * deletes reach the rows that meet a {@link Condition}; a row is addressed by its primary key with
 * a condition that compares each key column. Conditions render the same way on every dialect, as
 * {@code =}, {@code <>}, {@code <}, {@code >}, {@code <=}, {@code >=}, {@code IS [NOT] NULL},
 * {@code IN}, {@code AND}, {@code OR} and {@code NOT}; a dialect differs in how a select limits
 * its rows.
 * <p>
 * Every method refuses a condition or ordering on a column the table lacks, and an
 * {@linkplain Comparison#isOrdered() ordered} comparison or an {@link Ordering} of a column whose
 * type is not {@linkplain Comparison#orders ordered}, with an {@link IllegalArgumentException}.
 */
public interface StatementRenderer {
	/**
	 * Renders the insert of a row with every column.
	 *
	 * @param table table to insert into
	 * @return the statement, parameterized by every column in table order
	 */
	@NotNull SqlStatement insert(@NotNull TableDefinition table);

	/**
	 * Renders the insert of a row that leaves the table as it is when a row already holds the
	 * inserted row's primary key or one of its unique keys. Any other failure, such as a missing
	 * referenced row, still fails the statement.
	 * <p>
	 * PostgreSQL and SQLite write {@code INSERT ... ON CONFLICT DO NOTHING}, which then changes no
	 * row. MySQL, MariaDB and H2 have no such clause, so the statement is a plain insert that fails
	 * with an error {@link me.whereareiam.dialectica.Dialect#isKeyConflict(java.sql.SQLException)}
	 * recognises; their failed statement does not end a transaction around it. Either way, of
	 * several instances that insert the same key at once, exactly one inserts the row.
	 *
	 * @param table table to insert into
	 * @return the statement, parameterized by every column in table order
	 */
	@NotNull SqlStatement insertIfAbsent(@NotNull TableDefinition table);

	/**
	 * Renders the write of a row that inserts it, or, when a row has its primary key, updates only
	 * the given columns of that row. With no columns, the existing row stays as it is. A conflict
	 * on another unique key of the table fails the write.
	 * <p>
	 * For a table whose primary key is its only unique key, PostgreSQL and SQLite render
	 * {@code INSERT ... ON CONFLICT (key) DO UPDATE SET column = excluded.column}, and MySQL and
	 * MariaDB {@code INSERT ... ON DUPLICATE KEY UPDATE}, each as {@link Upsert.OnConflict one
	 * statement}. For any other table, and always on H2, they render {@link Upsert.InsertThenUpdate
	 * an insert and an update}, so that a conflict on another unique key fails alike everywhere and
	 * concurrent writes of the same key all succeed: PostgreSQL's {@code ON CONFLICT (key)} lets a
	 * concurrent insert fail on another unique key, and MySQL's and MariaDB's statement cannot tell
	 * the keys apart.
	 *
	 * @param table   table to write to
	 * @param columns columns outside the primary key to update when the row exists
	 * @return the upsert
	 * @throws IllegalArgumentException when the table has no primary key, a column belongs to the
	 *                                  key, a column is not one of the table's, or a column is
	 *                                  given twice
	 */
	@NotNull Upsert upsert(@NotNull TableDefinition table, @NotNull List<String> columns);

	/**
	 * Renders the update of every column outside the primary key of the row with a primary key,
	 * which replaces the row's values.
	 *
	 * @param table table to update
	 * @return the statement, parameterized by the columns outside the key, then the key
	 * @throws IllegalArgumentException when every column belongs to the primary key
	 */
	@NotNull SqlStatement replace(@NotNull TableDefinition table);

	/**
	 * Renders the update of some columns of every row that meets a condition:
	 * <pre>{@code
	 * UPDATE table SET column = ?, ... WHERE condition
	 * }</pre>
	 * The update is one statement, so of several instances that update the same row under a
	 * condition its update breaks at once, only the first changes it.
	 *
	 * @param table     table to update
	 * @param columns   columns to set, outside the primary key; at least one
	 * @param condition condition the rows meet
	 * @return the statement, parameterized by the columns to set, then by the condition
	 * @throws IllegalArgumentException when no columns are given, a column to set belongs to the
	 *                                  key, a column is not one of the table's, or a column is set
	 *                                  twice
	 */
	@NotNull SqlStatement update(@NotNull TableDefinition table, @NotNull List<String> columns, @NotNull Condition condition);

	/**
	 * Renders the select of every column of the rows of a selection, in its order and range. A
	 * dialect renders the range as the ANSI {@code OFFSET n ROWS FETCH FIRST m ROWS ONLY}, which
	 * PostgreSQL and H2 do, or as {@code LIMIT m OFFSET n}, which MySQL, MariaDB and SQLite do.
	 *
	 * @param table     table to read
	 * @param selection rows to read
	 * @return the statement, parameterized by the selection's condition
	 */
	@NotNull SqlStatement select(@NotNull TableDefinition table, @NotNull Selection selection);

	/**
	 * Renders the count of the rows that meet a condition, {@code SELECT COUNT(*) FROM table WHERE
	 * condition}, whose one row holds the count.
	 *
	 * @param table     table to read
	 * @param condition condition the rows meet
	 * @return the statement, parameterized by the condition
	 */
	@NotNull SqlStatement count(@NotNull TableDefinition table, @NotNull Condition condition);

	/**
	 * Renders a select that returns one row when a row meets a condition and none otherwise.
	 *
	 * @param table     table to read
	 * @param condition condition a row meets
	 * @return the statement, parameterized by the condition
	 */
	@NotNull SqlStatement exists(@NotNull TableDefinition table, @NotNull Condition condition);

	/**
	 * Renders the delete of every row that meets a condition.
	 *
	 * @param table     table to delete from
	 * @param condition condition the rows meet
	 * @return the statement, parameterized by the condition
	 */
	@NotNull SqlStatement delete(@NotNull TableDefinition table, @NotNull Condition condition);
}
