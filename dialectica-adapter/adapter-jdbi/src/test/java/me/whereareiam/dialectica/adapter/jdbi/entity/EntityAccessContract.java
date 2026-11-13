package me.whereareiam.dialectica.adapter.jdbi.entity;

import me.whereareiam.dialectica.Dialectica;
import me.whereareiam.dialectica.adapter.jdbi.DialecticaPlugin;
import me.whereareiam.dialectica.entity.EntityColumn;
import me.whereareiam.dialectica.entity.Where;
import me.whereareiam.dialectica.exception.KeyConflictException;
import me.whereareiam.dialectica.fixture.Account;
import me.whereareiam.dialectica.fixture.AccountEvent;
import me.whereareiam.dialectica.fixture.AccountEventColumns;
import me.whereareiam.dialectica.fixture.AccountColumns;
import me.whereareiam.dialectica.fixture.AccountReservation;
import me.whereareiam.dialectica.fixture.AccountReservationColumns;
import me.whereareiam.dialectica.fixture.ProviderLink;
import me.whereareiam.dialectica.fixture.ProviderLinkColumns;
import me.whereareiam.dialectica.schema.Schema;
import me.whereareiam.dialectica.schema.change.DropTable;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.StatementException;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static me.whereareiam.dialectica.fixture.AccountColumns.LAST_SEEN_AT;
import static me.whereareiam.dialectica.fixture.AccountColumns.USERNAME;
import static me.whereareiam.dialectica.fixture.AccountReservationColumns.CREATED_AT;
import static me.whereareiam.dialectica.fixture.AccountReservationColumns.EXPIRES_AT;
import static me.whereareiam.dialectica.fixture.AccountReservationColumns.RESERVATION_KEY;
import static me.whereareiam.dialectica.fixture.ProviderLinkColumns.IS_PRIMARY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What {@link EntityAccess}, {@link DialecticaPlugin} and {@link RegisterDialecticaMapper}
 * guarantee on every dialect, with Identica's tables. Each dialect runs it against a database of its
 * own.
 */
abstract class EntityAccessContract {
	private static final UUID ALICE = UUID.fromString("6f1c2a52-8e1b-4a8e-9a43-3b1f7f0c2d11");
	private static final UUID BOB = UUID.fromString("0b6a3f4e-2c5d-4e7f-8a9b-1c2d3e4f5a6b");

	protected Jdbi jdbi;
	private EntityAccess<Account> accounts;
	private EntityAccess<ProviderLink> links;
	private EntityAccess<AccountReservation> reservations;
	private EntityAccess<AccountEvent> events;

	/**
	 * Creates a Jdbi for an empty or reusable database of the dialect under test.
	 */
	protected abstract Jdbi createJdbi();

	@BeforeEach
	void prepare() throws SQLException {
		jdbi = createJdbi();
		jdbi.installPlugin(new SqlObjectPlugin());
		jdbi.installPlugin(new DialecticaPlugin());

		jdbi.useHandle(handle -> {
			var writer = Dialectica.on(handle.getConnection());
			writer.apply(
					new DropTable("identica_provider_links"),
					new DropTable("identica_account_reservations"),
					new DropTable("identica_account_events"),
					new DropTable("identica_accounts")
			);
			writer.create(Schema.of(
					Dialectica.entity(Account.class).table(),
					Dialectica.entity(ProviderLink.class).table(),
					Dialectica.entity(AccountReservation.class).table(),
					Dialectica.entity(AccountEvent.class).table()
			));
		});

		accounts = EntityAccess.of(jdbi, Dialectica.entity(Account.class));
		links = EntityAccess.of(jdbi, Dialectica.entity(ProviderLink.class));
		reservations = EntityAccess.of(jdbi, Dialectica.entity(AccountReservation.class));
		events = EntityAccess.of(jdbi, Dialectica.entity(AccountEvent.class));
	}

	@Test
	void insertsAndFindsByAUuidKey() {
		Account alice = new Account(ALICE, "Alice", 1_700_000_000_000L, null);

		accounts.insert(alice);

		assertEquals(Optional.of(alice), accounts.find(ALICE));
		assertEquals(Optional.empty(), accounts.find(BOB));
	}

	@Test
	void findsByACompositeKeyWithBooleans() {
		accounts.insert(new Account(ALICE, "Alice", null, null));
		ProviderLink primary = new ProviderLink("premium", "alice-subject", ALICE, true, 10L, null);
		ProviderLink secondary = new ProviderLink("credential", "alice", ALICE, false, null, 20L);

		links.insert(primary);
		links.insert(secondary);

		assertEquals(Optional.of(primary), links.find("premium", "alice-subject"));
		assertEquals(Optional.of(secondary), links.find("credential", "alice"));
	}

	@Test
	void upsertsTheSameKeyTwice() {
		reservations.upsert(new AccountReservation("name:alice", ALICE, 1L, 2L));
		reservations.upsert(new AccountReservation("name:alice", BOB, 3L, 4L));

		assertEquals(Optional.of(new AccountReservation("name:alice", BOB, 3L, 4L)), reservations.find("name:alice"));
		assertEquals(1, reservations.query(RESERVATION_KEY.isNotNull()).count());
	}

