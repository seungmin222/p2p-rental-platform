package com.team7.rental.member.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import com.team7.rental.member.dto.TermsDocumentType;
import com.team7.rental.member.entity.TermsAgreementType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 약관·정책 문서 버전 (REQ-037). 본문(resources/terms/*.md)을 고치면 해당 버전을 올린다. 동의 대상 문서(이용약관·개인정보처리방침)나 만 14세 확인의 버전이
 * 바뀌면 다음 로그인에서 재동의를 요구한다. 길이 상한은 terms_agreements.terms_version VARCHAR(20).
 */
@Validated
@ConfigurationProperties("app.terms")
public record TermsProperties(
	@NotBlank @Size(max = 20)
	String termsOfServiceVersion,
	@NotBlank @Size(max = 20)
	String privacyPolicyVersion,
	@NotBlank @Size(max = 20)
	String prohibitedItemsVersion,
	@NotBlank @Size(max = 20)
	String ageOver14Version) {

	public String versionOf(TermsDocumentType type) {
		return switch (type) {
			case TERMS_OF_SERVICE -> termsOfServiceVersion;
			case PRIVACY_POLICY -> privacyPolicyVersion;
			case PROHIBITED_ITEMS -> prohibitedItemsVersion;
		};
	}

	/** 동의 항목의 현재 버전. 이용약관·개인정보 수집·이용은 해당 문서 버전을 따른다 */
	public String versionOf(TermsAgreementType type) {
		return switch (type) {
			case TERMS_OF_SERVICE -> termsOfServiceVersion;
			case PRIVACY_COLLECTION -> privacyPolicyVersion;
			case AGE_OVER_14 -> ageOver14Version;
		};
	}
}
