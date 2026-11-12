package com.team7.rental.ai.client;

import java.util.List;
import java.util.stream.Collectors;

/**
 * AI 카테고리 추천 프롬프트 (docs/domains/ai.md REQ-067: 활성 카테고리 code 중에서만, 없으면 ETC + 후보 이름). 시스템 프롬프트는 요청마다 같게 두고
 * 카테고리 목록은 사용자 메시지에 넣는다(관리자가 카테고리를 바꿔도 시스템 프롬프트는 그대로).
 */
final class CategoryPrompt {

	static final String SYSTEM = """
		당신은 물품 대여 플랫폼에서 사용자가 올린 물품 사진을 보고 알맞은 카테고리를 고르는 도우미입니다.
		결과는 사용자가 고칠 수 있는 추천입니다.

		규칙:
		- 사진 속 대여할 물품 하나를 기준으로, 주어진 카테고리 목록의 code 중 하나만 고릅니다.
		- 목록 중 어디에도 맞지 않으면 categoryCode를 "ETC"로 하고, suggestedName에 새 카테고리 이름을 한국어로 짧게(15자 이내) 적습니다.
		- 목록에 맞는 카테고리가 있으면 suggestedName은 null입니다.
		- 아래 스키마의 JSON 객체 하나만 출력합니다. 코드 블록, 설명 문장, 다른 키를 넣지 않습니다.

		출력 스키마:
		{
		  "categoryCode": "목록의 code",
		  "suggestedName": "새 카테고리 이름" 또는 null
		}
		""";

	static String instruction(int imageCount, List<CategoryOption> categories) {
		String list = categories.stream()
			.map(category -> "- " + category.code() + ": " + category.name())
			.collect(Collectors.joining("\n"));
		return "위 " + imageCount + "장은 같은 물품의 사진입니다. 카테고리 목록:\n" + list
			+ "\n스키마에 맞는 JSON 하나만 출력하세요.";
	}

	private CategoryPrompt() {}
}
