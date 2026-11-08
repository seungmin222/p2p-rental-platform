package com.team7.rental.ai.service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.team7.rental.rental.entity.AiChangeType;
import com.team7.rental.rental.entity.AiCompareResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * AI 응답 텍스트 → 고정 스키마(docs/domains/ai.md REQ-040) 검증. 하나라도 어긋나면 empty이고 부르는 쪽이 다시 부른다(ai.compare.max-retry).
 * <ul>
 *   <li>키는 changed·changeTypes·confidence·description·regions 다섯 개만, 모두 있어야 한다</li>
 *   <li>changeTypes는 허용 값만, 중복 없음. changed=false면 changeTypes·regions가 비어 있고, true면 changeTypes가 하나 이상</li>
 *   <li>confidence 0.0~1.0, description은 공백만이 아님, regions.imageIndex는 반납 사진 범위 안</li>
 * </ul>
 * 모델이 JSON을 코드 블록(```json)으로 감싼 경우만 벗겨 낸다. 그 밖의 앞뒤 문장은 형식 오류로 본다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiCompareParser {

	private static final Set<String> KEYS = Set.of("changed", "changeTypes", "confidence", "description", "regions");
	private static final Set<String> REGION_KEYS = Set.of("imageIndex", "note");

	private final JsonMapper jsonMapper;

	/**
	 * @param returnImageCount 반납 사진 장수 (imageIndex 범위)
	 * @return 스키마에 맞으면 결과
	 */
	public Optional<AiCompareResult> parse(String text, int returnImageCount) {
		if (text == null || text.isBlank()) {
			return invalid("empty");
		}
		JsonNode root;
		try {
			root = jsonMapper.readTree(stripCodeFence(text.strip()));
		} catch (JacksonException e) {
			return invalid("not json");
		}
		if (root == null || !root.isObject() || !keysOf(root).equals(KEYS)) {
			return invalid("keys");
		}
		JsonNode changed = root.get("changed");
		JsonNode confidence = root.get("confidence");
		JsonNode description = root.get("description");
		if (!changed.isBoolean() || !confidence.isNumber() || !description.isString()) {
			return invalid("types");
		}
		double confidenceValue = confidence.doubleValue();
		if (!(confidenceValue >= 0.0 && confidenceValue <= 1.0)) {
			return invalid("confidence range");
		}
		String descriptionValue = description.stringValue().strip();
		if (descriptionValue.isEmpty()) {
			return invalid("description blank");
		}
		Optional<List<AiChangeType>> changeTypes = changeTypes(root.get("changeTypes"));
		Optional<List<AiCompareResult.Region>> regions = regions(root.get("regions"), returnImageCount);
		if (changeTypes.isEmpty() || regions.isEmpty()) {
			return invalid("changeTypes/regions");
		}
		boolean changedValue = changed.booleanValue();
		if (changedValue == changeTypes.get().isEmpty() || (!changedValue && !regions.get().isEmpty())) {
			return invalid("changed consistency");
		}
		return Optional.of(new AiCompareResult(changedValue, changeTypes.get(), confidenceValue, descriptionValue,
			regions.get()));
	}

	private static Optional<List<AiChangeType>> changeTypes(JsonNode node) {
		if (!node.isArray()) {
			return Optional.empty();
		}
		EnumSet<AiChangeType> seen = EnumSet.noneOf(AiChangeType.class);
		List<AiChangeType> types = new ArrayList<>();
		for (JsonNode element : node) {
			if (!element.isString()) {
				return Optional.empty();
			}
			AiChangeType type;
			try {
				type = AiChangeType.valueOf(element.stringValue());
			} catch (IllegalArgumentException e) {
				return Optional.empty();
			}
			if (!seen.add(type)) {
				return Optional.empty();
			}
			types.add(type);
		}
		return Optional.of(types);
	}

	private static Optional<List<AiCompareResult.Region>> regions(JsonNode node, int returnImageCount) {
		if (!node.isArray()) {
			return Optional.empty();
		}
		List<AiCompareResult.Region> regions = new ArrayList<>();
		for (JsonNode element : node) {
			if (!element.isObject() || !keysOf(element).equals(REGION_KEYS)) {
				return Optional.empty();
			}
			JsonNode imageIndex = element.get("imageIndex");
			JsonNode note = element.get("note");
			if (!imageIndex.isIntegralNumber() || !imageIndex.canConvertToInt() || !note.isString()) {
				return Optional.empty();
			}
			int index = imageIndex.intValue();
			if (index < 0 || index >= returnImageCount) {
				return Optional.empty();
			}
			regions.add(new AiCompareResult.Region(index, note.stringValue().strip()));
		}
		return Optional.of(regions);
	}

	private static Set<String> keysOf(JsonNode node) {
		Set<String> keys = new java.util.HashSet<>();
		Iterator<String> names = node.propertyNames().iterator();
		names.forEachRemaining(keys::add);
		return keys;
	}

	private static String stripCodeFence(String text) {
		if (!text.startsWith("```") || !text.endsWith("```") || text.length() < 6) {
			return text;
		}
		String inner = text.substring(3, text.length() - 3);
		int newline = inner.indexOf('\n');
		// ```json 처럼 언어 표시가 붙은 첫 줄을 버린다
		return newline < 0 ? inner : inner.substring(newline + 1);
	}

	private static Optional<AiCompareResult> invalid(String reason) {
		// 응답 내용은 로그에 남기지 않는다(사진 설명이 들어 있다)
		log.info("AI compare response rejected: {}", reason);
		return Optional.empty();
	}
}
