package com.opinio.passform.support;

import org.flywaydb.core.api.FlywayException;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

// 실제 Postgres를 쓰는 테스트의 기반. 앱 전체를 ddl-auto=validate로 띄우고, 컨텍스트를 하나만 공유한다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(DatabaseTest.EmptyDatabaseConfig.class)
public abstract class DatabaseTest {

	@TestConfiguration(proxyBeanMethods = false)
	static class EmptyDatabaseConfig {

		// 앱을 띄울 때마다 passform_test 스키마를 비우므로 항상 빈 DB에서 마이그레이션을 검증한다.
		@Bean
		FlywayMigrationStrategy cleanThenMigrate() {
			return flyway -> {
				try {
					flyway.clean();
					flyway.migrate();
				}
				catch (FlywayException exception) {
					throw new IllegalStateException(
							"DB 테스트에는 Postgres가 필요합니다. .env를 준비하고 docker compose up -d db를 실행하세요.", exception);
				}
			};
		}

	}

}
