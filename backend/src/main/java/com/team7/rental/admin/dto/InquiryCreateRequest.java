package com.team7.rental.admin.dto;

import com.team7.rental.admin.entity.InquiryType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 문의·분쟁 접수 (REQ-063). 내용 최대 글자 수는 서비스가 policies.md inquiry.content-max로 검사한다.
 */
public record InquiryCreateRequest(
	@Schema(description = "문의 종류: GENERAL 일반 문의, DISPUTE 분쟁(rentalId 필수, 접수 시 그 대여의 정산 보류)") @NotNull
	InquiryType type,

	@Schema(description = "연결할 대여 ID. 분쟁이면 필수, 일반 문의는 선택. 본인이 대여자·제공자인 대여만", example = "10")
	Long rentalId,

	@Schema(description = "문의 내용. 공백만은 불가, policies.md inquiry.content-max(1,000자) 이하", example = "반납 후 상태 확인 결과에 이의가 있습니다.") @NotBlank
	String content) {
}
