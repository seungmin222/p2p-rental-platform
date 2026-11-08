package com.team7.rental.ai.service;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.team7.rental.rental.entity.AiChangeType;
import com.team7.rental.rental.entity.AiCompareResult;

import tools.jackson.databind.json.JsonMapper;

/** AI 응답 고정 스키마 검증 (REQ-040). 반납 사진은 2장으로 둔다 */
class AiCompareParserTest {

	private static final int RETURN_IMAGES = 2;

	private final AiCompareParser parser = new AiCompareParser(JsonMapper.builder().build());

	@Test
	@DisplayName("스키마에 맞으면 결과로 바꾸고 설명·위치 문구의 앞뒤 공백을 지운다")
	void parseValid() {
		// when
		var result = parser.parse("""
			{"changed": true, "changeTypes": ["SCRATCH", "STAIN"], "confidence": 0.75,
			 "description": "  모서리 흠집 ", "regions": [{"imageIndex": 1, "note": " 왼쪽 하단 "}]}""", RETURN_IMAGES);

		// then
		assertThat(result).contains(new AiCompareResult(true, java.util.List.of(AiChangeType.SCRATCH,
			AiChangeType.STAIN), 0.75, "모서리 흠집", java.util.List.of(new AiCompareResult.Region(1, "왼쪽 하단"))));
	}

	@Test
	@DisplayName("변화 없음은 changeTypes·regions가 비어 있어야 한다")
	void parseUnchanged() {
		// when
		var result = parser.parse("""
			{"changed": false, "changeTypes": [], "confidence": 0.9, "description": "변화 없음", "regions": []}""",
			RETURN_IMAGES);

		// then
		assertThat(result).hasValueSatisfying(value -> {
			assertThat(value.changed()).isFalse();
			assertThat(value.changeTypes()).isEmpty();
		});
	}

	@Test
	@DisplayName("```json 코드 블록으로 감싼 응답은 벗겨서 읽는다")
	void stripCodeFence() {
		// when
		var result = parser.parse("""
			```json
			{"changed": false, "changeTypes": [], "confidence": 0.9, "description": "변화 없음", "regions": []}
			```""", RETURN_IMAGES);

		// then
		assertThat(result).isPresent();
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = {
		"",
		"JSON이 아님",
		"[]",
		"앞 문장 {\"changed\": false, \"changeTypes\": [], \"confidence\": 0.9, \"description\": \"x\", \"regions\": []}",
		// 키 누락·추가
		"{\"changed\": false, \"changeTypes\": [], \"confidence\": 0.9, \"description\": \"x\"}",
		"{\"changed\": false, \"changeTypes\": [], \"confidence\": 0.9, \"description\": \"x\", \"regions\": [], \"extra\": 1}",
		// 타입
		"{\"changed\": \"true\", \"changeTypes\": [\"SCRATCH\"], \"confidence\": 0.9, \"description\": \"x\", \"regions\": []}",
		"{\"changed\": true, \"changeTypes\": [\"SCRATCH\"], \"confidence\": \"0.9\", \"description\": \"x\", \"regions\": []}",
		"{\"changed\": true, \"changeTypes\": \"SCRATCH\", \"confidence\": 0.9, \"description\": \"x\", \"regions\": []}",
		// 허용 값·중복
		"{\"changed\": true, \"changeTypes\": [\"DENT\"], \"confidence\": 0.9, \"description\": \"x\", \"regions\": []}",
		"{\"changed\": true, \"changeTypes\": [\"SCRATCH\", \"SCRATCH\"], \"confidence\": 0.9, \"description\": \"x\", \"regions\": []}",
		// 범위
		"{\"changed\": true, \"changeTypes\": [\"SCRATCH\"], \"confidence\": 1.1, \"description\": \"x\", \"regions\": []}",
		"{\"changed\": true, \"changeTypes\": [\"SCRATCH\"], \"confidence\": -0.1, \"description\": \"x\", \"regions\": []}",
		"{\"changed\": true, \"changeTypes\": [\"SCRATCH\"], \"confidence\": 0.9, \"description\": \"  \", \"regions\": []}",
		"{\"changed\": true, \"changeTypes\": [\"SCRATCH\"], \"confidence\": 0.9, \"description\": \"x\", \"regions\": [{\"imageIndex\": 2, \"note\": \"x\"}]}",
		"{\"changed\": true, \"changeTypes\": [\"SCRATCH\"], \"confidence\": 0.9, \"description\": \"x\", \"regions\": [{\"imageIndex\": -1, \"note\": \"x\"}]}",
		"{\"changed\": true, \"changeTypes\": [\"SCRATCH\"], \"confidence\": 0.9, \"description\": \"x\", \"regions\": [{\"imageIndex\": 0.5, \"note\": \"x\"}]}",
		"{\"changed\": true, \"changeTypes\": [\"SCRATCH\"], \"confidence\": 0.9, \"description\": \"x\", \"regions\": [{\"imageIndex\": 0}]}",
		// changed와 나머지가 어긋남
		"{\"changed\": true, \"changeTypes\": [], \"confidence\": 0.9, \"description\": \"x\", \"regions\": []}",
		"{\"changed\": false, \"changeTypes\": [\"SCRATCH\"], \"confidence\": 0.9, \"description\": \"x\", \"regions\": []}",
		"{\"changed\": false, \"changeTypes\": [], \"confidence\": 0.9, \"description\": \"x\", \"regions\": [{\"imageIndex\": 0, \"note\": \"x\"}]}"
	})
	@DisplayName("스키마에 어긋나면 empty")
	void rejectInvalid(String text) {
		assertThat(parser.parse(text, RETURN_IMAGES)).isEmpty();
	}

	@Test
	@DisplayName("null은 empty")
	void rejectNull() {
		assertThat(parser.parse(null, RETURN_IMAGES)).isEmpty();
	}
}
