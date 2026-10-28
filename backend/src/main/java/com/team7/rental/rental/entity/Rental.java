package com.team7.rental.rental.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import com.team7.rental.global.entity.BaseTimeEntity;

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
 * 대여 건. 물품·회원은 ID로만 참조한다. 상태 전이 메서드(cancel·handover 등)는 각 REQ 브랜치에서 추가한다.
 * <p>
 * 생성 팩토리는 프로그래밍 오류를 막는 불변식(기간 null·뒤집힘, 결제 대기의 만료 시각 null)만 검사하고 위반 시
 * IllegalArgumentException·NullPointerException을 던진다. 사용자 입력 검증(오늘 이후 시작, 일수 1~30, 400 응답)은
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
}
