package com.team7.rental.rental.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.team7.rental.rental.entity.InspectionPhase;
import com.team7.rental.rental.entity.InspectionResult;
import com.team7.rental.rental.entity.ItemInspection;
import com.team7.rental.rental.entity.RentalRole;
import com.team7.rental.rental.entity.ReturnChecklistItem;

import io.swagger.v3.oas.annotations.media.Schema;

/** 인계·반납 기록 하나 (REQ-017·018), 반납 기록에는 AI 비교 결과(REQ-040) */
public record RentalInspectionResponse(
	@Schema(description = "기록 ID", example = "1")
	Long inspectionId,

	@Schema(description = "단계")
	InspectionPhase phase,

	@Schema(description = "사진 URL (올린 순서)")
	List<String> imageUrls,

	@Schema(description = "인계 메모 또는 반납 메모. 없으면 null", example = "충전기 포함")
	String note,

	@Schema(description = "반납 체크리스트(ReturnChecklistItem별 true/false). 인계 기록이면 null")
	Map<ReturnChecklistItem, Boolean> checklist,

	@Schema(description = "작성자 역할. 반납 대리 접수면 OWNER")
	RentalRole writerRole,

	@Schema(description = "작성 시각")
	LocalDateTime createdAt,

	@Schema(description = "제공자 판정(반납 기록만). 판정 전이면 null")
	InspectionResult result,

	@Schema(description = "판정 내용. 없으면 null", example = "모서리 파손")
	String resultNote,

	@Schema(description = "판정 사진 URL. 없으면 빈 목록")
	List<String> resultImageUrls,

	@Schema(description = "AI 상태 비교(반납 기록만, REQ-040). 비교 전·인계 기록·미반납 신고면 null")
	RentalAiResultResponse aiResult) {

	/**
	 * @param processingDeadline AI 비교가 이 시각보다 먼저 시작돼 아직 PROCESSING이면 FAILED로 보인다
	 */
	public static RentalInspectionResponse of(ItemInspection inspection, RentalRole writerRole,
		LocalDateTime processingDeadline) {
		return new RentalInspectionResponse(
			inspection.getId(),
			inspection.getPhase(),
			inspection.getImageUrls(),
			inspection.getNote(),
			inspection.getChecklist(),
			writerRole,
			inspection.getCreatedAt(),
			inspection.getResult(),
			inspection.getResultNote(),
			inspection.getResultImageUrls() == null ? List.of() : inspection.getResultImageUrls(),
			RentalAiResultResponse.of(inspection.getAiResult(), processingDeadline));
	}
}
