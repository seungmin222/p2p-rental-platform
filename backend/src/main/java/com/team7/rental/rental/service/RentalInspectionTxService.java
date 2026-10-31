package com.team7.rental.rental.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.rental.dto.RentalDetailResponse;
import com.team7.rental.rental.entity.InspectionPhase;
import com.team7.rental.rental.entity.InspectionResult;
import com.team7.rental.rental.entity.ItemInspection;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalRole;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.entity.RentalStatusHistory;
import com.team7.rental.rental.entity.ReturnChecklistItem;
import com.team7.rental.rental.repository.ItemInspectionRepository;
import com.team7.rental.rental.repository.RentalRepository;
import com.team7.rental.rental.repository.RentalStatusHistoryRepository;
import com.team7.rental.rental.service.RentalRefundPort.Completion;
import com.team7.rental.rental.service.RentalRefundPort.RefundTicket;

import lombok.RequiredArgsConstructor;

/**
 * 인계·반납 기록 쓰기의 트랜잭션 부분 (REQ-017·018). 사진 처리·업로드는 트랜잭션 밖({@link RentalInspectionService})에서 끝내고 URL만 받는다.
 * <p>
 * validate* 는 사진을 처리하기 전에 거절될 요청을 걸러 내는 최적화일 뿐이다. 쓰기 메서드는 트랜잭션 안에서 대여를 다시 읽고 404·403·409를 전부
 * 다시 검사한다(사전 검사와 쓰기 사이에 상태가 바뀔 수 있다).
 * <p>
 * 인계·반납 요청은 행 락을 잡지 않는다(사진 처리 동안 행을 잠그지 않으려고 업로드를 밖으로 뺐고, 쓰기 트랜잭션은 짧다). 동시 변경은 Rental @Version이 막는다.
 * 상태 확인은 결제 행도 건드려 대여 행 락 → 결제 행 락 순서로 잡는다.
 * 전이 직후 rental을 flush해 버전 검사 UPDATE(rentals 행 X 락)를 기록 INSERT보다 먼저 보낸다. 늦은 요청은 그 락을 기다렸다가 버전 충돌(409)로
 * 끝난다. flush가 없으면 IDENTITY 키라 INSERT가 먼저 나가고, 기록의 FK 검사가 rentals 행에 S 락을 잡은 채 UK를 기다리다 앞선 요청의
 * UPDATE와 데드락(500)이 난다. 확인 결과는 docs/prompts/feat/rental-return/notes.md
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RentalInspectionTxService {

	private final RentalRepository rentalRepository;
	private final ItemInspectionRepository itemInspectionRepository;
	private final RentalStatusHistoryRepository rentalStatusHistoryRepository;
	private final RentalQueryService rentalQueryService;
	private final RentalRefundPort rentalRefundPort;
	private final PolicyProperties policyProperties;
	private final Clock clock;

	/**
	 * 인계 사전 검사.
	 *
	 * @throws BusinessException RENTAL_NOT_FOUND, RENTAL_NOT_PARTICIPANT, RENTAL_OWNER_ONLY, RENTAL_INVALID_TRANSITION,
	 *     RENTAL_HANDOVER_TOO_EARLY
	 */
	public void validateHandover(Long rentalId, Long memberId) {
		getOwnerRental(rentalId, memberId).validateHandover(LocalDate.now(clock));
	}

	/**
	 * 인계 확인: RESERVED → RENTING + HANDOVER 기록 + 이력.
	 *
	 * @param memo 정규화된 메모. 없으면 null
	 * @param imageUrls 업로드한 사진 URL (장수 검사는 호출하는 쪽)
	 * @throws BusinessException RENTAL_NOT_FOUND, RENTAL_NOT_PARTICIPANT, RENTAL_OWNER_ONLY, RENTAL_INVALID_TRANSITION,
	 *     RENTAL_HANDOVER_TOO_EARLY
	 * @throws org.springframework.orm.ObjectOptimisticLockingFailureException 다른 요청이 먼저 대여를 바꿨을 때(409)
	 */
	@Transactional
	public RentalDetailResponse handover(Long rentalId, Long memberId, String memo, List<String> imageUrls) {
		LocalDateTime now = LocalDateTime.now(clock);
		Rental rental = getOwnerRental(rentalId, memberId);
		RentalStatus from = rental.getStatus();
		rental.handover(now);
		// 버전 검사를 기록 INSERT보다 먼저 (클래스 주석)
		rentalRepository.flush();
		itemInspectionRepository.save(ItemInspection.handover(rentalId, memberId, imageUrls, memo));
		rentalStatusHistoryRepository.save(RentalStatusHistory.create(rentalId, from, rental.getStatus(), memberId,
			RentalStatusHistory.REASON_HANDOVER, now));
		return rentalQueryService.getRental(rentalId, memberId);
	}

	/**
	 * 반납 요청 사전 검사. 대여자 본인 또는 제공자(대리 접수).
	 *
	 * @throws BusinessException RENTAL_NOT_FOUND, RENTAL_NOT_PARTICIPANT, RENTAL_INVALID_TRANSITION
	 */
	public void validateReturnRequest(Long rentalId, Long memberId) {
		getParticipantRental(rentalId, memberId).validateReturnRequest();
	}

	/**
	 * 반납 요청: RENTING·OVERDUE → RETURNED + RETURN 기록 + 이력. 제공자가 부르면 대리 접수(기록 작성자·이력 changedBy = 제공자).
	 * 연체료(overdueFee)는 건드리지 않는다(REQ-023).
	 *
	 * @param checklist 검증된 체크리스트(모든 항목 true/false)
	 * @param note 정규화된 메모. 없으면 null
	 * @throws BusinessException RENTAL_NOT_FOUND, RENTAL_NOT_PARTICIPANT, RENTAL_INVALID_TRANSITION
	 * @throws org.springframework.orm.ObjectOptimisticLockingFailureException 다른 요청이 먼저 대여를 바꿨을 때(409)
	 */
	@Transactional
	public RentalDetailResponse requestReturn(Long rentalId, Long memberId, Map<ReturnChecklistItem, Boolean> checklist,
		String note, List<String> imageUrls) {
		LocalDateTime now = LocalDateTime.now(clock);
		Rental rental = getParticipantRental(rentalId, memberId);
		RentalStatus from = rental.getStatus();
		rental.requestReturn(now);
		// 버전 검사를 기록 INSERT보다 먼저 (클래스 주석)
		rentalRepository.flush();
		itemInspectionRepository.save(ItemInspection.returned(rentalId, memberId, imageUrls, checklist, note));
		rentalStatusHistoryRepository.save(RentalStatusHistory.create(rentalId, from, rental.getStatus(), memberId,
			RentalStatusHistory.REASON_RETURN_REQUEST, now));
		return rentalQueryService.getRental(rentalId, memberId);
	}

	/**
	 * 상태 확인 사전 검사. 제공자만, RETURNED이고 RETURN 기록이 아직 판정되지 않았을 때.
	 *
	 * @throws BusinessException RENTAL_NOT_FOUND, RENTAL_NOT_PARTICIPANT, RENTAL_OWNER_ONLY, RENTAL_INVALID_TRANSITION
	 */
	public void validateConfirmReturn(Long rentalId, Long memberId) {
		Rental rental = getOwnerRental(rentalId, memberId);
		rental.validateReturned();
		if (getReturnInspection(rentalId).isJudged()) {
			throw new BusinessException(ErrorCode.RENTAL_INVALID_TRANSITION);
		}
	}

	/**
	 * 제공자 상태 확인(TX1): RETURN 기록에 판정 기록 후 {@link #judge}. 대여 행 락으로 자동 확인 배치·환불 결과 반영과 한 줄로 선다
	 * (보증금 반환을 기다리면 대여가 바뀌지 않아 @Version만으로는 동시 판정을 막지 못한다).
	 *
	 * @param resultNote 정규화된 판정 내용. 없으면 null
	 * @param resultImageUrls ABNORMAL 사진. NORMAL이면 빈 목록
	 * @return 커밋 뒤 실행할 보증금 반환. 비어 있으면 PG가 필요 없다
	 * @throws BusinessException RENTAL_NOT_FOUND, RENTAL_NOT_PARTICIPANT, RENTAL_OWNER_ONLY,
	 *     RENTAL_INVALID_TRANSITION(RETURNED 아님 또는 이미 판정)
	 */
	@Transactional
	public Optional<RefundTicket> confirmReturn(Long rentalId, Long memberId, InspectionResult result,
		String resultNote, List<String> resultImageUrls) {
		LocalDateTime now = LocalDateTime.now(clock);
		Rental rental = rentalRepository.findByIdForUpdate(rentalId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RENTAL_NOT_FOUND));
		requireOwner(rental, memberId);
		rental.validateReturned();
		return judge(rental, getReturnInspection(rentalId), result, resultNote, resultImageUrls, memberId, now);
	}

	/** 자동 상태 확인 배치 대상 id (반납 요청 후 return.auto-confirm-days 경과, 판정 전, 미반납 신고 아님) */
	public List<Long> findAutoConfirmTargetIds(LocalDateTime now) {
		return rentalRepository.findAutoConfirmTargetIds(
			now.minusDays(policyProperties.returnPolicy().autoConfirmDays()));
	}

	/**
	 * 자동 상태 확인 한 건(TX1): 판정 NORMAL(판정자 없음) 후 {@link #judge}, 이력 changedBy = null. 대상 조회 뒤 상태가 바뀌었을 수 있어
	 * 대여 행 락을 잡고 조건을 다시 본다.
	 *
	 * @return confirmed: 처리했는지, refund: 커밋 뒤 실행할 보증금 반환(PG가 필요 없으면 null)
	 */
	@Transactional
	public AutoConfirmResult autoConfirm(Long rentalId, LocalDateTime now) {
		Rental rental = rentalRepository.findByIdForUpdate(rentalId).orElse(null);
		if (rental == null || !rental.isAutoConfirmDue(now, policyProperties.returnPolicy().autoConfirmDays())) {
			return new AutoConfirmResult(false, null);
		}
		ItemInspection inspection = getReturnInspection(rentalId);
		if (inspection.isJudged() || inspection.isLostReported()) {
			return new AutoConfirmResult(false, null);
		}
		return new AutoConfirmResult(true,
			judge(rental, inspection, InspectionResult.NORMAL, null, List.of(), null, now).orElse(null));
	}

	public record AutoConfirmResult(boolean confirmed, RefundTicket refund) {
	}

	/**
	 * 판정을 기록하고 보증금에 따라 끝맺는다. 호출 전에 대여 행 락을 잡는다.
	 * <ul>
	 *   <li>NORMAL: 보증금 반환에 PG가 필요하면 RETURNED로 두고 환불 요청을 돌려준다(반환이 끝나면 COMPLETED, REQ-025). 필요 없으면 바로 COMPLETED</li>
	 *   <li>ABNORMAL: 보증금이 동결돼 있으면 RETURNED 유지·FROZEN 유지(HELD 전이는 REQ-049). 동결된 보증금이 없으면 처리할 금액이 없어 COMPLETED</li>
	 * </ul>
	 *
	 * @param changedBy 판정한 제공자. 자동 확인 배치면 null
	 */
	private Optional<RefundTicket> judge(Rental rental, ItemInspection inspection, InspectionResult result,
		String resultNote, List<String> resultImageUrls, Long changedBy, LocalDateTime now) {
		inspection.judge(result, resultNote, resultImageUrls);
		RentalStatus from = rental.getStatus();
		String reason;
		if (result == InspectionResult.NORMAL) {
			reason = changedBy == null ? RentalStatusHistory.REASON_RETURN_AUTO_CONFIRM_BATCH
				: RentalStatusHistory.REASON_RETURN_CONFIRM_NORMAL;
			// 연체료 확정(REQ-023) 전이라 overdueFee는 0이다
			Optional<RefundTicket> refund = rentalRefundPort.prepareDepositReturn(rental.getId(),
				rental.getOverdueFee(), new Completion(changedBy, reason));
			if (refund.isPresent()) {
				return refund;
			}
			rental.confirmNormal(now);
		} else {
			rental.markAbnormal();
			if (rentalRefundPort.hasFrozenDeposit(rental.getId())) {
				return Optional.empty();
			}
			rental.complete(now);
			reason = RentalStatusHistory.REASON_RETURN_CONFIRM_ABNORMAL;
		}
		rentalStatusHistoryRepository.save(RentalStatusHistory.create(rental.getId(), from, rental.getStatus(),
			changedBy, reason, now));
		return Optional.empty();
	}

	private ItemInspection getReturnInspection(Long rentalId) {
		// RETURNED면 반납 기록이 있다(반납 요청이 같은 트랜잭션에 저장). 없으면 데이터 오류
		return itemInspectionRepository.findByRentalIdAndPhase(rentalId, InspectionPhase.RETURN)
			.orElseThrow(() -> new IllegalStateException("반납 기록이 없습니다: " + rentalId));
	}

	private Rental getParticipantRental(Long rentalId, Long memberId) {
		Rental rental = rentalRepository.findById(rentalId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RENTAL_NOT_FOUND));
		if (!rental.isParticipant(memberId)) {
			throw new BusinessException(ErrorCode.RENTAL_NOT_PARTICIPANT);
		}
		return rental;
	}

	private Rental getOwnerRental(Long rentalId, Long memberId) {
		Rental rental = getParticipantRental(rentalId, memberId);
		requireOwner(rental, memberId);
		return rental;
	}

	private void requireOwner(Rental rental, Long memberId) {
		if (!rental.isParticipant(memberId)) {
			throw new BusinessException(ErrorCode.RENTAL_NOT_PARTICIPANT);
		}
		if (rental.roleOf(memberId) != RentalRole.OWNER) {
			throw new BusinessException(ErrorCode.RENTAL_OWNER_ONLY);
		}
	}
}
