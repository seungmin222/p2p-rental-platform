package com.team7.rental.ai.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.util.unit.DataSize;

import com.team7.rental.global.policy.PolicyProperties;

import tools.jackson.databind.json.JsonMapper;

/** AI 등록 보조 응답 검증 (REQ-067). categoryCode만 필수이고 나머지는 필드별로 null 처리 */
class AiDraftParserTest {

	private final AiDraftParser parser = new AiDraftParser(JsonMapper.builder().build(), policies());

	@Test
	@DisplayName("모든 필드가 맞으면 문자열 앞뒤 공백을 지워 그대로 돌려준다")
	void parseValid() {
		// when
		var parsed = parser.parse("""
			{"categoryCode": " CAMPING ", "suggestedName": null, "title": " 4인용 돔 텐트 ",
			 "description": " 폴대·팩 포함 ", "itemPrice": 200000, "pricePerDay": 15000}""");

		// then
		assertThat(parsed).contains(new AiDraftParser.Parsed("CAMPING", null, "4인용 돔 텐트", "폴대·팩 포함", 200000, 15000));
	}

	@Test
	@DisplayName("categoryCode만 있어도 읽고, 빠진 필드는 null이다")
	void parseOnlyCategoryCode() {
		// when
		var parsed = parser.parse("{\"categoryCode\": \"TOOL\"}");

		// then
		assertThat(parsed).contains(new AiDraftParser.Parsed("TOOL", null, null, null, null, null));
	}

	@Test
	@DisplayName("모르는 키는 무시한다")
	void ignoreUnknownKeys() {
		// when
		var parsed = parser.parse("{\"categoryCode\": \"TOOL\", \"deposit\": 50000, \"confidence\": 0.9}");

		// then
		assertThat(parsed).contains(new AiDraftParser.Parsed("TOOL", null, null, null, null, null));
	}

	@Test
	@DisplayName("코드 펜스로 감싼 응답도 읽는다")
	void parseCodeFence() {
		// when
		var parsed = parser.parse("""
			```json
			{"categoryCode": "TOOL", "title": "전동 드릴"}
			```""");

		// then
		assertThat(parsed).map(AiDraftParser.Parsed::title).contains("전동 드릴");
	}

	@Test
	@DisplayName("문자열 필드가 공백뿐이거나 문자열이 아니면 그 필드만 null")
	void blankOrWrongTypeTextIsNull() {
		// when
		var parsed = parser.parse("""
			{"categoryCode": "ETC", "suggestedName": "   ", "title": 3, "description": ["설명"]}""");

		// then
		assertThat(parsed).contains(new AiDraftParser.Parsed("ETC", null, null, null, null, null));
	}

	@Test
	@DisplayName("후보 이름은 30자, 제목은 item.name.max, 설명은 item.description.max(코드 포인트)로 자르고 잘린 끝 공백을 지운다")
	void truncateTexts() {
		// given: 29자 + 공백 + 이모지 → 30자에서 자르면 끝이 공백
		String name = "가".repeat(29) + " " + "🎸".repeat(5);
		String title = "🎸".repeat(60);
		String description = "나".repeat(2001);

		// when
		var parsed = parser.parse("{\"categoryCode\": \"ETC\", \"suggestedName\": \"" + name + "\", \"title\": \""
			+ title + "\", \"description\": \"" + description + "\"}");

		// then
		assertThat(parsed).contains(new AiDraftParser.Parsed("ETC", "가".repeat(29), "🎸".repeat(50),
			"나".repeat(2000), null, null));
	}

	@Test
	@DisplayName("원가는 0 ~ item.item-price.max 정수만 받는다")
	void itemPriceRange() {
		assertThat(itemPrice("0")).isZero();
		assertThat(itemPrice("10000000")).isEqualTo(10_000_000);
		assertThat(itemPrice("10000001")).isNull();
		assertThat(itemPrice("-1")).isNull();
		assertThat(itemPrice("15000.5")).isNull();
		assertThat(itemPrice("\"15000\"")).isNull();
		assertThat(itemPrice("99999999999")).isNull();
		assertThat(itemPrice("null")).isNull();
	}

	@Test
	@DisplayName("일 대여료는 0원 또는 item.price-per-day 범위 정수만 받고 보정하지 않는다")
	void pricePerDayRange() {
		assertThat(pricePerDay("0")).isZero();
		assertThat(pricePerDay("1000")).isEqualTo(1000);
		assertThat(pricePerDay("1000000")).isEqualTo(1_000_000);
		assertThat(pricePerDay("999")).isNull();
		assertThat(pricePerDay("1000001")).isNull();
		assertThat(pricePerDay("-1000")).isNull();
		assertThat(pricePerDay("15000.0")).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"",
		"   ",
		"카테고리는 캠핑입니다",
		"[]",
		"{\"title\": \"텐트\"}",
		"{\"categoryCode\": 1}",
		"{\"categoryCode\": null}",
		"{\"categoryCode\": \"  \"}",
		"{\"categoryCode\": [\"ETC\"]}"
	})
	@DisplayName("JSON 객체가 아니거나 categoryCode가 공백 아닌 문자열이 아니면 empty (다시 부른다)")
	void rejectInvalid(String text) {
		assertThat(parser.parse(text)).isEmpty();
	}

	@Test
	@DisplayName("null 응답은 empty")
	void rejectNull() {
		assertThat(parser.parse(null)).isEmpty();
	}

	private Integer itemPrice(String json) {
		return parser.parse("{\"categoryCode\": \"ETC\", \"itemPrice\": " + json + "}").orElseThrow().itemPrice();
	}

	private Integer pricePerDay(String json) {
		return parser.parse("{\"categoryCode\": \"ETC\", \"pricePerDay\": " + json + "}").orElseThrow().pricePerDay();
	}

	static PolicyProperties policies() {
		PolicyProperties policies = mock(PolicyProperties.class);
		when(policies.item()).thenReturn(new PolicyProperties.Item(50, 2000,
			new PolicyProperties.Item.Amount(1000, 1000000), new PolicyProperties.Item.Amount(1000, 3000000),
			10000000, new PolicyProperties.Item.Images(1, 10, DataSize.ofMegabytes(10))));
		return policies;
	}
}
