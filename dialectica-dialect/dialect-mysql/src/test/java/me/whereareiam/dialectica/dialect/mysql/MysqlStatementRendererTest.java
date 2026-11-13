package me.whereareiam.dialectica.dialect.mysql;

import me.whereareiam.dialectica.common.dialect.StatementCases;
import me.whereareiam.dialectica.dialect.SqlStatement;
import me.whereareiam.dialectica.dialect.StatementRenderer;
import me.whereareiam.dialectica.schema.ColumnDefinition;
import me.whereareiam.dialectica.schema.IndexDefinition;
import me.whereareiam.dialectica.schema.TableDefinition;
import me.whereareiam.dialectica.schema.type.ColumnType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static me.whereareiam.dialectica.common.dialect.StatementCases.insertThenUpdate;
import static me.whereareiam.dialectica.common.dialect.StatementCases.onConflict;
import static me.whereareiam.dialectica.common.dialect.StatementCases.statement;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MysqlStatementRendererTest {
	private final StatementRenderer renderer = new MysqlDialect().statementRenderer();

	static Stream<Arguments> cases() {
		return StatementCases.expect(
				statement(
						"INSERT INTO identica_account_reservations (reservation_key, unique_id, created_at, expires_at) VALUES (?, ?, ?, ?)",
						"reservation_key", "unique_id", "created_at", "expires_at"
				),
				onConflict(
						"INSERT INTO identica_account_reservations (reservation_key, unique_id, created_at, expires_at) VALUES (?, ?, ?, ?) AS excluded ON DUPLICATE KEY UPDATE unique_id = excluded.unique_id, created_at = excluded.created_at, expires_at = excluded.expires_at",
						"reservation_key", "unique_id", "created_at", "expires_at"
				),
				statement(
						"UPDATE identica_account_reservations SET unique_id = ?, created_at = ?, expires_at = ? WHERE reservation_key = ?",
						"unique_id", "created_at", "expires_at", "reservation_key"
				),
				statement(
						"SELECT reservation_key, unique_id, created_at, expires_at FROM identica_account_reservations WHERE reservation_key = ?",
						"reservation_key"
				),
				statement(
						"DELETE FROM identica_account_reservations WHERE reservation_key = ?",
						"reservation_key"
				),
				statement(
						"INSERT INTO identica_provider_links (provider_id, provider_subject, unique_id, is_primary) VALUES (?, ?, ?, ?)",
						"provider_id", "provider_subject", "unique_id", "is_primary"
				),
				onConflict(
						"INSERT INTO identica_provider_links (provider_id, provider_subject, unique_id, is_primary) VALUES (?, ?, ?, ?) AS excluded ON DUPLICATE KEY UPDATE is_primary = excluded.is_primary",
						"provider_id", "provider_subject", "unique_id", "is_primary"
				),
				statement(
						"UPDATE identica_provider_links SET unique_id = ?, is_primary = ? WHERE provider_id = ? AND provider_subject = ?",
						"unique_id", "is_primary", "provider_id", "provider_subject"
				),
				statement(
						"SELECT provider_id, provider_subject, unique_id, is_primary FROM identica_provider_links WHERE provider_id = ? AND provider_subject = ?",
						"provider_id", "provider_subject"
				),
				statement(
						"DELETE FROM identica_provider_links WHERE provider_id = ? AND provider_subject = ?",
						"provider_id", "provider_subject"
				),
				onConflict(
						"INSERT INTO identica_flags (unique_id, `order`) VALUES (?, ?) AS excluded ON DUPLICATE KEY UPDATE unique_id = unique_id",
						"unique_id", "order"
				),
				statement(
						"SELECT unique_id, `order` FROM identica_flags WHERE unique_id = ? AND `order` = ?",
						"unique_id", "order"
				),
				statement(
						"UPDATE identica_account_reservations SET unique_id = ?, expires_at = ? WHERE reservation_key = ? AND expires_at = ?",
						"unique_id", "expires_at", "reservation_key", "expires_at"
				),
				statement(
						"UPDATE identica_provider_links SET is_primary = ? WHERE provider_id = ? AND provider_subject = ? AND unique_id IS NULL",
						"is_primary", "provider_id", "provider_subject"
				),
				statement(
						"UPDATE identica_settings SET `order` = ? WHERE unique_id = ? AND `order` = ?",
						"order", "unique_id", "order"
				),
				statement(
						"UPDATE identica_account_reservations SET expires_at = ? WHERE reservation_key = ? AND expires_at < ? AND expires_at > ? AND created_at <= ? AND created_at >= ?",
						"expires_at", "reservation_key", "expires_at", "expires_at", "created_at", "created_at"
				),
				statement(
						"INSERT INTO identica_account_reservations (reservation_key, unique_id, created_at, expires_at) VALUES (?, ?, ?, ?)",
						"reservation_key", "unique_id", "created_at", "expires_at"
				),
				insertThenUpdate(
						statement("INSERT INTO identica_provider_links (provider_id, provider_subject, unique_id, is_primary) VALUES (?, ?, ?, ?)", "provider_id", "provider_subject", "unique_id", "is_primary"),
						statement("UPDATE identica_provider_links SET is_primary = ? WHERE provider_id = ? AND provider_subject = ?", "is_primary", "provider_id", "provider_subject")
				)
		);
	}

	static Stream<Arguments> ranges() {
		return StatementCases.ranges(
				statement("SELECT reservation_key, unique_id, created_at, expires_at FROM identica_account_reservations WHERE expires_at <= ? ORDER BY expires_at ASC, reservation_key DESC LIMIT 20 OFFSET 40", "expires_at"),
				statement("SELECT reservation_key, unique_id, created_at, expires_at FROM identica_account_reservations WHERE expires_at <= ? LIMIT 5", "expires_at"),
				statement("SELECT reservation_key, unique_id, created_at, expires_at FROM identica_account_reservations WHERE expires_at <= ? LIMIT 18446744073709551615 OFFSET 3", "expires_at"),
				statement("SELECT 1 FROM identica_account_reservations WHERE expires_at <= ? LIMIT 1", "expires_at")
		);
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource({"cases", "ranges", "me.whereareiam.dialectica.common.dialect.StatementCases#shared"})
	void renders(String name, Function<StatementRenderer, Object> render, Object expected) {
		assertEquals(expected, render.apply(renderer));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("me.whereareiam.dialectica.common.dialect.StatementCases#refusals")
	void refuses(String name, Function<StatementRenderer, Object> render) {
		assertThrows(IllegalArgumentException.class, () -> render.apply(renderer));
	}

	@Test
	void insertsThenUpdatesATableWithAUniqueIndex() {
		TableDefinition indexed = TableDefinition.builder("identica_accounts")
				.column(ColumnDefinition.builder("unique_id", ColumnType.uuid()).notNull().build())
				.column(ColumnDefinition.builder("username", ColumnType.varchar(64)).notNull().build())
				.primaryKey("unique_id")
				.index(IndexDefinition.unique("identica_accounts_username", "username"))
				.build();

		assertEquals(insertThenUpdate(
				statement("INSERT INTO identica_accounts (unique_id, username) VALUES (?, ?)", "unique_id", "username"),
				statement("UPDATE identica_accounts SET username = ? WHERE unique_id = ?", "username", "unique_id")
		), renderer.upsert(indexed, List.of("username")));
	}
}
