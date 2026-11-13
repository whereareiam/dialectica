package me.whereareiam.dialectica.common.dialect;

import me.whereareiam.dialectica.dialect.Comparison;
import me.whereareiam.dialectica.dialect.Condition;
import me.whereareiam.dialectica.schema.ColumnDefinition;
import me.whereareiam.dialectica.schema.TableDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.UnaryOperator;

/**
 * Writes a {@link Condition} as the SQL every supported dialect shares, and collects the column of
 * each parameter in order. Conditions joined by {@code AND} or {@code OR} are parenthesized inside
 * others, and a negated condition always is. It refuses a column the table lacks, and an ordered
 * comparison of a column without an order.
 */
final class ConditionWriter implements Condition.Visitor<String> {
	private final TableDefinition table;
	private final UnaryOperator<String> identifier;
	private final List<String> parameters;

	ConditionWriter(TableDefinition table, UnaryOperator<String> identifier, List<String> parameters) {
		this.table = table;
		this.identifier = identifier;
		this.parameters = parameters;
	}

	@Override
	public String comparing(@NotNull Condition.Comparing condition) {
		Comparison comparison = condition.comparison();
		requireColumn(table, condition.column());
		if (comparison.isOrdered()) requireOrdered(table, condition.column(), "compared " + comparison);
		if (comparison.takesValue()) parameters.add(condition.column());

		return identifier.apply(condition.column()) + operator(comparison);
	}

	@Override
	public String in(@NotNull Condition.In condition) {
		requireColumn(table, condition.column());
		List<String> markers = new ArrayList<>();
		for (int index = 0; index < condition.values(); index++) {
			parameters.add(condition.column());
			markers.add("?");
		}

		return identifier.apply(condition.column()) + " IN (" + String.join(", ", markers) + ")";
	}

	@Override
	public String all(@NotNull Condition.All condition) {
		return joined(condition.conditions(), " AND ");
	}

	@Override
	public String any(@NotNull Condition.Any condition) {
		return joined(condition.conditions(), " OR ");
	}

	@Override
	public String not(@NotNull Condition.Not condition) {
		return "NOT (" + condition.condition().accept(this) + ")";
	}

	/**
	 * Refuses a column the table lacks, or one whose type has no order.
	 *
	 * @throws IllegalArgumentException when the column is missing or has no order
	 */
	static void requireOrdered(TableDefinition table, String name, String use) {
		ColumnDefinition column = requireColumn(table, name);
		if (!Comparison.orders(column.getType()))
			throw new IllegalArgumentException("Column " + name + " of " + table.getName() + " holds " + column.getType()
					+ ", which has no order and cannot be " + use);
	}

	private static ColumnDefinition requireColumn(TableDefinition table, String name) {
		ColumnDefinition column = table.column(name);
		if (column == null)
			throw new IllegalArgumentException("Table " + table.getName() + " has no column " + name);

		return column;
	}

	private String joined(List<Condition> conditions, String separator) {
		StringJoiner joiner = new StringJoiner(separator);
		for (Condition condition : conditions)
			joiner.add(nested(condition));

		return joiner.toString();
	}

	private String nested(Condition condition) {
		String sql = condition.accept(this);
		if (condition instanceof Condition.All || condition instanceof Condition.Any) return "(" + sql + ")";

		return sql;
	}

	private static String operator(Comparison comparison) {
		return switch (comparison) {
			case EQUALS -> " = ?";
			case NOT_EQUALS -> " <> ?";
			case IS_NULL -> " IS NULL";
			case IS_NOT_NULL -> " IS NOT NULL";
			case BELOW -> " < ?";
			case ABOVE -> " > ?";
			case AT_MOST -> " <= ?";
			case AT_LEAST -> " >= ?";
		};
	}
}