	@Test
	void insertsIfAbsentAndKeepsTheRowThatHoldsAKey() {
		accounts.insert(new Account(ALICE, "Alice", null, null));
		ProviderLink link = new ProviderLink("premium", "alice-subject", ALICE, true, 10L, null);

		assertTrue(links.insertIfAbsent(link));
		assertFalse(links.insertIfAbsent(new ProviderLink("premium", "alice-subject", ALICE, false, 20L, 30L)));
		assertFalse(links.insertIfAbsent(new ProviderLink("premium", "other-subject", ALICE, false, 20L, 30L)));
		assertTrue(links.insertIfAbsent(new ProviderLink("credential", "alice", ALICE, false, null, null)));

		assertEquals(Optional.of(link), links.find("premium", "alice-subject"));
		assertEquals(Optional.empty(), links.find("premium", "other-subject"));
	}

	@Test
	void stillFailsAnInsertIfAbsentForAnythingButATakenKey() {
		assertThrows(StatementException.class, () -> accounts.insertIfAbsent(new Account(ALICE, null, null, null)));
		assertEquals(Optional.empty(), accounts.find(ALICE));
	}

	@Test
	void letsExactlyOneOfTwoConcurrentInsertsIfAbsentInsert() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			for (int round = 0; round < 20; round++) {
				String key = "name:round-" + round;
				CyclicBarrier start = new CyclicBarrier(2);
				List<Future<Boolean>> inserts = new ArrayList<>();
				for (long contender = 1; contender <= 2; contender++) {
					long createdAt = contender;
					inserts.add(executor.submit(() -> {
						start.await(10, TimeUnit.SECONDS);
						return reservations.insertIfAbsent(new AccountReservation(key, ALICE, createdAt, 0L));
					}));
				}

				boolean first = inserts.get(0).get(30, TimeUnit.SECONDS);
				boolean second = inserts.get(1).get(30, TimeUnit.SECONDS);

				assertTrue(first ^ second, "round " + round + ": first " + first + ", second " + second);
				assertEquals(first ? 1L : 2L, reservations.find(key).orElseThrow().createdAt());
			}
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void upsertsOnlyTheColumnsItNames() {
		accounts.insert(new Account(ALICE, "Alice", null, null));
		EntityColumn<ProviderLink, Long> lastSeenAt = links.column("last_seen_at", Long.class);

		links.upsert(new ProviderLink("premium", "alice-subject", ALICE, true, 10L, 11L), IS_PRIMARY, ProviderLinkColumns.LAST_SEEN_AT);
		assertEquals(Optional.of(new ProviderLink("premium", "alice-subject", ALICE, true, 10L, 11L)), links.find("premium", "alice-subject"));

		links.upsert(new ProviderLink("premium", "alice-subject", ALICE, false, 20L, 21L), IS_PRIMARY, ProviderLinkColumns.LAST_SEEN_AT);
		assertEquals(Optional.of(new ProviderLink("premium", "alice-subject", ALICE, false, 10L, 21L)), links.find("premium", "alice-subject"));

		links.upsert(new ProviderLink("premium", "alice-subject", ALICE, true, 30L, 31L), lastSeenAt);
		assertEquals(Optional.of(new ProviderLink("premium", "alice-subject", ALICE, false, 10L, 31L)), links.find("premium", "alice-subject"));

		links.upsert(new ProviderLink("premium", "alice-subject", ALICE, true, 40L, 41L));
		assertEquals(Optional.of(new ProviderLink("premium", "alice-subject", ALICE, true, 40L, 41L)), links.find("premium", "alice-subject"));
	}

	@Test
	void failsAnUpsertThatTakesAnotherUniqueKey() {
		accounts.insert(new Account(ALICE, "Alice", null, null));
		ProviderLink link = new ProviderLink("premium", "alice-subject", ALICE, true, 10L, null);
		links.insert(link);
		ProviderLink second = new ProviderLink("premium", "other-subject", ALICE, false, 20L, 21L);

		assertThrows(KeyConflictException.class, () -> links.upsert(second));
		KeyConflictException conflict = assertThrows(KeyConflictException.class, () -> links.upsert(second, IS_PRIMARY, ProviderLinkColumns.LAST_SEEN_AT));
		assertEquals("identica_provider_links", conflict.getTable());

		assertEquals(Optional.of(link), links.find("premium", "alice-subject"));
		assertEquals(Optional.empty(), links.find("premium", "other-subject"));
	}

	@Test
	void failsAnUpdateThatTakesAnotherUniqueKey() {
		accounts.insert(new Account(ALICE, "Alice", null, null));
		accounts.insert(new Account(BOB, "Bob", null, null));
		ProviderLink alice = new ProviderLink("premium", "alice-subject", ALICE, true, 10L, null);
		ProviderLink bob = new ProviderLink("premium", "bob-subject", BOB, true, 20L, null);
		links.insert(alice);
		links.insert(bob);

		KeyConflictException conflict = assertThrows(KeyConflictException.class,
				() -> links.update("premium", "bob-subject").set(ProviderLinkColumns.UNIQUE_ID, ALICE).execute());
		assertEquals("identica_provider_links", conflict.getTable());

		assertEquals(Optional.of(alice), links.find("premium", "alice-subject"));
		assertEquals(Optional.of(bob), links.find("premium", "bob-subject"));
	}

