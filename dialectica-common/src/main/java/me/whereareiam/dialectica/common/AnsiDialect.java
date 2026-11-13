package me.whereareiam.dialectica.common;

import me.whereareiam.dialectica.Dialect;
import me.whereareiam.dialectica.schema.type.ColumnType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * What the supported dialects share: how names are quoted and how values are bound and read. A
 * dialect supplies its name, how it recognizes its database and its renderers, and overrides only
 * what it does differently, such as storing UUIDs as text. Instants are written to the
 * microsecond; finer parts are cut off rather than left to each database to round.
 */
public abstract class AnsiDialect implements Dialect {
	private static final DateTimeFormatter INSTANT_TEXT = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC);
	private static final DateTimeFormatter DATE_TEXT = DateTimeFormatter.ofPattern("uuuu-MM-dd");
	private static final Pattern BARE_IDENTIFIER = Pattern.compile("[a-z_][a-z0-9_]*");

	/**
	 * Words reserved by at least one supported dialect. An identifier among them is quoted on every
	 * dialect, so a model renders the same names everywhere.
	 */
	private static final Set<String> RESERVED_WORDS = Set.of(
			"_rowid_", "add", "all", "alter", "analyze", "and", "any", "array", "as", "asc", "asymmetric",
			"authorization", "before", "between", "bigint", "binary", "blob", "both", "by", "call", "cascade",
			"case", "cast", "change", "char", "character", "check", "collate", "column", "condition",
			"constraint", "continue", "convert", "create", "cross", "current", "current_date", "current_time",
			"current_timestamp", "current_user", "cursor", "database", "databases", "day", "dec", "decimal",
			"declare", "default", "delete", "desc", "describe", "distinct", "div", "double", "drop", "dual",
			"each", "else", "elseif", "end", "escape", "except", "exists", "exit", "explain",
			"false", "fetch", "float", "for", "force", "foreign", "from", "full", "fulltext", "function",
			"generated", "glob", "grant", "group", "groups", "having", "hour", "if", "ignore", "ilike", "in",
			"index", "inner", "insert", "int", "integer", "intersect", "interval", "into", "is", "join", "key",
			"keys", "kill", "lateral", "leading", "left", "like", "limit", "localtime", "localtimestamp", "lock",
			"long", "loop", "match", "minus", "minute", "month", "natural", "not", "null", "numeric",
			"of", "offset", "on", "only", "or", "order", "out", "outer", "over", "partition", "primary",
			"procedure", "qualify", "range", "read", "real", "recursive", "references", "regexp", "release",
			"rename", "repeat", "replace", "require", "restrict", "return", "returning", "revoke", "right",
			"rlike", "row", "rownum", "rows", "schema", "second", "select", "session_user", "set", "show",
			"smallint", "some", "sql", "symmetric", "system_user", "table", "then", "to", "top", "trailing",
			"trigger", "true", "uescape", "union", "unique", "unknown", "unlock", "unsigned", "update", "usage",
			"use", "user", "using", "value", "values", "varchar", "when", "where", "while", "window", "with",
			"write", "year"
	);

	@Override
	public @NotNull String identifier(@NotNull String name) {
		if (BARE_IDENTIFIER.matcher(name).matches() && !RESERVED_WORDS.contains(name)) return name;

		String quote = String.valueOf(quote());

		return quote + name.replace(quote, quote + quote) + quote;
	}

	@Override
	public void bind(@NotNull PreparedStatement statement, int index, @NotNull ColumnType type, @Nullable Object value) throws SQLException {
		if (value == null) {
			statement.setNull(index, sqlType(type));
			return;
		}

		if (type instanceof ColumnType.UuidType) bindUuid(statement, index, (UUID) value);
		else if (type instanceof ColumnType.VarcharType || type instanceof ColumnType.TextType) statement.setString(index, (String) value);
		else if (type instanceof ColumnType.BigintType) statement.setLong(index, (Long) value);
		else if (type instanceof ColumnType.IntegerType) statement.setInt(index, (Integer) value);
		else if (type instanceof ColumnType.BoolType) statement.setBoolean(index, (Boolean) value);
		else if (type instanceof ColumnType.InstantType) bindInstant(statement, index, ((Instant) value).truncatedTo(ChronoUnit.MICROS));
		else if (type instanceof ColumnType.DateType) bindDate(statement, index, (LocalDate) value);
		else throw new IllegalArgumentException("Unknown column type " + type);
	}

	@Override
	public @Nullable Object read(@NotNull ResultSet row, int column, @NotNull ColumnType type) throws SQLException {
		Object value;
		if (type instanceof ColumnType.UuidType) value = readUuid(row, column);
		else if (type instanceof ColumnType.VarcharType || type instanceof ColumnType.TextType) value = row.getString(column);
		else if (type instanceof ColumnType.BigintType) value = row.getLong(column);
		else if (type instanceof ColumnType.IntegerType) value = row.getInt(column);
		else if (type instanceof ColumnType.BoolType) value = row.getBoolean(column);
		else if (type instanceof ColumnType.InstantType) value = readInstant(row, column);
		else if (type instanceof ColumnType.DateType) value = readDate(row, column);
		else throw new IllegalArgumentException("Unknown column type " + type);

		return row.wasNull() ? null : value;
	}

	/**
	 * Recognises the SQL standard's state of a unique violation, {@code 23505}, which PostgreSQL and
	 * H2 report.
	 */
	@Override
	public boolean isKeyConflict(@NotNull SQLException exception) {
		return "23505".equals(exception.getSQLState());
	}

	@Override
	public String toString() {
		return name();
	}

	/**
	 * Returns the character the dialect quotes identifiers with.
	 *
	 * @return the quote character
	 */
	protected char quote() {
		return '"';
	}

	/**
	 * Returns the {@link Types JDBC type} a {@code NULL} of a column type is bound as.
	 *
	 * @param type column type
	 * @return the JDBC type
	 */
	protected int sqlType(@NotNull ColumnType type) {
		if (type instanceof ColumnType.UuidType) return Types.OTHER;
		if (type instanceof ColumnType.VarcharType) return Types.VARCHAR;
		if (type instanceof ColumnType.TextType) return Types.LONGVARCHAR;
		if (type instanceof ColumnType.BigintType) return Types.BIGINT;
		if (type instanceof ColumnType.IntegerType) return Types.INTEGER;
		if (type instanceof ColumnType.InstantType) return Types.TIMESTAMP_WITH_TIMEZONE;
		if (type instanceof ColumnType.DateType) return Types.DATE;

		return Types.BOOLEAN;
	}

	/**
	 * Binds an instant, as a timestamp with time zone in UTC by default.
	 *
	 * @param statement statement to bind to
	 * @param index     1-based index of the parameter
	 * @param value     instant to bind, already cut to the microsecond
	 * @throws SQLException when the driver refuses the value
	 */
	protected void bindInstant(@NotNull PreparedStatement statement, int index, @NotNull Instant value) throws SQLException {
		statement.setObject(index, OffsetDateTime.ofInstant(value, ZoneOffset.UTC));
	}

	/**
	 * Reads an instant, from a timestamp with time zone by default.
	 *
	 * @param row    result set positioned on a row
	 * @param column 1-based index of the column
	 * @return the instant, or {@code null}
	 * @throws SQLException when the value cannot be read
	 */
	protected @Nullable Instant readInstant(@NotNull ResultSet row, int column) throws SQLException {
		OffsetDateTime value = row.getObject(column, OffsetDateTime.class);

		return value == null ? null : value.toInstant();
	}

	/**
	 * Binds a date, natively by default.
	 *
	 * @param statement statement to bind to
	 * @param index     1-based index of the parameter
	 * @param value     date to bind
	 * @throws SQLException when the driver refuses the value
	 */
	protected void bindDate(@NotNull PreparedStatement statement, int index, @NotNull LocalDate value) throws SQLException {
		statement.setObject(index, value);
	}

	/**
	 * Reads a date, natively by default.
	 *
	 * @param row    result set positioned on a row
	 * @param column 1-based index of the column
	 * @return the date, or {@code null}
	 * @throws SQLException when the value cannot be read
	 */
	protected @Nullable LocalDate readDate(@NotNull ResultSet row, int column) throws SQLException {
		return row.getObject(column, LocalDate.class);
	}

	/**
	 * Binds an instant as ISO-8601 text of a fixed width in UTC, {@code 2026-11-14T09:30:00.000000Z},
	 * which sorts in time order, for dialects that store instants as text.
	 *
	 * @param statement statement to bind to
	 * @param index     1-based index of the parameter
	 * @param value     instant to bind, already cut to the microsecond
	 * @throws SQLException when the driver refuses the value
	 */
	protected static void bindInstantAsText(@NotNull PreparedStatement statement, int index, @NotNull Instant value) throws SQLException {
		statement.setString(index, INSTANT_TEXT.format(value));
	}

	/**
	 * Reads an instant stored as ISO-8601 text.
	 *
	 * @param row    result set positioned on a row
	 * @param column 1-based index of the column
	 * @return the instant, or {@code null}
	 * @throws SQLException when the value cannot be read
	 */
	protected static @Nullable Instant readInstantAsText(@NotNull ResultSet row, int column) throws SQLException {
		String text = row.getString(column);

		return text == null ? null : Instant.parse(text);
	}

	/**
	 * Binds a date as ISO-8601 text, {@code 2026-11-14}, for dialects that store dates as text.
	 *
	 * @param statement statement to bind to
	 * @param index     1-based index of the parameter
	 * @param value     date to bind
	 * @throws SQLException when the driver refuses the value
	 */
	protected static void bindDateAsText(@NotNull PreparedStatement statement, int index, @NotNull LocalDate value) throws SQLException {
		statement.setString(index, DATE_TEXT.format(value));
	}

	/**
	 * Reads a date stored as ISO-8601 text.
	 *
	 * @param row    result set positioned on a row
	 * @param column 1-based index of the column
	 * @return the date, or {@code null}
	 * @throws SQLException when the value cannot be read
	 */
	protected static @Nullable LocalDate readDateAsText(@NotNull ResultSet row, int column) throws SQLException {
		String text = row.getString(column);

		return text == null ? null : LocalDate.parse(text);
	}

	/**
	 * Binds a UUID, natively by default.
	 *
	 * @param statement statement to bind to
	 * @param index     1-based index of the parameter
	 * @param value     UUID to bind
	 * @throws SQLException when the driver refuses the value
	 */
	protected void bindUuid(@NotNull PreparedStatement statement, int index, @NotNull UUID value) throws SQLException {
		statement.setObject(index, value);
	}

	/**
	 * Reads a UUID, natively by default.
	 *
	 * @param row    result set positioned on a row
	 * @param column 1-based index of the column
	 * @return the UUID, or {@code null}
	 * @throws SQLException when the value cannot be read
	 */
	protected @Nullable UUID readUuid(@NotNull ResultSet row, int column) throws SQLException {
		return row.getObject(column, UUID.class);
	}

	/**
	 * Binds a UUID as its 36-character text, for dialects that store UUIDs as text.
	 *
	 * @param statement statement to bind to
	 * @param index     1-based index of the parameter
	 * @param value     UUID to bind
	 * @throws SQLException when the driver refuses the value
	 */
	protected static void bindUuidAsText(@NotNull PreparedStatement statement, int index, @NotNull UUID value) throws SQLException {
		statement.setString(index, value.toString());
	}

	/**
	 * Reads a UUID stored as its 36-character text.
	 *
	 * @param row    result set positioned on a row
	 * @param column 1-based index of the column
	 * @return the UUID, or {@code null}
	 * @throws SQLException when the value cannot be read
	 */
	protected static @Nullable UUID readUuidAsText(@NotNull ResultSet row, int column) throws SQLException {
		String text = row.getString(column);

		return text == null ? null : UUID.fromString(text);
	}
}
