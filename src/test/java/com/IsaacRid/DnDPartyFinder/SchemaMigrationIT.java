package com.IsaacRid.DnDPartyFinder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checks that the Flyway migrations build the schema we expect.
 * Uses plain SQL (JdbcTemplate) so we test the database itself, not JPA mappings.
 * Each test runs in a transaction that is rolled back afterwards, so tests don't see each other's rows.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class SchemaMigrationIT {

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void usersTableGeneratesIdAndCreatedAt() {
		Long id = jdbc.queryForObject(
				"INSERT INTO users (email, display_name) VALUES (?, ?) RETURNING id",
				Long.class, "gm@example.com", "Dungeon Dan");

		assertThat(id).isNotNull();
		assertThat(jdbc.queryForObject("SELECT created_at IS NOT NULL FROM users WHERE id = ?", Boolean.class, id))
				.isTrue();
	}

	@Test
	void usersEmailIsUniqueIgnoringCase() {
		jdbc.update("INSERT INTO users (email, display_name) VALUES (?, ?)", "player@example.com", "First");

		assertThatThrownBy(() -> jdbc.update(
				"INSERT INTO users (email, display_name) VALUES (?, ?)", "PLAYER@example.com", "Second"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void usersDisplayNameIsRequired() {
		assertThatThrownBy(() -> jdbc.update(
				"INSERT INTO users (email, display_name) VALUES (?, NULL)", "nameless@example.com"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

}
