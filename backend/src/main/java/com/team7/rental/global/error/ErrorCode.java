package com.team7.rental.global.error;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 모든 에러 코드를 한 곳에서 관리한다. 이름은 {DOMAIN}_{REASON} (docs/architecture.md §4).
 * 도메인 코드는 각 도메인 작업에서 아래에 도메인별로 묶어 추가한다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

	// 공통
	COMMON_INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
	COMMON_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 대상을 찾을 수 없습니다."),
	COMMON_METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),
	COMMON_UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 형식입니다."),
	COMMON_CONCURRENT_UPDATE(HttpStatus.CONFLICT, "다른 요청이 먼저 처리되었습니다. 새로고침 후 다시 시도해 주세요."),
	COMMON_INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."),

	// 인증·인가
	AUTH_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
	AUTH_FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
	AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
	/** 재발급 실패: RT 없음·서명 오류·만료, family 키 없음(로그아웃·강제 무효화), 이미 교체된 RT 재사용 (REQ-003) */
	AUTH_REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "로그인이 만료되었습니다. 다시 로그인해 주세요."),

	// 이미지 업로드 (global/storage)
	IMAGE_INVALID_TYPE(HttpStatus.BAD_REQUEST, "jpg 또는 png 이미지만 업로드할 수 있습니다."),
	IMAGE_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "이미지 용량이 너무 큽니다."),
	IMAGE_COUNT_INVALID(HttpStatus.BAD_REQUEST, "이미지 장수가 올바르지 않습니다."),

	// 회원·동네
	MEMBER_EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
	MEMBER_NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
	MEMBER_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다."),
	REGION_NOT_VERIFIED(HttpStatus.FORBIDDEN, "대표 동네 인증이 필요합니다."),
	REGION_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "더 이상 동네를 추가할 수 없습니다. 기존 동네를 삭제하세요."),
	REGION_ALREADY_ADDED(HttpStatus.CONFLICT, "이미 추가한 동네입니다."),
	REGION_VERIFY_MISMATCH(HttpStatus.BAD_REQUEST, "현재 위치가 선택한 동네와 다릅니다."),

	// 물품
	ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "물품을 찾을 수 없습니다."),
	ITEM_NOT_OWNER(HttpStatus.FORBIDDEN, "물품 등록자만 처리할 수 있습니다."),
	/** details에 충돌 기간을 한 건씩 담는다: { field: "availablePeriods", reason: "2026-11-03 ~ 2026-11-05" } (REQ-007) */
	ITEM_PERIOD_CONFLICT(HttpStatus.CONFLICT, "예약이 있는 날짜는 대여 가능 기간에서 뺄 수 없습니다."),
	/** details에 해당 대여 건을 한 건씩 담는다: { field: "rentals", reason: "..." } (REQ-008) */
	ITEM_HAS_ACTIVE_RENTAL(HttpStatus.CONFLICT, "진행 중인 대여가 있어 삭제할 수 없습니다."),

	// 대여
	RENTAL_NOT_FOUND(HttpStatus.NOT_FOUND, "대여 내역을 찾을 수 없습니다."),
	RENTAL_NOT_PARTICIPANT(HttpStatus.FORBIDDEN, "대여 당사자만 이용할 수 있습니다."),
	RENTAL_OWNER_ONLY(HttpStatus.FORBIDDEN, "물품 제공자만 처리할 수 있습니다."),
	RENTAL_INVALID_PERIOD(HttpStatus.BAD_REQUEST, "대여 기간이 올바르지 않습니다."),
	RENTAL_OWN_ITEM(HttpStatus.BAD_REQUEST, "본인 물품은 예약할 수 없습니다."),
	RENTAL_OUT_OF_AVAILABLE_PERIOD(HttpStatus.BAD_REQUEST, "대여 가능 기간을 벗어났습니다."),
	RENTAL_PERIOD_CONFLICT(HttpStatus.CONFLICT, "이미 예약된 기간입니다."),
	RENTAL_LOCK_TIMEOUT(HttpStatus.CONFLICT, "예약 요청이 몰려 처리하지 못했습니다. 잠시 후 다시 시도해 주세요."),
	RENTAL_INVALID_TRANSITION(HttpStatus.CONFLICT, "현재 대여 상태에서는 처리할 수 없는 요청입니다."),
	RENTAL_CANCEL_NOT_ALLOWED(HttpStatus.CONFLICT, "대여 시작일이 지나 단독으로 취소할 수 없습니다. 상대방에게 취소 동의를 요청해 주세요."),
	RENTAL_CANCEL_AGREEMENT_TOO_EARLY(HttpStatus.CONFLICT, "대여 시작일 전에는 단독으로 취소할 수 있습니다."),
	RENTAL_CANCEL_AGREEMENT_IN_PROGRESS(HttpStatus.CONFLICT, "이미 진행 중인 취소 동의 요청이 있습니다."),
	RENTAL_CANCEL_AGREEMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "취소 동의 요청을 찾을 수 없습니다."),
	RENTAL_CANCEL_AGREEMENT_SELF_RESPONSE(HttpStatus.FORBIDDEN, "본인이 보낸 취소 동의 요청에는 응답할 수 없습니다."),
	RENTAL_CANCEL_AGREEMENT_CLOSED(HttpStatus.CONFLICT, "이미 응답했거나 만료된 취소 동의 요청입니다."),
	RENTAL_HANDOVER_TOO_EARLY(HttpStatus.CONFLICT, "대여 시작일부터 인계를 확인할 수 있습니다."),

	// 결제
	PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "결제 내역을 찾을 수 없습니다."),
	PAYMENT_INVALID_STATUS(HttpStatus.CONFLICT, "현재 결제 상태에서는 처리할 수 없는 요청입니다."),
	PAYMENT_RENTER_ONLY(HttpStatus.FORBIDDEN, "대여자만 결제할 수 있습니다."),
	PAYMENT_EXPIRED(HttpStatus.CONFLICT, "결제 가능 시간이 지났습니다. 다시 예약해 주세요."),
	PAYMENT_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "결제 금액이 일치하지 않아 예약이 취소되었습니다."),
	PAYMENT_APPROVAL_FAILED(HttpStatus.BAD_REQUEST, "결제 승인에 실패했습니다. 다시 결제해 주세요."),
	/** PG 응답을 받지 못했다. 같은 요청을 다시 보내도 중복 승인되지 않는다 */
	PAYMENT_RESULT_UNKNOWN(HttpStatus.SERVICE_UNAVAILABLE, "결제 결과를 확인하고 있습니다. 잠시 후 다시 시도해 주세요."),
	/** 승인 직후 대여가 이미 만료·취소된 경합. 전액 취소를 진행한다 */
	PAYMENT_CANCELED_AFTER_EXPIRY(HttpStatus.CONFLICT, "결제 처리 중 예약이 만료되어 결제 금액을 전액 환불합니다.");

	private final HttpStatus status;
	private final String message;

	public String getCode() {
		return name();
	}
}
