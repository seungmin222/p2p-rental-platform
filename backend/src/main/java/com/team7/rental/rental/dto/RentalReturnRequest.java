package com.team7.rental.rental.dto;

import java.util.Map;

import com.team7.rental.rental.entity.ReturnChecklistItem;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 반납 요청 multipart의 request part (REQ-018). 체크리스트·메모 규칙은 {@link ReturnRequestValidator} */
@ValidInspectionText
public record RentalReturnRequest(
	@Schema(description = "반납 체크리스트. ReturnChecklistItem 5개 키를 모두 true/false로 보낸다(빠지거나 모르는 키면 400)", example = "{\"NO_EXTERIOR_DAMAGE\": true, \"ALL_PARTS_INCLUDED\": true, \"NO_CONTAMINATION\": false, \"WORKS_NORMALLY\": true, \"CONSUMABLES_RESTORED\": true}") @NotNull
	Map<ReturnChecklistItem, Boolean> checklist,

	@Schema(description = "반납 메모. 체크리스트에 false 항목이 있으면 필수, 최대 글자 수는 policies.md return.note-max", example = "옆면에 얼룩이 생겼어요")
	String note) {

	/** 앞뒤 공백을 지우고, 비어 있으면 null */
	public String normalizedNote() {
		return InspectionTexts.normalize(note);
	}

	public boolean hasUncheckedItem() {
		return checklist != null && checklist.containsValue(Boolean.FALSE);
	}
}
