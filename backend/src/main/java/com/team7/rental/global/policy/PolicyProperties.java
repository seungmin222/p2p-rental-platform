package com.team7.rental.global.policy;

import java.math.BigDecimal;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * docs/policies.md의 정책 수치. 값은 application.yml app.policy.* 에 두고, 바꾸면 policies.md도 함께 고친다.
 * 도메인 정책은 각 도메인 작업에서 섹션(record)을 추가한다.
 */
@Validated
@ConfigurationProperties("app.policy")
public record PolicyProperties(
	@Valid @NotNull
	Image image,
	@Valid @NotNull
	Region region,
	@Valid @NotNull
	Trust trust,
	@Valid @NotNull
	Rental rental,
	@Valid @NotNull
	Payment payment,
	@Valid @NotNull
	Cancel cancel,
	@Valid @NotNull
	CancelFee cancelFee,
	@Valid @NotNull
	CancelAgreement cancelAgreement,
	@Valid @NotNull
	Inspection inspection) {

	/**
	 * @param originalMaxLongSide 원본 재인코딩 시 긴 변 최대 픽셀 (image.original-max-long-side)
	 * @param thumbnailWidth 썸네일 가로 픽셀 (thumbnail.width)
	 * @param maxPixels 업로드 이미지 최대 픽셀 수(가로 × 세로) (image.max-pixels)
	 */
	public record Image(
		@Positive
		int originalMaxLongSide,
		@Positive
		int thumbnailWidth,
		@Positive
		long maxPixels) {
	}

	/**
	 * @param verifyValidDays 동네 인증 유효 일수 (region.verify-valid-days)
	 */
	public record Region(
		@Positive
		int verifyValidDays) {
	}

	/**
	 * @param initialScore 가입 시 신뢰 점수 (trust.initial)
	 */
	public record Trust(
		@NotNull
		BigDecimal initialScore) {
	}

	/**
	 * @param minDays 최초 예약 최소 일수 (rental.min-days)
	 * @param maxDays 최초 예약 최대 일수 (rental.max-days)
	 * @param lockWait 분산 락 대기 시간 (rental.lock-wait). 임대 시간은 두지 않는다(Redisson watchdog)
	 */
	public record Rental(
		@Positive
		int minDays,
		@Positive
		int maxDays,
		@NotNull
		Duration lockWait) {
	}

	/**
	 * @param expireMinutes 결제 대기 만료 분 (payment.expire-minutes)
	 */
	public record Payment(
		@Positive
		int expireMinutes) {
	}

	/**
	 * @param reasonTextMax 취소 사유 텍스트 최대 글자 수 (cancel.reason-text-max). 사유 OTHER면 필수
	 */
	public record Cancel(
		@Positive
		int reasonTextMax) {
	}

	/**
	 * 대여자 단독 취소 수수료 (policies.md §2 취소 수수료 표)
	 *
	 * @param ratePercent 대여료 대비 수수료율 % (cancel-fee.rate-percent)
	 * @param freeUntilDaysBefore 시작일 이 일수 전까지는 수수료 없음 (cancel-fee.free-until-days-before)
	 */
	public record CancelFee(
		@PositiveOrZero @Max(100)
		int ratePercent,
		@Positive
		int freeUntilDaysBefore) {
	}

	/**
	 * @param expireHours 양측 동의 취소 요청이 상대 무응답으로 만료되는 시간 (cancel-agreement.expire-hours)
	 */
	public record CancelAgreement(
		@Positive
		int expireHours) {
	}

	/**
	 * 인계·반납 기록 (REQ-017·018)
	 *
	 * @param handoverMemoMax 인계 메모 최대 글자 수 (inspection.handover-memo-max)
	 * @param abnormalNoteMax 상태 확인 '이상' 내용 최대 글자 수 (inspection.abnormal-note-max)
	 */
	public record Inspection(
		@Valid @NotNull
		Images images,
		@Positive
		int handoverMemoMax,
		@Positive
		int abnormalNoteMax) {

		/**
		 * 인계·반납·판정 사진 (inspection.images.*)
		 *
		 * @param min 최소 장수 (inspection.images.min). 판정 '이상' 사진도 이 범위
		 * @param max 최대 장수 (inspection.images.max)
		 * @param maxFileSize 장당 최대 용량 (inspection.images.max-file-size)
		 */
		public record Images(
			@Positive
			int min,
			@Positive
			int max,
			@NotNull
			DataSize maxFileSize) {
		}
	}
}
