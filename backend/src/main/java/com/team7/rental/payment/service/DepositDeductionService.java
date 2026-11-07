package com.team7.rental.payment.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.global.storage.FileStorage;
import com.team7.rental.global.storage.ImageProcessor;
import com.team7.rental.global.storage.ProcessedImage;
import com.team7.rental.global.storage.StorageKeys;
import com.team7.rental.global.storage.StoredFile;
import com.team7.rental.payment.dto.AdminDepositDecisionRequest;
import com.team7.rental.payment.dto.DeductionPolicyResponse;
import com.team7.rental.payment.dto.DepositActionRequest;
import com.team7.rental.payment.dto.HeldDepositResponse;
import com.team7.rental.payment.entity.DamageGrade;
import com.team7.rental.payment.repository.DeductionAutoAcceptTarget;
import com.team7.rental.payment.service.DepositDeductionTxService.ProposeRule;
import com.team7.rental.payment.service.DepositDeductionTxService.Settlement;
import com.team7.rental.rental.dto.RentalDepositInfo;
import com.team7.rental.rental.dto.RentalDetailResponse;
import com.team7.rental.rental.service.RentalNotificationPort;
import com.team7.rental.rental.service.RentalNotificationPort.TargetType;
import com.team7.rental.rental.service.RentalNotificationPort.Type;
import com.team7.rental.rental.service.RentalQueryService;
import com.team7.rental.rental.service.RentalRefundPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 보증금 보류·차감 (REQ-049). 트랜잭션을 열지 않고 사진·PG·알림을 다룬다.
 * <ol>
 *   <li>사전 검사(읽기 트랜잭션): 거절될 제안이면 사진을 처리하지 않는다</li>
 *   <li>증빙 장수 검사 + 전부 처리 → 업로드</li>
 *   <li>{@link DepositDeductionTxService}(대여 행 락 → 결제 행 락). 예외면 업로드한 파일을 지우고 다시 던진다</li>
 *   <li>커밋 뒤: 반환액이 있으면 PG 부분 취소({@link RentalRefundPort#execute}, 실패하면 재시도 배치), 알림</li>
 * </ol>
 * 증빙 사진·영수증의 장수·용량은 차감용 정책 키가 없어 inspection.images.*를 쓴다(상태 확인 '이상' 사진과 같음, open-issues).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepositDeductionService {

	private final DepositDeductionTxService depositDeductionTxService;
	private final RentalRefundPort rentalRefundPort;
	private final RentalNotificationPort rentalNotificationPort;
	private final RentalQueryService rentalQueryService;
	private final DepositQueryService depositQueryService;
	private final ImageProcessor imageProcessor;
	private final FileStorage fileStorage;
	private final PolicyProperties policyProperties;

	/** 등급별 차감 상한 조회 */
	public DeductionPolicyResponse getPolicy(Long rentalId, Long memberId, DamageGrade grade) {
		return DeductionPolicyResponse.from(depositDeductionTxService.getLimit(rentalId, memberId, grade));
	}

	/**
	 * 차감 제안·수락·거절 (PATCH /api/rentals/{id}/deposit).
	 *
	 * @param evidenceImages 손상 증빙 사진 (PROPOSE). 미반납 신고 건은 없어도 된다
	 * @param receiptImages 견적서·영수증·구매 내역 (PROPOSE, 정책이 ACTUAL이면 필수)
	 */
	public RentalDetailResponse act(Long rentalId, Long memberId, DepositActionRequest request,
		List<MultipartFile> evidenceImages, List<MultipartFile> receiptImages) {
		switch (request.action()) {
			case PROPOSE -> propose(rentalId, memberId, request, orEmpty(evidenceImages), orEmpty(receiptImages));
			case ACCEPT -> {
				requireNoFiles(evidenceImages, receiptImages);
				afterSettle(depositDeductionTxService.accept(rentalId, memberId, request.proposalId()));
			}
			case REJECT -> {
				requireNoFiles(evidenceImages, receiptImages);
				DepositDeductionTxService.Rejected rejected = depositDeductionTxService.reject(rentalId, memberId,
					request.proposalId());
				rentalNotificationPort.send(rejected.ownerId(), Type.DEDUCTION_REJECTED, TargetType.RENTAL, rentalId,
					RentalNotificationPort.deductionEventId(Type.DEDUCTION_REJECTED, rentalId, rejected.proposalId()));
			}
		}
		return rentalQueryService.getRental(rentalId, memberId);
	}

	/** 자동 수락 배치 대상 */
	public List<DeductionAutoAcceptTarget> findAutoAcceptTargets(LocalDateTime now) {
		return depositDeductionTxService.findAutoAcceptTargets(now);
	}

	/**
	 * 자동 수락 한 건 → 확정 처리.
	 *
	 * @return 처리했으면 true, 이미 대상이 아니면 false
	 */
	public boolean autoAccept(DeductionAutoAcceptTarget target, LocalDateTime now) {
		Optional<Settlement> settlement = depositDeductionTxService.autoAccept(target, now);
		settlement.ifPresent(this::afterSettle);
		return settlement.isPresent();
	}

	/** 관리자 확정 대상 목록 */
	public List<HeldDepositResponse> findHeld() {
		return depositDeductionTxService.findHeld();
	}

	/** 관리자 확정. 관리자는 당사자가 아니라 대여 상세 대신 확정 뒤 보증금 정보를 돌려준다 */
	public RentalDepositInfo adminDecide(Long rentalId, Long adminId, AdminDepositDecisionRequest request) {
		afterSettle(depositDeductionTxService.adminDecide(rentalId, adminId, request.grade(), request.amount(),
			request.normalizedReason()));
		return depositQueryService.findDepositInfo(rentalId).orElseThrow();
	}

	private void propose(Long rentalId, Long memberId, DepositActionRequest request, List<MultipartFile> evidence,
		List<MultipartFile> receipts) {
		ProposeRule rule = depositDeductionTxService.validatePropose(rentalId, memberId, request.grade(),
			request.amount());
		List<ProcessedImage> evidenceProcessed = processImages(evidence, !rule.lostReported());
		List<ProcessedImage> receiptProcessed = processImages(receipts, rule.receiptRequired());
		List<StoredFile> stored = new ArrayList<>();
		DepositDeductionTxService.Proposed proposed;
		try {
			List<String> evidenceUrls = upload(evidenceProcessed, stored);
			List<String> receiptUrls = upload(receiptProcessed, stored);
			proposed = depositDeductionTxService.propose(rentalId, memberId, request.grade(), request.amount(),
				request.normalizedReason(), evidenceUrls, receiptUrls);
		} catch (RuntimeException e) {
			deleteQuietly(stored);
			throw e;
		}
		rentalNotificationPort.send(proposed.renterId(), Type.DEDUCTION_PROPOSED, TargetType.RENTAL, rentalId,
			RentalNotificationPort.deductionEventId(Type.DEDUCTION_PROPOSED, rentalId, proposed.proposalId()));
	}

	/** 커밋 뒤: 반환액이 있으면 PG 부분 취소, 양측에 DEDUCTION_ACCEPTED (수락·자동 수락·관리자 확정 공통) */
	private void afterSettle(Settlement settlement) {
		if (settlement.refund() != null) {
			rentalRefundPort.execute(settlement.refund());
		}
		String eventId = RentalNotificationPort.deductionEventId(Type.DEDUCTION_ACCEPTED, settlement.rentalId(),
			settlement.proposalId());
		rentalNotificationPort.send(settlement.renterId(), Type.DEDUCTION_ACCEPTED, TargetType.RENTAL,
			settlement.rentalId(), eventId);
		rentalNotificationPort.send(settlement.ownerId(), Type.DEDUCTION_ACCEPTED, TargetType.RENTAL,
			settlement.rentalId(), eventId);
	}

	/**
	 * 장수 검사 + 전부 처리. 필수인데 0장이면 DEDUCTION_EVIDENCE_REQUIRED, 상한(inspection.images.max) 초과·필수일 때 최소 미만이면
	 * IMAGE_COUNT_INVALID.
	 */
	private List<ProcessedImage> processImages(List<MultipartFile> files, boolean required) {
		var policy = policyProperties.inspection().images();
		if (required && files.isEmpty()) {
			throw new BusinessException(ErrorCode.DEDUCTION_EVIDENCE_REQUIRED);
		}
		if (files.size() > policy.max() || (required && files.size() < policy.min())) {
			throw new BusinessException(ErrorCode.IMAGE_COUNT_INVALID);
		}
		return files.stream().map(file -> imageProcessor.process(file, policy.maxFileSize())).toList();
	}

	private List<String> upload(List<ProcessedImage> images, List<StoredFile> stored) {
		List<String> urls = new ArrayList<>();
		for (ProcessedImage image : images) {
			StoredFile file = fileStorage.upload(StorageKeys.DEDUCTIONS, image.format().getExtension(),
				image.format().getContentType(), image.original());
			stored.add(file);
			urls.add(file.url());
		}
		return urls;
	}

	// 정리 실패가 원래 예외를 가리지 않게 한다. 남은 파일은 DB가 참조하지 않는 고아 파일이다
	private void deleteQuietly(List<StoredFile> stored) {
		for (StoredFile file : stored) {
			try {
				fileStorage.delete(file.key());
			} catch (RuntimeException e) {
				log.warn("Failed to delete uploaded file: {}", file.key(), e);
			}
		}
	}

	/** 수락·거절에 사진을 보내면 400 (보낸 파일이 버려지지 않게) */
	private void requireNoFiles(List<MultipartFile> evidence, List<MultipartFile> receipts) {
		if (!orEmpty(evidence).isEmpty() || !orEmpty(receipts).isEmpty()) {
			throw new BusinessException(ErrorCode.IMAGE_COUNT_INVALID);
		}
	}

	private static List<MultipartFile> orEmpty(List<MultipartFile> files) {
		return files == null ? List.of() : files;
	}
}
