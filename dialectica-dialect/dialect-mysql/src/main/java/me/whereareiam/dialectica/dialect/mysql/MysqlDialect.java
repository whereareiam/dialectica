package me.whereareiam.dialectica.dialect.mysql;

import me.whereareiam.dialectica.common.AnsiDialect;
import me.whereareiam.dialectica.dialect.SchemaRenderer;
import me.whereareiam.dialectica.dialect.StatementRenderer;
import me.whereareiam.dialectica.schema.type.ColumnType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * The dialect of MySQL 8.0.19 or newer, which quotes names with backticks and stores UUIDs as
 * {@code CHAR(36)} text. A MariaDB server reached through MySQL's driver reports itself as MySQL
 * and is handled by this dialect.
 */
public class MysqlDialect extends AnsiDialect {
	private final SchemaRenderer schemaRenderer = new MysqlSchemaRenderer(this);
	private final StatementRenderer statementRenderer = new MysqlStatementRenderer(this);

	@Override
	public @NotNull String name() {
		return "MySQL";
	}

	@Override
	public boolean accepts(@NotNull DatabaseMetaData metaData) throws SQLException {
		return "MySQL".equalsIgnoreCase(metaData.getDatabaseProductName());
	}

	@Override
	public @NotNull SchemaRenderer schemaRenderer() {
		return schemaRenderer;
	}

	@Override
	public @NotNull StatementRenderer statementRenderer() {
		return statementRenderer;
	}

	@Override
	protected void bindUuid(@NotNull PreparedStatement statement, int index, @NotNull UUID value) throws SQLException {
		bindUuidAsText(statement, index, value);
	}

	@Override
	protected @Nullable UUID readUuid(@NotNull ResultSet row, int column) throws SQLException {
		return readUuidAsText(row, column);
	}

	/**
	 * Binds an instant as the date and time it is in UTC, which a {@code DATETIME(6)} column holds
	 * without converting it through the session's time zone.
	 */
	@Override
	protected void bindInstant(@NotNull PreparedStatement statement, int index, @NotNull Instant value) throws SQLException {
		statement.setObject(index, LocalDateTime.ofInstant(value, ZoneOffset.UTC));
	}

	/**
	 * Reads an instant from the date and time in UTC a {@code DATETIME(6)} column holds.
	 */
	@Override
	protected @Nullable Instant readInstant(@NotNull ResultSet row, int column) throws SQLException {
		LocalDateTime value = row.getObject(column, LocalDateTime.class);

		return value == null ? null : value.toInstant(ZoneOffset.UTC);
	}

	@Override
	protected int sqlType(@NotNull ColumnType type) {
		if (type instanceof ColumnType.UuidType) return Types.VARCHAR;
		if (type instanceof ColumnType.InstantType) return Types.TIMESTAMP;

		return super.sqlType(type);
	}

	/**
	 * Recognises MySQL's and MariaDB's duplicate entry errors, 1062 and 1586. Their state,
	 * {@code 23000}, also covers other violations, such as a missing referenced row.
	 */
	@Override
	public boolean isKeyConflict(@NotNull SQLException exception) {
		return exception.getErrorCode() == 1062 || exception.getErrorCode() == 1586;
	}

	@Override
	protected char quote() {
		return '`';
	}
}
