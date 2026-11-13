package me.whereareiam.dialectica.entity;

import me.whereareiam.dialectica.dialect.Ordering;
import org.jetbrains.annotations.NotNull;

/**
 * An order of an entity's rows by one column, from {@link EntityColumn#ascending()} or
 * {@link EntityColumn#descending()}:
 * <pre>{@code
 * entries.query(StoreEntryColumns.STORE_NAME.is(store))
 *         .orderBy(StoreEntryColumns.EXPIRES_AT.ascending(), StoreEntryColumns.ENTRY_KEY.ascending())
 *         .list();
 * }</pre>
 *
 * @param <T> entity type
 */
public final class Order<T> {
	private final @NotNull EntityColumn<T, ?> column;
	private final @NotNull Ordering.Direction direction;

	Order(@NotNull EntityColumn<T, ?> column, @NotNull Ordering.Direction direction) {
		this.column = column;
		this.direction = direction;
	}

	/**
	 * Returns the column the rows are ordered by.
	 *
	 * @return the column
	 */
	public @NotNull EntityColumn<T, ?> column() {
		return column;
	}

	/**
	 * Returns the direction of the order.
	 *
	 * @return the direction
	 */
	public @NotNull Ordering.Direction direction() {
		return direction;
	}

	/**
	 * Returns the order as a dialect renders it.
	 *
	 * @return the ordering
	 */
	public @NotNull Ordering ordering() {
		return new Ordering(column.name(), direction);
	}

	@Override
	public String toString() {
		return "Order[" + column.name() + " " + direction + "]";
	}
}
