package com.team7.rental.member.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team7.rental.global.response.ApiResponse;
import com.team7.rental.member.dto.TermsDocumentResponse;
import com.team7.rental.member.service.TermsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Member", description = "회원·인증·동네")
@RestController
@RequiredArgsConstructor
public class TermsController {

	private final TermsService termsService;

	@Operation(summary = "약관·정책 문서 (REQ-037)", description = "비회원도 볼 수 있다. 이용약관(TERMS_OF_SERVICE), 개인정보처리방침(PRIVACY_POLICY), "
		+ "대여 금지 품목(PROHIBITED_ITEMS, policies.md §2) 세 문서를 이 순서로 준다. 본문은 마크다운이고 version은 현재 버전이다. "
		+ "가입 화면의 이용약관·개인정보 수집·이용 동의는 각각 TERMS_OF_SERVICE·PRIVACY_POLICY 문서를 보여 준다.")
	@GetMapping("/api/terms")
	public ApiResponse<List<TermsDocumentResponse>> getTerms() {
		return ApiResponse.ok(termsService.getDocuments());
	}
}
