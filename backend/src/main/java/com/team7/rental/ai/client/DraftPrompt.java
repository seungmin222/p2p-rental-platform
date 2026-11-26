package com.team7.rental.ai.client;

import java.util.List;
import java.util.stream.Collectors;

/**
 * AI 등록 보조 프롬프트 (docs/domains/ai.md REQ-067): 카테고리(활성 카테고리 code 중에서만, 없으면 ETC + 후보 이름), 제목·설명 초안, 추정
 * 원가, 일 대여료 제안. 보증금은 묻지 않는다(서버가 추정 원가에 권장 보증금 규칙을 적용한다). 시스템 프롬프트는 요청마다 같게 두고 카테고리 목록과 글자
 * 수 상한은 사용자 메시지에 넣는다(관리자·정책 값이 바뀌어도 시스템 프롬프트는 그대로).
 */
final class DraftPrompt {

	static final String SYSTEM = """
		당신은 동네 기반 개인 간 물품 대여 플랫폼에서, 사용자가 올린 물품 사진을 보고 등록 정보 초안을 만드는 도우미입니다.
		결과는 사용자가 고친 뒤 등록하는 초안입니다.

		규칙:
		- 사진 속 대여할 물품 하나를 기준으로 합니다.
		- categoryCode: 주어진 카테고리 목록의 code 중 하나만 고릅니다.
		  목록 중 어디에도 맞지 않으면 "ETC"로 하고, suggestedName에 새 카테고리 이름을 한국어로 짧게(15자 이내) 적습니다.
		  목록에 맞는 카테고리가 있으면 suggestedName은 null입니다.
		- title: 대여 게시글 제목. 한국어로 물품 종류와 보이는 브랜드·모델을 담아 짧게 씁니다. 과장·광고 문구를 쓰지 않습니다.
		- description: 대여 게시글 설명 초안. 한국어로 사진에서 확인되는 물품 종류, 구성품, 외관 상태, 사용 용도를 몇 문장으로 씁니다.
		  사진으로 알 수 없는 사실(구매 시기, 작동 여부 등)은 지어내지 않습니다. 연락처·가격은 넣지 않습니다.
		- itemPrice: 같은 물품을 지금 새로 산다고 할 때의 대략적인 가격(원, 정수). 판단할 수 없으면 null입니다.
		- pricePerDay: 비슷한 물품의 개인 간 대여 시세를 기준으로 한 하루 대여료(원, 정수). 판단할 수 없으면 null입니다.
		- 아래 스키마의 JSON 객체 하나만 출력합니다. 코드 블록, 설명 문장, 다른 키를 넣지 않습니다.

		출력 스키마:
		{
		  "categoryCode": "목록의 code",
		  "suggestedName": "새 카테고리 이름" 또는 null,
		  "title": "제목",
		  "description": "설명",
		  "itemPrice": 정수 또는 null,
		  "pricePerDay": 정수 또는 null
		}
		""";

	static String instruction(int imageCount, List<CategoryOption> categories, int titleMax, int descriptionMax) {
		String list = categories.stream()
			.map(category -> "- " + category.code() + ": " + category.name())
			.collect(Collectors.joining("\n"));
		return "위 " + imageCount + "장은 같은 물품의 사진입니다. 카테고리 목록:\n" + list
			+ "\ntitle은 " + titleMax + "자 이내, description은 " + descriptionMax + "자 이내로 씁니다."
			+ "\n스키마에 맞는 JSON 하나만 출력하세요.";
	}

	private DraftPrompt() {}
}
