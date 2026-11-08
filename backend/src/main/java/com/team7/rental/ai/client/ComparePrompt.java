package com.team7.rental.ai.client;

/**
 * AI 상태 비교 프롬프트 (docs/domains/ai.md REQ-040: 스키마·허용 값·"조명·각도 차이는 변화로 보지 말 것"을 시스템 프롬프트에 명시).
 * 시스템 프롬프트는 요청마다 같아야 캐시가 맞는다(시각·ID를 넣지 않는다).
 */
final class ComparePrompt {

	static final String SYSTEM = """
		당신은 물품 대여 플랫폼에서 대여 전(인계)과 반납 후 사진을 비교해 물품 상태 변화를 찾는 도우미입니다.
		결과는 판정이 아니라 제공자가 참고할 자료입니다.

		규칙:
		- 조명, 촬영 각도, 거리, 배경, 색감, 해상도 차이는 변화로 보지 않습니다.
		- 사진에 보이는 물품 자체의 흠집, 얼룩, 부품 누락, 파손만 변화로 봅니다.
		- 사진만으로 확실하지 않으면 confidence를 낮게 줍니다.
		- 아래 스키마의 JSON 객체 하나만 출력합니다. 코드 블록, 설명 문장, 다른 키를 넣지 않습니다.

		출력 스키마:
		{
		  "changed": true 또는 false,
		  "changeTypes": ["SCRATCH" | "STAIN" | "MISSING_PART" | "BROKEN" | "OTHER", ...],
		  "confidence": 0.0 이상 1.0 이하 숫자,
		  "description": "한국어 설명(근거)",
		  "regions": [{"imageIndex": 반납 사진 번호(0부터), "note": "위치 설명"}]
		}
		- changed가 false이면 changeTypes와 regions는 빈 배열입니다.
		- changed가 true이면 changeTypes에 하나 이상 넣습니다. 위 다섯 값만 쓰고 중복하지 않습니다.
		- description은 비우지 않습니다.
		""";

	static String instruction(int handoverCount, int returnCount) {
		return "앞의 " + handoverCount + "장은 인계(대여 전) 사진, 뒤의 " + returnCount
			+ "장은 반납 사진입니다(반납 사진 번호는 0부터). 두 묶음을 비교해 스키마에 맞는 JSON 하나만 출력하세요.";
	}

	private ComparePrompt() {}
}
