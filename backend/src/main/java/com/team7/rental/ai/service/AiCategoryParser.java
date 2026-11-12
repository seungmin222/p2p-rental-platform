package com.team7.rental.ai.service;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * AI 카테고리 추천 응답 텍스트 → 고정 스키마(categoryCode·suggestedName) 검증 (REQ-067). 어긋나면 empty이고 부르는 쪽이 다시 부른다(ai.max-retry).
 * <ul>
 *   <li>키는 categoryCode·suggestedName 두 개만, 모두 있어야 한다</li>
 *   <li>categoryCode는 공백이 아닌 문자열. 목록에 있는지는 부르는 쪽이 본다(없으면 ETC)</li>
 *   <li>suggestedName은 문자열 또는 null. 공백만이면 null로 보고, 앞뒤 공백을 지운 뒤 {@link #SUGGESTED_NAME_MAX}자(코드 포인트)로 자른다</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiCategoryParser {

	/** category_suggestions.suggested_name·categories.name 길이 */
	static final int SUGGESTED_NAME_MAX = 30;

	private static final Set<String> KEYS = Set.of("categoryCode", "suggestedName");

	private final JsonMapper jsonMapper;

	public record Parsed(String categoryCode, String suggestedName) {
	}

	public Optional<Parsed> parse(String text) {
		if (text == null || text.isBlank()) {
			return invalid("empty");
		}
		JsonNode root;
		try {
			root = jsonMapper.readTree(AiCompareParser.stripCodeFence(text.strip()));
		} catch (JacksonException e) {
			return invalid("not json");
		}
		if (root == null || !root.isObject() || !keysOf(root).equals(KEYS)) {
			return invalid("keys");
		}
		JsonNode code = root.get("categoryCode");
		JsonNode name = root.get("suggestedName");
		if (!code.isString() || code.stringValue().isBlank() || !(name.isNull() || name.isString())) {
			return invalid("types");
		}
		return Optional
			.of(new Parsed(code.stringValue().strip(), name.isNull() ? null : normalize(name.stringValue())));
	}

	private static String normalize(String name) {
		String stripped = name.strip();
		if (stripped.isEmpty()) {
			return null;
		}
		if (stripped.codePointCount(0, stripped.length()) <= SUGGESTED_NAME_MAX) {
			return stripped;
		}
		return stripped.substring(0, stripped.offsetByCodePoints(0, SUGGESTED_NAME_MAX)).strip();
	}

	private static Set<String> keysOf(JsonNode node) {
		Set<String> keys = new HashSet<>();
		node.propertyNames().forEach(keys::add);
		return keys;
	}

	private static Optional<Parsed> invalid(String reason) {
		log.info("AI category response rejected: {}", reason);
		return Optional.empty();
	}
}
