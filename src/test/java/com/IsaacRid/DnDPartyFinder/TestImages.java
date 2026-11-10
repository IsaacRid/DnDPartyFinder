package com.IsaacRid.DnDPartyFinder;

import org.testcontainers.utility.DockerImageName;

/**
 * Docker images used by integration tests, defined once so every test runs the same version.
 * Keep in sync with the image in compose.yaml (local dev) and the Render database version.
 */
final class TestImages {

	// Pinned to the major version: gets patch releases, never jumps to a new major by itself
	static final DockerImageName POSTGRES = DockerImageName.parse("postgres:18");

	private TestImages() {
		// constants only, no instances
	}

}
