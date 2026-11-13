package me.whereareiam.dialectica.dialect.h2;

import me.whereareiam.dialectica.Dialect;
import me.whereareiam.dialectica.common.dialect.AnsiSchemaRenderer;
import me.whereareiam.dialectica.schema.type.ColumnType;
import org.jetbrains.annotations.NotNull;

/**
 * Renders H2's SQL. Unlimited text is a {@code CLOB}, which H2 cannot index.
 */
public final class H2SchemaRenderer extends AnsiSchemaRenderer {
	/**
	 * Creates the renderer of a dialect.
	 *
	 * @param dialect dialect whose SQL is rendered
	 */
	public H2SchemaRenderer(@NotNull Dialect dialect) {
		super(dialect);
	}

	@Override
	protected @NotNull String type(@NotNull ColumnType type) {
		if (type instanceof ColumnType.UuidType) return "UUID";
		if (type instanceof ColumnType.VarcharType varchar) return "VARCHAR(" + varchar.length() + ")";
		if (type instanceof ColumnType.TextType) return "CLOB";
		if (type instanceof ColumnType.BigintType) return "BIGINT";
		if (type instanceof ColumnType.IntegerType) return "INTEGER";
		if (type instanceof ColumnType.BoolType) return "BOOLEAN";
		if (type instanceof ColumnType.InstantType) return "TIMESTAMP WITH TIME ZONE";
		if (type instanceof ColumnType.DateType) return "DATE";

		throw new IllegalArgumentException("Unknown column type " + type);
	}
}
