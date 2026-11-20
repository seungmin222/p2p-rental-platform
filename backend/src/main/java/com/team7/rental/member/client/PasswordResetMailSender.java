package com.team7.rental.member.client;

/** 비밀번호 재설정 메일 (REQ-036). 구현은 SMTP({@link SmtpPasswordResetMailSender}), 테스트는 Fake. 실패하면 예외를 던진다 */
public interface PasswordResetMailSender {

	/** 재설정 링크 메일 */
	void sendResetLink(String to, String resetUrl, long validMinutes);

	/** 소셜 전용 계정(비밀번호 없음) 안내 메일: 카카오 로그인으로 가입한 계정이라 재설정할 비밀번호가 없다 */
	void sendSocialAccountNotice(String to);
}
