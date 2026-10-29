package com.team7.rental.rental.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.global.response.PageResponse;
import com.team7.rental.item.dto.ItemSummary;
import com.team7.rental.item.service.ItemQueryService;
import com.team7.rental.member.service.MemberQueryService;
import com.team7.rental.rental.dto.ItemRentalResponse;
import com.team7.rental.rental.dto.RentalDetailResponse.RentalCounterpart;
import com.team7.rental.rental.dto.RentalDetailResponse.RentalItem;
import com.team7.rental.rental.dto.RentalHistoryResponse;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalHistoryTab;
import com.team7.rental.rental.repository.RentalHistoryRow;
import com.team7.rental.rental.repository.RentalRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RentalHistoryService {

	private final RentalRepository rentalRepository;
	private final ItemQueryService itemQueryService;
	private final MemberQueryService memberQueryService;
	private final PolicyProperties policyProperties;
	private final Clock clock;

	/**
	 * 빌린 내역(REQ-019). 본인이 대여자인 건만. 물품·제공자 닉네임은 페이지 단위로 한 번씩 일괄 조회해 쿼리 수가 항목 수와 상관없다
	 * (대여·물품·대표 이미지·닉네임, 필요하면 count). 빈 페이지면 일괄 조회 메서드가 쿼리를 내지 않는다.
	 */
	public PageResponse<RentalHistoryResponse> getRenterHistory(Long memberId, RentalHistoryTab tab, int page) {
		LocalDateTime now = LocalDateTime.now(clock);
		Page<RentalHistoryRow> rows = rentalRepository.findRenterHistory(memberId, tab.getStatuses(),
			PageRequest.of(page, policyProperties.rentalHistory().page()));

		Set<Long> itemIds = rows.stream().map(RentalHistoryRow::itemId).collect(Collectors.toSet());
		Set<Long> ownerIds = rows.stream().map(RentalHistoryRow::ownerId).collect(Collectors.toSet());
		Map<Long, ItemSummary> items = itemQueryService.getSummaries(itemIds);
		Map<Long, String> nicknames = memberQueryService.getNicknames(ownerIds);
		int reviewDeadlineDays = policyProperties.review().writeDeadlineDays();

		return PageResponse.of(rows, row -> toResponse(row, items, nicknames, now, reviewDeadlineDays));
	}

	private RentalHistoryResponse toResponse(RentalHistoryRow row, Map<Long, ItemSummary> items,
		Map<Long, String> nicknames, LocalDateTime now, int reviewDeadlineDays) {
		// 대여가 FK로 참조하는 행이고 물품·회원은 소프트 삭제라 없으면 데이터 오류다(getSummary·getNickname과 같은 처리)
		ItemSummary item = items.get(row.itemId());
		String nickname = nicknames.get(row.ownerId());
		if (item == null || nickname == null) {
			throw new IllegalStateException("대여가 참조하는 물품·회원이 없습니다: rentalId=" + row.rentalId());
		}
		return new RentalHistoryResponse(
			row.rentalId(),
			row.status(),
			new RentalItem(item.itemId(), item.name(), item.thumbnailUrl(), item.isDeleted()),
			new RentalCounterpart(row.ownerId(), nickname),
			row.startDate(),
			row.endDate(),
			(long)row.rentalFee() + row.deposit(),
			// "후기 미작성" 조건은 feat/review(REQ-034)에서 더한다
			Rental.canWriteReview(row.status(), row.completedAt(), now, reviewDeadlineDays));
	}

	/**
	 * 물품별 예약 현황 (REQ-020, 제공자). 다가오는·진행 중 건을 시작일 오름차순으로 먼저, 완료·취소 건을 시작일 내림차순으로 뒤에.
	 * 페이지 크기 policies.md item-rentals.page.
	 */
	public PageResponse<ItemRentalResponse> getItemRentals(Long itemId, Long memberId, int page) {
		throw new UnsupportedOperationException("미구현: feat/item-feed-search");
	}
}
