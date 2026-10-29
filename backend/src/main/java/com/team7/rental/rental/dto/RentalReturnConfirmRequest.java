package com.team7.rental.rental.dto;

import com.team7.rental.rental.entity.InspectionResult;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 제공자 상태 확인 multipart의 request part (REQ-018). 내용 규칙은 {@link ReturnConfirmRequestValidator} */
@ValidInspectionText
public record RentalReturnConfirmRequest(
	@Schema(description = "판정. ABNORMAL이면 resultNote와 사진 필수, NORMAL이면 사진을 보내지 않는다") @NotNull
	InspectionResult result,

	@Schema(description = "판정 내용. ABNORMAL이면 필수(NORMAL은 선택), 최대 글자 수는 policies.md inspection.abnormal-note-max", example = "모서리가 깨져 있어요")
	String resultNote) {

	/** 앞뒤 공백을 지우고, 비어 있으면 null */
	public String normalizedResultNote() {
		return InspectionTexts.normalize(resultNote);
	}
}
