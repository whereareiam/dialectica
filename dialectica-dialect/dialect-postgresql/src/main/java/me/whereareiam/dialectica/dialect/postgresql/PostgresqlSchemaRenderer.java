package me.whereareiam.dialectica.dialect.postgresql;

import me.whereareiam.dialectica.Dialect;
import me.whereareiam.dialectica.common.dialect.AnsiSchemaRenderer;
import me.whereareiam.dialectica.schema.type.ColumnType;
import org.jetbrains.annotations.NotNull;

/**
 * Renders PostgreSQL's SQL.
 */
public final class PostgresqlSchemaRenderer extends AnsiSchemaRenderer {
	/**
	 * Creates the renderer of a dialect.
	 *
	 * @param dialect dialect whose SQL is rendered
	 */
	public PostgresqlSchemaRenderer(@NotNull Dialect dialect) {
		super(dialect);
	}

	@Override
	protected @NotNull String type(@NotNull ColumnType type) {
		if (type instanceof ColumnType.UuidType) return "UUID";
		if (type instanceof ColumnType.VarcharType varchar) return "VARCHAR(" + varchar.length() + ")";
		if (type instanceof ColumnType.TextType) return "TEXT";
		if (type instanceof ColumnType.BigintType) return "BIGINT";
		if (type instanceof ColumnType.IntegerType) return "INTEGER";
		if (type instanceof ColumnType.BoolType) return "BOOLEAN";
		if (type instanceof ColumnType.InstantType) return "TIMESTAMP WITH TIME ZONE";
		if (type instanceof ColumnType.DateType) return "DATE";

		throw new IllegalArgumentException("Unknown column type " + type);
	}
}
