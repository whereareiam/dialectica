package me.whereareiam.dialectica.dialect;

import me.whereareiam.dialectica.schema.type.ColumnType;
import org.jetbrains.annotations.NotNull;

/**
 * How a {@link Condition.Comparing} compares a column's current value. Every comparison but
 * {@link #IS_NULL} and {@link #IS_NOT_NULL} compares with a parameter; a column that is {@code NULL}
 * satisfies none of them, {@link #NOT_EQUALS} included.
 */
public enum Comparison {
	/**
	 * The column equals the parameter: {@code column = ?}.
	 */
	EQUALS,

	/**
	 * The column holds a value other than the parameter: {@code column <> ?}.
	 */
	NOT_EQUALS,

	/**
	 * The column is {@code NULL}: {@code column IS NULL}. Takes no parameter.
	 */
	IS_NULL,

	/**
	 * The column holds a value: {@code column IS NOT NULL}. Takes no parameter.
	 */
	IS_NOT_NULL,

	/**
	 * The column is less than the parameter: {@code column < ?}.
	 */
	BELOW,

	/**
	 * The column is greater than the parameter: {@code column > ?}.
	 */
	ABOVE,

	/**
	 * The column is less than or equal to the parameter: {@code column <= ?}.
	 */
	AT_MOST,

	/**
	 * The column is greater than or equal to the parameter: {@code column >= ?}.
	 */
	AT_LEAST;

	/**
	 * Returns whether the comparison compares with a parameter.
	 *
	 * @return {@code false} for {@link #IS_NULL} and {@link #IS_NOT_NULL}, {@code true} otherwise
	 */
	public boolean takesValue() {
		return this != IS_NULL && this != IS_NOT_NULL;
	}

	/**
	 * Returns whether the comparison orders values, which only columns of an
	 * {@linkplain #orders(ColumnType) ordered type} allow.
	 *
	 * @return {@code true} for {@link #BELOW}, {@link #ABOVE}, {@link #AT_MOST} and {@link #AT_LEAST}
	 */
	public boolean isOrdered() {
		return this == BELOW || this == ABOVE || this == AT_MOST || this == AT_LEAST;
	}

	/**
	 * Tells whether the values of a column type have an order that ordered comparisons and
	 * {@link Ordering orderings} follow: numbers ({@link ColumnType#bigint()},
	 * {@link ColumnType#integer()}) by value, instants ({@link ColumnType#instant()}) and dates
	 * ({@link ColumnType#date()}) in time, and text ({@link ColumnType#varchar(int)},
	 * {@link ColumnType#text()}) by the collation of the database, which differs between databases;
	 * MySQL and MariaDB, for example, ignore case by default. UUIDs and booleans have no order.
	 *
	 * @param type column type
	 * @return whether its values are ordered
	 */
	public static boolean orders(@NotNull ColumnType type) {
		return type instanceof ColumnType.BigintType
				|| type instanceof ColumnType.IntegerType
				|| type instanceof ColumnType.VarcharType
				|| type instanceof ColumnType.TextType
				|| type instanceof ColumnType.InstantType
				|| type instanceof ColumnType.DateType;
	}
}
