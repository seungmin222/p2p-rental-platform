package com.team7.rental;

import static com.team7.rental.support.IntegrationTestSupport.*;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

/** TestRentalApplication(로컬 인프라 없이 컨테이너로 앱 실행)용. 테스트 코드는 IntegrationTestSupport를 상속한다. */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	MySQLContainer mysqlContainer() {
		return new MySQLContainer(MYSQL_IMAGE);
	}

	@Bean
	@ServiceConnection
	RabbitMQContainer rabbitContainer() {
		return new RabbitMQContainer(RABBITMQ_IMAGE);
	}

	@Bean
	@ServiceConnection(name = "redis")
	GenericContainer<?> redisContainer() {
		return new GenericContainer<>(REDIS_IMAGE).withExposedPorts(6379);
	}

}
