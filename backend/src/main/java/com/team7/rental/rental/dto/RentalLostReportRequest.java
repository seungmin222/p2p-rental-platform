package com.team7.rental.rental.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 미반납 신고 (REQ-018·049). 메모 길이는 반납 메모와 같은 return.note-max ({@link LostReportRequestValidator}) */
@ValidInspectionText
public record RentalLostReportRequest(
	@Schema(description = "신고 메모(선택). 최대 글자 수는 policies.md return.note-max", example = "연락이 닿지 않고 물건을 돌려받지 못했어요")
	String note) {

	/** 앞뒤 공백을 지우고, 비어 있으면 null */
	public String normalizedNote() {
		return InspectionTexts.normalize(note);
	}
}
