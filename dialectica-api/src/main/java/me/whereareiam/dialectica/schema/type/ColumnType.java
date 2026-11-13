package me.whereareiam.dialectica.schema.type;

import org.jetbrains.annotations.NotNull;

/**
 * The type of a column, independent of any dialect. Each renderer maps it to the dialect's own
 * type:
 * <table>
 *     <caption>Rendered types</caption>
 *     <tr><th>Type</th><th>SQLite</th><th>H2</th><th>PostgreSQL</th><th>MySQL, MariaDB</th></tr>
 *     <tr><td>{@link #uuid()}</td><td>TEXT</td><td>UUID</td><td>UUID</td><td>CHAR(36)</td></tr>
 *     <tr><td>{@link #varchar(int) varchar(n)}</td><td>VARCHAR(n)</td><td>VARCHAR(n)</td><td>VARCHAR(n)</td><td>VARCHAR(n)</td></tr>
 *     <tr><td>{@link #text()}</td><td>TEXT</td><td>CLOB</td><td>TEXT</td><td>TEXT</td></tr>
 *     <tr><td>{@link #bigint()}</td><td>INTEGER</td><td>BIGINT</td><td>BIGINT</td><td>BIGINT</td></tr>
 *     <tr><td>{@link #integer()}</td><td>INTEGER</td><td>INTEGER</td><td>INTEGER</td><td>INT</td></tr>
 *     <tr><td>{@link #bool()}</td><td>INTEGER</td><td>BOOLEAN</td><td>BOOLEAN</td><td>BOOLEAN</td></tr>
 *     <tr><td>{@link #instant()}</td><td>TEXT</td><td>TIMESTAMP WITH TIME ZONE</td><td>TIMESTAMP WITH TIME ZONE</td><td>DATETIME(6)</td></tr>
 *     <tr><td>{@link #date()}</td><td>TEXT</td><td>DATE</td><td>DATE</td><td>DATE</td></tr>
 * </table>
 * MySQL, MariaDB and H2 cannot use a {@link #text()} column in a key or an index; use
 * {@link #varchar(int)} for those.
 */
public sealed interface ColumnType permits
		ColumnType.UuidType,
		ColumnType.VarcharType,
		ColumnType.TextType,
		ColumnType.BigintType,
		ColumnType.IntegerType,
		ColumnType.BoolType,
		ColumnType.InstantType,
		ColumnType.DateType {
	/**
	 * Returns the type of a UUID column.
	 *
	 * @return the UUID type
	 */
	static @NotNull ColumnType uuid() {
		return new UuidType();
	}

	/**
	 * Returns the type of a text column limited to a number of characters.
	 *
	 * @param length maximum number of characters, at least 1
	 * @return the limited text type
	 * @throws IllegalArgumentException when the length is below 1
	 */
	static @NotNull ColumnType varchar(int length) {
		return new VarcharType(length);
	}

	/**
	 * Returns the type of a text column without a length limit.
	 *
	 * @return the unlimited text type
	 */
	static @NotNull ColumnType text() {
		return new TextType();
	}

	/**
	 * Returns the type of a 64-bit integer column.
	 *
	 * @return the 64-bit integer type
	 */
	static @NotNull ColumnType bigint() {
		return new BigintType();
	}

	/**
	 * Returns the type of a 32-bit integer column.
	 *
	 * @return the 32-bit integer type
	 */
	static @NotNull ColumnType integer() {
		return new IntegerType();
	}

	/**
	 * Returns the type of a boolean column. SQLite stores it as an integer, 1 for true and 0 for
	 * false.
	 *
	 * @return the boolean type
	 */
	static @NotNull ColumnType bool() {
		return new BoolType();
	}

	/**
	 * Returns the type of a column that holds a point in time, to the microsecond; finer parts of
	 * a written value are cut off. PostgreSQL and H2 store it as {@code TIMESTAMP WITH TIME ZONE},
	 * MySQL and MariaDB as {@code DATETIME(6)} in UTC, which, unlike their {@code TIMESTAMP}, reaches
	 * past 2038, and SQLite as ISO-8601 text of a fixed width in UTC,
	 * {@code 2026-11-14T09:30:00.000000Z}, which sorts in time order. Every database holds the
	 * years 1000 to 9999.
	 *
	 * @return the instant type
	 */
	static @NotNull ColumnType instant() {
		return new InstantType();
	}

	/**
	 * Returns the type of a column that holds a calendar date without a time of day: {@code DATE},
	 * and ISO-8601 text, {@code 2026-11-14}, on SQLite. Every database holds the years 1000 to 9999.
	 *
	 * @return the date type
	 */
	static @NotNull ColumnType date() {
		return new DateType();
	}

	/**
	 * A UUID.
	 */
	record UuidType() implements ColumnType {
		@Override
		public String toString() {
			return "uuid";
		}
	}

	/**
	 * Text limited to a number of characters.
	 *
	 * @param length maximum number of characters
	 */
	record VarcharType(int length) implements ColumnType {
		/**
		 * Checks the length.
		 *
		 * @param length maximum number of characters, at least 1
		 */
		public VarcharType {
			if (length < 1) throw new IllegalArgumentException("A varchar length must be at least 1, not " + length);
		}

		@Override
		public String toString() {
			return "varchar(" + length + ")";
		}
	}

	/**
	 * Text without a length limit.
	 */
	record TextType() implements ColumnType {
		@Override
		public String toString() {
			return "text";
		}
	}

	/**
	 * A 64-bit integer.
	 */
	record BigintType() implements ColumnType {
		@Override
		public String toString() {
			return "bigint";
		}
	}

	/**
	 * A 32-bit integer.
	 */
	record IntegerType() implements ColumnType {
		@Override
		public String toString() {
			return "integer";
		}
	}

	/**
	 * A boolean.
	 */
	record BoolType() implements ColumnType {
		@Override
		public String toString() {
			return "bool";
		}
	}

	/**
	 * A point in time, to the microsecond.
	 */
	record InstantType() implements ColumnType {
		@Override
		public String toString() {
			return "instant";
		}
	}

	/**
	 * A calendar date.
	 */
	record DateType() implements ColumnType {
		@Override
		public String toString() {
			return "date";
		}
	}
}
