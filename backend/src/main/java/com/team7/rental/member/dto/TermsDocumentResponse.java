package com.team7.rental.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 약관·정책 문서 (REQ-037, SCR-15) */
public record TermsDocumentResponse(
	@Schema(description = "문서 종류 (/terms/:type)")
	TermsDocumentType type,

	@Schema(description = "제목", example = "이용약관")
	String title,

	@Schema(description = "현재 버전. 동의 기록(terms_agreements)에 이 값을 남긴다", example = "2026-11-20")
	String version,

	@Schema(description = "본문 (마크다운)", example = "# 이용약관\n\n## 제1조 (목적)\n...")
	String content) {
}
