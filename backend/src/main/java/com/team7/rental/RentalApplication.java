package com.team7.rental;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.team7.rental.global.config.TimeConfig;

@SpringBootApplication
public class RentalApplication {

	public static void main(String[] args) {
		// JVM 기본 타임존을 서버 타임존으로 고정 (테스트 JVM은 build.gradle에서 같은 값으로 설정)
		TimeZone.setDefault(TimeZone.getTimeZone(TimeConfig.ZONE_ID));
		SpringApplication.run(RentalApplication.class, args);
	}

}