	@Test
	void letsTwoConcurrentUpsertsOfAKeyBothSucceed() throws Exception {
		accounts.insert(new Account(ALICE, "Alice", null, null));
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			for (int round = 0; round < 20; round++) {
				String subject = "subject-" + round;
				CyclicBarrier start = new CyclicBarrier(2);
				List<Future<?>> upserts = new ArrayList<>();
				for (long contender = 1; contender <= 2; contender++) {
					long seenAt = contender;
					upserts.add(executor.submit(() -> {
						start.await(10, TimeUnit.SECONDS);
						links.upsert(new ProviderLink("premium", subject, ALICE, false, 5L, seenAt), ProviderLinkColumns.LAST_SEEN_AT);
						return null;
					}));
				}
				for (Future<?> upsert : upserts)
					upsert.get(30, TimeUnit.SECONDS);

				ProviderLink stored = links.find("premium", subject).orElseThrow();
				assertTrue(stored.getLastSeenAt() == 1L || stored.getLastSeenAt() == 2L, "round " + round + ": " + stored);
				assertEquals(5L, stored.getLinkedAt());
				links.delete("premium", subject);
			}
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	@SuppressWarnings({"unchecked", "rawtypes"})
	void refusesUpsertsThatDoNotFitTheTable() {
		EntityColumn<ProviderLink, String> foreign = (EntityColumn) USERNAME;
		ProviderLink link = new ProviderLink("premium", "alice-subject", ALICE, true, 10L, null);

		assertThrows(IllegalArgumentException.class, () -> links.upsert(link, ProviderLinkColumns.PROVIDER_ID));
		assertThrows(IllegalArgumentException.class, () -> links.upsert(link, IS_PRIMARY, IS_PRIMARY));
		assertThrows(IllegalArgumentException.class, () -> links.upsert(link, new EntityColumn[0]));
		assertThrows(IllegalArgumentException.class, () -> links.upsert(link, foreign));
		assertThrows(IllegalArgumentException.class, () -> links.column("missing", Long.class));
		assertThrows(IllegalArgumentException.class, () -> links.column("last_seen_at", String.class));
		assertEquals(Optional.empty(), links.find("premium", "alice-subject"));
	}

	@Test
	void replacesTheRowWithTheEntitysKey() {
		accounts.insert(new Account(ALICE, "Alice", 1L, null));

		assertTrue(accounts.replace(new Account(ALICE, "Alicia", 1L, 5L)));
		assertFalse(accounts.replace(new Account(BOB, "Bob", 1L, 5L)));

		assertEquals(Optional.of(new Account(ALICE, "Alicia", 1L, 5L)), accounts.find(ALICE));
		assertEquals(Optional.empty(), accounts.find(BOB));
	}

	@Test
	void updatesOnlyTheColumnsItSets() {
		accounts.insert(new Account(ALICE, "Alice", 1L, null));

		assertEquals(1, accounts.update(ALICE).set(LAST_SEEN_AT, 5L).execute());
		assertEquals(1, accounts.update(ALICE).set(USERNAME, "Alicia").set(AccountColumns.CREATED_AT, null).execute());
		assertEquals(0, accounts.update(BOB).set(LAST_SEEN_AT, 5L).execute());

		assertEquals(Optional.of(new Account(ALICE, "Alicia", null, 5L)), accounts.find(ALICE));
		assertEquals(Optional.empty(), accounts.find(BOB));
	}

	@Test
	void updatesByACompositeKeyWithAGuard() {
		accounts.insert(new Account(ALICE, "Alice", null, null));
		links.insert(new ProviderLink("premium", "alice-subject", ALICE, true, 10L, null));
		links.insert(new ProviderLink("credential", "alice", ALICE, false, null, null));

		assertEquals(1, links.update("credential", "alice").set(IS_PRIMARY, true).set(ProviderLinkColumns.LAST_SEEN_AT, 30L).when(IS_PRIMARY.is(false)).execute());
		assertEquals(1, links.update("premium", "alice-subject").set(IS_PRIMARY, false).execute());

		assertEquals(Optional.of(new ProviderLink("credential", "alice", ALICE, true, null, 30L)), links.find("credential", "alice"));
		assertEquals(Optional.of(new ProviderLink("premium", "alice-subject", ALICE, false, 10L, null)), links.find("premium", "alice-subject"));
	}

	@Test
	void updatesOnlyWhileTheGuardsHold() {
		reservations.insert(new AccountReservation("recovery:alice", ALICE, 1L, 0L));

		assertEquals(1, reservations.update("recovery:alice").set(EXPIRES_AT, 7L).when(EXPIRES_AT.is(0L)).execute());
		assertEquals(0, reservations.update("recovery:alice").set(EXPIRES_AT, 8L).when(EXPIRES_AT.is(0L)).execute());
		assertEquals(0, reservations.update("recovery:alice").set(EXPIRES_AT, 9L)
				.when(EXPIRES_AT.is(7L))
				.when(AccountReservationColumns.UNIQUE_ID.is(BOB))
				.execute());
		assertEquals(1, reservations.update("recovery:alice").set(EXPIRES_AT, 10L)
				.when(AccountReservationColumns.UNIQUE_ID.is(BOB).or(EXPIRES_AT.is(7L)))
				.execute());

		assertEquals(Optional.of(new AccountReservation("recovery:alice", ALICE, 1L, 10L)), reservations.find("recovery:alice"));
	}

	@Test
	void guardsOnNullWithIsNull() {
		accounts.insert(new Account(ALICE, "Alice", 1L, null));

		assertEquals(1, accounts.update(ALICE).set(LAST_SEEN_AT, 5L).when(LAST_SEEN_AT.isNull()).execute());
		assertEquals(0, accounts.update(ALICE).set(LAST_SEEN_AT, 6L).when(LAST_SEEN_AT.isNull()).execute());
		assertEquals(1, accounts.update(ALICE).set(LAST_SEEN_AT, 7L).when(LAST_SEEN_AT.isNotNull()).execute());

		assertEquals(Optional.of(new Account(ALICE, "Alice", 1L, 7L)), accounts.find(ALICE));
	}

	@Test
	void letsExactlyOneOfTwoConcurrentGuardedUpdatesWin() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			for (int round = 0; round < 20; round++) {
				reservations.upsert(new AccountReservation("recovery:alice", ALICE, 1L, 0L));
				CyclicBarrier start = new CyclicBarrier(2);
				List<Future<Integer>> updates = new ArrayList<>();
				for (long contender = 1; contender <= 2; contender++) {
					long usedAt = contender;
					updates.add(executor.submit(() -> {
						start.await(10, TimeUnit.SECONDS);
						return reservations.update("recovery:alice").set(EXPIRES_AT, usedAt).when(EXPIRES_AT.is(0L)).execute();
					}));
				}

				int first = updates.get(0).get(30, TimeUnit.SECONDS);
				int second = updates.get(1).get(30, TimeUnit.SECONDS);

				assertEquals(1, first + second, "round " + round + ": first " + first + ", second " + second);
				assertEquals(first == 1 ? 1L : 2L, reservations.find("recovery:alice").orElseThrow().expiresAt());
			}
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void updatesOnlyWhileAnOrderedGuardHolds() {
		reservations.insert(new AccountReservation("totp:alice", ALICE, 1L, 0L));

		assertEquals(1, reservations.update("totp:alice").set(EXPIRES_AT, 5L).when(EXPIRES_AT.below(5L)).execute());
		assertEquals(0, reservations.update("totp:alice").set(EXPIRES_AT, 5L).when(EXPIRES_AT.below(5L)).execute());
		assertEquals(0, reservations.update("totp:alice").set(EXPIRES_AT, 4L).when(EXPIRES_AT.below(4L)).execute());
		assertEquals(1, reservations.update("totp:alice").set(EXPIRES_AT, 6L).when(EXPIRES_AT.below(6L)).execute());

		assertEquals(Optional.of(new AccountReservation("totp:alice", ALICE, 1L, 6L)), reservations.find("totp:alice"));
	}

	@Test
	void comparesAboveAtMostAndAtLeast() {
		reservations.insert(new AccountReservation("totp:alice", ALICE, 1L, 5L));

		assertEquals(0, reservations.update("totp:alice").set(CREATED_AT, 2L).when(EXPIRES_AT.above(5L)).execute());
		assertEquals(1, reservations.update("totp:alice").set(CREATED_AT, 2L).when(EXPIRES_AT.above(4L)).execute());
		assertEquals(0, reservations.update("totp:alice").set(CREATED_AT, 3L).when(EXPIRES_AT.atMost(4L)).execute());
		assertEquals(1, reservations.update("totp:alice").set(CREATED_AT, 3L).when(EXPIRES_AT.atMost(5L)).execute());
		assertEquals(0, reservations.update("totp:alice").set(CREATED_AT, 4L).when(EXPIRES_AT.atLeast(6L)).execute());
		assertEquals(1, reservations.update("totp:alice").set(CREATED_AT, 4L).when(EXPIRES_AT.atLeast(5L)).execute());
		assertEquals(0, reservations.update("totp:alice").set(CREATED_AT, 5L).when(EXPIRES_AT.atLeast(1L).and(EXPIRES_AT.below(5L))).execute());
		assertEquals(1, reservations.update("totp:alice").set(CREATED_AT, 5L).when(EXPIRES_AT.atLeast(1L).and(EXPIRES_AT.below(6L))).execute());

		assertEquals(Optional.of(new AccountReservation("totp:alice", ALICE, 5L, 5L)), reservations.find("totp:alice"));
	}

	@Test
	void meetsNoComparisonWhileTheColumnIsNull() {
		accounts.insert(new Account(ALICE, "Alice", 1L, null));

		assertEquals(0, accounts.update(ALICE).set(LAST_SEEN_AT, 5L).when(LAST_SEEN_AT.below(5L)).execute());
		assertEquals(0, accounts.update(ALICE).set(LAST_SEEN_AT, 5L).when(LAST_SEEN_AT.atLeast(Long.MIN_VALUE)).execute());
		assertEquals(0, accounts.update(ALICE).set(LAST_SEEN_AT, 5L).when(LAST_SEEN_AT.isNot(3L)).execute());
		assertEquals(0, accounts.update(ALICE).set(LAST_SEEN_AT, 5L).when(LAST_SEEN_AT.is(3L).not()).execute());

		assertEquals(Optional.of(new Account(ALICE, "Alice", 1L, null)), accounts.find(ALICE));
	}

	@Test
	void letsExactlyOneOfTwoConcurrentOrderedUpdatesWin() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			for (int round = 0; round < 20; round++) {
				reservations.upsert(new AccountReservation("totp:alice", ALICE, 0L, 4L));
				CyclicBarrier start = new CyclicBarrier(2);
				List<Future<Integer>> updates = new ArrayList<>();
				for (long contender = 1; contender <= 2; contender++) {
					long winner = contender;
					updates.add(executor.submit(() -> {
						start.await(10, TimeUnit.SECONDS);
						return reservations.update("totp:alice").set(EXPIRES_AT, 5L).set(CREATED_AT, winner).when(EXPIRES_AT.below(5L)).execute();
					}));
				}

				int first = updates.get(0).get(30, TimeUnit.SECONDS);
				int second = updates.get(1).get(30, TimeUnit.SECONDS);

				assertEquals(1, first + second, "round " + round + ": first " + first + ", second " + second);
				assertEquals(new AccountReservation("totp:alice", ALICE, first == 1 ? 1L : 2L, 5L), reservations.find("totp:alice").orElseThrow());
			}
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	@SuppressWarnings({"unchecked", "rawtypes"})
	void refusesUpdatesThatDoNotFitTheTable() {
		EntityColumn<Account, Boolean> foreign = (EntityColumn) IS_PRIMARY;
		EntityColumn<Account, Object> raw = (EntityColumn) USERNAME;

		assertThrows(IllegalArgumentException.class, () -> accounts.update(ALICE, BOB));
		assertThrows(IllegalArgumentException.class, () -> accounts.update("alice"));
		assertThrows(IllegalArgumentException.class, () -> accounts.update(ALICE).set(AccountColumns.UNIQUE_ID, BOB));
		assertThrows(IllegalArgumentException.class, () -> accounts.update(ALICE).set(raw, 5L));
		assertThrows(IllegalArgumentException.class, () -> accounts.update(ALICE).set(USERNAME, null));
		assertThrows(IllegalArgumentException.class, () -> accounts.update(ALICE).set(LAST_SEEN_AT, 1L).set(LAST_SEEN_AT, 2L));
		assertThrows(IllegalArgumentException.class, () -> accounts.update(ALICE).set(foreign, true));
		assertThrows(IllegalArgumentException.class, () -> accounts.update(ALICE).when(foreign.is(true)));
		assertThrows(IllegalArgumentException.class, () -> accounts.update(foreign.is(true)));
		assertThrows(IllegalArgumentException.class, () -> accounts.query(foreign.isNull()));
		assertThrows(IllegalArgumentException.class, () -> accounts.delete(foreign.isNull()));
		assertThrows(IllegalStateException.class, () -> accounts.update(ALICE).when(LAST_SEEN_AT.isNull()).execute());
	}

	@Test
	@SuppressWarnings({"unchecked", "rawtypes"})
	void refusesConditionsThatDoNotFitTheirColumn() {
		EntityColumn<Account, Object> raw = (EntityColumn) LAST_SEEN_AT;

		assertThrows(IllegalArgumentException.class, () -> AccountColumns.UNIQUE_ID.below(ALICE));
		assertThrows(IllegalArgumentException.class, () -> IS_PRIMARY.atLeast(true));
		assertThrows(IllegalArgumentException.class, () -> IS_PRIMARY.ascending());
		assertThrows(IllegalArgumentException.class, () -> raw.is(1));
		assertThrows(IllegalArgumentException.class, () -> USERNAME.in(List.of()));
		assertThrows(NullPointerException.class, () -> USERNAME.is(null));
	}

	@Test
	void queriesByConditionsInOrder() {
		insertReservations();

		assertEquals(List.of("a", "b", "c", "d", "e"), keys(reservations.query(RESERVATION_KEY.isNotNull()).orderBy(RESERVATION_KEY.ascending()).list()));
		assertEquals(List.of("e", "d", "c"), keys(reservations.query(EXPIRES_AT.atLeast(30L)).orderBy(EXPIRES_AT.descending()).list()));
		assertEquals(List.of("b", "d"), keys(reservations.query(RESERVATION_KEY.in("b", "d", "z")).orderBy(RESERVATION_KEY.ascending()).list()));
		assertEquals(List.of("a", "e"), keys(reservations.query(EXPIRES_AT.below(20L).or(EXPIRES_AT.above(40L))).orderBy(RESERVATION_KEY.ascending()).list()));
		assertEquals(List.of("b", "c", "d"), keys(reservations.query(EXPIRES_AT.below(20L).or(EXPIRES_AT.above(40L)).not()).orderBy(RESERVATION_KEY.ascending()).list()));
		assertEquals(List.of("c", "e"), keys(reservations.query(AccountReservationColumns.UNIQUE_ID.is(BOB).and(RESERVATION_KEY.isNot("x"))).orderBy(RESERVATION_KEY.ascending()).list()));
		assertEquals(List.of("a", "b"), keys(reservations.query(RESERVATION_KEY.below("c")).orderBy(RESERVATION_KEY.ascending()).list()));
		assertEquals(List.of("d", "c", "a", "b"), keys(reservations.query(EXPIRES_AT.atMost(40L))
				.orderBy(CREATED_AT.ascending(), RESERVATION_KEY.descending())
				.list()));
		assertEquals(List.of(), reservations.query(EXPIRES_AT.above(100L)).list());
	}

	@Test
	void readsPagesTheFirstRowCountsAndExistence() {
		insertReservations();
		Where<AccountReservation> every = RESERVATION_KEY.isNotNull();

		assertEquals(List.of("a", "b"), keys(reservations.query(every).orderBy(RESERVATION_KEY.ascending()).limit(2).list()));
		assertEquals(List.of("c", "d"), keys(reservations.query(every).orderBy(RESERVATION_KEY.ascending()).offset(2).limit(2).list()));
		assertEquals(List.of("d", "e"), keys(reservations.query(every).orderBy(RESERVATION_KEY.ascending()).offset(3).list()));
		assertEquals("e", reservations.query(every).orderBy(EXPIRES_AT.descending()).first().orElseThrow().reservationKey());
		assertEquals("b", reservations.query(every).orderBy(EXPIRES_AT.descending()).offset(3).first().orElseThrow().reservationKey());
		assertEquals(Optional.empty(), reservations.query(EXPIRES_AT.above(100L)).first());

		assertEquals(5, reservations.query(every).count());
		assertEquals(2, reservations.query(AccountReservationColumns.UNIQUE_ID.is(BOB)).limit(1).count());
		assertEquals(0, reservations.query(EXPIRES_AT.above(100L)).count());
		assertTrue(reservations.query(EXPIRES_AT.is(30L)).exists());
		assertFalse(reservations.query(EXPIRES_AT.is(31L)).exists());
	}

	@Test
	void streamsEntitiesInsideItsHandle() {
		insertReservations();

		List<String> streamed = new ArrayList<>();
		reservations.query(EXPIRES_AT.atLeast(20L))
				.orderBy(RESERVATION_KEY.descending())
				.stream(stream -> stream.map(AccountReservation::reservationKey).forEach(streamed::add));

		assertEquals(List.of("e", "d", "c", "b"), streamed);
	}

	@Test
	void comparesAndOrdersText() {
		accounts.insert(new Account(ALICE, "alice", null, null));
		accounts.insert(new Account(BOB, "bob", null, null));

		assertEquals(List.of(BOB, ALICE), accounts.query(USERNAME.atLeast("alice")).orderBy(USERNAME.descending()).list().stream().map(Account::uniqueId).toList());
		assertEquals(List.of(ALICE), accounts.query(USERNAME.below("b")).list().stream().map(Account::uniqueId).toList());
	}

	@Test
	void updatesAndDeletesEveryRowThatMeetsACondition() {
		insertReservations();

		assertEquals(2, reservations.update(AccountReservationColumns.UNIQUE_ID.is(BOB)).set(CREATED_AT, 0L).execute());
		assertEquals(1, reservations.update(AccountReservationColumns.UNIQUE_ID.is(BOB)).set(CREATED_AT, 9L).when(EXPIRES_AT.above(40L)).execute());
		assertEquals(0, reservations.update(EXPIRES_AT.above(100L)).set(CREATED_AT, 9L).execute());
		assertEquals(List.of(0L, 9L), reservations.query(AccountReservationColumns.UNIQUE_ID.is(BOB)).orderBy(RESERVATION_KEY.ascending()).list().stream()
				.map(AccountReservation::createdAt)
				.toList());

		assertEquals(3, reservations.delete(EXPIRES_AT.atMost(30L)));
		assertEquals(0, reservations.delete(EXPIRES_AT.atMost(30L)));
		assertEquals(List.of("d", "e"), keys(reservations.query(RESERVATION_KEY.isNotNull()).orderBy(RESERVATION_KEY.ascending()).list()));
	}

	@Test
	void insertsAndUpsertsInBatches() {
		accounts.insertAll(List.of(new Account(ALICE, "Alice", null, null), new Account(BOB, "Bob", null, null)));
		links.insert(new ProviderLink("premium", "alice-subject", ALICE, true, 10L, null));
		reservations.insertAll(List.of());

		links.upsertAll(List.of(
				new ProviderLink("premium", "alice-subject", ALICE, false, 20L, 21L),
				new ProviderLink("premium", "bob-subject", BOB, true, 30L, null)
		), ProviderLinkColumns.LAST_SEEN_AT);
		reservations.upsertAll(List.of(new AccountReservation("a", ALICE, 1L, 2L), new AccountReservation("b", BOB, 3L, 4L)));
		reservations.upsertAll(List.of(new AccountReservation("a", BOB, 5L, 6L)));

		assertEquals(2, accounts.query(AccountColumns.UNIQUE_ID.in(ALICE, BOB)).count());
		assertEquals(Optional.of(new ProviderLink("premium", "alice-subject", ALICE, true, 10L, 21L)), links.find("premium", "alice-subject"));
		assertEquals(Optional.of(new ProviderLink("premium", "bob-subject", BOB, true, 30L, null)), links.find("premium", "bob-subject"));
		assertEquals(List.of(new AccountReservation("a", BOB, 5L, 6L), new AccountReservation("b", BOB, 3L, 4L)),
				reservations.query(RESERVATION_KEY.isNotNull()).orderBy(RESERVATION_KEY.ascending()).list());
	}

	@Test
	void insertsABatchEntirelyOrNotAtAll() {
		accounts.insert(new Account(BOB, "Bob", null, null));

		assertThrows(StatementException.class, () -> accounts.insertAll(List.of(new Account(ALICE, "Alice", null, null), new Account(BOB, "Bobby", null, null))));
		assertThrows(KeyConflictException.class, () -> links.upsertAll(List.of(
				new ProviderLink("premium", "bob-subject", BOB, true, 10L, null),
				new ProviderLink("premium", "other-subject", BOB, true, 20L, null)
		)));

		assertEquals(Optional.empty(), accounts.find(ALICE));
		assertEquals(Optional.of(new Account(BOB, "Bob", null, null)), accounts.find(BOB));
		assertEquals(Optional.empty(), links.find("premium", "bob-subject"));
	}

	@Test
	void runsOnTheHandleOfATransaction() {
		jdbi.useTransaction(handle -> {
			EntityAccess<Account> bound = accounts.on(handle);
			bound.insert(new Account(ALICE, "Alice", null, null));
			bound.update(ALICE).set(LAST_SEEN_AT, 5L).execute();
			assertEquals(Optional.of(new Account(ALICE, "Alice", null, 5L)), bound.find(ALICE));
		});
		assertThrows(IllegalStateException.class, () -> jdbi.useTransaction(handle -> {
			EntityAccess<Account> bound = accounts.on(handle);
			bound.insertAll(List.of(new Account(BOB, "Bob", null, null)));
			bound.delete(ALICE);
			assertEquals(1, bound.query(AccountColumns.UNIQUE_ID.isNotNull()).count());
			throw new IllegalStateException("roll back");
		}));

		assertEquals(Optional.of(new Account(ALICE, "Alice", null, 5L)), accounts.find(ALICE));
		assertEquals(Optional.empty(), accounts.find(BOB));
	}

	@Test
	void deletesTheRowWithAKey() {
		reservations.insert(new AccountReservation("name:alice", ALICE, 1L, 2L));

		assertTrue(reservations.delete("name:alice"));
		assertFalse(reservations.delete("name:alice"));

		assertEquals(Optional.empty(), reservations.find("name:alice"));
	}

	@Test
	void mapsHandWrittenQueriesByColumnName() {
		accounts.insert(new Account(ALICE, "Alice", 1L, null));
		accounts.insert(new Account(BOB, "Bob", 2L, 3L));
		links.insert(new ProviderLink("premium", "alice-subject", ALICE, true, 10L, null));
		links.insert(new ProviderLink("credential", "alice", ALICE, false, null, null));

		Queries queries = jdbi.onDemand(Queries.class);

		assertEquals(Optional.of(new Account(BOB, "Bob", 2L, 3L)), queries.account(BOB));
		assertEquals(List.of(new ProviderLink("premium", "alice-subject", ALICE, true, 10L, null)), queries.links(ALICE, true));
		assertEquals(List.of(ALICE, BOB), queries.uniqueIds());
		assertEquals(List.of(false, true), queries.primaries());
	}

	@Test
	void writesAndReadsInstantsToTheMicrosecondAndDates() {
		AccountEvent login = new AccountEvent("login", ALICE, Instant.parse("2026-11-14T09:30:00.123456789Z"), LocalDate.of(2026, 12, 1));
		AccountEvent early = new AccountEvent("early", ALICE, Instant.parse("1969-07-20T20:17:40Z"), null);
		AccountEvent late = new AccountEvent("late", BOB, Instant.parse("9999-12-31T23:59:59.999999Z"), LocalDate.of(9999, 12, 31));
		events.insertAll(List.of(login, early, late));

		assertEquals(Optional.of(new AccountEvent("login", ALICE, Instant.parse("2026-11-14T09:30:00.123456Z"), LocalDate.of(2026, 12, 1))), events.find("login"));
		assertEquals(Optional.of(early), events.find("early"));
		assertEquals(Optional.of(late), events.find("late"));

		Instant moved = Instant.parse("2027-01-02T03:04:05.000006Z");
		assertEquals(1, events.update("early").set(AccountEventColumns.HAPPENED_AT, moved).set(AccountEventColumns.DUE_ON, LocalDate.of(2027, 1, 2)).execute());
		assertEquals(Optional.of(new AccountEvent("early", ALICE, moved, LocalDate.of(2027, 1, 2))), events.find("early"));
	}

	@Test
	void comparesAndOrdersInstantsAndDatesInTime() {
		events.insertAll(List.of(
				new AccountEvent("a", ALICE, Instant.parse("2026-11-14T09:30:00Z"), LocalDate.of(2026, 11, 14)),
				new AccountEvent("b", ALICE, Instant.parse("2026-11-14T09:30:00.5Z"), LocalDate.of(2026, 2, 1)),
				new AccountEvent("c", BOB, Instant.parse("2026-11-14T10:00:00Z"), null),
				new AccountEvent("d", BOB, Instant.parse("2025-01-01T00:00:00Z"), LocalDate.of(2027, 1, 1))
		));
		Instant now = Instant.parse("2026-11-14T09:30:00.5Z");

		assertEquals(List.of("d", "a", "b", "c"), eventKeys(events.query(AccountEventColumns.EVENT_KEY.isNotNull()).orderBy(AccountEventColumns.HAPPENED_AT.ascending()).list()));
		assertEquals(List.of("d", "a"), eventKeys(events.query(AccountEventColumns.HAPPENED_AT.below(now)).orderBy(AccountEventColumns.HAPPENED_AT.ascending()).list()));
		assertEquals(List.of("b", "c"), eventKeys(events.query(AccountEventColumns.HAPPENED_AT.atLeast(now)).orderBy(AccountEventColumns.EVENT_KEY.ascending()).list()));
		assertEquals(List.of("b"), eventKeys(events.query(AccountEventColumns.HAPPENED_AT.is(now)).list()));
		assertEquals(List.of("d", "a"), eventKeys(events.query(AccountEventColumns.DUE_ON.above(LocalDate.of(2026, 6, 1))).orderBy(AccountEventColumns.DUE_ON.descending()).list()));
		assertEquals(List.of("b", "a"), eventKeys(events.query(AccountEventColumns.DUE_ON.atMost(LocalDate.of(2026, 11, 14))).orderBy(AccountEventColumns.DUE_ON.ascending()).list()));
		assertEquals(2, events.delete(AccountEventColumns.UNIQUE_ID.is(ALICE).and(AccountEventColumns.HAPPENED_AT.atMost(now))));
		assertEquals(List.of("c", "d"), eventKeys(events.query(AccountEventColumns.EVENT_KEY.isNotNull()).orderBy(AccountEventColumns.EVENT_KEY.ascending()).list()));
	}

	@Test
	void bindsAndMapsInstantsAndDatesInHandWrittenQueries() {
		Instant happenedAt = Instant.parse("2026-11-14T09:30:00.25Z");
		events.insert(new AccountEvent("login", ALICE, happenedAt, LocalDate.of(2026, 12, 1)));

		Instant read = jdbi.withHandle(handle -> handle.createQuery("SELECT happened_at FROM identica_account_events WHERE happened_at = :at AND due_on = :on")
				.bind("at", happenedAt)
				.bind("on", LocalDate.of(2026, 12, 1))
				.mapTo(Instant.class)
				.one());
		assertEquals(happenedAt, read);
	}

	private static List<String> eventKeys(List<AccountEvent> stored) {
		return stored.stream().map(AccountEvent::eventKey).toList();
	}

	private void insertReservations() {
		reservations.insertAll(List.of(
				new AccountReservation("a", ALICE, 3L, 10L),
				new AccountReservation("b", ALICE, 4L, 20L),
				new AccountReservation("c", BOB, 2L, 30L),
				new AccountReservation("d", ALICE, 1L, 40L),
				new AccountReservation("e", BOB, 5L, 50L)
		));
	}

	private static List<String> keys(List<AccountReservation> stored) {
		return stored.stream().map(AccountReservation::reservationKey).toList();
	}

	@RegisterDialecticaMapper(Account.class)
	@RegisterDialecticaMapper(ProviderLink.class)
	public interface Queries {
		@SqlQuery("SELECT * FROM identica_accounts WHERE unique_id = :uniqueId")
		Optional<Account> account(@Bind("uniqueId") UUID uniqueId);

		@SqlQuery("SELECT * FROM identica_provider_links WHERE unique_id = :uniqueId AND is_primary = :primary")
		List<ProviderLink> links(@Bind("uniqueId") UUID uniqueId, @Bind("primary") boolean primary);

		@SqlQuery("SELECT unique_id FROM identica_accounts ORDER BY username")
		List<UUID> uniqueIds();

		@SqlQuery("SELECT is_primary FROM identica_provider_links ORDER BY provider_id")
		List<Boolean> primaries();
	}
}
