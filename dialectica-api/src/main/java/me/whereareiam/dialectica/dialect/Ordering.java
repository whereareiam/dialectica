package me.whereareiam.dialectica.dialect;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * How a {@link Selection} orders rows by one column: {@code column ASC} or {@code column DESC}.
 * Rows that hold {@code NULL} in the column come first or last as the database puts them.
 */
@Getter
@ToString
@EqualsAndHashCode
public final class Ordering {
	/**
	 * Name of the column, of an {@linkplain Comparison#orders ordered type}.
	 */
	private final @NotNull String column;

	/**
	 * Direction of the order.
	 */
	private final @NotNull Direction direction;

	/**
	 * Creates an ordering.
	 *
	 * @param column    name of a column of an ordered type
	 * @param direction direction of the order
	 */
	public Ordering(@NotNull String column, @NotNull Direction direction) {
		this.column = Objects.requireNonNull(column, "column");
		this.direction = Objects.requireNonNull(direction, "direction");
	}

	/**
	 * The direction of an ordering.
	 */
	public enum Direction {
		/**
		 * Smallest value first: {@code ASC}.
		 */
		ASCENDING,

		/**
		 * Largest value first: {@code DESC}.
		 */
		DESCENDING
	}
}
