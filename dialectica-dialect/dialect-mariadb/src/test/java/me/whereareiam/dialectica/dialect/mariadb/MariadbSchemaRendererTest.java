package me.whereareiam.dialectica.dialect.mariadb;

import me.whereareiam.dialectica.common.dialect.SchemaCases;
import me.whereareiam.dialectica.dialect.SchemaRenderer;
import me.whereareiam.dialectica.schema.change.SchemaChange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MariadbSchemaRendererTest {
	private final SchemaRenderer renderer = new MariadbDialect().schemaRenderer();

	static Stream<Arguments> cases() {
		return SchemaCases.expect(
				List.of(
						"CREATE TABLE identica_accounts (unique_id CHAR(36) NOT NULL, username VARCHAR(64) NOT NULL, created_at BIGINT, PRIMARY KEY (unique_id))",
						"CREATE INDEX identica_accounts_username ON identica_accounts (username)"
				),
				List.of(
						"CREATE TABLE identica_provider_links (provider_id VARCHAR(64) NOT NULL, provider_subject VARCHAR(128) NOT NULL, unique_id CHAR(36) NOT NULL, is_primary BOOLEAN DEFAULT FALSE NOT NULL, attempts INT DEFAULT 0 NOT NULL, note TEXT, PRIMARY KEY (provider_id, provider_subject), UNIQUE (unique_id, provider_id), FOREIGN KEY (unique_id) REFERENCES identica_accounts (unique_id) ON DELETE CASCADE)"
				),
				List.of(
						"CREATE TABLE identica_sessions (id BIGINT NOT NULL, unique_id CHAR(36), parent_id BIGINT, `key` VARCHAR(32), PRIMARY KEY (id), FOREIGN KEY (unique_id) REFERENCES identica_accounts (unique_id) ON DELETE SET NULL, FOREIGN KEY (parent_id) REFERENCES identica_sessions (id) ON DELETE RESTRICT, FOREIGN KEY (parent_id) REFERENCES identica_sessions (id) ON DELETE NO ACTION)"
				),
				List.of(
						"DROP TABLE identica_accounts"
				),
				List.of(
						"ALTER TABLE identica_cracked_accounts RENAME TO identica_accounts"
				),
				List.of(
						"ALTER TABLE identica_accounts ADD COLUMN last_seen_at BIGINT"
				),
				List.of(
						"ALTER TABLE identica_accounts ADD COLUMN is_locked BOOLEAN DEFAULT TRUE NOT NULL"
				),
				List.of(
						"ALTER TABLE identica_accounts ADD COLUMN motto TEXT DEFAULT 'it''s a\\\\b'"
				),
				List.of(
						"ALTER TABLE identica_accounts DROP COLUMN created_at"
				),
				List.of(
						"ALTER TABLE identica_sessions DROP COLUMN `key`"
				),
				List.of(
						"CREATE UNIQUE INDEX identica_provider_links_primary ON identica_provider_links (unique_id, is_primary)"
				),
				List.of(
						"CREATE INDEX legacy_order ON `Legacy Table` (`order`)"
				),
				List.of(
						"DROP INDEX identica_accounts_username ON identica_accounts"
				),
				List.of(
						"CREATE TABLE identica_account_events (event_key VARCHAR(64) NOT NULL, happened_at DATETIME(6) NOT NULL, due_on DATE, PRIMARY KEY (event_key))"
				)
		);
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("cases")
	void renders(String name, SchemaChange change, List<String> expected) {
		assertEquals(expected, renderer.render(change));
	}

	@Test
	void addsARequiredColumnWithoutADefault() {
		assertEquals(
				List.of("ALTER TABLE identica_accounts ADD COLUMN source VARCHAR(32) NOT NULL"),
				renderer.render(SchemaCases.ADD_REQUIRED_WITHOUT_DEFAULT)
		);
	}
}
