package com.team7.rental.rental.controller;

import static com.team7.rental.global.error.ErrorCode.*;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.team7.rental.global.config.OpenApiConfig;
import com.team7.rental.global.error.ApiErrorCodes;
import com.team7.rental.global.response.ApiResponse;
import com.team7.rental.global.security.LoginMember;
import com.team7.rental.rental.dto.CancelAgreementRespondRequest;
import com.team7.rental.rental.dto.CancelAgreementResponse;
import com.team7.rental.rental.dto.RentalCancelRequest;
import com.team7.rental.rental.dto.RentalCreateRequest;
import com.team7.rental.rental.dto.RentalDetailResponse;
import com.team7.rental.rental.dto.RentalHandoverRequest;
import com.team7.rental.rental.dto.RentalInspectionResponse;
import com.team7.rental.rental.dto.RentalReturnConfirmRequest;
import com.team7.rental.rental.dto.RentalReturnRequest;
import com.team7.rental.rental.service.RentalCancelService;
import com.team7.rental.rental.service.RentalInspectionService;
import com.team7.rental.rental.service.RentalQueryService;
import com.team7.rental.rental.service.RentalService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Rental", description = "예약·대여·반납")
@RestController
@RequestMapping("/api/rentals")
@RequiredArgsConstructor
public class RentalController {

	private final RentalService rentalService;
	private final RentalQueryService rentalQueryService;
	private final RentalCancelService rentalCancelService;
	private final RentalInspectionService rentalInspectionService;

