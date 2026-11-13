package me.whereareiam.dialectica.dialect;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * A condition on the current values of a row, without the values it compares with, as a
 * {@link StatementRenderer} renders it into a {@code WHERE} clause. Projects build conditions as
 * {@link me.whereareiam.dialectica.entity.Where} values, which carry the values too.
 * <p>
 * A rendered condition is parameterized by the column of each {@code ?} it holds, in the order the
 * comparisons appear in the condition, depth first: {@code All(a = ?, Any(b = ?, c IN (?, ?)))}
 * takes {@code a}, {@code b}, {@code c}, {@code c}.
 */
public sealed interface Condition permits Condition.Comparing, Condition.In, Condition.All, Condition.Any, Condition.Not {
	/**
	 * Hands this condition to the method of a visitor that handles its kind.
	 *
	 * @param visitor visitor of every kind of condition
	 * @param <R>     result of the visitor
	 * @return the visitor's result
	 */
	<R> R accept(@NotNull Visitor<R> visitor);

	/**
	 * Handles each kind of condition, as a renderer does.
	 *
	 * @param <R> result of handling a condition
	 */
	interface Visitor<R> {
		/**
		 * Handles a comparison of one column.
		 *
		 * @param condition the comparison
		 * @return the result
		 */
		R comparing(@NotNull Comparing condition);

		/**
		 * Handles a column that equals one of several values.
		 *
		 * @param condition the condition
		 * @return the result
		 */
		R in(@NotNull In condition);

		/**
		 * Handles conditions that all have to hold.
		 *
		 * @param condition the conditions
		 * @return the result
		 */
		R all(@NotNull All condition);

		/**
		 * Handles conditions of which one has to hold.
		 *
		 * @param condition the conditions
		 * @return the result
		 */
		R any(@NotNull Any condition);

		/**
		 * Handles a condition that must not hold.
		 *
		 * @param condition the negated condition
		 * @return the result
		 */
		R not(@NotNull Not condition);
	}

	/**
	 * A column compared as its {@link Comparison} says, such as {@code expires_at <= ?} or
	 * {@code used_at IS NULL}.
	 *
	 * @param column     name of the column
	 * @param comparison how the column is compared
	 */
	record Comparing(@NotNull String column, @NotNull Comparison comparison) implements Condition {
		/**
		 * Checks the comparison.
		 *
		 * @param column     name of the column
		 * @param comparison how the column is compared
		 */
		public Comparing {
			Objects.requireNonNull(column, "column");
			Objects.requireNonNull(comparison, "comparison");
		}

		@Override
		public <R> R accept(@NotNull Visitor<R> visitor) {
			return visitor.comparing(this);
		}
	}

	/**
	 * A column that equals one of several values: {@code column IN (?, ?, ...)}.
	 *
	 * @param column name of the column
	 * @param values number of values, at least one
	 */
	record In(@NotNull String column, int values) implements Condition {
		/**
		 * Checks the condition.
		 *
		 * @param column name of the column
		 * @param values number of values
		 * @throws IllegalArgumentException when there is no value
		 */
		public In {
			Objects.requireNonNull(column, "column");
			if (values < 1) throw new IllegalArgumentException("A condition on " + column + " needs a value to be in");
		}

		@Override
		public <R> R accept(@NotNull Visitor<R> visitor) {
			return visitor.in(this);
		}
	}

	/**
	 * Conditions that all have to hold: {@code a AND b AND ...}.
	 *
	 * @param conditions the conditions, at least one
	 */
	record All(@NotNull List<Condition> conditions) implements Condition {
		/**
		 * Checks and copies the conditions.
		 *
		 * @param conditions the conditions
		 * @throws IllegalArgumentException when there is no condition
		 */
		public All {
			conditions = List.copyOf(conditions);
			if (conditions.isEmpty()) throw new IllegalArgumentException("All needs a condition");
		}

		@Override
		public <R> R accept(@NotNull Visitor<R> visitor) {
			return visitor.all(this);
		}
	}

	/**
	 * Conditions of which at least one has to hold: {@code a OR b OR ...}.
	 *
	 * @param conditions the conditions, at least one
	 */
	record Any(@NotNull List<Condition> conditions) implements Condition {
		/**
		 * Checks and copies the conditions.
		 *
		 * @param conditions the conditions
		 * @throws IllegalArgumentException when there is no condition
		 */
		public Any {
			conditions = List.copyOf(conditions);
			if (conditions.isEmpty()) throw new IllegalArgumentException("Any needs a condition");
		}

		@Override
		public <R> R accept(@NotNull Visitor<R> visitor) {
			return visitor.any(this);
		}
	}

	/**
	 * A condition that must not hold: {@code NOT (condition)}. As in SQL, a comparison of a column
	 * that is {@code NULL} neither holds nor fails, so its negation does not hold either.
	 *
	 * @param condition the negated condition
	 */
	record Not(@NotNull Condition condition) implements Condition {
		/**
		 * Checks the condition.
		 *
		 * @param condition the negated condition
		 */
		public Not {
			Objects.requireNonNull(condition, "condition");
		}

		@Override
		public <R> R accept(@NotNull Visitor<R> visitor) {
			return visitor.not(this);
		}
	}
}
