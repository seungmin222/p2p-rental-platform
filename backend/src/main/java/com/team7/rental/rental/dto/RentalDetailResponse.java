package com.team7.rental.rental.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.team7.rental.item.dto.ItemSummary;
import com.team7.rental.rental.entity.CancelReason;
import com.team7.rental.rental.entity.CancelType;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalCancelAgreement;
import com.team7.rental.rental.entity.RentalRole;
import com.team7.rental.rental.entity.RentalStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 대여 상세 (REQ-016, SCR-08). 스텝바와 상태 × 역할별 버튼을 그릴 만큼만 담는다.
 * 인계·반납 기록, 결제, 연체료는 해당 REQ에서 추가한다.
 */
public record RentalDetailResponse(
	@Schema(description = "대여 ID", example = "1")
	Long rentalId,

	@Schema(description = "대여 상태")
	RentalStatus status,

	@Schema(description = "조회한 회원의 역할")
	RentalRole myRole,

	RentalItem item,

	@Schema(description = "상대 당사자 (대여자면 제공자, 제공자면 대여자)")
	RentalCounterpart counterpart,

	@Schema(description = "대여 시작일 (포함)", example = "2026-11-03")
	LocalDate startDate,

	@Schema(description = "대여 종료일 (포함)", example = "2026-11-05")
	LocalDate endDate,

	@Schema(description = "대여 일수 (양 끝 포함)", example = "3")
	int rentalDays,

	@Schema(description = "일 대여료 (예약 시점)", example = "10000")
	int pricePerDay,

	@Schema(description = "대여료 = 일 대여료 × 일수", example = "30000")
	int rentalFee,

	@Schema(description = "보증금 (예약 시점)", example = "50000")
	int deposit,

	@Schema(description = "결제 금액 = 대여료 + 보증금", example = "80000")
	long totalAmount,

	@Schema(description = "결제 만료 시각. 결제 대기(PENDING_PAYMENT)일 때만 값이 있다")
	LocalDateTime paymentExpiresAt,

	@Schema(description = "인계 확인 시각")
	LocalDateTime handedOverAt,

	@Schema(description = "반납 요청(또는 미반납 신고) 시각")
	LocalDateTime returnedAt,

	@Schema(description = "거래 완료 시각")
	LocalDateTime completedAt,

	@Schema(description = "취소 시각")
	LocalDateTime canceledAt,

	@Schema(description = "취소 종류")
	CancelType cancelType,

	@Schema(description = "취소 사유")
	CancelReason cancelReason,

	@Schema(description = "취소 사유 텍스트 (사유 OTHER)")
	String cancelReasonText,

	@Schema(description = "취소 수수료", example = "0")
	int cancelFee,

	@Schema(description = "예약 시각")
	LocalDateTime createdAt,

	@Schema(description = "진행 중인 양측 동의 취소 요청(REQUESTED이고 만료 전). 없으면 null")
	ActiveCancelAgreement activeCancelAgreement) {

	/**
	 * @param activeCancelAgreement 진행 중인 동의 취소 요청. 없으면 null
	 */
	public static RentalDetailResponse of(Rental rental, RentalRole myRole, ItemSummary item, Long counterpartId,
		String counterpartNickname, RentalCancelAgreement activeCancelAgreement, Long memberId) {
		return new RentalDetailResponse(
			rental.getId(),
			rental.getStatus(),
			myRole,
			new RentalItem(item.itemId(), item.name(), item.thumbnailUrl(), item.isDeleted()),
			new RentalCounterpart(counterpartId, counterpartNickname),
			rental.getStartDate(),
			rental.getEndDate(),
			Rental.countDays(rental.getStartDate(), rental.getEndDate()),
			rental.getPricePerDay(),
			rental.getRentalFee(),
			rental.getDeposit(),
			(long)rental.getRentalFee() + rental.getDeposit(),
			rental.getPaymentExpiresAt(),
			rental.getHandedOverAt(),
			rental.getReturnedAt(),
			rental.getCompletedAt(),
			rental.getCanceledAt(),
			rental.getCancelType(),
			rental.getCancelReason(),
			rental.getCancelReasonText(),
			rental.getCancelFee(),
			rental.getCreatedAt(),
			activeCancelAgreement == null ? null : ActiveCancelAgreement.of(activeCancelAgreement, memberId));
	}

	public record RentalItem(
		@Schema(description = "물품 ID", example = "1")
		Long itemId,

		@Schema(description = "물품 이름", example = "4인용 거실형 텐트")
		String name,

		@Schema(description = "대표 이미지 썸네일 URL. 이미지가 없으면 null")
		String thumbnailUrl,

		@Schema(description = "삭제된 물품이면 true (화면에 \"삭제된 물품\" 표시)")
		boolean isDeleted) {
	}

	public record ActiveCancelAgreement(
		@Schema(description = "동의 취소 요청 ID (응답 API 경로에 쓴다)", example = "1")
		Long agreementId,

		@Schema(description = "조회한 회원이 요청자면 true (요청자는 응답할 수 없다)")
		boolean requestedByMe,

		@Schema(description = "취소 사유")
		CancelReason reason,

		@Schema(description = "취소 사유 텍스트 (사유 OTHER)")
		String reasonText,

		@Schema(description = "상대가 응답하지 않으면 만료되는 시각 (policies.md cancel-agreement.expire-hours)")
		LocalDateTime expiresAt) {

		static ActiveCancelAgreement of(RentalCancelAgreement agreement, Long memberId) {
			return new ActiveCancelAgreement(agreement.getId(), agreement.isRequestedBy(memberId),
				agreement.getReason(), agreement.getReasonText(), agreement.getExpiresAt());
		}
	}

	public record RentalCounterpart(
		@Schema(description = "회원 ID", example = "2")
		Long memberId,

		@Schema(description = "닉네임", example = "텐트초보")
		String nickname) {
	}
}
