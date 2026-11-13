package me.whereareiam.dialectica.dialect.sqlite;

import me.whereareiam.dialectica.Dialect;
import me.whereareiam.dialectica.common.dialect.AnsiStatementRenderer;
import me.whereareiam.dialectica.dialect.SqlStatement;
import me.whereareiam.dialectica.dialect.Upsert;
import me.whereareiam.dialectica.schema.TableDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.OptionalLong;

/**
 * Renders SQLite's entity statements; an insert if absent and an upsert are an
 * {@code INSERT ... ON CONFLICT}, and a select's range is {@code LIMIT m OFFSET n}.
 */
public final class SqliteStatementRenderer extends AnsiStatementRenderer {
	/**
	 * Creates the renderer of a dialect.
	 *
	 * @param dialect dialect whose SQL is rendered
	 */
	public SqliteStatementRenderer(@NotNull Dialect dialect) {
		super(dialect);
	}

	@Override
	public @NotNull SqlStatement insertIfAbsent(@NotNull TableDefinition table) {
		return insertOnConflictDoingNothing(table);
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * SQLite writes {@code LIMIT m OFFSET n}. An offset needs a limit, so an offset over every row
	 * limits to {@code -1}, which SQLite reads as no limit.
	 */
	@Override
	protected @NotNull String range(@NotNull OptionalLong limit, long offset) {
		if (limit.isEmpty() && offset == 0) return "";

		String rows = limit.isPresent() ? Long.toString(limit.getAsLong()) : "-1";
		return " LIMIT " + rows + (offset > 0 ? " OFFSET " + offset : "");
	}

	@Override
	public @NotNull Upsert upsert(@NotNull TableDefinition table, @NotNull List<String> columns) {
		return insertOnConflict(table, columns);
	}
}
