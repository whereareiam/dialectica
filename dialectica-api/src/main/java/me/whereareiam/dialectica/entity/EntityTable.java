package me.whereareiam.dialectica.entity;

import me.whereareiam.dialectica.schema.TableDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * The table an entity type is stored in: the schema {@link TableDefinition}, the typed
 * {@link EntityColumn}s that read an entity's values, and the way an entity is built from a row.
 * Everything Dialectica does with entities works on this model, however it was made.
 * <p>
 * {@code Dialectica.entity(Account.class)} reads it from an annotated class. A table can also be
 * written by hand:
 * <pre>{@code
 * public final class AccountTable extends EntityTable<Account> {
 *     static final EntityColumn<Account, UUID> UNIQUE_ID = EntityColumn.of(UUID.class,
 *             Column.builder("unique_id", ColumnType.uuid()).notNull().build(),
 *             Account::uniqueId);
 *     static final EntityColumn<Account, String> USERNAME = EntityColumn.of(String.class,
 *             Column.builder("username", ColumnType.varchar(64)).notNull().build(),
 *             Account::username);
 *
 *     public AccountTable() {
 *         super(Account.class, "identica_accounts", List.of(UNIQUE_ID, USERNAME),
 *                 table -> table.primaryKey("unique_id"));
 *     }
 *
 *     @Override
 *     public Account create(Row<Account> row) {
 *         return new Account(row.get(UNIQUE_ID), row.get(USERNAME));
 *     }
 * }
 * }</pre>
 *
 * @param <T> type of the entity
 */
public abstract class EntityTable<T> {
	private final @NotNull Class<T> type;
	private final @NotNull TableDefinition table;
	private final @NotNull List<EntityColumn<T, ?>> columns;
	private final @NotNull List<EntityColumn<T, ?>> primaryKey;

	/**
	 * Creates the table of an entity type.
	 *
	 * @param type        entity type
	 * @param name        name of the table
	 * @param columns     columns, in table order
	 * @param constraints adds the primary key, unique keys, foreign keys and indexes to a builder
	 *                    that already holds the columns
	 * @throws IllegalArgumentException when the resulting table is invalid
	 */
	protected EntityTable(
			@NotNull Class<T> type,
			@NotNull String name,
			@NotNull List<EntityColumn<T, ?>> columns,
			@NotNull UnaryOperator<TableDefinition.Builder> constraints
	) {
		this.type = Objects.requireNonNull(type, "type");
		this.columns = List.copyOf(columns);

		TableDefinition.Builder builder = TableDefinition.builder(name);
		for (EntityColumn<T, ?> column : this.columns)
			builder.column(column.column());
		this.table = Objects.requireNonNull(constraints.apply(builder), "constraints").build();

		List<EntityColumn<T, ?>> key = new ArrayList<>();
		for (String column : table.getPrimaryKey())
			key.add(column(column));
		this.primaryKey = List.copyOf(key);
	}

	/**
	 * Builds an entity from the values of a row.
	 *
	 * @param row values of the row
	 * @return the entity
	 */
	public abstract @NotNull T create(@NotNull Row<T> row);

	/**
	 * Returns the entity type.
	 *
	 * @return the entity type
	 */
	public final @NotNull Class<T> type() {
		return type;
	}

	/**
	 * Returns the schema table, for creating it or rendering statements.
	 *
	 * @return the schema table
	 */
	public final @NotNull TableDefinition table() {
		return table;
	}

	/**
	 * Returns the columns, in table order.
	 *
	 * @return the columns
	 */
	public final @NotNull List<EntityColumn<T, ?>> columns() {
		return columns;
	}

	/**
	 * Returns the columns of the primary key, in key order; empty for a table without one.
	 *
	 * @return the primary key columns
	 */
	public final @NotNull List<EntityColumn<T, ?>> primaryKey() {
		return primaryKey;
	}

	/**
	 * Finds a column by name.
	 *
	 * @param name name of the column
	 * @return the column
	 * @throws IllegalArgumentException when the table has no column of that name
	 */
	public final @NotNull EntityColumn<T, ?> column(@NotNull String name) {
		EntityColumn<T, ?> column = findColumn(name);
		if (column == null) throw new IllegalArgumentException("Table " + table.getName() + " has no column " + name);

		return column;
	}

	/**
	 * Finds a column by name, typed by the Java type of its values, for code that knows a column's
	 * name only at runtime:
	 * <pre>{@code
	 * EntityColumn<StoreEntry, String> storeName = table.column("store_name", String.class);
	 * }</pre>
	 *
	 * @param name name of the column
	 * @param type Java type of the column's values, a wrapper type for primitives
	 * @param <V>  Java type of the column's values
	 * @return the column
	 * @throws IllegalArgumentException when the table has no column of that name, or its values are
	 *                                  of another type
	 */
	@SuppressWarnings("unchecked")
	public final <V> @NotNull EntityColumn<T, V> column(@NotNull String name, @NotNull Class<V> type) {
		EntityColumn<T, ?> column = column(name);
		if (!column.type().equals(type))
			throw new IllegalArgumentException("Column " + name + " of " + table.getName() + " holds "
					+ column.type().getSimpleName() + " values, not " + type.getSimpleName());

		return (EntityColumn<T, V>) column;
	}

	/**
	 * Returns the table's own column for a column of the same name and definition, which may come
	 * from another reading of the same entity, such as a generated {@code <Entity>Columns} class.
	 *
	 * @param column a column
	 * @param <V>    Java type of the column's values
	 * @return the table's column
	 * @throws IllegalArgumentException when the table has no such column
	 */
	@SuppressWarnings("unchecked")
	public final <V> @NotNull EntityColumn<T, V> column(@NotNull EntityColumn<T, V> column) {
		Objects.requireNonNull(column, "column");
		EntityColumn<T, ?> own = findColumn(column.name());
		if (own == null || !own.column().equals(column.column()) || !own.type().equals(column.type()))
			throw new IllegalArgumentException(column + " is not a column of " + table.getName());

		return (EntityColumn<T, V>) own;
	}

	private @Nullable EntityColumn<T, ?> findColumn(String name) {
		for (EntityColumn<T, ?> column : columns)
			if (column.name().equals(name)) return column;

		return null;
	}

	@Override
	public String toString() {
		return "EntityTable[" + type.getName() + " -> " + table.getName() + "]";
	}
}
