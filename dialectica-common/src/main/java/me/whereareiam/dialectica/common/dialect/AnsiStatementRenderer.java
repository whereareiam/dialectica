package me.whereareiam.dialectica.common.dialect;

import me.whereareiam.dialectica.Dialect;
import me.whereareiam.dialectica.dialect.Condition;
import me.whereareiam.dialectica.dialect.Ordering;
import me.whereareiam.dialectica.dialect.Selection;
import me.whereareiam.dialectica.dialect.SqlStatement;
import me.whereareiam.dialectica.dialect.StatementRenderer;
import me.whereareiam.dialectica.dialect.Upsert;
import me.whereareiam.dialectica.schema.ColumnDefinition;
import me.whereareiam.dialectica.schema.TableDefinition;
import me.whereareiam.dialectica.schema.type.ColumnType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Function;

/**
 * Renders the entity statements every supported dialect shares. Upserts and inserts if absent
 * differ between dialects, so each dialect's renderer supplies its own from the forms rendered
 * here. A select's range renders as the ANSI {@code OFFSET n ROWS FETCH FIRST m ROWS ONLY} unless
 * the dialect {@linkplain #range(OptionalLong, long) renders its own}.
 */
public abstract class AnsiStatementRenderer implements StatementRenderer {
	private final @NotNull Dialect dialect;

	/**
	 * Creates the renderer of a dialect, which names tables and columns.
	 *
	 * @param dialect dialect whose SQL is rendered
	 */
	protected AnsiStatementRenderer(@NotNull Dialect dialect) {
		this.dialect = dialect;
	}

	@Override
	public @NotNull SqlStatement insert(@NotNull TableDefinition table) {
		return new SqlStatement("INSERT INTO " + identifier(table.getName()) + " " + columnList(table) + " VALUES " + markers(table), columns(table));
	}

	@Override
	public @NotNull SqlStatement replace(@NotNull TableDefinition table) {
		List<String> values = valueColumns(table);
		if (values.isEmpty())
			throw new IllegalArgumentException("Table " + table.getName() + " has no column outside its primary key to update");

		List<String> parameters = new ArrayList<>(values);
		parameters.addAll(table.getPrimaryKey());

		return new SqlStatement("UPDATE " + identifier(table.getName()) + " SET " + join(values, ", ", column -> identifier(column) + " = ?")
				+ " WHERE " + keyCondition(table), parameters);
	}

	@Override
	public @NotNull SqlStatement update(@NotNull TableDefinition table, @NotNull List<String> columns, @NotNull Condition condition) {
		if (columns.isEmpty())
			throw new IllegalArgumentException("An update of " + table.getName() + " needs a column to set");
		requireSettable(table, columns);

		List<String> parameters = new ArrayList<>(columns);
		String where = where(table, condition, parameters);

		return new SqlStatement("UPDATE " + identifier(table.getName()) + " SET " + join(columns, ", ", column -> identifier(column) + " = ?")
				+ where, parameters);
	}

	@Override
	public @NotNull SqlStatement select(@NotNull TableDefinition table, @NotNull Selection selection) {
		List<String> parameters = new ArrayList<>();
		String where = where(table, selection.getCondition(), parameters);

		return new SqlStatement("SELECT " + join(columns(table), ", ", this::identifier) + " FROM " + identifier(table.getName())
				+ where + orderBy(table, selection.getOrderings()) + range(selection.getLimit(), selection.getOffset()), parameters);
	}

	@Override
	public @NotNull SqlStatement count(@NotNull TableDefinition table, @NotNull Condition condition) {
		List<String> parameters = new ArrayList<>();
		String where = where(table, condition, parameters);

		return new SqlStatement("SELECT COUNT(*) FROM " + identifier(table.getName()) + where, parameters);
	}

	@Override
	public @NotNull SqlStatement exists(@NotNull TableDefinition table, @NotNull Condition condition) {
		List<String> parameters = new ArrayList<>();
		String where = where(table, condition, parameters);

		return new SqlStatement("SELECT 1 FROM " + identifier(table.getName()) + where + range(OptionalLong.of(1), 0), parameters);
	}

	@Override
	public @NotNull SqlStatement delete(@NotNull TableDefinition table, @NotNull Condition condition) {
		List<String> parameters = new ArrayList<>();
		String where = where(table, condition, parameters);

		return new SqlStatement("DELETE FROM " + identifier(table.getName()) + where, parameters);
	}

