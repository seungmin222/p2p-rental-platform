package com.team7.rental.rental.dto;

import java.time.LocalDateTime;

import com.team7.rental.rental.entity.AiCompareRecord;
import com.team7.rental.rental.entity.AiCompareResult;
import com.team7.rental.rental.entity.AiCompareStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * AI 상태 비교 결과 (REQ-040). 판정이 아닌 참고 자료이며, 화면은 "조명·각도 차이로 오판 가능"을 함께 보여 준다.
 * 결과가 늦거나 없어도 제공자는 상태 확인을 할 수 있다.
 */
public record RentalAiResultResponse(
	@Schema(description = "진행 상태. PROCESSING이 ai.compare.processing-timeout-minutes를 넘으면 FAILED로 내려간다")
	AiCompareStatus status,

	@Schema(description = "비교 결과(DONE일 때만). 나머지는 null")
	AiCompareResult result,

	@Schema(description = "끝난 시각. PROCESSING이면 null")
	LocalDateTime analyzedAt) {

	/**
	 * @param processingDeadline 이 시각보다 먼저 맡은 PROCESSING은 FAILED로 보인다(워커가 죽은 경우, open-issues #46)
	 * @return 비교 전이면 null
	 */
	public static RentalAiResultResponse of(AiCompareRecord record, LocalDateTime processingDeadline) {
		if (record == null) {
			return null;
		}
		if (record.isProcessing() && record.startedAt().isBefore(processingDeadline)) {
			return new RentalAiResultResponse(AiCompareStatus.FAILED, null, null);
		}
		return new RentalAiResultResponse(record.status(), record.result(), record.finishedAt());
	}
}
