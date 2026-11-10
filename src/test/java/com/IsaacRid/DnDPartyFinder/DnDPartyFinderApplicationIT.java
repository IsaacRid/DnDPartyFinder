package com.IsaacRid.DnDPartyFinder;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * The project's single Spring Boot test: starts the whole app (real web server, Flyway, Testcontainers
 * PostgreSQL) and checks it reports healthy over HTTP, the same way Render will.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT) // real web server on a free port
class DnDPartyFinderApplicationIT {

	@Value("${local.server.port}") // the random port Spring picked
	int port;

	@Test
	void healthEndpointReportsUp() throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health"))
				.build();

		HttpResponse<String> response = HttpClient.newHttpClient()
				.send(request, HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(200);
		// "UP" also means the database health check passed
		assertThat(response.body()).contains("\"status\":\"UP\"");
	}

}
