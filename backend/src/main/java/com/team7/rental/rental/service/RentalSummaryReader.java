package com.team7.rental.rental.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.item.dto.ItemSummary;
import com.team7.rental.item.service.ItemQueryService;
import com.team7.rental.rental.dto.RentalDetailResponse.RentalItem;
import com.team7.rental.rental.dto.RentalSummary;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.repository.RentalRepository;

import lombok.RequiredArgsConstructor;

/** 다른 도메인 목록용 대여 요약 일괄 조회. 쿼리는 대여 수와 상관없이 3개(대여·물품·대표 이미지)다. 빈 목록이면 쿼리를 내지 않는다 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RentalSummaryReader {

	private final RentalRepository rentalRepository;
	private final ItemQueryService itemQueryService;

	/**
	 * @return 대여 ID → 요약. 없는 ID는 담지 않는다
	 * @throws IllegalStateException 대여가 참조하는 물품이 없을 때(물품은 소프트 삭제라 데이터 오류)
	 */
	public Map<Long, RentalSummary> getSummaries(Collection<Long> rentalIds) {
		if (rentalIds.isEmpty()) {
			return Map.of();
		}
		List<Rental> rentals = rentalRepository.findAllById(rentalIds);
		Map<Long, ItemSummary> items = itemQueryService.getSummaries(
			rentals.stream().map(Rental::getItemId).collect(Collectors.toSet()));
		Map<Long, RentalSummary> summaries = new HashMap<>();
		for (Rental rental : rentals) {
			ItemSummary item = items.get(rental.getItemId());
			if (item == null) {
				throw new IllegalStateException("대여가 참조하는 물품이 없습니다: rentalId=" + rental.getId());
			}
			summaries.put(rental.getId(), new RentalSummary(rental.getId(),
				new RentalItem(item.itemId(), item.name(), item.thumbnailUrl(), item.isDeleted()),
				rental.getStartDate(), rental.getEndDate(), rental.getStatus(), rental.getOverdueFee(),
				rental.getCancelFee()));
		}
		return summaries;
	}
}
