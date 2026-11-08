package com.team7.rental.rental.entity;

import java.util.List;

/**
 * AI 비교 출력 (docs/domains/ai.md REQ-040 고정 스키마). 판정이 아니라 참고 자료다.
 *
 * @param changed 변화가 있다고 보는지
 * @param changeTypes 변화 유형. 변화가 없으면 빈 목록
 * @param confidence 0.0 ~ 1.0
 * @param description 한국어 설명(근거)
 * @param regions 변화 위치. imageIndex는 반납 사진 기준 0부터
 */
public record AiCompareResult(boolean changed, List<AiChangeType> changeTypes, double confidence, String description,
	List<Region> regions) {

	public AiCompareResult {
		changeTypes = changeTypes == null ? List.of() : List.copyOf(changeTypes);
		regions = regions == null ? List.of() : List.copyOf(regions);
	}

	/**
	 * @param imageIndex 반납 사진 순서(0부터)
	 * @param note 위치 설명
	 */
	public record Region(int imageIndex, String note) {
	}
}
