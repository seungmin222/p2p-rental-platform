package com.team7.rental.support;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import com.team7.rental.global.security.JwtProvider;

/**
 * 통합 테스트 기반 클래스. 상속만 하면 test 프로필 + MySQL·Redis·RabbitMQ 컨테이너 + MockMvc가 준비된다.
 *
 * <ul>
 *   <li>컨테이너: JVM당 한 번만 띄워 모든 테스트 클래스가 같이 쓴다(싱글턴 컨테이너). 종료는 Testcontainers(Ryuk)가 한다.</li>
 *   <li>시각 고정: {@link #testClock}{@code .fixAt(...)}. 각 테스트가 끝나면 실제 시각으로 돌아온다.</li>
 *   <li>데이터 정리: 각 테스트가 끝나면 {@link DataCleaner}가 MySQL 테이블과 Redis를 비운다.</li>
 *   <li>테스트 데이터: {@link #fixture}로 회원·인증된 대표 동네·물품+가능 기간을, {@link #rentalFixture}로 지정한 상태의 대여를 만든다.</li>
 *   <li>인증: {@link #bearerToken}으로 만든 값을 Authorization 헤더에 넣는다. 실제 JWT 필터를 타므로 401·403까지 검증된다.</li>
 * </ul>
 *
 * 하위 클래스에서 {@code @MockitoBean}·{@code @TestPropertySource}·{@code @Import}를 추가하면 스프링 컨텍스트가 따로 만들어져
 * 느려진다(컨테이너는 그대로 공유). 꼭 필요할 때만 쓴다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestClockConfig.class, DataCleaner.class, MemberItemFixture.class, RentalFixture.class,
	FakePaymentGateway.class})
public abstract class IntegrationTestSupport {

	// 이미지 버전은 infra/docker-compose.local.yml과 같게
	public static final DockerImageName MYSQL_IMAGE = DockerImageName.parse("mysql:8.4");
	public static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7-alpine");
	public static final DockerImageName RABBITMQ_IMAGE = DockerImageName.parse("rabbitmq:4-management");

	@ServiceConnection
	static final MySQLContainer MYSQL = new MySQLContainer(MYSQL_IMAGE);

	@ServiceConnection(name = "redis")
	static final GenericContainer<?> REDIS = new GenericContainer<>(REDIS_IMAGE).withExposedPorts(6379);

	@ServiceConnection
	static final RabbitMQContainer RABBITMQ = new RabbitMQContainer(RABBITMQ_IMAGE);

	static {
		MYSQL.start();
		REDIS.start();
		RABBITMQ.start();
	}

	@Autowired
	protected MockMvc mockMvc;

	@Autowired
	protected TestClock testClock;

	@Autowired
	protected MemberItemFixture fixture;

	@Autowired
	protected RentalFixture rentalFixture;

	@Autowired
	protected FakePaymentGateway paymentGateway;

	@Autowired
	private DataCleaner dataCleaner;

	@Autowired
	private JwtProvider jwtProvider;

	/** Authorization 헤더 값 "Bearer {Access Token}" (role USER) */
	protected String bearerToken(Long memberId) {
		return "Bearer " + jwtProvider.createAccessToken(memberId, "USER");
	}

	@AfterEach
	void cleanUpIntegrationTest() {
		testClock.reset();
		paymentGateway.reset();
		dataCleaner.clean();
	}
}
