package com.team7.rental;

import static org.assertj.core.api.Assertions.*;

import java.time.Clock;
import java.time.ZoneId;
import java.util.TimeZone;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class RentalApplicationTests {

	@Autowired
	private Clock clock;

	@Test
	void contextLoads() {}

	@Test
	@DisplayName("Clock 빈과 JVM 기본 타임존은 Asia/Seoul이다")
	void timeZoneIsSeoul() {
		// then
		assertThat(clock.getZone()).isEqualTo(ZoneId.of("Asia/Seoul"));
		assertThat(TimeZone.getDefault().toZoneId()).isEqualTo(ZoneId.of("Asia/Seoul"));
	}

}
