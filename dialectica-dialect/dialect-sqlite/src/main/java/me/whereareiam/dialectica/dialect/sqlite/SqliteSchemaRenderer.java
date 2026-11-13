package me.whereareiam.dialectica.dialect.sqlite;

import me.whereareiam.dialectica.Dialect;
import me.whereareiam.dialectica.common.dialect.AnsiSchemaRenderer;
import me.whereareiam.dialectica.exception.UnsupportedChangeException;
import me.whereareiam.dialectica.schema.ColumnDefinition;
import me.whereareiam.dialectica.schema.change.AddColumn;
import me.whereareiam.dialectica.schema.type.ColumnType;
import org.jetbrains.annotations.NotNull;

/**
 * Renders SQLite's SQL. SQLite stores booleans as the integers 1 and 0 and UUIDs as text. It adds
 * a column that refuses {@code NULL} only with a default, and drops a column only when no key or
 * index covers it; anything else would need the table to be rebuilt.
 */
public final class SqliteSchemaRenderer extends AnsiSchemaRenderer {
	/**
	 * Creates the renderer of a dialect.
	 *
	 * @param dialect dialect whose SQL is rendered
	 */
	public SqliteSchemaRenderer(@NotNull Dialect dialect) {
		super(dialect);
	}

	@Override
	protected @NotNull String type(@NotNull ColumnType type) {
		if (type instanceof ColumnType.VarcharType varchar) return "VARCHAR(" + varchar.length() + ")";
		if (type instanceof ColumnType.UuidType || type instanceof ColumnType.TextType) return "TEXT";
		if (type instanceof ColumnType.InstantType || type instanceof ColumnType.DateType) return "TEXT";
		if (type instanceof ColumnType.BigintType || type instanceof ColumnType.IntegerType) return "INTEGER";
		if (type instanceof ColumnType.BoolType) return "INTEGER";

		throw new IllegalArgumentException("Unknown column type " + type);
	}

	@Override
	protected @NotNull String addColumn(@NotNull AddColumn change) {
		ColumnDefinition column = change.getColumn();
		if (!column.isNullable() && column.getDefaultValue() == null)
			throw new UnsupportedChangeException(dialect(), change, "SQLite adds a NOT NULL column only with a default");

		return super.addColumn(change);
	}

	@Override
	public boolean dropsCoveredColumns() {
		return false;
	}

	@Override
	protected @NotNull String bool(boolean truth) {
		return truth ? "1" : "0";
	}
}
