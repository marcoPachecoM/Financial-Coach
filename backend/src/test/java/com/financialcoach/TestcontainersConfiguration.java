package com.financialcoach;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Shared by every integration test that needs a real PostgreSQL. */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	/**
	 * Production (Supabase) runs PostgreSQL 17, so tests do too by default. CI also runs them on 18
	 * (compose.yaml, local development): -Dtest.postgres.image=postgres:18-alpine.
	 */
	static final String POSTGRES_IMAGE = System.getProperty("test.postgres.image", "postgres:17-alpine");

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE));
	}

}
