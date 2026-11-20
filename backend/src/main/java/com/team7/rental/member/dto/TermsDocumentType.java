package com.team7.rental.member.dto;

/** 약관·정책 문서 (REQ-037, SCR-15 /terms/:type). 본문은 resources/terms/{파일}.md, 버전은 application.yml app.terms.* */
public enum TermsDocumentType {
	TERMS_OF_SERVICE("이용약관", "terms/terms-of-service.md"),
	PRIVACY_POLICY("개인정보처리방침", "terms/privacy-policy.md"),
	/** policies.md §2 대여 금지 품목을 옮긴 안내. 목록을 바꾸면 policies.md와 함께 고친다 */
	PROHIBITED_ITEMS("대여 금지 품목", "terms/prohibited-items.md");

	private final String title;
	private final String resourcePath;

	TermsDocumentType(String title, String resourcePath) {
		this.title = title;
		this.resourcePath = resourcePath;
	}

	public String getTitle() {
		return title;
	}

	public String getResourcePath() {
		return resourcePath;
	}
}