	@Operation(summary = "대여 예약 요청 (REQ-013, REQ-014)", description = "1순위 단계(결제 비활성)와 무료 대여는 바로 RESERVED, 결제를 켜면 PENDING_PAYMENT로 생성해 결제 대기 시간(policies.md payment.expire-minutes) 동안 기간을 선점한다. "
		+ "응답은 대여 상세와 같다.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({COMMON_INVALID_INPUT, RENTAL_INVALID_PERIOD, RENTAL_OWN_ITEM, RENTAL_OUT_OF_AVAILABLE_PERIOD,
		AUTH_UNAUTHORIZED, REGION_NOT_VERIFIED, ITEM_NOT_FOUND, RENTAL_PERIOD_CONFLICT, RENTAL_LOCK_TIMEOUT})
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<RentalDetailResponse> createRental(@LoginMember
	Long memberId,
		@Valid @RequestBody
		RentalCreateRequest request) {
		return ApiResponse.ok(rentalService.create(memberId, request));
	}

	@Operation(summary = "대여 상세 조회 (REQ-016)", description = "대여자·제공자만 조회할 수 있다.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({COMMON_INVALID_INPUT, AUTH_UNAUTHORIZED, RENTAL_NOT_PARTICIPANT, RENTAL_NOT_FOUND})
	@GetMapping("/{rentalId}")
	public ApiResponse<RentalDetailResponse> getRental(@LoginMember
	Long memberId, @PathVariable
	Long rentalId) {
		return ApiResponse.ok(rentalQueryService.getRental(rentalId, memberId));
	}

	@Operation(summary = "인계·반납 기록 조회 (REQ-017, REQ-018)", description = "대여자·제공자만 조회할 수 있다. 인계(HANDOVER) → 반납(RETURN) 순이고, 아직 없는 단계는 빠진다. "
		+ "반납 기록에는 체크리스트와 제공자 판정(result·resultNote·resultImageUrls)이 있다. 수정 API는 없다.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({COMMON_INVALID_INPUT, AUTH_UNAUTHORIZED, RENTAL_NOT_PARTICIPANT, RENTAL_NOT_FOUND})
	@GetMapping("/{rentalId}/inspections")
	public ApiResponse<List<RentalInspectionResponse>> getInspections(@LoginMember
	Long memberId, @PathVariable
	Long rentalId) {
		return ApiResponse.ok(rentalQueryService.getInspections(rentalId, memberId));
	}

	@Operation(summary = "대여 단독 취소 (REQ-015)", description = "대여자·제공자가 결제 대기(PENDING_PAYMENT) 또는 시작일 전의 대여 예정(RESERVED)을 취소한다. "
		+ "시작일이 지난 대여 예정은 409 RENTAL_CANCEL_NOT_ALLOWED이며 양측 동의 취소를 쓴다. "
		+ "대여자가 시작일 직전에 취소하면 cancelFee가 생긴다(policies.md cancel-fee.rate-percent, cancel-fee.free-until-days-before). "
		+ "승인된 결제가 있으면 대여료 − 수수료 + 보증금을 환불한다. 환불이 실패해도 대여는 CANCELED이고 재시도 배치가 끝낸다. 결제가 없으면 수수료를 기록만 한다. reason=OTHER면 reasonText 필수(cancel.reason-text-max). 응답은 대여 상세와 같다.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({COMMON_INVALID_INPUT, AUTH_UNAUTHORIZED, RENTAL_NOT_PARTICIPANT, RENTAL_NOT_FOUND,
		RENTAL_INVALID_TRANSITION, RENTAL_CANCEL_NOT_ALLOWED})
	@PatchMapping("/{rentalId}/cancel")
	public ApiResponse<RentalDetailResponse> cancelRental(@LoginMember
	Long memberId, @PathVariable
	Long rentalId,
		@Valid @RequestBody
		RentalCancelRequest request) {
		return ApiResponse.ok(rentalCancelService.cancel(rentalId, memberId, request));
	}

	@Operation(summary = "양측 동의 취소 요청 (REQ-015)", description = "시작일이 지나고 인계 전인 대여 예정(RESERVED)을 취소하려면 상대 동의가 필요하다. "
		+ "당사자 한쪽이 요청하고, 상대가 응답하지 않으면 만료된다(policies.md cancel-agreement.expire-hours). "
		+ "진행 중인 요청이 있으면 409, 거절·만료된 뒤에는 다시 요청할 수 있다. 시작일 전이면 단독 취소를 쓴다. "
		+ "reason=OTHER면 reasonText 필수(cancel.reason-text-max).")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({COMMON_INVALID_INPUT, AUTH_UNAUTHORIZED, RENTAL_NOT_PARTICIPANT, RENTAL_NOT_FOUND,
		RENTAL_INVALID_TRANSITION, RENTAL_CANCEL_AGREEMENT_TOO_EARLY, RENTAL_CANCEL_AGREEMENT_IN_PROGRESS})
	@PostMapping("/{rentalId}/cancel-agreements")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<CancelAgreementResponse> requestCancelAgreement(@LoginMember
	Long memberId, @PathVariable
	Long rentalId,
		@Valid @RequestBody
		RentalCancelRequest request) {
		return ApiResponse.ok(rentalCancelService.requestAgreement(rentalId, memberId, request));
	}

	@Operation(summary = "양측 동의 취소 응답 (REQ-015)", description = "요청하지 않은 당사자가 수락하거나 거절한다. "
		+ "ACCEPT면 대여가 CANCELED(cancelType=MUTUAL, 수수료 없음, 사유는 요청의 사유)가 되고, REJECT면 요청만 종료된다. "
		+ "이미 응답했거나 만료된 요청은 409. 응답은 대여 상세와 같다.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({COMMON_INVALID_INPUT, AUTH_UNAUTHORIZED, RENTAL_NOT_PARTICIPANT,
		RENTAL_CANCEL_AGREEMENT_SELF_RESPONSE, RENTAL_NOT_FOUND, RENTAL_CANCEL_AGREEMENT_NOT_FOUND,
		RENTAL_CANCEL_AGREEMENT_CLOSED, RENTAL_INVALID_TRANSITION})
	@PatchMapping("/{rentalId}/cancel-agreements/{agreementId}")
	public ApiResponse<RentalDetailResponse> respondCancelAgreement(@LoginMember
	Long memberId, @PathVariable
	Long rentalId, @PathVariable
	Long agreementId,
		@Valid @RequestBody
		CancelAgreementRespondRequest request) {
		return ApiResponse.ok(rentalCancelService.respondAgreement(rentalId, agreementId, memberId, request));
	}

	@Operation(summary = "인계 확인 (REQ-017)", description = "제공자가 인계 사진과 메모를 올리면 대여 예정(RESERVED)이 대여 중(RENTING)이 된다. "
		+ "대여 시작일 당일부터 할 수 있고, 기록은 수정할 수 없다. "
		+ "multipart: request part(Content-Type application/json, 생략 가능) { memo } + images 파일들. "
		+ "사진 장수 policies.md inspection.images.min~max, 장당 inspection.images.max-file-size, jpg/png. 메모 최대 글자 수 inspection.handover-memo-max. "
		+ "검사 순서: request part 검증 400 COMMON_INVALID_INPUT(part의 Content-Type이 json이 아니면 415 COMMON_UNSUPPORTED_MEDIA_TYPE) → "
		+ "404 RENTAL_NOT_FOUND → 403 RENTAL_NOT_PARTICIPANT → 403 RENTAL_OWNER_ONLY → 409 RENTAL_INVALID_TRANSITION → "
		+ "409 RENTAL_HANDOVER_TOO_EARLY → 사진 400(IMAGE_COUNT_INVALID, IMAGE_INVALID_TYPE, IMAGE_SIZE_EXCEEDED). "
		+ "다른 요청이 먼저 대여를 바꿨으면 409 COMMON_CONCURRENT_UPDATE 또는 RENTAL_INVALID_TRANSITION. 응답은 대여 상세와 같다.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({COMMON_INVALID_INPUT, COMMON_UNSUPPORTED_MEDIA_TYPE, AUTH_UNAUTHORIZED, RENTAL_NOT_FOUND,
		RENTAL_NOT_PARTICIPANT, RENTAL_OWNER_ONLY, RENTAL_INVALID_TRANSITION, RENTAL_HANDOVER_TOO_EARLY,
		IMAGE_COUNT_INVALID, IMAGE_INVALID_TYPE, IMAGE_SIZE_EXCEEDED, COMMON_CONCURRENT_UPDATE})
	@PatchMapping(value = "/{rentalId}/handover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ApiResponse<RentalDetailResponse> handover(@LoginMember
	Long memberId, @PathVariable
	Long rentalId,
		@Valid @RequestPart(value = "request", required = false)
		RentalHandoverRequest request,
		@RequestPart(value = "images", required = false)
		List<MultipartFile> images) {
		return ApiResponse.ok(rentalInspectionService.handover(rentalId, memberId, request, images));
	}

	@Operation(summary = "반납 요청 (REQ-018)", description = "대여자가 반납 사진·체크리스트·메모를 올리면 대여 중(RENTING) 또는 연체(OVERDUE)가 반납 완료(RETURNED, 상태 확인 대기)가 된다. "
		+ "대여자가 요청 없이 물건을 돌려줬으면 제공자가 같은 API로 대리 접수한다(기록 작성자 = 제공자). 기록은 수정할 수 없다. "
		+ "multipart: request part(Content-Type application/json, 필수) { checklist, note } + images 파일들. "
		+ "checklist는 ReturnChecklistItem 5개 키를 모두 true/false로 보낸다(빠지면 400, 모르는 키도 400). "
		+ "false 항목이 있으면 note 필수, 최대 글자 수 policies.md return.note-max. 사진 장수 inspection.images.min~max, 장당 inspection.images.max-file-size, jpg/png. "
		+ "검사 순서: request part 검증 400 COMMON_INVALID_INPUT(part의 Content-Type이 json이 아니면 415 COMMON_UNSUPPORTED_MEDIA_TYPE) → "
		+ "404 RENTAL_NOT_FOUND → 403 RENTAL_NOT_PARTICIPANT → 409 RENTAL_INVALID_TRANSITION → "
		+ "사진 400(IMAGE_COUNT_INVALID, IMAGE_INVALID_TYPE, IMAGE_SIZE_EXCEEDED). "
		+ "다른 요청이 먼저 대여를 바꿨으면 409 COMMON_CONCURRENT_UPDATE 또는 RENTAL_INVALID_TRANSITION. 응답은 대여 상세와 같다.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({COMMON_INVALID_INPUT, COMMON_UNSUPPORTED_MEDIA_TYPE, AUTH_UNAUTHORIZED, RENTAL_NOT_FOUND,
		RENTAL_NOT_PARTICIPANT, RENTAL_INVALID_TRANSITION, IMAGE_COUNT_INVALID, IMAGE_INVALID_TYPE, IMAGE_SIZE_EXCEEDED,
		COMMON_CONCURRENT_UPDATE})
	@PostMapping(value = "/{rentalId}/return-request", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ApiResponse<RentalDetailResponse> requestReturn(@LoginMember
	Long memberId, @PathVariable
	Long rentalId,
		@Valid @RequestPart("request")
		RentalReturnRequest request,
		@RequestPart(value = "images", required = false)
		List<MultipartFile> images) {
		return ApiResponse.ok(rentalInspectionService.requestReturn(rentalId, memberId, request, images));
	}

	@Operation(summary = "제공자 상태 확인 (REQ-018)", description = "제공자가 반납된 물품을 정상(NORMAL) 또는 이상(ABNORMAL)으로 판정한다. 판정은 한 번만 할 수 있다. "
		+ "NORMAL은 보증금을 반환하고 거래 완료(COMPLETED)가 된다. 반환이 실패했거나 PG 결과를 알 수 없으면(타임아웃) 200에 status RETURNED로 답하고 재시도 배치가 완료한다. "
		+ "ABNORMAL은 동결된 보증금이 있으면 RETURNED로 남고(차감은 REQ-049), 없으면 판정만 기록하고 COMPLETED가 된다. "
		+ "반납 요청 후 policies.md return.auto-confirm-days가 지나도록 판정하지 않으면 자동으로 정상 처리된다. "
		+ "multipart: request part(Content-Type application/json, 필수) { result, resultNote } + images 파일들. "
		+ "ABNORMAL이면 resultNote(최대 inspection.abnormal-note-max자)와 사진(inspection.images.min~max장) 필수, NORMAL이면 resultNote 선택이고 사진을 보내면 400. "
		+ "검사 순서: request part 검증 400 COMMON_INVALID_INPUT(part의 Content-Type이 json이 아니면 415 COMMON_UNSUPPORTED_MEDIA_TYPE) → "
		+ "404 RENTAL_NOT_FOUND → 403 RENTAL_NOT_PARTICIPANT → 403 RENTAL_OWNER_ONLY → 409 RENTAL_INVALID_TRANSITION(반납 완료 아님·이미 판정) → "
		+ "사진 400(IMAGE_COUNT_INVALID, IMAGE_INVALID_TYPE, IMAGE_SIZE_EXCEEDED). "
		+ "다른 요청(자동 확인 포함)이 먼저 판정했으면 409 RENTAL_INVALID_TRANSITION. 응답은 대여 상세와 같다.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({COMMON_INVALID_INPUT, COMMON_UNSUPPORTED_MEDIA_TYPE, AUTH_UNAUTHORIZED, RENTAL_NOT_FOUND,
		RENTAL_NOT_PARTICIPANT, RENTAL_OWNER_ONLY, RENTAL_INVALID_TRANSITION, IMAGE_COUNT_INVALID, IMAGE_INVALID_TYPE,
		IMAGE_SIZE_EXCEEDED})
	@PatchMapping(value = "/{rentalId}/return", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ApiResponse<RentalDetailResponse> confirmReturn(@LoginMember
	Long memberId, @PathVariable
	Long rentalId,
		@Valid @RequestPart("request")
		RentalReturnConfirmRequest request,
		@RequestPart(value = "images", required = false)
		List<MultipartFile> images) {
		return ApiResponse.ok(rentalInspectionService.confirmReturn(rentalId, memberId, request, images));
	}
}
