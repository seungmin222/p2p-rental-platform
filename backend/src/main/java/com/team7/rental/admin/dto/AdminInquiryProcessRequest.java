package com.team7.rental.admin.dto;

import com.team7.rental.admin.entity.InquiryStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 관리자 문의 처리 (REQ-057). 답변 글자 수·공백 검사와 RESOLVED의 답변 필수는 서비스가 policies.md inquiry.answer-max로 한다.
 */
public record AdminInquiryProcessRequest(
	@Schema(description = "바꿀 처리 상태: IN_PROGRESS 처리 중, RESOLVED 처리 완료(되돌릴 수 없다)") @NotNull
	InquiryStatus status,

	@Schema(description = "답변. 주면 기존 답변을 덮어쓴다. RESOLVED에는 필수(이미 남긴 답변이 있으면 생략 가능). "
		+ "공백만은 불가, policies.md inquiry.answer-max(1,000자) 이하", example = "상태 확인 사진을 검토해 정산 보류를 해제했습니다.")
	String answer) {
}
