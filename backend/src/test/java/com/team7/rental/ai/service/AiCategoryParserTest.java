package com.team7.rental.ai.service;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.json.JsonMapper;

/** AI 카테고리 추천 응답 고정 스키마 검증 (REQ-067) */
class AiCategoryParserTest {

	private final AiCategoryParser parser = new AiCategoryParser(JsonMapper.builder().build());

	@Test
	@DisplayName("스키마에 맞으면 code와 후보 이름의 앞뒤 공백을 지워 돌려준다")
	void parseValid() {
		// when
		var parsed = parser.parse("""
			{"categoryCode": " ETC ", "suggestedName": "  악기 "}""");

		// then
		assertThat(parsed).contains(new AiCategoryParser.Parsed("ETC", "악기"));
	}

	@Test
	@DisplayName("후보 이름이 null이면 null 그대로 돌려준다")
	void parseNullSuggestedName() {
		// when
		var parsed = parser.parse("""
			{"categoryCode": "CAMPING", "suggestedName": null}""");

		// then
		assertThat(parsed).contains(new AiCategoryParser.Parsed("CAMPING", null));
	}

	@Test
	@DisplayName("후보 이름이 공백뿐이면 null로 본다")
	void parseBlankSuggestedName() {
		// when
		var parsed = parser.parse("""
			{"categoryCode": "ETC", "suggestedName": "   "}""");

		// then
		assertThat(parsed).contains(new AiCategoryParser.Parsed("ETC", null));
	}

	@Test
	@DisplayName("코드 펜스로 감싼 응답도 읽는다")
	void parseCodeFence() {
		// when
		var parsed = parser.parse("""
			```json
			{"categoryCode": "TOOL", "suggestedName": null}
			```""");

		// then
		assertThat(parsed).contains(new AiCategoryParser.Parsed("TOOL", null));
	}

	@Test
	@DisplayName("후보 이름은 30자(코드 포인트)로 자르고 잘린 끝의 공백을 지운다")
	void truncateSuggestedName() {
		// given: 29자 + 공백 + 이모지 여러 개 → 30자에서 자르면 끝이 공백
		String name = "가".repeat(29) + " " + "🎸".repeat(5);

		// when
		var parsed = parser.parse("{\"categoryCode\": \"ETC\", \"suggestedName\": \"" + name + "\"}");

		// then
		assertThat(parsed).contains(new AiCategoryParser.Parsed("ETC", "가".repeat(29)));
	}

	@Test
	@DisplayName("30자(코드 포인트) 이모지 이름은 그대로 둔다")
	void keepThirtyCodePoints() {
		// given
		String name = "🎸".repeat(AiCategoryParser.SUGGESTED_NAME_MAX);

		// when
		var parsed = parser.parse("{\"categoryCode\": \"ETC\", \"suggestedName\": \"" + name + "\"}");

		// then
		assertThat(parsed).contains(new AiCategoryParser.Parsed("ETC", name));
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"",
		"   ",
		"카테고리는 캠핑입니다",
		"[]",
		"{\"categoryCode\": \"ETC\"}",
		"{\"suggestedName\": null}",
		"{\"categoryCode\": \"ETC\", \"suggestedName\": null, \"confidence\": 0.9}",
		"{\"categoryCode\": 1, \"suggestedName\": null}",
		"{\"categoryCode\": null, \"suggestedName\": null}",
		"{\"categoryCode\": \"  \", \"suggestedName\": null}",
		"{\"categoryCode\": \"ETC\", \"suggestedName\": 3}",
		"{\"categoryCode\": \"ETC\", \"suggestedName\": [\"악기\"]}"
	})
	@DisplayName("키가 빠지거나 더 있거나 타입이 틀리면 empty")
	void rejectInvalid(String text) {
		assertThat(parser.parse(text)).isEmpty();
	}

	@Test
	@DisplayName("null 응답은 empty")
	void rejectNull() {
		assertThat(parser.parse(null)).isEmpty();
	}
}
