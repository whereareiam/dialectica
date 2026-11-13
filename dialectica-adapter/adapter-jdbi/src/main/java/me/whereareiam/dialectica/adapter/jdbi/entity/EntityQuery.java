package me.whereareiam.dialectica.adapter.jdbi.entity;

import me.whereareiam.dialectica.dialect.Ordering;
import me.whereareiam.dialectica.dialect.Selection;
import me.whereareiam.dialectica.dialect.SqlStatement;
import me.whereareiam.dialectica.entity.Order;
import me.whereareiam.dialectica.entity.Where;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.result.ResultIterable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * A query of the entities whose rows meet a condition, started by {@link EntityAccess#query(Where)}.
 * It can order the rows, skip some and limit how many it reads, and runs when one of its terminal
 * methods is called:
 * <pre>{@code
 * List<StoreEntry> page = entries.query(StoreEntryColumns.STORE_NAME.is(store))
 *         .orderBy(StoreEntryColumns.ENTRY_KEY.ascending())
 *         .offset(40)
 *         .limit(20)
 *         .list();
 * boolean taken = accounts.query(AccountColumns.USERNAME.is(name)).exists();
 * }</pre>
 * A query is an immutable value: {@link #orderBy(Order[]) orderBy}, {@link #limit(long) limit}
 * and {@link #offset(long) offset} return a changed copy, and a query can run any number of times.
 * Without an order, the database returns rows in an order of its choosing, which can differ between
 * runs; a page read with an offset needs an order to be stable.
 *
 * @param <T> entity type
 */
public final class EntityQuery<T> {
	private final EntityStatements<T> statements;
	private final Where<T> where;
	private final Selection selection;

	EntityQuery(EntityStatements<T> statements, Where<T> where) {
		this(statements, where, Selection.of(where.condition()));
	}

	private EntityQuery(EntityStatements<T> statements, Where<T> where, Selection selection) {
		this.statements = statements;
		this.where = where;
		this.selection = selection;
	}

	/**
	 * Returns this query ordering its rows by columns, the first order deciding first. It replaces
	 * the orders given before.
	 *
	 * @param orders orders of the table's columns, from {@link me.whereareiam.dialectica.entity.EntityColumn#ascending()}
	 *               and {@link me.whereareiam.dialectica.entity.EntityColumn#descending()}
	 * @return the ordered query
	 * @throws IllegalArgumentException when an order's column is not one of the table's
	 */
	@SafeVarargs
	public final @NotNull EntityQuery<T> orderBy(@NotNull Order<T>... orders) {
		List<Ordering> orderings = new ArrayList<>();
		for (Order<T> order : Objects.requireNonNull(orders, "orders")) {
			statements.table().column(Objects.requireNonNull(order, "order").column());
			orderings.add(order.ordering());
		}

		return new EntityQuery<>(statements, where, selection.withOrderings(orderings));
	}

	/**
	 * Returns this query reading at most a number of rows.
	 *
	 * @param limit largest number of rows, at least one
	 * @return the limited query
	 * @throws IllegalArgumentException when the limit is below one
	 */
	public @NotNull EntityQuery<T> limit(long limit) {
		return new EntityQuery<>(statements, where, selection.withLimit(limit));
	}

	/**
	 * Returns this query skipping a number of rows before the first one it reads.
	 *
	 * @param offset number of rows to skip, at least zero
	 * @return the query
	 * @throws IllegalArgumentException when the offset is negative
	 */
	public @NotNull EntityQuery<T> offset(long offset) {
		return new EntityQuery<>(statements, where, selection.withOffset(offset));
	}

	/**
	 * Reads the entities.
	 *
	 * @return the entities, in the query's order
	 */
	public @NotNull List<T> list() {
		return statements.withHandle(handle -> rows(handle, selection).list());
	}

	/**
	 * Reads the first entity, in the query's order and after its offset.
	 *
	 * @return the entity, or empty when no row meets the condition
	 */
	public @NotNull Optional<T> first() {
		return statements.withHandle(handle -> rows(handle, selection.withLimit(1)).findFirst());
	}

	/**
	 * Counts the rows that meet the condition. Order, limit and offset do not apply to the count.
	 *
	 * @return the number of rows
	 */
	public long count() {
		SqlStatement statement = statements.renderer().count(statements.table().table(), where.condition());

		return statements.withHandle(handle -> EntityStatements.bind(handle.createQuery(statement.getSql()), statements.arguments(where))
				.mapTo(Long.class)
				.one());
	}

	/**
	 * Tells whether a row meets the condition. Order, limit and offset do not apply.
	 *
	 * @return whether such a row exists
	 */
	public boolean exists() {
		SqlStatement statement = statements.renderer().exists(statements.table().table(), where.condition());

		return statements.withHandle(handle -> EntityStatements.bind(handle.createQuery(statement.getSql()), statements.arguments(where))
				.map((row, context) -> true)
				.findFirst()
				.isPresent());
	}

	/**
	 * Hands the entities to a consumer as a stream that reads rows as the consumer pulls them, for
	 * more entities than fit in memory at once. The stream is open only while the consumer runs,
	 * on the handle the query runs on, and is closed afterwards.
	 * <pre>{@code
	 * entries.query(StoreEntryColumns.STORE_NAME.is(store))
	 *         .stream(stream -> stream.forEach(entry -> cache.put(entry.entryKey(), entry)));
	 * }</pre>
	 *
	 * @param consumer consumer of the stream, in the query's order
	 */
	public void stream(@NotNull Consumer<Stream<T>> consumer) {
		Objects.requireNonNull(consumer, "consumer");
		statements.withHandle(handle -> {
			try (Stream<T> stream = rows(handle, selection).stream()) {
				consumer.accept(stream);
			}

			return null;
		});
	}

	private ResultIterable<T> rows(Handle handle, Selection rows) {
		SqlStatement statement = statements.renderer().select(statements.table().table(), rows);

		return EntityStatements.bind(handle.createQuery(statement.getSql()), statements.arguments(where)).map(statements.mapper());
	}
}
