package com.team7.rental.rental.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.rental.dto.RentalDetailResponse;
import com.team7.rental.rental.entity.ItemInspection;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalRole;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.entity.RentalStatusHistory;
import com.team7.rental.rental.repository.ItemInspectionRepository;
import com.team7.rental.rental.repository.RentalRepository;
import com.team7.rental.rental.repository.RentalStatusHistoryRepository;

import lombok.RequiredArgsConstructor;

/**
 * 인계·반납 기록 쓰기의 트랜잭션 부분 (REQ-017·018). 사진 처리·업로드는 트랜잭션 밖({@link RentalInspectionService})에서 끝내고 URL만 받는다.
 * <p>
 * validate* 는 사진을 처리하기 전에 거절될 요청을 걸러 내는 최적화일 뿐이다. 쓰기 메서드는 트랜잭션 안에서 대여를 다시 읽고 404·403·409를 전부
 * 다시 검사한다(사전 검사와 쓰기 사이에 상태가 바뀔 수 있다).
 * <p>
 * 행 락을 잡지 않는다(사진 처리 동안 행을 잠그지 않으려고 업로드를 밖으로 뺐고, 쓰기 트랜잭션은 짧다). 동시 변경은 Rental @Version이 막는다.
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
		if (rental.roleOf(memberId) != RentalRole.OWNER) {
			throw new BusinessException(ErrorCode.RENTAL_OWNER_ONLY);
		}
		return rental;
	}
}
