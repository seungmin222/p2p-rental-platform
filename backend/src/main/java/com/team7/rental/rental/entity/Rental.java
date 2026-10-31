package com.team7.rental.rental.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import com.team7.rental.global.entity.BaseTimeEntity;
import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 대여 건. 물품·회원은 ID로만 참조한다. 1순위 전이(취소·인계·반납·판정·완료)가 있고, 결제 승인·만료·금액 불일치(024)와 인계 없음 자동 취소(017)도 있다. 연체(023)·
 * 미반납 신고(018 2순위)·연장(038)은 각 브랜치에서 추가한다. 전이 메서드는 상태 가드만 보고, 권한(당사자·역할)과 이력 저장은 서비스가 한다.
 * <p>
 * 생성 팩토리는 프로그래밍 오류를 막는 불변식(기간 null·뒤집힘, 결제 대기의 만료 시각 null)만 검사하고 위반 시
 * IllegalArgumentException·NullPointerException을 던진다. 사용자 입력 검증(오늘 이후 시작, 일수 rental.min-days ~ rental.max-days, 400 응답)은
 * REQ-013 서비스가 한다.
 */
@Getter
@Entity
@Table(name = "rentals")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Rental extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long itemId;

	/** items.ownerId 비정규화. 물품 소유자는 바뀌지 않는다 */
	@Column(nullable = false)
	private Long ownerId;

	@Column(nullable = false)
	private Long renterId;

	@Column(nullable = false)
	private LocalDate startDate;

	/** 연장(REQ-038) 시 갱신 */
	@Column(nullable = false)
	private LocalDate endDate;

	/** 최초 대여료. 연장료는 rental_extensions */
	@Column(nullable = false)
	private int rentalFee;

	/** 예약 시점 스냅샷 */
	@Column(nullable = false)
	private int deposit;

	/** 예약 시점 스냅샷. 연체료 계산 기준 */
	@Column(nullable = false)
	private int pricePerDay;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private RentalStatus status;

	/** PENDING_PAYMENT 만료 시각. 바로 확정한 대여는 null */
	private LocalDateTime paymentExpiresAt;

	private LocalDateTime handedOverAt;

	/** 반납 요청(또는 미반납 신고) 시각 */
	private LocalDateTime returnedAt;

	private LocalDateTime completedAt;

	private LocalDateTime canceledAt;

	@Enumerated(EnumType.STRING)
	@Column(length = 30)
	private CancelType cancelType;

	@Enumerated(EnumType.STRING)
	@Column(length = 30)
	private CancelReason cancelReason;

	@Column(length = 200)
	private String cancelReasonText;

	/** 시스템 취소면 null */
	private Long canceledBy;

	@Column(nullable = false)
	private int cancelFee;

	@Column(nullable = false)
	private int overdueFee;

	@Column(nullable = false)
	private int extensionCount;

	@Version
	@Column(nullable = false)
	private Long version;

	/**
	 * 결제 대기로 생성(REQ-013, 결제 금액 > 0). 사용자 입력 검증은 호출하는 서비스가 한다.
	 *
	 * @param paymentExpiresAt 결제 만료 시각(서비스가 Clock + payment.expire-minutes로 계산). null 불가
	 * @throws IllegalArgumentException 기간이 null이거나 endDate가 startDate보다 앞설 때
	 */
	public static Rental createPendingPayment(Long itemId, Long ownerId, Long renterId, LocalDate startDate,
		LocalDate endDate, int pricePerDay, int deposit, LocalDateTime paymentExpiresAt) {
		Rental rental = create(itemId, ownerId, renterId, startDate, endDate, pricePerDay, deposit,
			RentalStatus.PENDING_PAYMENT);
		rental.paymentExpiresAt = Objects.requireNonNull(paymentExpiresAt, "paymentExpiresAt");
		return rental;
	}

	/**
	 * 결제 없이 바로 확정(REQ-013, 1순위 단계 또는 무료 대여). 사용자 입력 검증은 호출하는 서비스가 한다.
	 *
	 * @throws IllegalArgumentException 기간이 null이거나 endDate가 startDate보다 앞설 때
	 */
	public static Rental createReserved(Long itemId, Long ownerId, Long renterId, LocalDate startDate,
		LocalDate endDate, int pricePerDay, int deposit) {
		return create(itemId, ownerId, renterId, startDate, endDate, pricePerDay, deposit, RentalStatus.RESERVED);
	}

	private static Rental create(Long itemId, Long ownerId, Long renterId, LocalDate startDate, LocalDate endDate,
		int pricePerDay, int deposit, RentalStatus status) {
		validatePeriod(startDate, endDate);
		Rental rental = new Rental();
		rental.itemId = itemId;
		rental.ownerId = ownerId;
		rental.renterId = renterId;
		rental.startDate = startDate;
		rental.endDate = endDate;
		rental.pricePerDay = pricePerDay;
		rental.deposit = deposit;
		rental.rentalFee = Math.multiplyExact(pricePerDay, countDays(startDate, endDate));
		rental.status = status;
		return rental;
	}

	private static void validatePeriod(LocalDate startDate, LocalDate endDate) {
		if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
			throw new IllegalArgumentException("대여 기간이 올바르지 않습니다: " + startDate + " ~ " + endDate);
		}
	}

	/** 대여 일수. 시작일·종료일을 모두 포함한다(같은 날이면 1일). 예약 일수 검증(REQ-013)도 이 계산을 쓴다 */
	public static int countDays(LocalDate startDate, LocalDate endDate) {
		return Math.toIntExact(ChronoUnit.DAYS.between(startDate, endDate) + 1);
	}

	/**
	 * 이 대여가 now 시점에 기간을 점유하는지. {@link RentalStatus#ACTIVE_FOR_PERIOD}에 속하고, PENDING_PAYMENT면
	 * paymentExpiresAt > now일 때만 점유한다(정확히 같은 시각은 만료).
	 */
	public boolean isOccupyingPeriod(LocalDateTime now) {
		if (!RentalStatus.ACTIVE_FOR_PERIOD.contains(status)) {
			return false;
		}
		return status != RentalStatus.PENDING_PAYMENT || (paymentExpiresAt != null && paymentExpiresAt.isAfter(now));
	}

	/** {@link #canWriteReview(RentalStatus, LocalDateTime, LocalDateTime, int)}와 같은 판정. 후기 작성 가드(REQ-034)용 */
	public boolean canWriteReview(LocalDateTime now, int deadlineDays) {
		return canWriteReview(status, completedAt, now, deadlineDays);
	}

	/**
	 * 후기 작성 기간인지: COMPLETED이고 now ≤ completedAt + deadlineDays(review.write-deadline-days). 정확히 기한 시각이면 아직 작성할 수
	 * 있다. COMPLETED인데 completedAt이 없는 깨진 행은 false다. 빌린 내역(REQ-019)은 엔티티 대신 조회 컬럼으로 부른다.
	 * <p>
	 * "후기 미작성" 조건은 후기 테이블이 생기는 feat/review(REQ-034)에서 호출하는 쪽에 더한다.
	 */
	public static boolean canWriteReview(RentalStatus status, LocalDateTime completedAt, LocalDateTime now,
		int deadlineDays) {
		if (status != RentalStatus.COMPLETED || completedAt == null) {
			return false;
		}
		return !now.isAfter(completedAt.plusDays(deadlineDays));
	}

	public boolean isParticipant(Long memberId) {
		return renterId.equals(memberId) || ownerId.equals(memberId);
	}

	/**
	 * @throws IllegalArgumentException 당사자가 아닐 때. 호출 전에 {@link #isParticipant}로 확인한다
	 */
	public RentalRole roleOf(Long memberId) {
		if (renterId.equals(memberId)) {
			return RentalRole.RENTER;
		}
		if (ownerId.equals(memberId)) {
			return RentalRole.OWNER;
		}
		throw new IllegalArgumentException("대여 당사자가 아닙니다: " + memberId);
	}

	public Long counterpartOf(Long memberId) {
		return roleOf(memberId) == RentalRole.RENTER ? ownerId : renterId;
	}

	/**
	 * 취소할 수 있는지 확인한다(REQ-015, state-machines.md §1). 서비스가 수수료 계산 전에 부르고 {@link #cancel}도 다시 확인한다.
	 * <ul>
	 *   <li>RENTER·OWNER(단독 취소): PENDING_PAYMENT는 시점 제한 없음(자정 직전 생성으로 시작일이 지난 결제 대기도 있다).
	 *   RESERVED는 오늘 &lt; startDate만, 오늘 ≥ startDate면 RENTAL_CANCEL_NOT_ALLOWED(양측 동의 취소로 안내)</li>
	 *   <li>MUTUAL(양측 동의 취소): RESERVED이고 오늘 ≥ startDate</li>
	 * </ul>
	 * 그 외는 RENTAL_INVALID_TRANSITION.
	 *
	 * @throws IllegalArgumentException 시스템 취소 종류(PAYMENT_EXPIRED 등). 해당 REQ에서 추가한다
	 */
	public void validateCancelable(CancelType type, LocalDate today) {
		switch (type) {
			case RENTER, OWNER -> {
				if (status == RentalStatus.PENDING_PAYMENT) {
					return;
				}
				if (status != RentalStatus.RESERVED) {
					throw new BusinessException(ErrorCode.RENTAL_INVALID_TRANSITION);
				}
				if (!today.isBefore(startDate)) {
					throw new BusinessException(ErrorCode.RENTAL_CANCEL_NOT_ALLOWED);
				}
			}
			case MUTUAL -> {
				if (status != RentalStatus.RESERVED || today.isBefore(startDate)) {
					throw new BusinessException(ErrorCode.RENTAL_INVALID_TRANSITION);
				}
			}
			default -> throw new IllegalArgumentException("지원하지 않는 취소 종류입니다: " + type);
		}
	}

	/**
	 * → CANCELED (REQ-015). 이력은 changedBy가 canceledBy와 다를 수 있어(MUTUAL은 수락자) 서비스가 저장한다.
	 *
	 * @param canceledBy 단독 취소면 취소한 당사자, MUTUAL이면 동의 요청자
	 * @param cancelFee CancelFeeCalculator가 계산한 수수료. 1순위는 기록만 한다
	 * @throws BusinessException RENTAL_INVALID_TRANSITION, RENTAL_CANCEL_NOT_ALLOWED ({@link #validateCancelable})
	 */
	public void cancel(Long canceledBy, CancelType type, CancelReason reason, String reasonText, int cancelFee,
		LocalDateTime now) {
		validateCancelable(type, now.toLocalDate());
		this.status = RentalStatus.CANCELED;
		this.canceledAt = now;
		this.canceledBy = canceledBy;
		this.cancelType = type;
		this.cancelReason = reason;
		this.cancelReasonText = reasonText;
		this.cancelFee = cancelFee;
	}

	/**
	 * PENDING_PAYMENT → RESERVED (REQ-024 결제 승인).
	 *
	 * @throws BusinessException RENTAL_INVALID_TRANSITION
	 */
	public void confirmPayment() {
		requirePendingPayment();
		this.status = RentalStatus.RESERVED;
	}

	/** 결제 만료 시각이 지났는지. 정확히 같은 시각이면 만료({@link #isOccupyingPeriod}와 같은 기준) */
	public boolean isPaymentExpired(LocalDateTime now) {
		return status == RentalStatus.PENDING_PAYMENT && !paymentExpiresAt.isAfter(now);
	}

	/**
	 * PENDING_PAYMENT → CANCELED(PAYMENT_EXPIRED) (REQ-024 결제 만료).
	 *
	 * @throws BusinessException RENTAL_INVALID_TRANSITION
	 */
	public void expirePayment(LocalDateTime now) {
		requirePendingPayment();
		cancelBySystem(CancelType.PAYMENT_EXPIRED, now);
	}

	/**
	 * PENDING_PAYMENT → CANCELED(AMOUNT_MISMATCH) (REQ-024 금액 불일치).
	 *
	 * @throws BusinessException RENTAL_INVALID_TRANSITION
	 */
	public void cancelForAmountMismatch(LocalDateTime now) {
		requirePendingPayment();
		cancelBySystem(CancelType.AMOUNT_MISMATCH, now);
	}

	/** 인계 없음 자동 취소 대상인지: RESERVED이고 오늘 &gt; 시작일 + autoCancelDays (REQ-017) */
	public boolean isNoHandoverCancelDue(LocalDate today, int autoCancelDays) {
		return status == RentalStatus.RESERVED && today.isAfter(startDate.plusDays(autoCancelDays));
	}

	/**
	 * RESERVED → CANCELED(NO_HANDOVER) (REQ-017 인계 없음 자동 취소). 수수료 없음.
	 *
	 * @throws BusinessException RENTAL_INVALID_TRANSITION
	 */
	public void cancelForNoHandover(LocalDateTime now) {
		if (status != RentalStatus.RESERVED) {
			throw new BusinessException(ErrorCode.RENTAL_INVALID_TRANSITION);
		}
		cancelBySystem(CancelType.NO_HANDOVER, now);
	}

	private void requirePendingPayment() {
		if (status != RentalStatus.PENDING_PAYMENT) {
			throw new BusinessException(ErrorCode.RENTAL_INVALID_TRANSITION);
		}
	}

	private void cancelBySystem(CancelType type, LocalDateTime now) {
		this.status = RentalStatus.CANCELED;
		this.canceledAt = now;
		this.cancelType = type;
	}

	/**
	 * RESERVED → RENTING (REQ-017 인계 확인).
	 *
	 * @throws BusinessException RENTAL_INVALID_TRANSITION(RESERVED 아님), RENTAL_HANDOVER_TOO_EARLY(오늘 &lt; startDate)
	 */
	public void handover(LocalDateTime now) {
		validateHandover(now.toLocalDate());
		this.status = RentalStatus.RENTING;
		this.handedOverAt = now;
	}

	/**
	 * 인계할 수 있는지 확인한다. 서비스가 사진을 처리하기 전에 부르고 {@link #handover}도 다시 확인한다.
	 *
	 * @throws BusinessException RENTAL_INVALID_TRANSITION, RENTAL_HANDOVER_TOO_EARLY
	 */
	public void validateHandover(LocalDate today) {
		if (status != RentalStatus.RESERVED) {
			throw new BusinessException(ErrorCode.RENTAL_INVALID_TRANSITION);
		}
		if (today.isBefore(startDate)) {
			throw new BusinessException(ErrorCode.RENTAL_HANDOVER_TOO_EARLY);
		}
	}

	/**
	 * RENTING·OVERDUE → RETURNED (REQ-018 반납 요청, 제공자 대리 접수 포함). 연체료(overdueFee) 확정은 REQ-023에서 추가한다.
	 *
	 * @throws BusinessException RENTAL_INVALID_TRANSITION
	 */
	public void requestReturn(LocalDateTime now) {
		validateReturnRequest();
		this.status = RentalStatus.RETURNED;
		this.returnedAt = now;
	}

	/**
	 * 반납 요청할 수 있는지 확인한다. 서비스가 사진을 처리하기 전에 부르고 {@link #requestReturn}도 다시 확인한다.
	 *
	 * @throws BusinessException RENTAL_INVALID_TRANSITION
	 */
	public void validateReturnRequest() {
		if (status != RentalStatus.RENTING && status != RentalStatus.OVERDUE) {
			throw new BusinessException(ErrorCode.RENTAL_INVALID_TRANSITION);
		}
	}

	/**
	 * 상태 확인 '정상' (REQ-018, 제공자 또는 자동 확인 배치). 1순위는 보증금 처리 없이 바로 {@link #complete}.
	 * REQ-025에서 보증금 반환 뒤 완료로 바뀐다.
	 *
	 * @throws BusinessException RENTAL_INVALID_TRANSITION(RETURNED 아님)
	 */
	public void confirmNormal(LocalDateTime now) {
		complete(now);
	}

	/**
	 * 상태 확인 '이상' (REQ-018). RETURNED를 유지한다. 1순위는 상태 변화가 없어 서비스가 이어서 {@link #complete}를 부른다.
	 * REQ-049에서 보증금 HELD로 바뀐다(RETURNED → RETURNED 이력).
	 *
	 * @throws BusinessException RENTAL_INVALID_TRANSITION(RETURNED 아님)
	 */
	public void markAbnormal() {
		validateReturned();
	}

	/**
	 * RETURNED → COMPLETED (REQ-018 1순위, 이후 차감 확정·환불 재시도 REQ-025·049도 이 메서드).
	 *
	 * @throws BusinessException RENTAL_INVALID_TRANSITION
	 */
	public void complete(LocalDateTime now) {
		validateReturned();
		this.status = RentalStatus.COMPLETED;
		this.completedAt = now;
	}

	/**
	 * 상태 확인할 수 있는지 확인한다(RETURNED). 서비스가 사진을 처리하기 전에 부른다.
	 *
	 * @throws BusinessException RENTAL_INVALID_TRANSITION
	 */
	public void validateReturned() {
		if (status != RentalStatus.RETURNED) {
			throw new BusinessException(ErrorCode.RENTAL_INVALID_TRANSITION);
		}
	}

	/** 자동 상태 확인 대상 시각이 됐는지. returnedAt + autoConfirmDays ≤ now (정확히 같은 시각이면 대상) */
	public boolean isAutoConfirmDue(LocalDateTime now, int autoConfirmDays) {
		return status == RentalStatus.RETURNED && returnedAt != null
			&& !returnedAt.plusDays(autoConfirmDays).isAfter(now);
	}
}