	/**
	 * Renders the range of a select after its ordering, with a leading space, or nothing for every
	 * row. The ANSI form, which PostgreSQL and H2 speak, is {@code OFFSET n ROWS FETCH FIRST m ROWS
	 * ONLY}, leaving out the part that does not apply.
	 *
	 * @param limit  largest number of rows, or empty for every row
	 * @param offset number of rows to skip
	 * @return the range clause
	 */
	protected @NotNull String range(@NotNull OptionalLong limit, long offset) {
		String skip = offset > 0 ? " OFFSET " + offset + " ROWS" : "";
		if (limit.isEmpty()) return skip;

		return skip + " FETCH FIRST " + limit.getAsLong() + " ROWS ONLY";
	}

	/**
	 * Renders an insert that does nothing when a row holds one of the inserted row's keys, as
	 * {@code INSERT ... ON CONFLICT DO NOTHING}, as PostgreSQL and SQLite write it.
	 *
	 * @param table table to insert into
	 * @return the statement, parameterized by every column in table order
	 */
	protected final @NotNull SqlStatement insertOnConflictDoingNothing(@NotNull TableDefinition table) {
		return new SqlStatement(insert(table).getSql() + " ON CONFLICT DO NOTHING", columns(table));
	}

	/**
	 * Renders an upsert as PostgreSQL and SQLite write it: for a table whose primary key is
	 * its only unique key, {@code INSERT ... ON CONFLICT (key) DO UPDATE SET column = excluded.column},
	 * or with {@code DO NOTHING} when no column is given; for any other table, an
	 * {@code INSERT ... ON CONFLICT DO NOTHING} followed by an update.
	 *
	 * @param table   table to write to
	 * @param columns columns outside the primary key to update
	 * @return the upsert
	 * @throws IllegalArgumentException when the columns cannot be updated
	 */
	protected final @NotNull Upsert insertOnConflict(@NotNull TableDefinition table, @NotNull List<String> columns) {
		requireUpsert(table, columns);
		if (hasOtherUniqueKeys(table)) return insertThenUpdate(table, columns, insertOnConflictDoingNothing(table));
		String action = columns.isEmpty()
				? "DO NOTHING"
				: "DO UPDATE SET " + join(columns, ", ", column -> identifier(column) + " = excluded." + identifier(column));

		return new Upsert.OnConflict(new SqlStatement(insert(table).getSql() + " ON CONFLICT " + identifiers(table.getPrimaryKey()) + " " + action, columns(table)));
	}

	/**
	 * Renders an upsert as an insert and the update of the given columns of the row with the
	 * primary key, for tables and dialects whose single statement could resolve a conflict on
	 * another unique key, or a concurrent one, wrongly. Without columns, the update sets the first
	 * key column to itself, which only tells whether the row exists.
	 *
	 * @param table   table to write to
	 * @param columns columns outside the primary key to update
	 * @param insert  the insert, which skips the row or fails on a key conflict, parameterized by
	 *                every column in table order
	 * @return the upsert, an insert and an update
	 * @throws IllegalArgumentException when the columns cannot be updated
	 */
	protected final @NotNull Upsert insertThenUpdate(@NotNull TableDefinition table, @NotNull List<String> columns, @NotNull SqlStatement insert) {
		requireUpsert(table, columns);
		String assignments = columns.isEmpty()
				? identifier(table.getPrimaryKey().get(0)) + " = " + identifier(table.getPrimaryKey().get(0))
				: join(columns, ", ", column -> identifier(column) + " = ?");
		List<String> parameters = new ArrayList<>(columns);
		parameters.addAll(table.getPrimaryKey());

		return new Upsert.InsertThenUpdate(insert, new SqlStatement("UPDATE " + identifier(table.getName()) + " SET " + assignments
				+ " WHERE " + keyCondition(table), parameters));
	}

	/**
	 * Tells whether a table has a unique key or unique index besides its primary key.
	 *
	 * @param table table
	 * @return whether another key can conflict with an insert
	 */
	protected static boolean hasOtherUniqueKeys(@NotNull TableDefinition table) {
		return !table.getUniqueKeys().isEmpty() || table.getIndexes().stream().anyMatch(index -> index.isUnique());
	}

