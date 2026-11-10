package com.IsaacRid.DnDPartyFinder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Checks that the Flyway migrations build the schema we expect, using a real PostgreSQL.
 * Plain JUnit + Testcontainers + JDBC: no Spring context.
 */
@Testcontainers
class SchemaMigrationIT {

	// SQLState codes defined by the SQL standard / PostgreSQL
	private static final String UNIQUE_VIOLATION = "23505";
	private static final String NOT_NULL_VIOLATION = "23502";
	private static final String CHECK_VIOLATION = "23514";
	private static final String FOREIGN_KEY_VIOLATION = "23503";

	// Valid coordinates (central London) used whenever a test isn't about location
	private static final double VALID_LATITUDE = 51.5;
	private static final double VALID_LONGITUDE = -0.12;

	// static = one container shared by every test in this class (started once, stopped at the end)
	@Container
	static PostgreSQLContainer postgres = new PostgreSQLContainer(TestImages.POSTGRES);

	private Connection connection;

	@BeforeAll
	static void runMigrations() {
		// Same migrations the app runs on startup (default location: classpath:db/migration)
		Flyway.configure()
				.dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
				.load()
				.migrate();
	}

	@BeforeEach
	void openTransaction() throws SQLException {
		connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
		connection.setAutoCommit(false); // everything a test does stays in one uncommitted transaction
	}

	@AfterEach
	void rollBackTransaction() throws SQLException {
		connection.rollback(); // undo the test's inserts so tests can't affect each other
		connection.close();
	}

	// ---------- users (V1) ----------

	@Test
	void usersTableGeneratesIdAndCreatedAt() throws SQLException {
		try (PreparedStatement insert = connection.prepareStatement(
				"INSERT INTO users (email, display_name) VALUES (?, ?) RETURNING id, created_at")) {
			insert.setString(1, "dm@example.com");
			insert.setString(2, "Dungeon Dan");

			try (ResultSet row = insert.executeQuery()) {
				assertThat(row.next()).isTrue();
				assertThat(row.getLong("id")).isPositive();
				assertThat(row.getTimestamp("created_at")).isNotNull();
			}
		}
	}

	@Test
	void usersEmailIsUniqueIgnoringCase() throws SQLException {
		insertUser("player@example.com", "First");

		assertSqlState(UNIQUE_VIOLATION, () -> insertUser("PLAYER@example.com", "Second"));
	}

	@Test
	void usersDisplayNameIsRequired() {
		assertSqlState(NOT_NULL_VIOLATION, () -> insertUser("nameless@example.com", null));
	}

	// ---------- party_tables (a DM's table that players apply to join) ----------

	@Test
	void partyTablesAppliesDefaults() throws SQLException {
		long tableId = insertPartyTable(insertDm(), 4); // 4 = the largest allowed seat limit

		try (PreparedStatement select = connection.prepareStatement(
				"SELECT accepted_count, status, version, created_at, updated_at FROM party_tables WHERE id = ?")) {
			select.setLong(1, tableId);

			try (ResultSet row = select.executeQuery()) {
				assertThat(row.next()).isTrue();
				assertThat(row.getInt("accepted_count")).isZero();
				assertThat(row.getString("status")).isEqualTo("OPEN");
				assertThat(row.getLong("version")).isZero();
				assertThat(row.getTimestamp("created_at")).isNotNull();
				assertThat(row.getTimestamp("updated_at")).isNotNull();
			}
		}
	}

	@Test
	void partyTablesSeatLimitMustBeAtLeastOne() throws SQLException {
		long dmId = insertDm();

		assertSqlState(CHECK_VIOLATION, () -> insertPartyTable(dmId, 0));
	}

	@Test
	void partyTablesSeatLimitCannotExceedFourPlayers() throws SQLException {
		long dmId = insertDm();

		assertSqlState(CHECK_VIOLATION, () -> insertPartyTable(dmId, 5));
	}

	@Test
	void partyTablesAcceptedCountCannotExceedSeatLimit() throws SQLException {
		// 2 and 3 (not 4 and 5) so a fixed "<= 4" check would NOT pass this test:
		// it must compare against this table's own seat_limit
		long tableId = insertPartyTable(insertDm(), 2);

		assertSqlState(CHECK_VIOLATION,
				() -> update("UPDATE party_tables SET accepted_count = 3 WHERE id = ?", tableId));
	}

	@Test
	void partyTablesStatusMustBeKnownValue() throws SQLException {
		long tableId = insertPartyTable(insertDm(), 4);

		assertSqlState(CHECK_VIOLATION,
				() -> update("UPDATE party_tables SET status = 'FULL' WHERE id = ?", tableId));
	}

	@Test
	void partyTablesDmMustBeExistingUser() {
		long userIdThatDoesNotExist = 999_999L;

		assertSqlState(FOREIGN_KEY_VIOLATION, () -> insertPartyTable(userIdThatDoesNotExist, 4));
	}

	@Test
	void partyTablesLatitudeMustBeOnEarth() throws SQLException {
		long dmId = insertDm();

		assertSqlState(CHECK_VIOLATION, () -> insertPartyTable(dmId, 4, 91.0, VALID_LONGITUDE));
	}

