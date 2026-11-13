package me.whereareiam.dialectica.dialect.mysql;

import me.whereareiam.dialectica.Dialect;
import me.whereareiam.dialectica.common.dialect.AnsiSchemaRenderer;
import me.whereareiam.dialectica.schema.change.DropIndex;
import me.whereareiam.dialectica.schema.type.ColumnType;
import org.jetbrains.annotations.NotNull;

/**
 * Renders MySQL's schema SQL. MySQL keeps index names per table, stores UUIDs as
 * {@code CHAR(36)} and reads a backslash in a string literal as an escape. It
 * accepts the default of a {@code TEXT} column only as an expression, so that default is written in
 * parentheses, which needs MySQL 8.0.13 or newer.
 */
public class MysqlSchemaRenderer extends AnsiSchemaRenderer {
	/**
	 * Creates the renderer of a dialect.
	 *
	 * @param dialect dialect whose SQL is rendered
	 */
	public MysqlSchemaRenderer(@NotNull Dialect dialect) {
		super(dialect);
	}

	@Override
	protected @NotNull String type(@NotNull ColumnType type) {
		if (type instanceof ColumnType.UuidType) return "CHAR(36)";
		if (type instanceof ColumnType.VarcharType varchar) return "VARCHAR(" + varchar.length() + ")";
		if (type instanceof ColumnType.TextType) return "TEXT";
		if (type instanceof ColumnType.BigintType) return "BIGINT";
		if (type instanceof ColumnType.IntegerType) return "INT";
		if (type instanceof ColumnType.BoolType) return "BOOLEAN";
		if (type instanceof ColumnType.InstantType) return "DATETIME(6)";
		if (type instanceof ColumnType.DateType) return "DATE";

		throw new IllegalArgumentException("Unknown column type " + type);
	}

	@Override
	protected @NotNull String dropIndex(@NotNull DropIndex change) {
		return "DROP INDEX " + identifier(change.getIndex()) + " ON " + identifier(change.getTable());
	}

	@Override
	protected @NotNull String defaultValue(@NotNull ColumnType type, @NotNull Object value) {
		String literal = super.defaultValue(type, value);

		return type instanceof ColumnType.TextType ? "(" + literal + ")" : literal;
	}

	@Override
	protected @NotNull String string(@NotNull String text) {
		return super.string(text.replace("\\", "\\\\"));
	}

}