	/**
	 * Refuses an upsert of a table without a primary key, or of columns that cannot be updated.
	 *
	 * @param table   table to write to
	 * @param columns columns outside the primary key to update
	 * @throws IllegalArgumentException when the table has no primary key, a column belongs to the
	 *                                  key, a column is not one of the table's, or a column is
	 *                                  given twice
	 */
	protected static void requireUpsert(@NotNull TableDefinition table, @NotNull List<String> columns) {
		requireKey(table);
		requireSettable(table, columns);
	}

	/**
	 * Writes a name as the dialect does.
	 *
	 * @param name name as declared
	 * @return the name as written in SQL
	 */
	protected final @NotNull String identifier(@NotNull String name) {
		return dialect.identifier(name);
	}

	/**
	 * Writes names as a parenthesized, comma-separated list.
	 *
	 * @param names names as declared
	 * @return the list as written in SQL
	 */
	protected final @NotNull String identifiers(@NotNull List<String> names) {
		return "(" + join(names, ", ", this::identifier) + ")";
	}

	/**
	 * Returns the columns of a table in order.
	 *
	 * @param table table
	 * @return the column names
	 */
	protected static @NotNull List<String> columns(@NotNull TableDefinition table) {
		return table.getColumns().stream().map(ColumnDefinition::getName).toList();
	}

	/**
	 * Returns the columns outside the primary key, in order.
	 *
	 * @param table table
	 * @return the column names outside the key
	 */
	protected static @NotNull List<String> valueColumns(@NotNull TableDefinition table) {
		return columns(table).stream().filter(column -> !table.getPrimaryKey().contains(column)).toList();
	}

	/**
	 * Returns a parenthesized list of one {@code ?} per column.
	 *
	 * @param table table
	 * @return the parameter markers
	 */
	protected static @NotNull String markers(@NotNull TableDefinition table) {
		return "(" + join(columns(table), ", ", column -> "?") + ")";
	}

	/**
	 * Refuses a table without a primary key.
	 *
	 * @param table table
	 * @throws IllegalArgumentException when the table has no primary key
	 */
	protected static void requireKey(@NotNull TableDefinition table) {
		if (table.getPrimaryKey().isEmpty())
			throw new IllegalArgumentException("Table " + table.getName() + " has no primary key");
	}

	/**
	 * Joins names after writing each one.
	 *
	 * @param names     names to join
	 * @param separator separator between them
	 * @param writer    writes one name
	 * @return the joined text
	 */
	protected static @NotNull String join(@NotNull List<String> names, @NotNull String separator, @NotNull Function<String, String> writer) {
		StringJoiner joiner = new StringJoiner(separator);
		for (String name : names)
			joiner.add(writer.apply(name));

		return joiner.toString();
	}

	private static void requireSettable(TableDefinition table, List<String> names) {
		Set<String> seen = new HashSet<>();
		for (String name : names) {
			requireColumn(table, name);
			if (!seen.add(name))
				throw new IllegalArgumentException("Column " + name + " of " + table.getName() + " is set twice");
			if (table.getPrimaryKey().contains(name))
				throw new IllegalArgumentException("Column " + name + " of " + table.getName() + " belongs to the primary key and cannot be set");
		}
	}

	private static ColumnDefinition requireColumn(TableDefinition table, String name) {
		ColumnDefinition column = table.column(name);
		if (column == null)
			throw new IllegalArgumentException("Table " + table.getName() + " has no column " + name);

		return column;
	}

	private String columnList(TableDefinition table) {
		return identifiers(columns(table));
	}

	private String keyCondition(TableDefinition table) {
		requireKey(table);

		return join(table.getPrimaryKey(), " AND ", column -> identifier(column) + " = ?");
	}

	private String where(TableDefinition table, Condition condition, List<String> parameters) {
		return " WHERE " + condition.accept(new ConditionWriter(table, this::identifier, parameters));
	}

	private String orderBy(TableDefinition table, List<Ordering> orderings) {
		if (orderings.isEmpty()) return "";

		StringJoiner joiner = new StringJoiner(", ", " ORDER BY ", "");
		for (Ordering ordering : orderings) {
			ConditionWriter.requireOrdered(table, ordering.getColumn(), "ordered by");
			joiner.add(identifier(ordering.getColumn()) + (ordering.getDirection() == Ordering.Direction.ASCENDING ? " ASC" : " DESC"));
		}

		return joiner.toString();
	}
}
