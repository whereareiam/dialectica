package me.whereareiam.dialectica.common.dialect;

import me.whereareiam.dialectica.schema.ColumnDefinition;
import me.whereareiam.dialectica.schema.ForeignKeyDefinition;
import me.whereareiam.dialectica.schema.IndexDefinition;
import me.whereareiam.dialectica.schema.TableDefinition;
import me.whereareiam.dialectica.schema.change.AddColumn;
import me.whereareiam.dialectica.schema.change.CreateIndex;
import me.whereareiam.dialectica.schema.change.CreateTable;
import me.whereareiam.dialectica.schema.change.DropColumn;
import me.whereareiam.dialectica.schema.change.DropIndex;
import me.whereareiam.dialectica.schema.change.DropTable;
import me.whereareiam.dialectica.schema.change.RenameTable;
import me.whereareiam.dialectica.schema.change.SchemaChange;
import me.whereareiam.dialectica.schema.type.ColumnType;
import me.whereareiam.dialectica.schema.type.ReferentialAction;
import org.junit.jupiter.params.provider.Arguments;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * The schema changes every dialect's schema renderer test renders, each paired by name with the
 * statements the dialect expects.
 */
public final class SchemaCases {
	public static final TableDefinition ACCOUNTS = TableDefinition.builder("identica_accounts")
			.column(ColumnDefinition.builder("unique_id", ColumnType.uuid()).notNull().build())
			.column(ColumnDefinition.builder("username", ColumnType.varchar(64)).notNull().build())
			.column(ColumnDefinition.builder("created_at", ColumnType.bigint()).build())
			.primaryKey("unique_id")
			.index(IndexDefinition.plain("identica_accounts_username", "username"))
			.build();

	public static final TableDefinition LINKS = TableDefinition.builder("identica_provider_links")
			.column(ColumnDefinition.builder("provider_id", ColumnType.varchar(64)).notNull().build())
			.column(ColumnDefinition.builder("provider_subject", ColumnType.varchar(128)).notNull().build())
			.column(ColumnDefinition.builder("unique_id", ColumnType.uuid()).notNull().build())
			.column(ColumnDefinition.builder("is_primary", ColumnType.bool()).notNull().defaultValue(false).build())
			.column(ColumnDefinition.builder("attempts", ColumnType.integer()).notNull().defaultValue(0).build())
			.column(ColumnDefinition.builder("note", ColumnType.text()).build())
			.primaryKey("provider_id", "provider_subject")
			.unique("unique_id", "provider_id")
			.foreignKey(ForeignKeyDefinition.builder("unique_id")
					.references("identica_accounts", "unique_id")
					.onDelete(ReferentialAction.CASCADE)
					.build())
			.build();

	public static final TableDefinition SESSIONS = TableDefinition.builder("identica_sessions")
			.column(ColumnDefinition.builder("id", ColumnType.bigint()).notNull().build())
			.column(ColumnDefinition.builder("unique_id", ColumnType.uuid()).build())
			.column(ColumnDefinition.builder("parent_id", ColumnType.bigint()).build())
			.column(ColumnDefinition.builder("key", ColumnType.varchar(32)).build())
			.primaryKey("id")
			.foreignKey(ForeignKeyDefinition.builder("unique_id")
					.references("identica_accounts", "unique_id")
					.onDelete(ReferentialAction.SET_NULL)
					.build())
			.foreignKey(ForeignKeyDefinition.builder("parent_id")
					.references("identica_sessions", "id")
					.onDelete(ReferentialAction.RESTRICT)
					.build())
			.foreignKey(ForeignKeyDefinition.builder("parent_id")
					.references("identica_sessions", "id")
					.build())
			.build();

	public static final TableDefinition EVENTS = TableDefinition.builder("identica_account_events")
			.column(ColumnDefinition.builder("event_key", ColumnType.varchar(64)).notNull().build())
			.column(ColumnDefinition.builder("happened_at", ColumnType.instant()).notNull().build())
			.column(ColumnDefinition.builder("due_on", ColumnType.date()).build())
			.primaryKey("event_key")
			.build();

	public static final AddColumn ADD_NULLABLE = new AddColumn("identica_accounts",
			ColumnDefinition.builder("last_seen_at", ColumnType.bigint()).build());

	public static final AddColumn ADD_REQUIRED = new AddColumn("identica_accounts",
			ColumnDefinition.builder("is_locked", ColumnType.bool()).notNull().defaultValue(true).build());

	public static final AddColumn ADD_TEXT = new AddColumn("identica_accounts",
			ColumnDefinition.builder("motto", ColumnType.text()).defaultValue("it's a\\b").build());

	public static final AddColumn ADD_REQUIRED_WITHOUT_DEFAULT = new AddColumn("identica_accounts",
			ColumnDefinition.builder("source", ColumnType.varchar(32)).notNull().build());

	private SchemaCases() {
	}

	/**
	 * Pairs every case with its expected statements, given in the order of {@link #names()}.
	 */
	public static Stream<Arguments> expect(List<?>... statements) {
		List<String> names = names();
		List<SchemaChange> changes = changes();
		if (statements.length != changes.size())
			throw new IllegalArgumentException("Expected " + changes.size() + " cases, not " + statements.length);

		return IntStream.range(0, changes.size())
				.mapToObj(index -> Arguments.of(names.get(index), changes.get(index), statements[index]));
	}

	private static List<String> names() {
		return List.of(
				"create accounts", "create links", "create sessions", "drop table", "rename table",
				"add nullable column", "add required column", "add text column", "drop column",
				"drop reserved column", "create unique index", "create quoted index", "drop index",
				"create a table of instants and dates"
		);
	}

	private static List<SchemaChange> changes() {
		return List.of(
				new CreateTable(ACCOUNTS),
				new CreateTable(LINKS),
				new CreateTable(SESSIONS),
				new DropTable("identica_accounts"),
				new RenameTable("identica_cracked_accounts", "identica_accounts"),
				ADD_NULLABLE,
				ADD_REQUIRED,
				ADD_TEXT,
				new DropColumn("identica_accounts", "created_at"),
				new DropColumn("identica_sessions", "key"),
				new CreateIndex("identica_provider_links", IndexDefinition.unique("identica_provider_links_primary", "unique_id", "is_primary")),
				new CreateIndex("Legacy Table", IndexDefinition.plain("legacy_order", "order")),
				new DropIndex("identica_accounts", "identica_accounts_username"),
				new CreateTable(EVENTS)
		);
	}
}
