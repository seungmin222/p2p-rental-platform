package com.team7.rental.item.service;

import static org.assertj.core.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import com.team7.rental.support.IntegrationTestSupport;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "server.tomcat.remoteip.internal-proxies=(?!)")
class ItemUntrustedForwardedHeadersIntegrationTest extends IntegrationTestSupport {
	@Autowired
	private Environment environment;
	private Long itemId;

	@BeforeEach
	void setUp() {
		itemId = fixture.createItem(fixture.createVerifiedMember(),
			LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 8)).item().getId();
	}

	@Test
	void directRequestCannotSpoofAddressWithForwardedHeader() throws Exception {
		assertThat(count(null)).isEqualTo(1);
		assertThat(count("198.51.100.10")).isEqualTo(1);
		assertThat(count("198.51.100.11")).isEqualTo(1);
	}

	private int count(String forwardedFor) throws Exception {
		var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
			+ environment.getRequiredProperty("local.server.port") + "/api/items/" + itemId))
			.timeout(Duration.ofSeconds(10)).GET();
		if (forwardedFor != null)
			request.header("X-Forwarded-For", forwardedFor);
		try (var client = HttpClient.newHttpClient()) {
			var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
			assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(200);
			var matcher = Pattern.compile("\"viewCount\"\\s*:\\s*(\\d+)").matcher(response.body());
			assertThat(matcher.find()).isTrue();
			return Integer.parseInt(matcher.group(1));
		}
	}
}
