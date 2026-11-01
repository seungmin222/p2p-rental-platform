package com.team7.rental.member.client;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.member.dto.RegionCoordRequest;

@Component
public class KakaoRegionLookupClient implements RegionLookupClient {
	private final RestClient client;
	private final String apiKey;

	public KakaoRegionLookupClient(@Value("${KAKAO_REST_API_KEY:}")
	String apiKey) {
		this.apiKey = apiKey;
		var factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(3));
		factory.setReadTimeout(Duration.ofSeconds(5));
		this.client = RestClient.builder().baseUrl("https://dapi.kakao.com")
			.requestFactory(factory).build();
	}

	@Override
	public String findAdministrativeCode(RegionCoordRequest coord) {
		if (apiKey.isBlank()) {
			throw new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR);
		}
		if (coord == null || coord.lat() == null || coord.lng() == null
			|| !Double.isFinite(coord.lat()) || !Double.isFinite(coord.lng())
			|| Math.abs(coord.lat()) > 90 || Math.abs(coord.lng()) > 180) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT);
		}
		try {
			var body = client.get().uri(builder -> builder.path("/v2/local/geo/coord2regioncode.json")
				.queryParam("x", coord.lng()).queryParam("y", coord.lat()).build())
				.header("Authorization", "KakaoAK " + apiKey).retrieve().body(RegionBody.class);
			if (body == null || body.documents() == null) {
				throw new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR);
			}
			return body.documents().stream().filter(value -> value != null && "H".equals(value.regionType()))
				.map(RegionDocument::code).filter(code -> code != null && code.matches("[0-9]{10}"))
				.findFirst().orElseThrow(() -> new BusinessException(ErrorCode.COMMON_NOT_FOUND));
		} catch (RestClientException e) {
			// 응답 원문에는 요청 정보가 포함될 수 있어 토큰·좌표와 함께 기록하지 않는다.
			throw new BusinessException(ErrorCode.COMMON_INTERNAL_ERROR);
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record RegionBody(List<RegionDocument> documents) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record RegionDocument(@JsonProperty("region_type")
	String regionType, String code) {
	}
}
