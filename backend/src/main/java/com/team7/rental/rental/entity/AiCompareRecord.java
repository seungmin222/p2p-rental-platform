package com.team7.rental.rental.entity;

import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * item_inspections.ai_result JSON (REQ-040). 반납 기록에 대여 건당 한 번만 채운다(ai.compare.per-rental): 비어 있을 때만 PROCESSING으로
 * 맡고, PROCESSING일 때만 끝난 상태로 바꾼다.
 *
 * @param status 진행 상태
 * @param result DONE일 때만. 나머지는 null
 * @param model 호출한 모델. 호출하지 않았으면 null
 * @param startedAt 워커가 맡은 시각
 * @param finishedAt 끝난 시각. PROCESSING이면 null
 */
public record AiCompareRecord(AiCompareStatus status, AiCompareResult result, String model, LocalDateTime startedAt,
	LocalDateTime finishedAt) {

	public AiCompareRecord {
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(startedAt, "startedAt");
	}

	public static AiCompareRecord processing(LocalDateTime now) {
		return new AiCompareRecord(AiCompareStatus.PROCESSING, null, null, now, null);
	}

	public AiCompareRecord done(AiCompareResult result, String model, LocalDateTime now) {
		return new AiCompareRecord(AiCompareStatus.DONE, Objects.requireNonNull(result, "result"), model, startedAt,
			now);
	}

	public AiCompareRecord failed(String model, LocalDateTime now) {
		return new AiCompareRecord(AiCompareStatus.FAILED, null, model, startedAt, now);
	}

	public AiCompareRecord limitExceeded(String model, LocalDateTime now) {
		return new AiCompareRecord(AiCompareStatus.LIMIT_EXCEEDED, null, model, startedAt, now);
	}

	/** JSON 컬럼에 processing 키로 들어가지 않게 (Hibernate Jackson 직렬화) */
	@JsonIgnore
	public boolean isProcessing() {
		return status == AiCompareStatus.PROCESSING;
	}
}
