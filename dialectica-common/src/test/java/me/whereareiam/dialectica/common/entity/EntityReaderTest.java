package me.whereareiam.dialectica.common.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Value;
import me.whereareiam.dialectica.entity.EntityColumn;
import me.whereareiam.dialectica.entity.EntityTable;
import me.whereareiam.dialectica.entity.Row;
import me.whereareiam.dialectica.entity.annotation.Column;
import me.whereareiam.dialectica.entity.annotation.Default;
import me.whereareiam.dialectica.entity.annotation.ForeignKey;
import me.whereareiam.dialectica.entity.annotation.Id;
import me.whereareiam.dialectica.entity.annotation.Index;
import me.whereareiam.dialectica.entity.annotation.Length;
import me.whereareiam.dialectica.entity.annotation.Nullable;
import me.whereareiam.dialectica.entity.annotation.References;
import me.whereareiam.dialectica.entity.annotation.Table;
import me.whereareiam.dialectica.entity.annotation.Unique;
import me.whereareiam.dialectica.schema.ForeignKeyDefinition;
import me.whereareiam.dialectica.schema.type.ColumnType;
import me.whereareiam.dialectica.schema.type.ReferentialAction;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityReaderTest {
	private static final UUID ACCOUNT = UUID.fromString("00000000-0000-0000-0000-000000000001");

	@Table("identica_accounts")
	@Index(name = "identica_accounts_username", columns = "username")
	record Account(@Id UUID uniqueId, @Length(64) String username, @Nullable Long createdAt, @Nullable Long lastSeenAt) {
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@Table("identica_provider_links")
	@Unique({"uniqueId", "providerId"})
	@Index(name = "identica_provider_links_primary", columns = {"uniqueId", "primary"}, unique = true)
	static class ProviderLink {
		@Id
		@Length(64)
		private String providerId;
		@Id
		@Length(128)
		private String providerSubject;
		@References(entity = Account.class, onDelete = ReferentialAction.CASCADE)
		private UUID uniqueId;
		@Column("is_primary")
		private boolean primary;
		@Nullable
		private Long linkedAt;
		private transient String cached;
		private static final String IGNORED = "ignored";
	}

	@Getter
	@Setter
	@NoArgsConstructor
	@Table("identica_settings")
	static class Setting {
		@Id
		@Length(32)
		private String name;
		@Nullable
		private String value;
		private int revision;
	}

	@Value
	@Table("identica_sessions")
	static class Session {
		@Id
		long id;
		@References(entity = Session.class)
		@Jakarta.Nullable
		Long parentId;
		String token;
	}

	@Table("identica_provider_profiles")
	@ForeignKey(fields = {"subject", "provider"}, entity = ProviderLink.class, onDelete = ReferentialAction.CASCADE)
	@ForeignKey(fields = "owner", entity = Account.class)
	record ProviderProfile(@Length(128) String subject, @Id @Length(64) String provider, @Id @Length(64) String username, UUID owner) {
	}

	@Test
	void readsARecord() {
		EntityTable<Account> table = EntityReader.read(Account.class);

		assertEquals("identica_accounts", table.table().getName());
		assertEquals(List.of("unique_id", "username", "created_at", "last_seen_at"), names(table));
		assertEquals(List.of("unique_id"), table.table().getPrimaryKey());
		assertEquals(ColumnType.uuid(), table.column("unique_id").column().getType());
		assertEquals(ColumnType.varchar(64), table.column("username").column().getType());
		assertFalse(table.column("username").column().isNullable());
		assertTrue(table.column("created_at").column().isNullable());
		assertEquals("identica_accounts_username", table.table().getIndexes().get(0).getName());

		Account account = new Account(ACCOUNT, "Alice", 1L, null);
		assertEquals("Alice", table.column("username").get(account));
		assertEquals(account, table.create(row(table, Map.of("unique_id", ACCOUNT, "username", "Alice", "created_at", 1L))));
	}

	@Test
	void readsALombokClassWithACompositeKey() {
		EntityTable<ProviderLink> table = EntityReader.read(ProviderLink.class);

		assertEquals(List.of("provider_id", "provider_subject", "unique_id", "is_primary", "linked_at"), names(table));
		assertEquals(List.of("provider_id", "provider_subject"), table.table().getPrimaryKey());
		assertEquals(2, table.primaryKey().size());
		assertEquals(ColumnType.bool(), table.column("is_primary").column().getType());
		assertFalse(table.column("is_primary").column().isNullable());
		assertEquals(List.of(List.of("unique_id", "provider_id")), table.table().getUniqueKeys());
		assertEquals(List.of("unique_id", "is_primary"), table.table().getIndexes().get(0).getColumns());
		assertTrue(table.table().getIndexes().get(0).isUnique());

		ForeignKeyDefinition foreignKey = table.table().getForeignKeys().get(0);
		assertEquals("identica_accounts", foreignKey.getReferencedTable());
		assertEquals(List.of("unique_id"), foreignKey.getReferencedColumns());
		assertEquals(ReferentialAction.CASCADE, foreignKey.getOnDelete());

		ProviderLink link = table.create(row(table, Map.of(
				"provider_id", "premium", "provider_subject", "subject", "unique_id", ACCOUNT, "is_primary", true)));
		assertEquals("premium", link.getProviderId());
		assertEquals(ACCOUNT, link.getUniqueId());
		assertTrue(link.isPrimary());
		assertEquals(true, table.column("is_primary").get(link));
	}

	@Test
	void buildsAClassWithoutAllColumnsConstructorThroughItsSetters() {
		EntityTable<Setting> table = EntityReader.read(Setting.class);

		Setting setting = table.create(row(table, Map.of("name", "motd", "revision", 3)));

		assertEquals("motd", setting.getName());
		assertEquals(3, setting.getRevision());
		assertEquals(ColumnType.text(), table.column("value").column().getType());
		assertEquals(ColumnType.integer(), table.column("revision").column().getType());
	}

	@Test
	void readsAValueClassThatReferencesItself() {
		EntityTable<Session> table = EntityReader.read(Session.class);

		assertTrue(table.column("parent_id").column().isNullable());
		assertEquals("identica_sessions", table.table().getForeignKeys().get(0).getReferencedTable());
		assertEquals(ReferentialAction.NO_ACTION, table.table().getForeignKeys().get(0).getOnDelete());
		assertEquals(new Session(7, null, "token"), table.create(row(table, Map.of("id", 7L, "token", "token"))));
	}

	@Test
	void readsForeignKeysToTheWholeKeyOfAnotherEntity() {
		EntityTable<ProviderProfile> table = EntityReader.read(ProviderProfile.class);

		List<ForeignKeyDefinition> foreignKeys = table.table().getForeignKeys();
		assertEquals(2, foreignKeys.size());
		assertEquals(List.of("subject", "provider"), foreignKeys.get(0).getColumns());
		assertEquals("identica_provider_links", foreignKeys.get(0).getReferencedTable());
		assertEquals(List.of("provider_id", "provider_subject"), foreignKeys.get(0).getReferencedColumns());
		assertEquals(ReferentialAction.CASCADE, foreignKeys.get(0).getOnDelete());
		assertEquals(List.of("owner"), foreignKeys.get(1).getColumns());
		assertEquals("identica_accounts", foreignKeys.get(1).getReferencedTable());
		assertEquals(List.of("unique_id"), foreignKeys.get(1).getReferencedColumns());
		assertEquals(ReferentialAction.NO_ACTION, foreignKeys.get(1).getOnDelete());
	}

	@Table("identica_verification_selections")
	@ForeignKey(fields = {"provider", "subject"}, table = "identica_provider_links", columns = {"provider_id", "provider_subject"})
	record ForeignTableReference(
			@Id @References(table = "identica_accounts", column = "unique_id", onDelete = ReferentialAction.CASCADE) UUID uniqueId,
			@Length(64) String provider,
			@Length(128) String subject
	) {
	}

	@Test
	void readsForeignKeysToANamedTable() {
		EntityTable<ForeignTableReference> table = EntityReader.read(ForeignTableReference.class);

		List<ForeignKeyDefinition> foreignKeys = table.table().getForeignKeys();
		assertEquals(2, foreignKeys.size());
		assertEquals(List.of("unique_id"), foreignKeys.get(0).getColumns());
		assertEquals("identica_accounts", foreignKeys.get(0).getReferencedTable());
		assertEquals(List.of("unique_id"), foreignKeys.get(0).getReferencedColumns());
		assertEquals(ReferentialAction.CASCADE, foreignKeys.get(0).getOnDelete());
		assertEquals(List.of("provider", "subject"), foreignKeys.get(1).getColumns());
		assertEquals("identica_provider_links", foreignKeys.get(1).getReferencedTable());
		assertEquals(List.of("provider_id", "provider_subject"), foreignKeys.get(1).getReferencedColumns());
		assertEquals(ReferentialAction.NO_ACTION, foreignKeys.get(1).getOnDelete());
	}

	@Table("identica_settings")
	record Defaulted(
			@Id @Length(32) String name,
			@Default("true") boolean enabled,
			@Default("false") @Nullable Boolean locked,
			@Default("-9000000000") long since,
			@Default("3") Integer attempts,
			@Default("it's a\\b") String note,
			@Default("en") @Length(2) String language,
			@Default("00000000-0000-0000-0000-00000000000A") UUID owner,
			@Nullable Long untouched
	) {
	}

	@Test
	void readsDefaultsByTheFieldsType() {
		EntityTable<Defaulted> table = EntityReader.read(Defaulted.class);

		assertEquals(true, table.column("enabled").column().getDefaultValue());
		assertEquals(false, table.column("locked").column().getDefaultValue());
		assertEquals(-9_000_000_000L, table.column("since").column().getDefaultValue());
		assertEquals(3L, table.column("attempts").column().getDefaultValue());
		assertEquals("it's a\\b", table.column("note").column().getDefaultValue());
		assertEquals("en", table.column("language").column().getDefaultValue());
		assertEquals("00000000-0000-0000-0000-00000000000a", table.column("owner").column().getDefaultValue());
		assertNull(table.column("untouched").column().getDefaultValue());
		assertNull(table.column("name").column().getDefaultValue());
		assertFalse(table.column("enabled").column().isNullable());
		assertTrue(table.column("locked").column().isNullable());
	}

	@Table("broken")
	record DefaultBooleanOfAnotherWord(@Id UUID id, @Default("yes") boolean enabled) {
	}

	@Table("broken")
	record DefaultNumberOfText(@Id UUID id, @Default("zero") long since) {
	}

	@Table("broken")
	record DefaultIntegerOutOfRange(@Id UUID id, @Default("3000000000") int attempts) {
	}

	@Table("broken")
	record DefaultUuidOfText(@Id UUID id, @Default("nobody") UUID owner) {
	}

	@Table("broken")
	record DefaultUuidOfShortForm(@Id UUID id, @Default("0-0-0-0-1") UUID owner) {
	}

	@Table("broken")
	record DefaultLongerThanLength(@Id UUID id, @Default("eng") @Length(2) String language) {
	}

	@Table("broken")
	record UnsupportedType(@Id UUID id, double score) {
	}

	@Table("broken")
	record DefaultInstant(@Id UUID id, @Default("2026-01-01T00:00:00Z") Instant since) {
	}

	@Table("identica_account_events")
	record AccountEvent(@Id @Length(64) String eventKey, Instant happenedAt, @Nullable LocalDate dueOn) {
	}

	@Test
	void readsInstantsAndDates() {
		EntityTable<AccountEvent> table = EntityReader.read(AccountEvent.class);
		Instant now = Instant.parse("2026-11-14T09:30:00.123456Z");
		AccountEvent event = new AccountEvent("login", now, LocalDate.of(2026, 12, 1));

		assertEquals(ColumnType.instant(), table.column("happened_at").column().getType());
		assertEquals(ColumnType.date(), table.column("due_on").column().getType());
		assertFalse(table.column("happened_at").column().isNullable());
		assertTrue(table.column("due_on").column().isNullable());
		assertEquals(now, table.column("happened_at", Instant.class).get(event));
		assertEquals(LocalDate.of(2026, 12, 1), table.column("due_on", LocalDate.class).get(event));
	}

	@Table("broken")
	record NullablePrimitive(@Id UUID id, @Nullable long score) {
	}

	@Table("broken")
	record LengthOnNumber(@Id UUID id, @Length(4) long score) {
	}

	@Table("broken")
	record NullableKey(@Id @Nullable UUID id) {
	}

	@Table("broken")
	@Unique("missing")
	record UnknownUniqueField(@Id UUID id) {
	}

	@Table("broken")
	record ReferenceToCompositeKey(@Id UUID id, @References(entity = ProviderLink.class) @Length(64) String link) {
	}

	@Table("broken")
	@ForeignKey(fields = "provider", entity = ProviderLink.class)
	record ForeignKeyTooShort(@Id @Length(64) String provider) {
	}

	@Table("broken")
	@ForeignKey(fields = {"provider", "subject"}, entity = ProviderLink.class)
	record ForeignKeyOfAnotherType(@Id @Length(64) String provider, long subject) {
	}

	@Table("broken")
	@ForeignKey(fields = {"provider", "missing"}, entity = ProviderLink.class)
	record ForeignKeyToUnknownField(@Id @Length(64) String provider) {
	}

	@Table("broken")
	@ForeignKey(fields = "id", entity = Keyless.class)
	record ForeignKeyToKeylessEntity(@Id UUID id) {
	}

	@Table("keyless")
	record Keyless(UUID id) {
	}

	@Table("broken")
	record ReferenceToEntityAndTable(@Id UUID id, @References(entity = Account.class, table = "identica_accounts", column = "unique_id") UUID owner) {
	}

	@Table("broken")
	record ReferenceToNothing(@Id UUID id, @References UUID owner) {
	}

	@Table("broken")
	record ReferenceToTableWithoutColumn(@Id UUID id, @References(table = "identica_accounts") UUID owner) {
	}

	@Table("broken")
	@ForeignKey(fields = "id", entity = Account.class, table = "identica_accounts", columns = "unique_id")
	record ForeignKeyToEntityAndTable(@Id UUID id) {
	}

	@Table("broken")
	@ForeignKey(fields = "id", table = "identica_accounts")
	record ForeignKeyToTableWithoutColumns(@Id UUID id) {
	}

	@Table("broken")
	@ForeignKey(fields = {"provider", "subject"}, table = "identica_provider_links", columns = "provider_id")
	record ForeignKeyToFewerColumns(@Id @Length(64) String provider, @Length(128) String subject) {
	}

	record NotAnnotated(UUID id) {
	}

	@Getter
	@Table("broken")
	static class NoUsableConstructor {
		@Id
		private final UUID id;
		private final String name;

		NoUsableConstructor(String name) {
			this.id = UUID.randomUUID();
			this.name = name;
		}
	}

	@Test
	void refusesInvalidEntitiesWhenReadingThem() {
		assertMessage(UnsupportedType.class, "score", "has no column type");
		assertMessage(DefaultInstant.class, "since", "@Default applies to UUID, text, number and boolean fields, not to Instant");
		assertMessage(NullablePrimitive.class, "score", "primitive field cannot be nullable");
		assertMessage(LengthOnNumber.class, "score", "@Length applies to String fields only");
		assertMessage(NullableKey.class, "id", "key field cannot be nullable");
		assertMessage(UnknownUniqueField.class, "missing", "no column field");
		assertMessage(ReferenceToCompositeKey.class, "ProviderLink", "exactly one @Id field");
		assertMessage(ForeignKeyTooShort.class, "[provider]", "must name 2 fields");
		assertMessage(ForeignKeyOfAnotherType.class, "subject", "of type java.lang.String, not long");
		assertMessage(ForeignKeyToUnknownField.class, "missing", "no column field");
		assertMessage(ForeignKeyToKeylessEntity.class, "Keyless", "needs an @Id field");
		assertMessage(ReferenceToEntityAndTable.class, "owner", "names both an entity and a table");
		assertMessage(ReferenceToNothing.class, "owner", "names neither an entity nor a table");
		assertMessage(ReferenceToTableWithoutColumn.class, "owner", "names a column together with a table");
		assertMessage(ForeignKeyToEntityAndTable.class, "[id]", "names both an entity and a table");
		assertMessage(ForeignKeyToTableWithoutColumns.class, "[id]", "names columns together with a table");
		assertMessage(ForeignKeyToFewerColumns.class, "[provider, subject]", "as many fields as columns of identica_provider_links");
		assertMessage(DefaultBooleanOfAnotherWord.class, "enabled", "is true or false, not \"yes\"");
		assertMessage(DefaultNumberOfText.class, "since", "decimal number in its range, not \"zero\"");
		assertMessage(DefaultIntegerOutOfRange.class, "attempts", "decimal number in its range, not \"3000000000\"");
		assertMessage(DefaultUuidOfText.class, "owner", "36-character form, not \"nobody\"");
		assertMessage(DefaultUuidOfShortForm.class, "owner", "36-character form, not \"0-0-0-0-1\"");
		assertMessage(DefaultLongerThanLength.class, "language", "longer than the field's @Length(2)");
		assertMessage(NotAnnotated.class, "NotAnnotated", "not annotated with @Table");
		assertMessage(NoUsableConstructor.class, "NoUsableConstructor", "needs a constructor");
	}

	private static void assertMessage(Class<?> type, String subject, String reason) {
		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> EntityReader.read(type));

		assertTrue(failure.getMessage().contains(subject) && failure.getMessage().contains(reason), failure.getMessage());
	}

	private static <T> List<String> names(EntityTable<T> table) {
		return table.columns().stream().map(EntityColumn::name).toList();
	}

	private static <T> Row<T> row(EntityTable<T> table, Map<String, Object> values) {
		return new Row<>() {
			@Override
			@SuppressWarnings("unchecked")
			public <V> V get(EntityColumn<T, V> column) {
				return (V) values.get(column.name());
			}
		};
	}

	/**
	 * Stands in for a runtime-visible {@code Nullable} of another library, such as Jakarta's.
	 */
	static final class Jakarta {
		@Retention(RetentionPolicy.RUNTIME)
		@interface Nullable {
		}
	}
}
