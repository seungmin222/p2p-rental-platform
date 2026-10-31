package com.team7.rental.global.policy;

import java.math.BigDecimal;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Name;
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
	Handover handover,
	@Valid @NotNull
	Refund refund,
	@Valid @NotNull
	Reconciliation reconciliation,
	@Valid @NotNull
	Inspection inspection,
	/** 키는 return.* 그대로. return이 Java 예약어라 컴포넌트 이름만 다르다 */
	@Valid @NotNull @Name("return")
	Return returnPolicy,
	@Valid @NotNull
	Review review,
	@Valid @NotNull
	RentalHistory rentalHistory,
	@Valid @NotNull
	Member member,
	@Valid @NotNull
	Item item) {

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
	 * @param autoCancelDays 시작일 + 이 일수까지 인계 확인이 없으면 자동 취소, 전액 환불 (handover.auto-cancel-days)
	 */
	public record Handover(
		@Positive
		int autoCancelDays) {
	}

	/**
	 * 환불 재시도 (refund.retry)
	 *
	 * @param maxRetry 최대 재시도 횟수. 다 실패하면 관리자 확인 대상
	 * @param interval 재시도 간격
	 */
	public record Refund(
		@Positive
		int maxRetry,
		@NotNull
		Duration interval) {
	}

	/**
	 * @param lookbackHours 조정 배치가 대조하는 최근 결제 범위 (reconciliation.lookback-hours)
	 */
	public record Reconciliation(
		@Positive
		int lookbackHours) {
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

	/**
	 * 반납 (REQ-018). yml·policies.md 키는 return.* 이다({@link Name}으로 바인딩).
	 *
	 * @param noteMax 반납 메모 최대 글자 수 (return.note-max). 체크리스트에 false 항목이 있으면 필수
	 * @param autoConfirmDays 반납 요청 후 이 일수가 지나면 자동 '정상' 처리 (return.auto-confirm-days)
	 */
	public record Return(
		@Positive
		int noteMax,
		@Positive
		int autoConfirmDays) {
	}

	/**
	 * @param writeDeadlineDays 거래 완료(completed_at) 후 후기 작성 기한 일수 (review.write-deadline-days)
	 */
	public record Review(
		@Positive
		int writeDeadlineDays) {
	}

	/**
	 * @param page 빌린 내역(REQ-019) 페이지 크기 (rental-history.page). 요청에서 size를 받지 않는다
	 */
	public record RentalHistory(
		@Positive
		int page) {
	}

	/**
	 * 회원 입력 규칙 (REQ-001·005). 비밀번호 구성(영문·숫자·특수문자 모두 포함)은 수치가 아니라 DTO의 @Pattern에 둔다.
	 *
	 * @param emailMax 이메일 최대 글자 수 (member.email.max)
	 * @param password 비밀번호 글자 수 (member.password)
	 * @param nickname 닉네임 글자 수 (member.nickname)
	 */
	public record Member(
		@Positive
		int emailMax,
		@Valid @NotNull
		Length password,
		@Valid @NotNull
		Length nickname) {
	}

	/**
	 * 물품 등록·수정 입력 규칙 (REQ-006·007)
	 *
	 * @param nameMax 물품 이름 최대 글자 수 (item.name.max)
	 * @param descriptionMax 설명 최대 글자 수 (item.description.max)
	 * @param pricePerDay 일 대여료. 0원(무료) 또는 min~max (item.price-per-day)
	 * @param deposit 보증금. 0원 또는 min~max (item.deposit)
	 * @param itemPriceMax 물품 가격 상한, 0원부터 (item.item-price.max)
	 */
	public record Item(
		@Positive
		int nameMax,
		@Positive
		int descriptionMax,
		@Valid @NotNull
		Amount pricePerDay,
		@Valid @NotNull
		Amount deposit,
		@Positive
		int itemPriceMax) {

		/**
		 * 0원 또는 [min, max] 원. 1~(min-1)원을 막는 이유는 policies.md §1 물품 표 아래 참고
		 *
		 * @param min 0원이 아닐 때 최소 금액
		 * @param max 최대 금액
		 */
		public record Amount(
			@Positive
			int min,
			@Positive
			int max) {

			public boolean isAllowed(int amount) {
				return amount == 0 || (amount >= min && amount <= max);
			}
		}
	}

	/** 글자 수 범위 [min, max] (코드 포인트 기준) */
	public record Length(
		@Positive
		int min,
		@Positive
		int max) {
	}
}
