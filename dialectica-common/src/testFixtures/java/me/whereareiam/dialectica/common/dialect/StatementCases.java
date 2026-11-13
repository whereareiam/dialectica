package me.whereareiam.dialectica.common.dialect;

import me.whereareiam.dialectica.dialect.Comparison;
import me.whereareiam.dialectica.dialect.Condition;
import me.whereareiam.dialectica.dialect.Ordering;
import me.whereareiam.dialectica.dialect.Selection;
import me.whereareiam.dialectica.dialect.SqlStatement;
import me.whereareiam.dialectica.dialect.StatementRenderer;
import me.whereareiam.dialectica.dialect.Upsert;
import me.whereareiam.dialectica.schema.ColumnDefinition;
import me.whereareiam.dialectica.schema.TableDefinition;
import me.whereareiam.dialectica.schema.type.ColumnType;
import org.junit.jupiter.params.provider.Arguments;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * The entity statements every dialect's statement renderer test renders: {@link #expect} pairs the
 * cases that differ between dialects with the statements a dialect expects, {@link #ranges} the
 * selects whose range differs, {@link #shared()} the statements every dialect renders alike, and
 * {@link #refusals()} the statements every dialect refuses.
 */
public final class StatementCases {
	public static final TableDefinition RESERVATIONS = TableDefinition.builder("identica_account_reservations")
			.column(ColumnDefinition.builder("reservation_key", ColumnType.varchar(192)).notNull().build())
			.column(ColumnDefinition.builder("unique_id", ColumnType.uuid()).notNull().build())
			.column(ColumnDefinition.builder("created_at", ColumnType.bigint()).notNull().build())
			.column(ColumnDefinition.builder("expires_at", ColumnType.bigint()).notNull().build())
			.primaryKey("reservation_key")
			.build();

	public static final TableDefinition LINKS = TableDefinition.builder("identica_provider_links")
			.column(ColumnDefinition.builder("provider_id", ColumnType.varchar(64)).notNull().build())
			.column(ColumnDefinition.builder("provider_subject", ColumnType.varchar(128)).notNull().build())
			.column(ColumnDefinition.builder("unique_id", ColumnType.uuid()).notNull().build())
			.column(ColumnDefinition.builder("is_primary", ColumnType.bool()).notNull().build())
			.primaryKey("provider_id", "provider_subject")
			.build();

	public static final TableDefinition UNIQUE_LINKS = TableDefinition.builder("identica_provider_links")
			.column(ColumnDefinition.builder("provider_id", ColumnType.varchar(64)).notNull().build())
			.column(ColumnDefinition.builder("provider_subject", ColumnType.varchar(128)).notNull().build())
			.column(ColumnDefinition.builder("unique_id", ColumnType.uuid()).notNull().build())
			.column(ColumnDefinition.builder("is_primary", ColumnType.bool()).notNull().build())
			.primaryKey("provider_id", "provider_subject")
			.unique("unique_id", "provider_id")
			.build();

	public static final TableDefinition FLAGS = TableDefinition.builder("identica_flags")
			.column(ColumnDefinition.builder("unique_id", ColumnType.uuid()).notNull().build())
			.column(ColumnDefinition.builder("order", ColumnType.varchar(16)).notNull().build())
			.primaryKey("unique_id", "order")
			.build();

	public static final TableDefinition SETTINGS = TableDefinition.builder("identica_settings")
			.column(ColumnDefinition.builder("unique_id", ColumnType.uuid()).notNull().build())
			.column(ColumnDefinition.builder("order", ColumnType.varchar(16)).build())
			.primaryKey("unique_id")
			.build();

	public static final TableDefinition EVENTS = TableDefinition.builder("identica_account_events")
			.column(ColumnDefinition.builder("event_key", ColumnType.varchar(64)).notNull().build())
			.column(ColumnDefinition.builder("happened_at", ColumnType.instant()).notNull().build())
			.column(ColumnDefinition.builder("due_on", ColumnType.date()).build())
			.primaryKey("event_key")
			.build();

	public static final TableDefinition UNKEYED = TableDefinition.builder("identica_events")
			.column(ColumnDefinition.builder("unique_id", ColumnType.uuid()).build())
			.build();

	private StatementCases() {
	}

	/**
	 * Pairs every case with its expected statement, given in the order of {@link #names()}.
	 */
	public static Stream<Arguments> expect(Object... statements) {
		List<String> names = names();
		List<Function<StatementRenderer, Object>> renders = renders();
		if (statements.length != renders.size())
			throw new IllegalArgumentException("Expected " + renders.size() + " cases, not " + statements.length);

		return IntStream.range(0, renders.size())
				.mapToObj(index -> Arguments.of(names.get(index), renders.get(index), statements[index]));
	}

	/**
	 * Creates the comparison of a column.
	 */
	public static Condition condition(String column, Comparison comparison) {
		return new Condition.Comparing(column, comparison);
	}

	/**
	 * Creates the condition that the primary key of a table holds values, as entity access does.
	 */
	public static Condition key(TableDefinition table) {
		return all(table.getPrimaryKey().stream().map(column -> condition(column, Comparison.EQUALS)).toArray(Condition[]::new));
	}

	/**
	 * Creates the condition that the primary key of a table holds values and other conditions hold.
	 */
	public static Condition key(TableDefinition table, Condition... conditions) {
		List<Condition> all = new ArrayList<>(((Condition.All) key(table)).conditions());
		all.addAll(List.of(conditions));

		return new Condition.All(all);
	}

	/**
	 * Creates the condition that every given condition holds.
	 */
	public static Condition all(Condition... conditions) {
		return new Condition.All(List.of(conditions));
	}

	/**
	 * Creates the condition that one of the given conditions holds.
	 */
	public static Condition any(Condition... conditions) {
		return new Condition.Any(List.of(conditions));
	}

	/**
	 * Creates an expected upsert of one statement.
	 */
	public static Upsert onConflict(String sql, String... parameters) {
		return new Upsert.OnConflict(statement(sql, parameters));
	}

	/**
	 * Creates an expected upsert of an insert and an update.
	 */
	public static Upsert insertThenUpdate(SqlStatement insert, SqlStatement update) {
		return new Upsert.InsertThenUpdate(insert, update);
	}

	/**
	 * Creates an expected statement.
	 */
	public static SqlStatement statement(String sql, String... parameters) {
		return new SqlStatement(sql, List.of(parameters));
	}

	private static List<String> names() {
		return List.of(
				"insert reservation", "upsert reservation", "replace reservation", "select reservation", "delete reservation",
				"insert link", "upsert link", "replace link", "select link", "delete link",
				"upsert key-only table", "select quoted column",
				"update reservation conditionally", "update link where a column is null", "update quoted column conditionally",
				"update reservation within ordered conditions",
				"insert reservation if absent", "upsert some columns of a link with another unique key"
		);
	}

	private static List<Function<StatementRenderer, Object>> renders() {
		return List.of(
				renderer -> renderer.insert(RESERVATIONS),
				renderer -> renderer.upsert(RESERVATIONS, List.of("unique_id", "created_at", "expires_at")),
				renderer -> renderer.replace(RESERVATIONS),
				renderer -> renderer.select(RESERVATIONS, Selection.of(key(RESERVATIONS))),
				renderer -> renderer.delete(RESERVATIONS, key(RESERVATIONS)),
				renderer -> renderer.insert(LINKS),
				renderer -> renderer.upsert(LINKS, List.of("is_primary")),
				renderer -> renderer.replace(LINKS),
				renderer -> renderer.select(LINKS, Selection.of(key(LINKS))),
				renderer -> renderer.delete(LINKS, key(LINKS)),
				renderer -> renderer.upsert(FLAGS, List.of()),
				renderer -> renderer.select(FLAGS, Selection.of(key(FLAGS))),
				renderer -> renderer.update(RESERVATIONS, List.of("unique_id", "expires_at"), key(RESERVATIONS, condition("expires_at", Comparison.EQUALS))),
				renderer -> renderer.update(LINKS, List.of("is_primary"), key(LINKS, condition("unique_id", Comparison.IS_NULL))),
				renderer -> renderer.update(SETTINGS, List.of("order"), key(SETTINGS, condition("order", Comparison.EQUALS))),
				renderer -> renderer.update(RESERVATIONS, List.of("expires_at"), key(RESERVATIONS,
						condition("expires_at", Comparison.BELOW),
						condition("expires_at", Comparison.ABOVE),
						condition("created_at", Comparison.AT_MOST),
						condition("created_at", Comparison.AT_LEAST)
				)),
				renderer -> renderer.insertIfAbsent(RESERVATIONS),
				renderer -> renderer.upsert(UNIQUE_LINKS, List.of("is_primary"))
		);
	}

	/**
	 * Pairs the selects whose range differs between dialects with the statements a dialect
	 * expects: a page that is ordered, limited and offset; a limit alone; an offset alone; and an
	 * exists.
	 */
	public static Stream<Arguments> ranges(SqlStatement page, SqlStatement limited, SqlStatement skipping, SqlStatement exists) {
		Condition expiring = condition("expires_at", Comparison.AT_MOST);
		Selection selection = Selection.of(expiring)
				.withOrderings(List.of(new Ordering("expires_at", Ordering.Direction.ASCENDING), new Ordering("reservation_key", Ordering.Direction.DESCENDING)));

		return Stream.of(
				Arguments.of("select a page", (Function<StatementRenderer, Object>) renderer -> renderer.select(RESERVATIONS, selection.withLimit(20).withOffset(40)), page),
				Arguments.of("select a limited number", (Function<StatementRenderer, Object>) renderer -> renderer.select(RESERVATIONS, Selection.of(expiring).withLimit(5)), limited),
				Arguments.of("select skipping rows", (Function<StatementRenderer, Object>) renderer -> renderer.select(RESERVATIONS, Selection.of(expiring).withOffset(3)), skipping),
				Arguments.of("tell whether a row exists", (Function<StatementRenderer, Object>) renderer -> renderer.exists(RESERVATIONS, expiring), exists)
		);
	}

	/**
	 * Pairs the statements every dialect renders alike, because they name no quoted column and
	 * have no range, with the statement they render to.
	 */
	public static Stream<Arguments> shared() {
		Condition every = all(
				any(new Condition.In("unique_id", 2), new Condition.Not(condition("expires_at", Comparison.NOT_EQUALS))),
				condition("created_at", Comparison.IS_NOT_NULL),
				new Condition.Not(all(condition("unique_id", Comparison.EQUALS), condition("created_at", Comparison.AT_LEAST)))
		);
		String where = " WHERE (unique_id IN (?, ?) OR NOT (expires_at <> ?)) AND created_at IS NOT NULL AND NOT (unique_id = ? AND created_at >= ?)";
		String[] parameters = {"unique_id", "unique_id", "expires_at", "unique_id", "created_at"};

		return Stream.of(
				Arguments.of("select by every kind of condition", (Function<StatementRenderer, Object>) renderer -> renderer.select(RESERVATIONS, Selection.of(every)),
						statement("SELECT reservation_key, unique_id, created_at, expires_at FROM identica_account_reservations" + where, parameters)),
				Arguments.of("count by a condition", (Function<StatementRenderer, Object>) renderer -> renderer.count(RESERVATIONS, every),
						statement("SELECT COUNT(*) FROM identica_account_reservations" + where, parameters)),
				Arguments.of("delete by a condition", (Function<StatementRenderer, Object>) renderer -> renderer.delete(RESERVATIONS, condition("expires_at", Comparison.BELOW)),
						statement("DELETE FROM identica_account_reservations WHERE expires_at < ?", "expires_at")),
				Arguments.of("update by a condition", (Function<StatementRenderer, Object>) renderer -> renderer.update(RESERVATIONS, List.of("expires_at"),
								any(condition("expires_at", Comparison.ABOVE), condition("unique_id", Comparison.IS_NULL))),
						statement("UPDATE identica_account_reservations SET expires_at = ? WHERE expires_at > ? OR unique_id IS NULL", "expires_at", "expires_at")),
				Arguments.of("compare and order instants and dates", (Function<StatementRenderer, Object>) renderer -> renderer.select(EVENTS,
								Selection.of(all(condition("happened_at", Comparison.AT_LEAST), condition("due_on", Comparison.BELOW)))
										.withOrderings(List.of(new Ordering("happened_at", Ordering.Direction.DESCENDING), new Ordering("due_on", Ordering.Direction.ASCENDING)))),
						statement("SELECT event_key, happened_at, due_on FROM identica_account_events WHERE happened_at >= ? AND due_on < ? ORDER BY happened_at DESC, due_on ASC",
								"happened_at", "due_on")),
				Arguments.of("select a table without a primary key", (Function<StatementRenderer, Object>) renderer -> renderer.select(UNKEYED, Selection.of(condition("unique_id", Comparison.IS_NULL))),
						statement("SELECT unique_id FROM identica_events WHERE unique_id IS NULL")),
				Arguments.of("update a table without a primary key", (Function<StatementRenderer, Object>) renderer -> renderer.update(UNKEYED, List.of("unique_id"), condition("unique_id", Comparison.EQUALS)),
						statement("UPDATE identica_events SET unique_id = ? WHERE unique_id = ?", "unique_id", "unique_id"))
		);
	}

	/**
	 * Returns, by name, the statements every dialect refuses with an
	 * {@link IllegalArgumentException}.
	 */
	public static Stream<Arguments> refusals() {
		Selection unordered = Selection.of(condition("expires_at", Comparison.BELOW));

		return Stream.of(
				refusal("upsert a table without a primary key", renderer -> renderer.upsert(UNKEYED, List.of())),
				refusal("upsert a key column", renderer -> renderer.upsert(RESERVATIONS, List.of("reservation_key"))),
				refusal("upsert an unknown column", renderer -> renderer.upsert(RESERVATIONS, List.of("missing"))),
				refusal("upsert a column twice", renderer -> renderer.upsert(RESERVATIONS, List.of("expires_at", "expires_at"))),
				refusal("replace a table whose columns all belong to the key", renderer -> renderer.replace(FLAGS)),
				refusal("update no column", renderer -> renderer.update(RESERVATIONS, List.of(), key(RESERVATIONS))),
				refusal("update a key column", renderer -> renderer.update(RESERVATIONS, List.of("reservation_key"), key(RESERVATIONS))),
				refusal("update an unknown column", renderer -> renderer.update(RESERVATIONS, List.of("missing"), key(RESERVATIONS))),
				refusal("update a column twice", renderer -> renderer.update(RESERVATIONS, List.of("expires_at", "expires_at"), key(RESERVATIONS))),
				refusal("compare an unknown column", renderer -> renderer.delete(RESERVATIONS, condition("missing", Comparison.EQUALS))),
				refusal("look for an unknown column in values", renderer -> renderer.count(RESERVATIONS, new Condition.In("missing", 1))),
				refusal("order a UUID column", renderer -> renderer.update(RESERVATIONS, List.of("expires_at"), condition("unique_id", Comparison.BELOW))),
				refusal("order a boolean column", renderer -> renderer.select(LINKS, Selection.of(condition("is_primary", Comparison.ABOVE)))),
				refusal("order by a UUID column", renderer -> renderer.select(RESERVATIONS, unordered.withOrderings(List.of(new Ordering("unique_id", Ordering.Direction.ASCENDING))))),
				refusal("order by an unknown column", renderer -> renderer.select(RESERVATIONS, unordered.withOrderings(List.of(new Ordering("missing", Ordering.Direction.DESCENDING)))))
		);
	}

	private static Arguments refusal(String name, Function<StatementRenderer, Object> render) {
		return Arguments.of(name, render);
	}
}