	@Test
	void partyTablesLongitudeMustBeOnEarth() throws SQLException {
		long dmId = insertDm();

		assertSqlState(CHECK_VIOLATION, () -> insertPartyTable(dmId, 4, VALID_LATITUDE, 181.0));
	}

	// ---------- applications (a player asking to join a party table) ----------

	@Test
	void applicationsAppliesDefaults() throws SQLException {
		long tableId = insertPartyTable(insertDm(), 4);
		long applicationId = insertApplication(tableId, insertPlayer());

		try (PreparedStatement select = connection.prepareStatement(
				"SELECT status, version, created_at, updated_at FROM applications WHERE id = ?")) {
			select.setLong(1, applicationId);

			try (ResultSet row = select.executeQuery()) {
				assertThat(row.next()).isTrue();
				assertThat(row.getString("status")).isEqualTo("PENDING");
				assertThat(row.getLong("version")).isZero();
				assertThat(row.getTimestamp("created_at")).isNotNull();
				assertThat(row.getTimestamp("updated_at")).isNotNull();
			}
		}
	}

	@Test
	void applicationsPlayerCanOnlyApplyOnceToTable() throws SQLException {
		long tableId = insertPartyTable(insertDm(), 4);
		long playerId = insertPlayer();
		insertApplication(tableId, playerId);

		assertSqlState(UNIQUE_VIOLATION, () -> insertApplication(tableId, playerId));
	}

	@Test
	void applicationsStatusMustBeKnownValue() throws SQLException {
		long tableId = insertPartyTable(insertDm(), 4);
		long applicationId = insertApplication(tableId, insertPlayer());

		assertSqlState(CHECK_VIOLATION,
				() -> update("UPDATE applications SET status = 'MAYBE' WHERE id = ?", applicationId));
	}

	@Test
	void applicationsTableMustExist() throws SQLException {
		long playerId = insertPlayer();
		long tableIdThatDoesNotExist = 999_999L;

		assertSqlState(FOREIGN_KEY_VIOLATION, () -> insertApplication(tableIdThatDoesNotExist, playerId));
	}

	@Test
	void applicationsApplicantMustBeExistingUser() throws SQLException {
		long tableId = insertPartyTable(insertDm(), 4);
		long userIdThatDoesNotExist = 999_999L;

		assertSqlState(FOREIGN_KEY_VIOLATION, () -> insertApplication(tableId, userIdThatDoesNotExist));
	}

	// ---------- helpers ----------

	/** Runs the code and asserts it fails with the given PostgreSQL error code. */
	private static void assertSqlState(String expectedSqlState, ThrowingCallable code) {
		assertThatThrownBy(code)
				.isInstanceOfSatisfying(SQLException.class,
						e -> assertThat(e.getSQLState()).isEqualTo(expectedSqlState));
	}

	private long insertUser(String email, String displayName) throws SQLException {
		try (PreparedStatement insert = connection.prepareStatement(
				"INSERT INTO users (email, display_name) VALUES (?, ?) RETURNING id")) {
			insert.setString(1, email);
			insert.setString(2, displayName);

			try (ResultSet row = insert.executeQuery()) {
				row.next();
				return row.getLong("id");
			}
		}
	}

	private long insertDm() throws SQLException {
		return insertUser("dm@example.com", "Dungeon Dan");
	}

	/** Inserts a party table at a valid location. */
	private long insertPartyTable(long dmId, int seatLimit) throws SQLException {
		return insertPartyTable(dmId, seatLimit, VALID_LATITUDE, VALID_LONGITUDE);
	}

	/** Inserts a party table with fixed values for everything except the parts a test wants to vary. */
	private long insertPartyTable(long dmId, int seatLimit, double latitude, double longitude) throws SQLException {
		try (PreparedStatement insert = connection.prepareStatement("""
				INSERT INTO party_tables (dm_id, title, first_session_at, location_name, latitude, longitude, seat_limit)
				VALUES (?, ?, ?, ?, ?, ?, ?)
				RETURNING id""")) {
			insert.setLong(1, dmId);
			insert.setString(2, "Friday Night Curse of Strahd");
			insert.setObject(3, OffsetDateTime.now().plusDays(7));
			insert.setString(4, "The Dice Tower Cafe");
			insert.setDouble(5, latitude);
			insert.setDouble(6, longitude);
			insert.setInt(7, seatLimit);

			try (ResultSet row = insert.executeQuery()) {
				row.next();
				return row.getLong("id");
			}
		}
	}

	private long insertPlayer() throws SQLException {
		return insertUser("player@example.com", "Player Pat");
	}

	private long insertApplication(long partyTableId, long applicantId) throws SQLException {
		try (PreparedStatement insert = connection.prepareStatement(
				"INSERT INTO applications (party_table_id, applicant_id) VALUES (?, ?) RETURNING id")) {
			insert.setLong(1, partyTableId);
			insert.setLong(2, applicantId);

			try (ResultSet row = insert.executeQuery()) {
				row.next();
				return row.getLong("id");
			}
		}
	}

	private void update(String sql, long id) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setLong(1, id);
			statement.executeUpdate();
		}
	}

}
