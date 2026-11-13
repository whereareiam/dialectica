package me.whereareiam.dialectica.adapter.jdbi.entity;

import me.whereareiam.dialectica.Dialect;
import me.whereareiam.dialectica.adapter.jdbi.DialecticaConfig;
import me.whereareiam.dialectica.dialect.SqlStatement;
import me.whereareiam.dialectica.dialect.StatementRenderer;
import me.whereareiam.dialectica.entity.EntityColumn;
import me.whereareiam.dialectica.entity.EntityTable;
import me.whereareiam.dialectica.entity.Where;
import me.whereareiam.dialectica.exception.KeyConflictException;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.HandleCallback;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.argument.Argument;
import org.jdbi.v3.core.statement.StatementException;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * What the operations of an {@link EntityAccess} share: the table, the dialect's renderer, the
 * handles the statements run on, and binding values the way the dialect stores them.
 *
 * @param <T> entity type
 */
final class EntityStatements<T> {
	private final EntityTable<T> table;
	private final Dialect dialect;
	private final EntityRowMapper<T> mapper;
	private final Handles handles;

	EntityStatements(Jdbi jdbi, EntityTable<T> table) {
		this(table, jdbi.getConfig(DialecticaConfig.class).dialect(), new EntityRowMapper<>(table), jdbi::withHandle);
	}

	private EntityStatements(EntityTable<T> table, Dialect dialect, EntityRowMapper<T> mapper, Handles handles) {
		this.table = table;
		this.dialect = dialect;
		this.mapper = mapper;
		this.handles = handles;
	}

	/**
	 * Returns the same statements, run on one handle.
	 */
	EntityStatements<T> on(Handle handle) {
		return new EntityStatements<>(table, dialect, mapper, new Handles() {
			@Override
			public <R> R with(HandleCallback<R, RuntimeException> callback) {
				return callback.withHandle(handle);
			}
		});
	}

	/**
	 * Runs a callback on a handle: a new one of the {@code Jdbi}, or the one this is bound to.
	 */
	<R> R withHandle(HandleCallback<R, RuntimeException> callback) {
		return handles.with(callback);
	}

	EntityTable<T> table() {
		return table;
	}

	StatementRenderer renderer() {
		return dialect.statementRenderer();
	}

	EntityRowMapper<T> mapper() {
		return mapper;
	}

	/**
	 * Builds the condition that the primary key holds the given values.
	 *
	 * @throws IllegalStateException    when the table has no primary key
	 * @throws IllegalArgumentException when the values do not fit the key's columns
	 */
	Where<T> key(String operation, Object[] values) {
		List<EntityColumn<T, ?>> key = table.primaryKey();
		if (key.isEmpty())
			throw new IllegalStateException("Cannot " + operation + " a row of " + table.table().getName() + " by its key: it has no primary key");
		if (values.length != key.size())
			throw new IllegalArgumentException(table.table().getName() + " has a key of " + key.size() + " columns, not " + values.length);

		Where<T> where = is(key.get(0), values[0]);
		for (int index = 1; index < values.length; index++)
			where = where.and(is(key.get(index), values[index]));

		return where;
	}

	/**
	 * Checks that every column a condition compares is one of the table's.
	 *
	 * @throws IllegalArgumentException when a column is not
	 */
	Where<T> resolve(Where<T> where) {
		where.columns().forEach(table::column);

		return where;
	}

	/**
	 * Returns the arguments of a condition, in the order of its parameters.
	 */
	List<Argument> arguments(Where<T> where) {
		List<Argument> arguments = new ArrayList<>();
		for (Where.Parameter<T, ?> parameter : where.parameters())
			arguments.add(argument(parameter));

		return arguments;
	}

	/**
	 * Returns the arguments of a statement that writes an entity, one column value per parameter.
	 */
	List<Argument> row(SqlStatement statement, T entity) {
		List<Argument> arguments = new ArrayList<>();
		for (String name : statement.getParameters())
			arguments.add(value(table.column(name), entity));

		return arguments;
	}

	/**
	 * Binds a value of a column as the dialect stores it.
	 */
	<V> Argument argument(EntityColumn<T, V> column, @Nullable V value) {
		return (position, statement, context) -> dialect.bind(statement, position, column.column().getType(), value);
	}

	/**
	 * Runs a statement that writes rows.
	 *
	 * @return the number of rows the statement matched
	 */
	int execute(Handle handle, SqlStatement statement, List<Argument> arguments) {
		return bind(handle.createUpdate(statement.getSql()), arguments).execute();
	}

	/**
	 * Runs a statement that writes rows, reporting a taken unique key as a
	 * {@link KeyConflictException}.
	 *
	 * @return the number of rows the statement matched
	 */
	int write(Handle handle, SqlStatement statement, List<Argument> arguments) {
		try {
			return execute(handle, statement, arguments);
		} catch (StatementException e) {
			if (isKeyConflict(e)) throw new KeyConflictException(table.table().getName(), e);
			throw e;
		}
	}

	boolean isKeyConflict(StatementException exception) {
		for (Throwable cause = exception.getCause(); cause != null; cause = cause.getCause())
			if (cause instanceof SQLException sql && dialect.isKeyConflict(sql)) return true;

		return false;
	}

	static <S extends org.jdbi.v3.core.statement.SqlStatement<S>> S bind(S statement, List<Argument> arguments) {
		for (int index = 0; index < arguments.size(); index++)
			statement.bind(index, arguments.get(index));

		return statement;
	}

	private <V> Argument argument(Where.Parameter<T, V> parameter) {
		return argument(parameter.column(), parameter.value());
	}

	private <V> Argument value(EntityColumn<T, V> column, T entity) {
		return argument(column, column.get(entity));
	}

	private static <T, V> Where<T> is(EntityColumn<T, V> column, @Nullable Object value) {
		if (!column.type().isInstance(value))
			throw new IllegalArgumentException("Key column " + column.name() + " takes a " + column.type().getSimpleName() + ", not " + value);

		return column.is(column.type().cast(value));
	}

	/**
	 * Where statements run: on a new handle of a {@code Jdbi} each time, or on one handle.
	 */
	private interface Handles {
		<R> R with(HandleCallback<R, RuntimeException> callback);
	}
}
