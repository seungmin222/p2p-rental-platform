package com.team7.rental.rental.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 인계 확인 multipart의 request part (REQ-017). part 자체를 생략해도 된다(메모 없음) */
@ValidInspectionText
public record RentalHandoverRequest(
	@Schema(description = "인계 메모 (선택). 최대 글자 수는 policies.md inspection.handover-memo-max", example = "충전기 포함, 앞면 작은 흠집 있음")
	String memo) {

	/** 앞뒤 공백을 지우고, 비어 있으면 null */
	public String normalizedMemo() {
		return InspectionTexts.normalize(memo);
	}
}
