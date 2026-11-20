package com.team7.rental.member.entity;

/** 가입 시 필수 동의 항목 (REQ-037). 셋 다 필수다. 버전은 application.yml app.terms.* */
public enum TermsAgreementType {
	/** 이용약관 동의. 문서 TERMS_OF_SERVICE 버전을 따른다 */
	TERMS_OF_SERVICE,
	/** 개인정보 수집·이용 동의. 문서 PRIVACY_POLICY 버전을 따른다 */
	PRIVACY_COLLECTION,
	/** 만 14세 이상 확인. 문서가 없고 버전은 app.terms.age-over14-version */
	AGE_OVER_14
}
