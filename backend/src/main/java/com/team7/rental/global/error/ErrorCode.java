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
	COMMON_CONCURRENT_UPDATE(HttpStatus.CONFLICT, "다른 요청이 먼저 처리되었습니다. 새로고침 후 다시 시도해 주세요."),
	COMMON_INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."),

	// 인증·인가
	AUTH_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
	AUTH_FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),

	// 이미지 업로드 (global/storage)
	IMAGE_INVALID_TYPE(HttpStatus.BAD_REQUEST, "jpg 또는 png 이미지만 업로드할 수 있습니다."),
	IMAGE_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "이미지 용량이 너무 큽니다."),

	// 회원·동네
	REGION_NOT_VERIFIED(HttpStatus.FORBIDDEN, "대표 동네 인증이 필요합니다."),

	// 물품
	ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "물품을 찾을 수 없습니다."),

	// 대여
	RENTAL_NOT_FOUND(HttpStatus.NOT_FOUND, "대여 내역을 찾을 수 없습니다."),
	RENTAL_NOT_PARTICIPANT(HttpStatus.FORBIDDEN, "대여 당사자만 이용할 수 있습니다."),
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
	RENTAL_HANDOVER_TOO_EARLY(HttpStatus.CONFLICT, "대여 시작일부터 인계를 확인할 수 있습니다.");

	private final HttpStatus status;
	private final String message;

	public String getCode() {
		return name();
	}
}
