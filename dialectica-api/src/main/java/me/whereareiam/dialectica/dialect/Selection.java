package me.whereareiam.dialectica.dialect;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * Which rows of a table a {@link StatementRenderer#select(me.whereareiam.dialectica.schema.TableDefinition, Selection) select}
 * reads: those that meet a condition, in an order, and of those at most a number after skipping
 * some. A selection is immutable; each {@code with} method returns a changed copy.
 * <pre>{@code
 * Selection page = Selection.of(condition)
 *         .withOrderings(List.of(new Ordering("created_at", Ordering.Direction.DESCENDING)))
 *         .withLimit(20)
 *         .withOffset(40);
 * }</pre>
 */
@Getter
@ToString
@EqualsAndHashCode
public final class Selection {
	/**
	 * Condition the rows meet.
	 */
	private final @NotNull Condition condition;

	/**
	 * Orderings of the rows, the first one deciding first; empty for the order the database
	 * returns them in.
	 */
	private final @NotNull List<Ordering> orderings;

	/**
	 * Largest number of rows to read, or empty for every row.
	 */
	private final @NotNull OptionalLong limit;

	/**
	 * Number of rows to skip before the first row read.
	 */
	private final long offset;

	private Selection(Condition condition, List<Ordering> orderings, OptionalLong limit, long offset) {
		this.condition = condition;
		this.orderings = orderings;
		this.limit = limit;
		this.offset = offset;
	}

	/**
	 * Selects every row that meets a condition, in the order the database returns them.
	 *
	 * @param condition condition the rows meet
	 * @return the selection
	 */
	public static @NotNull Selection of(@NotNull Condition condition) {
		return new Selection(Objects.requireNonNull(condition, "condition"), List.of(), OptionalLong.empty(), 0);
	}

	/**
	 * Returns this selection with other orderings.
	 *
	 * @param orderings orderings, the first one deciding first
	 * @return the changed selection
	 */
	public @NotNull Selection withOrderings(@NotNull List<Ordering> orderings) {
		return new Selection(condition, List.copyOf(orderings), limit, offset);
	}

	/**
	 * Returns this selection reading at most a number of rows.
	 *
	 * @param limit largest number of rows, at least one
	 * @return the changed selection
	 * @throws IllegalArgumentException when the limit is below one
	 */
	public @NotNull Selection withLimit(long limit) {
		if (limit < 1) throw new IllegalArgumentException("A limit is at least 1, not " + limit);

		return new Selection(condition, orderings, OptionalLong.of(limit), offset);
	}

	/**
	 * Returns this selection skipping a number of rows first.
	 *
	 * @param offset number of rows to skip, at least zero
	 * @return the changed selection
	 * @throws IllegalArgumentException when the offset is negative
	 */
	public @NotNull Selection withOffset(long offset) {
		if (offset < 0) throw new IllegalArgumentException("An offset is at least 0, not " + offset);

		return new Selection(condition, orderings, limit, offset);
	}
}
