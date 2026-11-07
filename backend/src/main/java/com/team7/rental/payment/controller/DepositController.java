package com.team7.rental.payment.controller;

import static com.team7.rental.global.error.ErrorCode.*;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.team7.rental.global.config.OpenApiConfig;
import com.team7.rental.global.error.ApiErrorCodes;
import com.team7.rental.global.response.ApiResponse;
import com.team7.rental.global.security.LoginMember;
import com.team7.rental.payment.dto.DeductionPolicyResponse;
import com.team7.rental.payment.dto.DepositActionRequest;
import com.team7.rental.payment.entity.DamageGrade;
import com.team7.rental.payment.service.DepositDeductionService;
import com.team7.rental.rental.dto.RentalDetailResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Payment", description = "결제·보증금")
@RestController
@RequestMapping("/api/rentals/{rentalId}/deposit")
@RequiredArgsConstructor
public class DepositController {

	private final DepositDeductionService depositDeductionService;

	@Operation(summary = "보증금 차감 제안·수락·거절 (REQ-049)", description = "상태 확인 '이상' 또는 미반납 신고로 보증금이 보류(HELD)된 반납 완료(RETURNED) 건의 차감을 협의한다. "
		+ "multipart: request part(Content-Type application/json, 필수) { action, proposalId, grade, amount, reason } + evidenceImages·receiptImages 파일들. "
		+ "PROPOSE(제공자): grade·amount·reason 필수(사유 최대 policies.md deduction.reason.max자). A 등급 불가, 미반납 신고 건은 D만. "
		+ "amount는 0 이상 GET .../deposit/policy의 maxAmount 이하(남은 보증금 = 보증금 − 연체료). "
		+ "evidenceImages(손상 사진)는 필수(미반납 신고 건은 선택), receiptImages(견적서·영수증·구매 내역)는 정책이 ACTUAL(C 전부, 공구 B)이면 필수다. "
		+ "장수 inspection.images.min~max, 장당 inspection.images.max-file-size, jpg/png. 대여자에게 DEDUCTION_PROPOSED. "
		+ "ACCEPT·REJECT(대여자): proposalId 필수, 사진을 보내면 400. 제안 후 policies.md deduction.renter-response-hours가 지나면 응답할 수 없고 자동 수락된다. "
		+ "ACCEPT면 남은 보증금 − 차감액을 부분 취소로 돌려주고(0원이면 FORFEITED) 거래 완료(COMPLETED), 양측에 DEDUCTION_ACCEPTED. "
		+ "반환이 실패했거나 PG 결과를 알 수 없으면 200에 status RETURNED로 답하고 재시도 배치가 완료한다. "
		+ "REJECT면 보류가 이어지고 제공자에게 DEDUCTION_REJECTED, 제공자는 다시 제안할 수 있다. "
		+ "보류 후 policies.md deposit.hold-agreement-days가 지나도록 합의하지 못하면 관리자가 확정한다. "
		+ "검사 순서: request part 400 COMMON_INVALID_INPUT → 404 RENTAL_NOT_FOUND → 403 RENTAL_NOT_PARTICIPANT → 403 RENTAL_OWNER_ONLY·RENTAL_RENTER_ONLY → "
		+ "409 RENTAL_INVALID_TRANSITION(반납 완료 아님) → 409 DEPOSIT_NOT_HELD → "
		+ "PROPOSE: 409 DEDUCTION_PROPOSAL_IN_PROGRESS → 400 DEDUCTION_GRADE_NOT_ALLOWED → 400 DEDUCTION_EXCEEDS_DEPOSIT → 400 DEDUCTION_EXCEEDS_CAP(details maxAmount) → "
		+ "400 DEDUCTION_EVIDENCE_REQUIRED·IMAGE_*; ACCEPT·REJECT: 404 DEDUCTION_PROPOSAL_NOT_FOUND → 409 DEDUCTION_PROPOSAL_CLOSED. 응답은 대여 상세와 같다.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({COMMON_INVALID_INPUT, COMMON_UNSUPPORTED_MEDIA_TYPE, AUTH_UNAUTHORIZED, RENTAL_NOT_FOUND,
		RENTAL_NOT_PARTICIPANT, RENTAL_OWNER_ONLY, RENTAL_RENTER_ONLY, RENTAL_INVALID_TRANSITION, DEPOSIT_NOT_HELD,
		DEDUCTION_PROPOSAL_IN_PROGRESS, DEDUCTION_GRADE_NOT_ALLOWED, DEDUCTION_EXCEEDS_DEPOSIT, DEDUCTION_EXCEEDS_CAP,
		DEDUCTION_EVIDENCE_REQUIRED, IMAGE_COUNT_INVALID, IMAGE_INVALID_TYPE, IMAGE_SIZE_EXCEEDED,
		DEDUCTION_PROPOSAL_NOT_FOUND, DEDUCTION_PROPOSAL_CLOSED})
	@PatchMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ApiResponse<RentalDetailResponse> act(@LoginMember
	Long memberId, @PathVariable
	Long rentalId,
		@Valid @RequestPart("request")
		DepositActionRequest request,
		@RequestPart(value = "evidenceImages", required = false)
		List<MultipartFile> evidenceImages,
		@RequestPart(value = "receiptImages", required = false)
		List<MultipartFile> receiptImages) {
		return ApiResponse.ok(depositDeductionService.act(rentalId, memberId, request, evidenceImages, receiptImages));
	}

	@Operation(summary = "등급별 차감 상한 (REQ-049)", description = "policies.md §4 계산 규칙: 남은 보증금 = 보증금 − 연체료, 상한 B = 카테고리 정액(공구는 실비 → 남은 보증금), "
		+ "C = floor(보증금 × 카테고리 상한율), D = 남은 보증금. maxAmount = min(상한, 남은 보증금). 상한은 물품의 현재 카테고리 정책으로 계산한다. "
		+ "당사자만. A 등급은 차감할 수 없고, 미반납 신고 건은 D만(400 DEDUCTION_GRADE_NOT_ALLOWED).")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({COMMON_INVALID_INPUT, DEDUCTION_GRADE_NOT_ALLOWED, AUTH_UNAUTHORIZED, RENTAL_NOT_PARTICIPANT,
		RENTAL_NOT_FOUND})
	@GetMapping("/policy")
	public ApiResponse<DeductionPolicyResponse> getPolicy(@LoginMember
	Long memberId, @PathVariable
	Long rentalId, @RequestParam
	DamageGrade grade) {
		return ApiResponse.ok(depositDeductionService.getPolicy(rentalId, memberId, grade));
	}
}
