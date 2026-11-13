package me.whereareiam.dialectica.adapter.jdbi.entity;

import me.whereareiam.dialectica.dialect.SqlStatement;
import me.whereareiam.dialectica.entity.EntityColumn;
import me.whereareiam.dialectica.entity.EntityTable;
import me.whereareiam.dialectica.entity.Where;
import org.jdbi.v3.core.argument.Argument;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An update of some columns of the rows that meet a condition, started by
 * {@link EntityAccess#update(Object...)} for the row with a primary key or by
 * {@link EntityAccess#update(Where)} for every row that meets a condition. It sets only the columns
 * it names, and with a {@link #when(Where) guard} it changes a row only while the row's current
 * values meet it:
 * <pre>{@code
 * accounts.update(uniqueId).set(AccountColumns.LAST_SEEN_AT, now).execute();
 *
 * boolean used = codes.update(uniqueId, code)
 *         .set(RecoveryCodeColumns.USED_AT, now)
 *         .when(RecoveryCodeColumns.USED_AT.is(0L))
 *         .execute() > 0;
 *
 * int closed = sessions.update(SessionColumns.EXPIRES_AT.atMost(now))
 *         .set(SessionColumns.CLOSED, true)
 *         .execute();
 * }</pre>
 * {@link #execute()} runs one statement, so when several instances run the same guarded update of
 * a row at once, exactly one of them changes it.
 *
 * @param <T> entity type
 */
public final class EntityUpdate<T> {
	private final EntityStatements<T> statements;
	private final List<Assignment<T, ?>> assignments = new ArrayList<>();
	private Where<T> where;

	EntityUpdate(EntityStatements<T> statements, Where<T> where) {
		this.statements = statements;
		this.where = where;
	}

	/**
	 * Sets a column to a value.
	 *
	 * @param column a column of the table, outside the primary key
	 * @param value  value, or {@code null} for a nullable column
	 * @param <V>    Java type of the column's values
	 * @return this update
	 * @throws IllegalArgumentException when the column is not one of the table's, belongs to the key,
	 *                                  is set already, refuses {@code null}, or the value is not of
	 *                                  its Java type
	 */
	public <V> @NotNull EntityUpdate<T> set(@NotNull EntityColumn<T, V> column, @Nullable V value) {
		EntityTable<T> table = statements.table();
		EntityColumn<T, V> own = table.column(Objects.requireNonNull(column, "column"));
		if (table.primaryKey().contains(own))
			throw new IllegalArgumentException("Column " + own.name() + " belongs to the primary key of " + table.table().getName() + " and cannot be set");
		if (value == null && !own.column().isNullable())
			throw new IllegalArgumentException("Column " + own.name() + " of " + table.table().getName() + " is not nullable");
		if (value != null && !own.type().isInstance(value))
			throw new IllegalArgumentException("Column " + own.name() + " takes a " + own.type().getSimpleName() + ", not " + value);
		for (Assignment<T, ?> assignment : assignments)
			if (assignment.column().equals(own)) throw new IllegalArgumentException("Column " + own.name() + " is set already");

		assignments.add(new Assignment<>(own, value));
		return this;
	}

	/**
	 * Lets the update change a row only while its current values meet a condition as well. Given
	 * more than once, every guard has to hold.
	 *
	 * @param guard condition on the row's current values
	 * @return this update
	 * @throws IllegalArgumentException when the condition compares a column that is not one of the
	 *                                  table's
	 */
	public @NotNull EntityUpdate<T> when(@NotNull Where<T> guard) {
		where = where.and(statements.resolve(Objects.requireNonNull(guard, "guard")));

		return this;
	}

	/**
	 * Runs the update as one statement.
	 *
	 * @return the number of rows that met the condition and were updated: for an update by key, 1
	 *         when the row existed and met every guard, 0 otherwise
	 * @throws IllegalStateException when no column is set
	 * @throws me.whereareiam.dialectica.exception.KeyConflictException when the update would give a row a unique key
	 *                                                                  another row holds
	 */
	public int execute() {
		if (assignments.isEmpty())
			throw new IllegalStateException("An update of " + statements.table().table().getName() + " needs a column to set");

		List<String> columns = assignments.stream().map(assignment -> assignment.column().name()).toList();
		SqlStatement statement = statements.renderer().update(statements.table().table(), columns, where.condition());
		List<Argument> arguments = new ArrayList<>();
		for (Assignment<T, ?> assignment : assignments)
			arguments.add(assignment.argument(statements));
		arguments.addAll(statements.arguments(where));

		return statements.withHandle(handle -> statements.write(handle, statement, arguments));
	}

	/**
	 * A column and the value it is set to.
	 */
	private record Assignment<T, V>(EntityColumn<T, V> column, @Nullable V value) {
		Argument argument(EntityStatements<T> statements) {
			return statements.argument(column, value);
		}
	}
}
