package com.team7.rental.rental.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.rental.entity.AiCompareRecord;
import com.team7.rental.rental.entity.InspectionPhase;
import com.team7.rental.rental.entity.ItemInspection;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.repository.ItemInspectionRepository;
import com.team7.rental.rental.repository.RentalRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AI 상태 비교(REQ-040)가 쓰는 대여 쪽 창구. ai 도메인은 item_inspections를 직접 읽지 않고 이 서비스를 부른다(ai → rental 방향).
 * 두 메서드 모두 짧은 트랜잭션이고 Claude 호출은 그 사이, 트랜잭션 밖에서 한다. 반납 기록 행만 잠그고 대여 행은 잠그지 않아 상태 확인(대여 → 기록 순)과
 * 락 순서가 엇갈리지 않는다.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RentalAiCompareService {

	private final ItemInspectionRepository itemInspectionRepository;
	private final RentalRepository rentalRepository;
	private final Clock clock;

	/**
	 * 비교를 맡는다. 반납 기록을 PROCESSING으로 바꾸고 비교할 사진을 돌려준다(ai.compare.per-rental: 대여 건당 1회).
	 *
	 * @return 맡을 것이 없으면(반납 기록 없음·미반납 신고·이미 맡음·인계 기록 없음) empty
	 */
	@Transactional
	public Optional<CompareTarget> start(Long rentalId) {
		Optional<ItemInspection> found = itemInspectionRepository.findByRentalIdAndPhaseForUpdate(rentalId,
			InspectionPhase.RETURN);
		if (found.isEmpty()) {
			log.warn("AI compare skipped: no return inspection, rentalId={}", rentalId);
			return Optional.empty();
		}
		ItemInspection returned = found.get();
		if (returned.isLostReported() || returned.getAiResult() != null) {
			log.info("AI compare skipped: lostReported={}, already={}, rentalId={}", returned.isLostReported(),
				returned.getAiResult() != null, rentalId);
			return Optional.empty();
		}
		Optional<ItemInspection> handover = itemInspectionRepository.findByRentalIdAndPhase(rentalId,
			InspectionPhase.HANDOVER);
		if (handover.isEmpty()) {
			// 반납 요청은 RENTING·OVERDUE에서만 되므로 인계 기록이 항상 있다. 데이터가 깨졌을 때만 여기 온다
			log.warn("AI compare skipped: no handover inspection, rentalId={}", rentalId);
			return Optional.empty();
		}
		LocalDateTime now = LocalDateTime.now(clock);
		returned.startAiCompare(now);
		return Optional.of(new CompareTarget(rentalId, handover.get().getImageUrls(), returned.getImageUrls(),
			returned.getAiResult()));
	}

	/**
	 * 결과를 저장한다. PROCESSING일 때만 바꾼다.
	 *
	 * @param finished DONE·FAILED·LIMIT_EXCEEDED
	 * @return 저장했으면 알림 받을 당사자, 아니면 empty
	 */
	@Transactional
	public Optional<Participants> finish(Long rentalId, AiCompareRecord finished) {
		Optional<ItemInspection> found = itemInspectionRepository.findByRentalIdAndPhaseForUpdate(rentalId,
			InspectionPhase.RETURN);
		if (found.isEmpty() || !found.get().finishAiCompare(finished)) {
			log.warn("AI compare result not saved: rentalId={}, status={}", rentalId, finished.status());
			return Optional.empty();
		}
		Rental rental = rentalRepository.findById(rentalId).orElseThrow();
		return Optional.of(new Participants(rental.getRenterId(), rental.getOwnerId()));
	}

	/**
	 * @param handoverImageUrls 인계 사진 (올린 순서)
	 * @param returnImageUrls 반납 사진 (올린 순서, regions.imageIndex 기준)
	 * @param processing 맡은 상태. 끝낼 때 {@link AiCompareRecord#done} 등으로 이어 쓴다
	 */
	public record CompareTarget(Long rentalId, List<String> handoverImageUrls, List<String> returnImageUrls,
		AiCompareRecord processing) {
	}

	public record Participants(Long renterId, Long ownerId) {
	}
}
