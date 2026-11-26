package com.team7.rental.ai.service;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.team7.rental.global.policy.PolicyProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * AI 등록 보조 응답 텍스트 → 초안 (REQ-067).
 * <ul>
 *   <li>JSON 객체가 아니거나 categoryCode가 공백 아닌 문자열이 아니면 empty이고 부르는 쪽이 다시 부른다(ai.max-retry). 카테고리는 폼에 바로
 *   선택되는 필수 결과라서다. 목록에 있는지는 부르는 쪽이 본다(없으면 ETC)</li>
 *   <li>나머지 필드는 하나씩 본다. 없거나 형식·범위가 틀리면 그 필드만 null이고 다시 부르지 않는다. 모르는 키는 무시한다</li>
 *   <li>suggestedName·title·description: 문자열이면 앞뒤 공백을 지우고 공백뿐이면 null. 각각 {@link #SUGGESTED_NAME_MAX}자,
 *   item.name.max, item.description.max(코드 포인트)로 자른다</li>
 *   <li>itemPrice: 0 ~ item.item-price.max 정수. pricePerDay: item.price-per-day에 맞는 정수(0원 또는 범위). 값을 보정하지 않는다</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiDraftParser {

	/** category_suggestions.suggested_name·categories.name 길이 */
	static final int SUGGESTED_NAME_MAX = 30;

	private final JsonMapper jsonMapper;
	private final PolicyProperties policyProperties;

	public record Parsed(String categoryCode, String suggestedName, String title, String description,
		Integer itemPrice, Integer pricePerDay) {
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
		if (root == null || !root.isObject()) {
			return invalid("not object");
		}
		JsonNode code = root.get("categoryCode");
		if (code == null || !code.isString() || code.stringValue().isBlank()) {
			return invalid("categoryCode");
		}
		PolicyProperties.Item item = policyProperties.item();
		Integer itemPrice = amount(root.get("itemPrice"));
		if (itemPrice != null && (itemPrice < 0 || itemPrice > item.itemPriceMax())) {
			itemPrice = null;
		}
		Integer pricePerDay = amount(root.get("pricePerDay"));
		if (pricePerDay != null && !item.pricePerDay().isAllowed(pricePerDay)) {
			pricePerDay = null;
		}
		return Optional.of(new Parsed(code.stringValue().strip(), text(root.get("suggestedName"), SUGGESTED_NAME_MAX),
			text(root.get("title"), item.nameMax()), text(root.get("description"), item.descriptionMax()), itemPrice,
			pricePerDay));
	}

	/** 문자열이 아니면 null. 앞뒤 공백을 지우고 max자(코드 포인트)로 자른 뒤 잘린 끝 공백도 지운다 */
	private static String text(JsonNode node, int max) {
		if (node == null || !node.isString()) {
			return null;
		}
		String stripped = node.stringValue().strip();
		if (stripped.isEmpty()) {
			return null;
		}
		if (stripped.codePointCount(0, stripped.length()) <= max) {
			return stripped;
		}
		return stripped.substring(0, stripped.offsetByCodePoints(0, max)).strip();
	}

	/** int 범위의 정수만. 소수(15000.5)·문자열("15000")은 null */
	private static Integer amount(JsonNode node) {
		if (node == null || !node.isIntegralNumber() || !node.canConvertToInt()) {
			return null;
		}
		return node.intValue();
	}

	private static Optional<Parsed> invalid(String reason) {
		log.info("AI draft response rejected: {}", reason);
		return Optional.empty();
	}
}
