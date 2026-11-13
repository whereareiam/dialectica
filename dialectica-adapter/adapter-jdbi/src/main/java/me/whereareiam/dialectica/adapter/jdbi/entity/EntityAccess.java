package me.whereareiam.dialectica.adapter.jdbi.entity;

import me.whereareiam.dialectica.adapter.jdbi.DialecticaPlugin;
import me.whereareiam.dialectica.dialect.SqlStatement;
import me.whereareiam.dialectica.entity.EntityColumn;
import me.whereareiam.dialectica.entity.EntityTable;
import me.whereareiam.dialectica.entity.Where;
import me.whereareiam.dialectica.exception.KeyConflictException;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Reads and writes the entities of an {@link EntityTable} through Jdbi, with statements the
 * dialect renders. {@link DialecticaPlugin} has to be installed on the {@code Jdbi}.
 * <pre>{@code
 * EntityAccess<StoreEntry> entries = EntityAccess.of(jdbi, Dialectica.entity(StoreEntry.class));
 * entries.insert(entry);
 * Optional<StoreEntry> found = entries.find(store, key);
 * List<StoreEntry> stored = entries.query(StoreEntryColumns.STORE_NAME.is(store)).list();
 * entries.update(store, key).set(StoreEntryColumns.EXPIRES_AT, later).execute();
 * int expired = entries.delete(StoreEntryColumns.STORE_NAME.is(store).and(StoreEntryColumns.EXPIRES_AT.atMost(now)));
 * }</pre>
 * Columns are typed {@link EntityColumn}s, usually the constants that Dialectica's annotation
 * processor writes into {@code <Entity>Columns}; {@link #column(String, Class)} finds one by a name
 * known only at runtime. Conditions are {@link Where} values built from them.
 * <p>
 * Each call runs on its own handle of the {@code Jdbi}; batches run in a transaction. To run several
 * calls in one transaction, bind the access to the transaction's handle with {@link #on(Handle)}:
 * <pre>{@code
 * jdbi.useTransaction(handle -> {
 *     EntityAccess<StoreEntry> bound = entries.on(handle);
 *     bound.delete(StoreEntryColumns.STORE_NAME.is(store));
 *     bound.insertAll(replacement);
 * });
 * }</pre>
 * The operations that address a row by its primary key need the table to have one; queries,
 * updates and deletes by a condition do not.
 *
 * @param <T> entity type
 */
public final class EntityAccess<T> {
	private final EntityStatements<T> statements;
	private final EntityWrites<T> writes;

	private EntityAccess(EntityStatements<T> statements, EntityWrites<T> writes) {
		this.statements = statements;
		this.writes = writes;
	}

	/**
	 * Creates the access to an entity table.
	 *
	 * @param jdbi  Jdbi with {@link DialecticaPlugin} installed
	 * @param table entity table
	 * @param <T>   entity type
	 * @return the access, which runs each call on a new handle of the {@code Jdbi}
	 * @throws IllegalStateException when the plugin is not installed
	 */
	public static <T> @NotNull EntityAccess<T> of(@NotNull Jdbi jdbi, @NotNull EntityTable<T> table) {
		EntityStatements<T> statements = new EntityStatements<>(Objects.requireNonNull(jdbi, "jdbi"), Objects.requireNonNull(table, "table"));

		return new EntityAccess<>(statements, new EntityWrites<>(statements));
	}

	/**
	 * Returns this access bound to a handle: every call runs on that handle, inside whatever
	 * transaction it has open, and leaves the handle open. The handle has to belong to a
	 * {@code Jdbi} of the same database.
	 *
	 * @param handle handle to run on
	 * @return the bound access
	 */
	public @NotNull EntityAccess<T> on(@NotNull Handle handle) {
		EntityStatements<T> bound = statements.on(Objects.requireNonNull(handle, "handle"));

		return new EntityAccess<>(bound, writes.on(bound));
	}

	/**
	 * Returns the entity table this access works on.
	 *
	 * @return the entity table
	 */
	public @NotNull EntityTable<T> table() {
		return statements.table();
	}

	/**
	 * Finds a column of the table by a name known only at runtime, as
	 * {@link EntityTable#column(String, Class)} does.
	 *
	 * @param name name of the column
	 * @param type Java type of the column's values, a wrapper type for primitives
	 * @param <V>  Java type of the column's values
	 * @return the column
	 * @throws IllegalArgumentException when the table has no column of that name, or its values are
	 *                                  of another type
	 */
	public <V> @NotNull EntityColumn<T, V> column(@NotNull String name, @NotNull Class<V> type) {
		return statements.table().column(Objects.requireNonNull(name, "name"), Objects.requireNonNull(type, "type"));
	}

	/**
	 * Finds the entity with a primary key.
	 *
	 * @param key values of the primary key columns, in key order
	 * @return the entity, or empty when no row has the key
	 * @throws IllegalArgumentException when the values do not fit the key's columns
	 * @throws IllegalStateException    when the table has no primary key
	 */
	public @NotNull Optional<T> find(@NotNull Object... key) {
		return new EntityQuery<>(statements, statements.key("find", key)).first();
	}

	/**
	 * Starts a query of the entities whose rows meet a condition.
	 *
	 * @param where condition the rows meet
	 * @return the query, which runs on one of its terminal methods
	 * @throws IllegalArgumentException when the condition compares a column that is not one of the
	 *                                  table's
	 */
	public @NotNull EntityQuery<T> query(@NotNull Where<T> where) {
		return new EntityQuery<>(statements, statements.resolve(Objects.requireNonNull(where, "where")));
	}

	/**
	 * Inserts an entity.
	 *
	 * @param entity entity to insert
	 */
	public void insert(@NotNull T entity) {
		writes.insert(entity);
	}

	/**
	 * Inserts entities as one batch, in one transaction: either every entity is inserted or none.
	 *
	 * @param entities entities to insert
	 */
	public void insertAll(@NotNull Collection<? extends T> entities) {
		writes.insertAll(entities);
	}

	/**
	 * Inserts an entity unless a row already holds its primary key or one of its unique keys, and
	 * leaves that row as it is. Of several instances that insert the same key at once, exactly one
	 * inserts the row and sees {@code true}. Any other failure, such as a missing referenced row,
	 * still throws.
	 *
	 * @param entity entity to insert
	 * @return whether the entity was inserted
	 */
	public boolean insertIfAbsent(@NotNull T entity) {
		return writes.insertIfAbsent(entity);
	}

	/**
	 * Inserts an entity or, when a row has its primary key, updates every column of that row
	 * outside the key.
	 *
	 * @param entity entity to write
	 * @throws KeyConflictException  when another row holds one of the entity's other unique keys
	 * @throws IllegalStateException when the table has no primary key
	 * @see #upsert(Object, EntityColumn[])
	 */
	public void upsert(@NotNull T entity) {
		writes.upsert(entity, List.of());
	}

	/**
	 * Inserts an entity or, when a row has its primary key, updates only the given columns of that
	 * row; the others keep the row's values:
	 * <pre>{@code
	 * links.upsert(link, ProviderLinkColumns.IS_PRIMARY, ProviderLinkColumns.LAST_SEEN_AT);
	 * }</pre>
	 * The primary key decides whether the row exists. Of several instances that write the same key
	 * at once, each one succeeds, and the row ends with the values of the last. On MySQL and MariaDB
	 * connections that set {@code useAffectedRows=true}, an upsert of a table with other unique keys
	 * that leaves the row's values as they are also throws a {@link KeyConflictException}.
	 *
	 * @param entity  entity to write
	 * @param columns columns of the table outside the primary key, at least one
	 * @throws KeyConflictException     when another row holds one of the entity's other unique keys
	 * @throws IllegalArgumentException when no column is given, a column is not one of the table's,
	 *                                  belongs to the key, or is given twice
	 * @throws IllegalStateException    when the table has no primary key
	 */
	@SafeVarargs
	public final void upsert(@NotNull T entity, @NotNull EntityColumn<T, ?>... columns) {
		writes.upsert(entity, someColumns(columns));
	}

	/**
	 * Upserts entities as {@link #upsert(Object)} does, in one transaction.
	 *
	 * @param entities entities to write
	 * @throws KeyConflictException  when another row holds one of an entity's other unique keys
	 * @throws IllegalStateException when the table has no primary key
	 */
	public void upsertAll(@NotNull Collection<? extends T> entities) {
		writes.upsertAll(entities, List.of());
	}

	/**
	 * Upserts entities as {@link #upsert(Object, EntityColumn[])} does, in one transaction.
	 *
	 * @param entities entities to write
	 * @param columns  columns of the table outside the primary key, at least one
	 * @throws KeyConflictException     when another row holds one of an entity's other unique keys
	 * @throws IllegalArgumentException when no column is given, a column is not one of the table's,
	 *                                  belongs to the key, or is given twice
	 * @throws IllegalStateException    when the table has no primary key
	 */
	@SafeVarargs
	public final void upsertAll(@NotNull Collection<? extends T> entities, @NotNull EntityColumn<T, ?>... columns) {
		writes.upsertAll(entities, someColumns(columns));
	}

	/**
	 * Replaces every column outside the primary key of the row with the entity's primary key by the
	 * entity's values.
	 *
	 * @param entity entity to write
	 * @return whether a row had that key
	 * @throws IllegalStateException when the table has no primary key or no column outside it
	 */
	public boolean replace(@NotNull T entity) {
		return writes.replace(entity);
	}

	/**
	 * Starts an update of some columns of the row with a primary key:
	 * <pre>{@code
	 * accounts.update(uniqueId).set(AccountColumns.LAST_SEEN_AT, now).execute();
	 * }</pre>
	 *
	 * @param key values of the primary key columns, in key order
	 * @return the update, which runs on {@link EntityUpdate#execute()}
	 * @throws IllegalArgumentException when the values do not fit the key's columns
	 * @throws IllegalStateException    when the table has no primary key
	 */
	public @NotNull EntityUpdate<T> update(@NotNull Object... key) {
		return new EntityUpdate<>(statements, statements.key("update", key));
	}

	/**
	 * Starts an update of some columns of every row that meets a condition.
	 *
	 * @param where condition the rows meet
	 * @return the update, which runs on {@link EntityUpdate#execute()}
	 * @throws IllegalArgumentException when the condition compares a column that is not one of the
	 *                                  table's
	 */
	public @NotNull EntityUpdate<T> update(@NotNull Where<T> where) {
		return new EntityUpdate<>(statements, statements.resolve(Objects.requireNonNull(where, "where")));
	}

	/**
	 * Deletes the row with a primary key.
	 *
	 * @param key values of the primary key columns, in key order
	 * @return whether a row had that key
	 * @throws IllegalArgumentException when the values do not fit the key's columns
	 * @throws IllegalStateException    when the table has no primary key
	 */
	public boolean delete(@NotNull Object... key) {
		return remove(statements.key("delete", key)) > 0;
	}

	/**
	 * Deletes every row that meets a condition.
	 *
	 * @param where condition the rows meet
	 * @return the number of deleted rows
	 * @throws IllegalArgumentException when the condition compares a column that is not one of the
	 *                                  table's
	 */
	public int delete(@NotNull Where<T> where) {
		return remove(statements.resolve(Objects.requireNonNull(where, "where")));
	}

	private int remove(Where<T> where) {
		SqlStatement statement = statements.renderer().delete(statements.table().table(), where.condition());

		return statements.withHandle(handle -> statements.execute(handle, statement, statements.arguments(where)));
	}

	private List<EntityColumn<T, ?>> someColumns(EntityColumn<T, ?>[] columns) {
		List<EntityColumn<T, ?>> given = List.of(Objects.requireNonNull(columns, "columns"));
		if (given.isEmpty())
			throw new IllegalArgumentException("An upsert of some columns of " + table().table().getName() + " needs a column to update");

		return given;
	}
}
