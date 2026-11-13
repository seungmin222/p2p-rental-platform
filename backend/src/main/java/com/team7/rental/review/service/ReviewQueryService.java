package com.team7.rental.review.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.global.response.PageResponse;
import com.team7.rental.member.dto.MemberPublicSummary;
import com.team7.rental.member.service.MemberQueryService;
import com.team7.rental.rental.dto.RentalParticipants;
import com.team7.rental.rental.entity.RentalRole;
import com.team7.rental.rental.service.RentalParticipantReader;
import com.team7.rental.rental.service.RentalReviewQueryPort;
import com.team7.rental.review.dto.ReceivedReviewResponse;
import com.team7.rental.review.entity.ComplimentType;
import com.team7.rental.review.entity.Review;
import com.team7.rental.review.repository.ReviewRepository;

import lombok.RequiredArgsConstructor;

/** 받은 후기 목록(REQ-034)과 대여 쪽에 주는 후기 작성 여부({@link RentalReviewQueryPort}) */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReviewQueryService implements RentalReviewQueryPort {

	private final ReviewRepository reviewRepository;
	private final RentalParticipantReader rentalParticipantReader;
	private final MemberQueryService memberQueryService;
	private final PolicyProperties policyProperties;

	/**
	 * 회원이 받은 후기, 최신 작성 순(id DESC). 비회원도 본다. 쿼리 수는 항목 수와 상관없이 회원 확인 1 + 후기 1(+ count) + 칭찬 1 + 대여 1 +
	 * 작성자 3이고, 빈 페이지면 일괄 조회가 쿼리를 내지 않는다. 대상 회원이 탈퇴했을 때의 비공개 처리는 프로필(REQ-021)에서 정한다.
	 *
	 * @throws BusinessException MEMBER_NOT_FOUND
	 */
	public PageResponse<ReceivedReviewResponse> getReceivedReviews(Long memberId, int page) {
		if (!memberQueryService.exists(memberId)) {
			throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
		}
		Page<Review> reviews = reviewRepository.findByTargetId(memberId,
			PageRequest.of(page, policyProperties.review().page(), Sort.by(Sort.Direction.DESC, "id")));

		List<Long> reviewIds = new ArrayList<>();
		Set<Long> rentalIds = new HashSet<>();
		Set<Long> writerIds = new HashSet<>();
		for (Review review : reviews) {
			reviewIds.add(review.getId());
			rentalIds.add(review.getRentalId());
			writerIds.add(review.getWriterId());
		}
		Map<Long, Set<ComplimentType>> compliments = findCompliments(reviewIds);
		Map<Long, RentalParticipants> rentals = rentalParticipantReader.getAll(rentalIds);
		Map<Long, MemberPublicSummary> writers = memberQueryService.getPublicSummaries(writerIds);

		return PageResponse.of(reviews, review -> toResponse(review, compliments, rentals, writers));
	}

	@Override
	public boolean isWritten(Long rentalId, Long writerId) {
		return reviewRepository.existsByRentalIdAndWriterId(rentalId, writerId);
	}

	@Override
	public Set<Long> findWrittenRentalIds(Long writerId, Collection<Long> rentalIds) {
		if (rentalIds.isEmpty()) {
			return Set.of();
		}
		return new HashSet<>(reviewRepository.findRentalIdsByWriter(writerId, rentalIds));
	}

	private Map<Long, Set<ComplimentType>> findCompliments(List<Long> reviewIds) {
		Map<Long, Set<ComplimentType>> result = new HashMap<>();
		if (reviewIds.isEmpty()) {
			return result;
		}
		for (Object[] row : reviewRepository.findCompliments(reviewIds)) {
			result.computeIfAbsent((Long)row[0], id -> EnumSet.noneOf(ComplimentType.class))
				.add((ComplimentType)row[1]);
		}
		return result;
	}

	private ReceivedReviewResponse toResponse(Review review, Map<Long, Set<ComplimentType>> compliments,
		Map<Long, RentalParticipants> rentals, Map<Long, MemberPublicSummary> writers) {
		// 후기가 FK로 참조하는 행이라 없으면 데이터 오류다(빌린 내역과 같은 처리)
		RentalParticipants rental = rentals.get(review.getRentalId());
		MemberPublicSummary writer = writers.get(review.getWriterId());
		if (rental == null || writer == null) {
			throw new IllegalStateException("후기가 참조하는 대여·회원이 없습니다: reviewId=" + review.getId());
		}
		RentalRole writerRole = rental.renterId().equals(review.getWriterId()) ? RentalRole.RENTER : RentalRole.OWNER;
		boolean withdrawn = writer.isWithdrawn();
		return new ReceivedReviewResponse(
			review.getId(),
			new ReceivedReviewResponse.Writer(writer.memberId(), withdrawn ? null : writer.nickname(),
				withdrawn ? null : writer.profileImageUrl(), withdrawn),
			writerRole,
			review.getSatisfaction(),
			List.copyOf(compliments.getOrDefault(review.getId(), EnumSet.noneOf(ComplimentType.class))),
			review.getContent(),
			review.getCreatedAt());
	}
}
