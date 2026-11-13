package me.whereareiam.dialectica.dialect.postgresql;

import me.whereareiam.dialectica.common.dialect.StatementCases;
import me.whereareiam.dialectica.dialect.SqlStatement;
import me.whereareiam.dialectica.dialect.StatementRenderer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.function.Function;
import java.util.stream.Stream;

import static me.whereareiam.dialectica.common.dialect.StatementCases.insertThenUpdate;
import static me.whereareiam.dialectica.common.dialect.StatementCases.onConflict;
import static me.whereareiam.dialectica.common.dialect.StatementCases.statement;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PostgresqlStatementRendererTest {
	private final StatementRenderer renderer = new PostgresqlDialect().statementRenderer();

	static Stream<Arguments> cases() {
		return StatementCases.expect(
				statement(
						"INSERT INTO identica_account_reservations (reservation_key, unique_id, created_at, expires_at) VALUES (?, ?, ?, ?)",
						"reservation_key", "unique_id", "created_at", "expires_at"
				),
				onConflict(
						"INSERT INTO identica_account_reservations (reservation_key, unique_id, created_at, expires_at) VALUES (?, ?, ?, ?) ON CONFLICT (reservation_key) DO UPDATE SET unique_id = excluded.unique_id, created_at = excluded.created_at, expires_at = excluded.expires_at",
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
						"INSERT INTO identica_provider_links (provider_id, provider_subject, unique_id, is_primary) VALUES (?, ?, ?, ?) ON CONFLICT (provider_id, provider_subject) DO UPDATE SET is_primary = excluded.is_primary",
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
						"INSERT INTO identica_flags (unique_id, \"order\") VALUES (?, ?) ON CONFLICT (unique_id, \"order\") DO NOTHING",
						"unique_id", "order"
				),
				statement(
						"SELECT unique_id, \"order\" FROM identica_flags WHERE unique_id = ? AND \"order\" = ?",
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
						"UPDATE identica_settings SET \"order\" = ? WHERE unique_id = ? AND \"order\" = ?",
						"order", "unique_id", "order"
				),
				statement(
						"UPDATE identica_account_reservations SET expires_at = ? WHERE reservation_key = ? AND expires_at < ? AND expires_at > ? AND created_at <= ? AND created_at >= ?",
						"expires_at", "reservation_key", "expires_at", "expires_at", "created_at", "created_at"
				),
				statement(
						"INSERT INTO identica_account_reservations (reservation_key, unique_id, created_at, expires_at) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING",
						"reservation_key", "unique_id", "created_at", "expires_at"
				),
				insertThenUpdate(
						statement("INSERT INTO identica_provider_links (provider_id, provider_subject, unique_id, is_primary) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING", "provider_id", "provider_subject", "unique_id", "is_primary"),
						statement("UPDATE identica_provider_links SET is_primary = ? WHERE provider_id = ? AND provider_subject = ?", "is_primary", "provider_id", "provider_subject")
				)
		);
	}

	static Stream<Arguments> ranges() {
		return StatementCases.ranges(
				statement("SELECT reservation_key, unique_id, created_at, expires_at FROM identica_account_reservations WHERE expires_at <= ? ORDER BY expires_at ASC, reservation_key DESC OFFSET 40 ROWS FETCH FIRST 20 ROWS ONLY", "expires_at"),
				statement("SELECT reservation_key, unique_id, created_at, expires_at FROM identica_account_reservations WHERE expires_at <= ? FETCH FIRST 5 ROWS ONLY", "expires_at"),
				statement("SELECT reservation_key, unique_id, created_at, expires_at FROM identica_account_reservations WHERE expires_at <= ? OFFSET 3 ROWS", "expires_at"),
				statement("SELECT 1 FROM identica_account_reservations WHERE expires_at <= ? FETCH FIRST 1 ROWS ONLY", "expires_at")
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
}
