package com.financialcoach;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Shared by every integration test that needs a real PostgreSQL. */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	/** Same major version as compose.yaml, so tests run against the real database engine. */
	static final String POSTGRES_IMAGE = "postgres:18-alpine";

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE));
	}

}
