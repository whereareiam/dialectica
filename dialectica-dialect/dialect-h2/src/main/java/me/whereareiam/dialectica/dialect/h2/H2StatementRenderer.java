package me.whereareiam.dialectica.dialect.h2;

import me.whereareiam.dialectica.Dialect;
import me.whereareiam.dialectica.common.dialect.AnsiStatementRenderer;
import me.whereareiam.dialectica.dialect.SqlStatement;
import me.whereareiam.dialectica.dialect.Upsert;
import me.whereareiam.dialectica.schema.TableDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Renders H2's entity statements. H2 in its default mode has no insert that skips or updates a row
 * on a conflict, and its {@code MERGE INTO ... KEY} writes every column it lists, so an insert if
 * absent is a plain insert and an upsert an insert followed by an update. A select's range is the
 * ANSI {@code OFFSET n ROWS FETCH FIRST m ROWS ONLY}.
 */
public final class H2StatementRenderer extends AnsiStatementRenderer {
	/**
	 * Creates the renderer of a dialect.
	 *
	 * @param dialect dialect whose SQL is rendered
	 */
	public H2StatementRenderer(@NotNull Dialect dialect) {
		super(dialect);
	}

	@Override
	public @NotNull SqlStatement insertIfAbsent(@NotNull TableDefinition table) {
		return insert(table);
	}

	@Override
	public @NotNull Upsert upsert(@NotNull TableDefinition table, @NotNull List<String> columns) {
		return insertThenUpdate(table, columns, insert(table));
	}
}
