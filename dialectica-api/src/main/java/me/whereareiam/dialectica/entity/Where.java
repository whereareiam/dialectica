package me.whereareiam.dialectica.entity;

import me.whereareiam.dialectica.dialect.Comparison;
import me.whereareiam.dialectica.dialect.Condition;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A condition on the rows of an entity's table, built from its typed {@link EntityColumn}s and
 * combined with {@link #and(Where)}, {@link #or(Where)} and {@link #not()}:
 * <pre>{@code
 * Where<StoreEntry> expired = StoreEntryColumns.STORE_NAME.is(store)
 *         .and(StoreEntryColumns.EXPIRES_AT.atMost(now));
 * Where<Account> unnamed = AccountColumns.USERNAME.isNull().or(AccountColumns.USERNAME.is(""));
 * }</pre>
 * A condition is an immutable value: it can be kept in a field and used for any number of queries,
 * updates and deletes. It compares as SQL does, so a column that is {@code NULL} meets no
 * comparison with a value, {@link EntityColumn#isNot(Object) isNot} included, and neither does the
 * negation of such a comparison; {@link EntityColumn#isNull()} and {@link EntityColumn#isNotNull()}
 * test for {@code NULL}.
 * <p>
 * {@link #condition()} is the condition without its values, as a dialect renders it, and
 * {@link #parameters()} are the values in the order of the rendered statement's parameters.
 *
 * @param <T> entity type
 */
public final class Where<T> {
	private final @NotNull Condition condition;
	private final @NotNull List<Parameter<T, ?>> parameters;
	private final @NotNull List<EntityColumn<T, ?>> columns;

	private Where(Condition condition, List<Parameter<T, ?>> parameters, List<EntityColumn<T, ?>> columns) {
		this.condition = condition;
		this.parameters = List.copyOf(parameters);
		this.columns = List.copyOf(columns);
	}

	/**
	 * Creates the comparison of a column, with the value it compares with when the comparison
	 * {@linkplain Comparison#takesValue() takes one}.
	 */
	static <T, V> Where<T> comparing(EntityColumn<T, V> column, Comparison comparison, V value) {
		List<Parameter<T, ?>> parameters = comparison.takesValue() ? List.of(new Parameter<>(column, value)) : List.of();

		return new Where<>(new Condition.Comparing(column.name(), comparison), parameters, List.of(column));
	}

	/**
	 * Creates the condition that a column equals one of several values.
	 */
	static <T, V> Where<T> in(EntityColumn<T, V> column, List<V> values) {
		List<Parameter<T, ?>> parameters = new ArrayList<>();
		for (V value : values)
			parameters.add(new Parameter<>(column, value));

		return new Where<>(new Condition.In(column.name(), values.size()), parameters, List.of(column));
	}

	/**
	 * Returns the condition that this and another condition both hold.
	 *
	 * @param other the other condition
	 * @return the combined condition
	 */
	public @NotNull Where<T> and(@NotNull Where<T> other) {
		Objects.requireNonNull(other, "other");
		List<Condition> conditions = new ArrayList<>(all(condition));
		conditions.addAll(all(other.condition));

		return combine(new Condition.All(conditions), other);
	}

	/**
	 * Returns the condition that this or another condition holds.
	 *
	 * @param other the other condition
	 * @return the combined condition
	 */
	public @NotNull Where<T> or(@NotNull Where<T> other) {
		Objects.requireNonNull(other, "other");
		List<Condition> conditions = new ArrayList<>(any(condition));
		conditions.addAll(any(other.condition));

		return combine(new Condition.Any(conditions), other);
	}

	/**
	 * Returns the condition that this condition does not hold. A row whose compared column is
	 * {@code NULL} meets neither the condition nor its negation.
	 *
	 * @return the negated condition
	 */
	public @NotNull Where<T> not() {
		return new Where<>(new Condition.Not(condition), parameters, columns);
	}

	/**
	 * Returns the condition without its values, as a dialect renders it.
	 *
	 * @return the condition
	 */
	public @NotNull Condition condition() {
		return condition;
	}

	/**
	 * Returns the values the condition compares with, in the order of the parameters of the
	 * statement its {@link #condition()} renders to.
	 *
	 * @return the parameters
	 */
	public @NotNull List<Parameter<T, ?>> parameters() {
		return parameters;
	}

	/**
	 * Returns every column the condition compares, in the order they appear, once for each time.
	 *
	 * @return the compared columns
	 */
	public @NotNull List<EntityColumn<T, ?>> columns() {
		return columns;
	}

	@Override
	public String toString() {
		return "Where[" + condition + " " + parameters + "]";
	}

	private Where<T> combine(Condition combined, Where<T> other) {
		List<Parameter<T, ?>> joined = new ArrayList<>(parameters);
		joined.addAll(other.parameters);
		List<EntityColumn<T, ?>> compared = new ArrayList<>(columns);
		compared.addAll(other.columns);

		return new Where<>(combined, joined, compared);
	}

	private static List<Condition> all(Condition condition) {
		if (condition instanceof Condition.All all) return all.conditions();

		return List.of(condition);
	}

	private static List<Condition> any(Condition condition) {
		if (condition instanceof Condition.Any any) return any.conditions();

		return List.of(condition);
	}

	/**
	 * A value a condition compares a column with.
	 *
	 * @param column the compared column
	 * @param value  the value, of the column's Java type
	 * @param <T>    entity type
	 * @param <V>    Java type of the column's values
	 */
	public record Parameter<T, V>(@NotNull EntityColumn<T, V> column, @NotNull V value) {
		/**
		 * Checks the parameter.
		 *
		 * @param column the compared column
		 * @param value  the value
		 */
		public Parameter {
			Objects.requireNonNull(column, "column");
			Objects.requireNonNull(value, "value");
		}
	}
}
