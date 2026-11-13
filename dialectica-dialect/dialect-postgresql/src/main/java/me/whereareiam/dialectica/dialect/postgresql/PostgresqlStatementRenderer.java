package me.whereareiam.dialectica.dialect.postgresql;

import me.whereareiam.dialectica.Dialect;
import me.whereareiam.dialectica.common.dialect.AnsiStatementRenderer;
import me.whereareiam.dialectica.dialect.SqlStatement;
import me.whereareiam.dialectica.dialect.Upsert;
import me.whereareiam.dialectica.schema.TableDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Renders PostgreSQL's entity statements; an insert if absent and an upsert are an
 * {@code INSERT ... ON CONFLICT}, and a select's range is the ANSI
 * {@code OFFSET n ROWS FETCH FIRST m ROWS ONLY}.
 */
public final class PostgresqlStatementRenderer extends AnsiStatementRenderer {
	/**
	 * Creates the renderer of a dialect.
	 *
	 * @param dialect dialect whose SQL is rendered
	 */
	public PostgresqlStatementRenderer(@NotNull Dialect dialect) {
		super(dialect);
	}

	@Override
	public @NotNull SqlStatement insertIfAbsent(@NotNull TableDefinition table) {
		return insertOnConflictDoingNothing(table);
	}

	@Override
	public @NotNull Upsert upsert(@NotNull TableDefinition table, @NotNull List<String> columns) {
		return insertOnConflict(table, columns);
	}
}
