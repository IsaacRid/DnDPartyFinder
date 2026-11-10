package com.IsaacRid.DnDPartyFinder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Checks that the Flyway migrations build the schema we expect, using a real PostgreSQL.
 * Plain JUnit + Testcontainers + JDBC: no Spring context.
 */
@Testcontainers
class SchemaMigrationIT {

	// SQLState codes defined by the SQL standard / PostgreSQL
	private static final String UNIQUE_VIOLATION = "23505";
	private static final String NOT_NULL_VIOLATION = "23502";

	// static = one container shared by every test in this class (started once, stopped at the end)
	@Container
	static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));

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

	@Test
	void usersTableGeneratesIdAndCreatedAt() throws SQLException {
		try (PreparedStatement insert = connection.prepareStatement(
				"INSERT INTO users (email, display_name) VALUES (?, ?) RETURNING id, created_at")) {
			insert.setString(1, "gm@example.com");
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

		assertThatThrownBy(() -> insertUser("PLAYER@example.com", "Second"))
				.isInstanceOfSatisfying(SQLException.class,
						e -> assertThat(e.getSQLState()).isEqualTo(UNIQUE_VIOLATION));
	}

	@Test
	void usersDisplayNameIsRequired() {
		assertThatThrownBy(() -> insertUser("nameless@example.com", null))
				.isInstanceOfSatisfying(SQLException.class,
						e -> assertThat(e.getSQLState()).isEqualTo(NOT_NULL_VIOLATION));
	}

	private void insertUser(String email, String displayName) throws SQLException {
		try (PreparedStatement insert = connection.prepareStatement(
				"INSERT INTO users (email, display_name) VALUES (?, ?)")) {
			insert.setString(1, email);
			insert.setString(2, displayName);
			insert.executeUpdate();
		}
	}

}
