package me.whereareiam.dialectica.adapter.jdbi.entity;

import me.whereareiam.dialectica.dialect.SqlStatement;
import me.whereareiam.dialectica.dialect.StatementRenderer;
import me.whereareiam.dialectica.dialect.Upsert;
import me.whereareiam.dialectica.entity.EntityColumn;
import me.whereareiam.dialectica.entity.EntityTable;
import me.whereareiam.dialectica.exception.KeyConflictException;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.argument.Argument;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.jdbi.v3.core.statement.StatementException;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Writes whole entities for an {@link EntityAccess}: inserts, upserts and replacements, one at a
 * time or in batches.
 *
 * @param <T> entity type
 */
final class EntityWrites<T> {
	private final EntityStatements<T> statements;
	private final SqlStatement insert;
	private final SqlStatement insertIfAbsent;
	private final @Nullable Upsert upsert;
	private final @Nullable SqlStatement replace;

	EntityWrites(EntityStatements<T> statements) {
		EntityTable<T> table = statements.table();
		StatementRenderer renderer = statements.renderer();
		boolean keyed = !table.primaryKey().isEmpty();

		this.statements = statements;
		this.insert = renderer.insert(table.table());
		this.insertIfAbsent = renderer.insertIfAbsent(table.table());
		this.upsert = keyed ? renderer.upsert(table.table(), valueColumns(table)) : null;
		this.replace = keyed && table.primaryKey().size() < table.columns().size() ? renderer.replace(table.table()) : null;
	}

	private EntityWrites(EntityStatements<T> statements, EntityWrites<T> rendered) {
		this.statements = statements;
		this.insert = rendered.insert;
		this.insertIfAbsent = rendered.insertIfAbsent;
		this.upsert = rendered.upsert;
		this.replace = rendered.replace;
	}

	/**
	 * Returns the same writes, run through other statements.
	 */
	EntityWrites<T> on(EntityStatements<T> bound) {
		return new EntityWrites<>(bound, this);
	}

	void insert(T entity) {
		Objects.requireNonNull(entity, "entity");
		statements.withHandle(handle -> statements.execute(handle, insert, statements.row(insert, entity)));
	}

	boolean insertIfAbsent(T entity) {
		Objects.requireNonNull(entity, "entity");
		return statements.withHandle(handle -> {
			try {
				return statements.execute(handle, insertIfAbsent, statements.row(insertIfAbsent, entity)) > 0;
			} catch (StatementException e) {
				if (statements.isKeyConflict(e)) return false;

				throw e;
			}
		});
	}

	void upsert(T entity, List<EntityColumn<T, ?>> columns) {
		Objects.requireNonNull(entity, "entity");
		Upsert rendered = upsert(columns);
		statements.withHandle(handle -> {
			upsert(handle, rendered, entity);
			return null;
		});
	}

	boolean replace(T entity) {
		Objects.requireNonNull(entity, "entity");
		SqlStatement statement = require(replace, "replace");

		return statements.withHandle(handle -> statements.execute(handle, statement, statements.row(statement, entity))) > 0;
	}

	void insertAll(Collection<? extends T> entities) {
		List<T> checked = checked(entities);
		if (checked.isEmpty()) return;

		statements.withHandle(handle -> handle.inTransaction(transaction -> batch(transaction, insert, checked)));
	}

	void upsertAll(Collection<? extends T> entities, List<EntityColumn<T, ?>> columns) {
		List<T> checked = checked(entities);
		Upsert rendered = upsert(columns);
		if (checked.isEmpty()) return;

		statements.withHandle(handle -> handle.inTransaction(transaction -> {
			if (rendered instanceof Upsert.OnConflict onConflict) return batch(transaction, onConflict.statement(), checked);

			for (T entity : checked)
				upsert(transaction, rendered, entity);
			return null;
		}));
	}

	/**
	 * Returns the upsert that updates the given columns, or every column outside the key when none
	 * is given.
	 */
	private Upsert upsert(List<EntityColumn<T, ?>> columns) {
		Upsert full = require(upsert, "upsert");
		if (columns.isEmpty()) return full;

		EntityTable<T> table = statements.table();
		List<String> names = new ArrayList<>();
		for (EntityColumn<T, ?> column : columns)
			names.add(table.column(Objects.requireNonNull(column, "column")).name());

		return statements.renderer().upsert(table.table(), names);
	}

	/**
	 * Runs an upsert of an entity. An {@link Upsert.InsertThenUpdate} runs its update when the insert
	 * inserted no row or failed with a key conflict, and throws a {@link KeyConflictException} when
	 * the update then matches no row.
	 */
	private void upsert(Handle handle, Upsert upsert, T entity) {
		if (upsert instanceof Upsert.OnConflict onConflict) {
			statements.execute(handle, onConflict.statement(), statements.row(onConflict.statement(), entity));
			return;
		}

		Upsert.InsertThenUpdate insertThenUpdate = (Upsert.InsertThenUpdate) upsert;
		StatementException conflict = null;
		try {
			if (statements.execute(handle, insertThenUpdate.insert(), statements.row(insertThenUpdate.insert(), entity)) > 0) return;
		} catch (StatementException e) {
			if (!statements.isKeyConflict(e)) throw e;

			conflict = e;
		}
		if (statements.execute(handle, insertThenUpdate.update(), statements.row(insertThenUpdate.update(), entity)) == 0)
			throw new KeyConflictException(statements.table().table().getName(), conflict);
	}

	private int[] batch(Handle handle, SqlStatement statement, List<T> entities) {
		PreparedBatch batch = handle.prepareBatch(statement.getSql());
		for (T entity : entities) {
			List<Argument> arguments = statements.row(statement, entity);
			for (int index = 0; index < arguments.size(); index++)
				batch.bind(index, arguments.get(index));
			batch.add();
		}

		return batch.execute();
	}

	private List<T> checked(Collection<? extends T> entities) {
		Objects.requireNonNull(entities, "entities");
		List<T> checked = new ArrayList<>();
		for (T entity : entities)
			checked.add(Objects.requireNonNull(entity, "entity"));

		return checked;
	}

	private <S> S require(@Nullable S statement, String operation) {
		if (statement == null)
			throw new IllegalStateException("Cannot " + operation + " " + statements.table().table().getName()
					+ ": it needs a primary key and, to replace, a column outside it");

		return statement;
	}

	private static List<String> valueColumns(EntityTable<?> table) {
		Set<String> key = new HashSet<>();
		table.primaryKey().forEach(column -> key.add(column.name()));

		return table.columns().stream()
				.map(EntityColumn::name)
				.filter(name -> !key.contains(name))
				.toList();
	}
}
