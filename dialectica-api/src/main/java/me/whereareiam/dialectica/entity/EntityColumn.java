package me.whereareiam.dialectica.entity;

import me.whereareiam.dialectica.dialect.Comparison;
import me.whereareiam.dialectica.dialect.Ordering;
import me.whereareiam.dialectica.schema.ColumnDefinition;
import me.whereareiam.dialectica.schema.type.ColumnType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * A typed column of an {@link EntityTable}: the schema {@link ColumnDefinition} together with the Java type
 * of its values and the accessor that reads the value from an entity.
 * <p>
 * The Java type has to match the column type:
 * <table>
 *     <caption>Java types of column types</caption>
 *     <tr><th>Column type</th><th>Java type</th></tr>
 *     <tr><td>{@link ColumnType#uuid()}</td><td>{@link UUID}</td></tr>
 *     <tr><td>{@link ColumnType#varchar(int)}, {@link ColumnType#text()}</td><td>{@link String}</td></tr>
 *     <tr><td>{@link ColumnType#bigint()}</td><td>{@link Long}</td></tr>
 *     <tr><td>{@link ColumnType#integer()}</td><td>{@link Integer}</td></tr>
 *     <tr><td>{@link ColumnType#bool()}</td><td>{@link Boolean}</td></tr>
 * </table>
 * <p>
 * A column builds the {@link Where conditions} and {@link Order orders} that queries, updates and
 * deletes take:
 * <pre>{@code
 * Where<StoreEntry> expired = StoreEntryColumns.STORE_NAME.is(store)
 *         .and(StoreEntryColumns.EXPIRES_AT.atMost(now));
 * Order<StoreEntry> soonest = StoreEntryColumns.EXPIRES_AT.ascending();
 * }</pre>
 * Dialectica's annotation processor writes the columns of an annotated entity as constants of a
 * class {@code <Entity>Columns}; {@link EntityTable#column(String, Class)} finds a column by a name
 * known only at runtime. A table written by hand creates its columns:
 * <pre>{@code
 * static final EntityColumn<Account, UUID> UNIQUE_ID = EntityColumn.of(UUID.class,
 *         ColumnDefinition.builder("unique_id", ColumnType.uuid()).notNull().build(),
 *         Account::uniqueId);
 * }</pre>
 * Ordered comparisons and orders need a column whose type is
 * {@linkplain Comparison#orders(ColumnType) ordered}: numbers, and text by the database's collation.
 *
 * @param <T> type of the entity
 * @param <V> Java type of the column's values
 */
public final class EntityColumn<T, V> {
	private final @NotNull ColumnDefinition column;
	private final @NotNull Class<V> type;
	private final @NotNull Function<T, V> accessor;

	private EntityColumn(@NotNull Class<V> type, @NotNull ColumnDefinition column, @NotNull Function<T, V> accessor) {
		this.type = type;
		this.column = column;
		this.accessor = accessor;
	}

	/**
	 * Creates an entity column.
	 *
	 * @param type     Java type of the values, a wrapper type for primitives
	 * @param column   schema column
	 * @param accessor reads the column's value from an entity
	 * @param <T>      type of the entity
	 * @param <V>      Java type of the values
	 * @return the entity column
	 * @throws IllegalArgumentException when the Java type does not match the column type
	 */
	public static <T, V> @NotNull EntityColumn<T, V> of(
			@NotNull Class<V> type,
			@NotNull ColumnDefinition column,
			@NotNull Function<T, V> accessor
	) {
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(column, "column");
		Objects.requireNonNull(accessor, "accessor");
		if (!javaType(column.getType()).equals(type))
			throw new IllegalArgumentException("Column " + column.getName() + " of type " + column.getType()
					+ " holds " + javaType(column.getType()).getSimpleName() + " values, not " + type.getSimpleName());

		return new EntityColumn<>(type, column, accessor);
	}

	/**
	 * Returns the Java type that holds values of a column type.
	 *
	 * @param type column type
	 * @return the Java type of its values
	 */
	public static @NotNull Class<?> javaType(@NotNull ColumnType type) {
		if (type instanceof ColumnType.UuidType) return UUID.class;
		if (type instanceof ColumnType.VarcharType || type instanceof ColumnType.TextType) return String.class;
		if (type instanceof ColumnType.BigintType) return Long.class;
		if (type instanceof ColumnType.IntegerType) return Integer.class;
		if (type instanceof ColumnType.BoolType) return Boolean.class;

		throw new IllegalArgumentException("Unknown column type " + type);
	}

	/**
	 * Returns the name of the column.
	 *
	 * @return the column name
	 */
	public @NotNull String name() {
		return column.getName();
	}

	/**
	 * Returns the schema column.
	 *
	 * @return the schema column
	 */
	public @NotNull ColumnDefinition column() {
		return column;
	}

	/**
	 * Returns the Java type of the column's values.
	 *
	 * @return the Java type
	 */
	public @NotNull Class<V> type() {
		return type;
	}

	/**
	 * Reads the column's value from an entity.
	 *
	 * @param entity entity to read
	 * @return the value, or {@code null}
	 */
	public @Nullable V get(@NotNull T entity) {
		return accessor.apply(entity);
	}

	/**
	 * Returns the condition that the column equals a value: {@code column = ?}.
	 *
	 * @param value value to compare with; {@link #isNull()} tests for {@code NULL}
	 * @return the condition
	 * @throws IllegalArgumentException when the value is not of the column's Java type
	 */
	public @NotNull Where<T> is(@NotNull V value) {
		return Where.comparing(this, Comparison.EQUALS, checked(value));
	}

	/**
	 * Returns the condition that the column holds a value other than the given one:
	 * {@code column <> ?}. A row whose column is {@code NULL} does not meet it.
	 *
	 * @param value value to compare with
	 * @return the condition
	 * @throws IllegalArgumentException when the value is not of the column's Java type
	 */
	public @NotNull Where<T> isNot(@NotNull V value) {
		return Where.comparing(this, Comparison.NOT_EQUALS, checked(value));
	}

	/**
	 * Returns the condition that the column is {@code NULL}.
	 *
	 * @return the condition
	 */
	public @NotNull Where<T> isNull() {
		return Where.comparing(this, Comparison.IS_NULL, null);
	}

	/**
	 * Returns the condition that the column is not {@code NULL}.
	 *
	 * @return the condition
	 */
	public @NotNull Where<T> isNotNull() {
		return Where.comparing(this, Comparison.IS_NOT_NULL, null);
	}

	/**
	 * Returns the condition that the column equals one of several values:
	 * {@code column IN (?, ...)}.
	 *
	 * @param values values to compare with, at least one
	 * @return the condition
	 * @throws IllegalArgumentException when no value is given or a value is not of the column's
	 *                                  Java type
	 */
	public @NotNull Where<T> in(@NotNull Collection<? extends V> values) {
		Objects.requireNonNull(values, "values");
		if (values.isEmpty()) throw new IllegalArgumentException("A condition on " + name() + " needs a value to be in");

		List<V> checked = new ArrayList<>();
		for (V value : values)
			checked.add(checked(value));

		return Where.in(this, checked);
	}

	/**
	 * Returns the condition that the column equals one of several values, as
	 * {@link #in(Collection)} does.
	 *
	 * @param values values to compare with, at least one
	 * @return the condition
	 * @throws IllegalArgumentException when no value is given or a value is not of the column's
	 *                                  Java type
	 */
	@SafeVarargs
	public final @NotNull Where<T> in(@NotNull V... values) {
		return in(List.of(Objects.requireNonNull(values, "values")));
	}

	/**
	 * Returns the condition that the column is less than a value: {@code column < ?}.
	 *
	 * @param value value to compare with
	 * @return the condition
	 * @throws IllegalArgumentException when the column's type is not ordered or the value is not of
	 *                                  its Java type
	 */
	public @NotNull Where<T> below(@NotNull V value) {
		return ordered(Comparison.BELOW, value);
	}

	/**
	 * Returns the condition that the column is greater than a value: {@code column > ?}.
	 *
	 * @param value value to compare with
	 * @return the condition
	 * @throws IllegalArgumentException when the column's type is not ordered or the value is not of
	 *                                  its Java type
	 */
	public @NotNull Where<T> above(@NotNull V value) {
		return ordered(Comparison.ABOVE, value);
	}

	/**
	 * Returns the condition that the column is less than or equal to a value: {@code column <= ?}.
	 *
	 * @param value value to compare with
	 * @return the condition
	 * @throws IllegalArgumentException when the column's type is not ordered or the value is not of
	 *                                  its Java type
	 */
	public @NotNull Where<T> atMost(@NotNull V value) {
		return ordered(Comparison.AT_MOST, value);
	}

	/**
	 * Returns the condition that the column is greater than or equal to a value:
	 * {@code column >= ?}.
	 *
	 * @param value value to compare with
	 * @return the condition
	 * @throws IllegalArgumentException when the column's type is not ordered or the value is not of
	 *                                  its Java type
	 */
	public @NotNull Where<T> atLeast(@NotNull V value) {
		return ordered(Comparison.AT_LEAST, value);
	}

	/**
	 * Returns the order of rows by this column, smallest value first.
	 *
	 * @return the order
	 * @throws IllegalArgumentException when the column's type is not ordered
	 */
	public @NotNull Order<T> ascending() {
		return new Order<>(requireOrdered(), Ordering.Direction.ASCENDING);
	}

	/**
	 * Returns the order of rows by this column, largest value first.
	 *
	 * @return the order
	 * @throws IllegalArgumentException when the column's type is not ordered
	 */
	public @NotNull Order<T> descending() {
		return new Order<>(requireOrdered(), Ordering.Direction.DESCENDING);
	}

	private Where<T> ordered(Comparison comparison, V value) {
		return Where.comparing(requireOrdered(), comparison, checked(value));
	}

	private EntityColumn<T, V> requireOrdered() {
		if (!Comparison.orders(column.getType()))
			throw new IllegalArgumentException("Column " + name() + " of type " + column.getType() + " has no order");

		return this;
	}

	private V checked(V value) {
		Objects.requireNonNull(value, "value");
		if (!type.isInstance(value))
			throw new IllegalArgumentException("Column " + name() + " takes a " + type.getSimpleName() + ", not " + value);

		return value;
	}

	@Override
	public String toString() {
		return "EntityColumn[" + column.getName() + " " + column.getType() + "]";
	}
}
