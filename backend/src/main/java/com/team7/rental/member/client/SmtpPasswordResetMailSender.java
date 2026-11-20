package com.team7.rental.member.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Spring Mail(SMTP)로 보내는 재설정 메일. 로컬은 Mailpit(localhost:1025, 웹 UI 8025) */
@Component
public class SmtpPasswordResetMailSender implements PasswordResetMailSender {

	private static final String SUBJECT = "[동네 대여] 비밀번호 재설정 안내";

	private final JavaMailSender mailSender;
	private final String from;

	public SmtpPasswordResetMailSender(JavaMailSender mailSender, @Value("${app.mail.from}")
	String from) {
		this.mailSender = mailSender;
		this.from = from;
	}

	@Override
	public void sendResetLink(String to, String resetUrl, long validMinutes) {
		send(to, """
			비밀번호 재설정을 요청하셨습니다.
			아래 링크에서 새 비밀번호를 설정해 주세요. 링크는 %d분 동안 한 번만 쓸 수 있습니다.

			%s

			직접 요청하지 않았다면 이 메일을 무시해 주세요. 비밀번호는 바뀌지 않습니다.
			""".formatted(validMinutes, resetUrl));
	}

	@Override
	public void sendSocialAccountNotice(String to) {
		send(to, """
			비밀번호 재설정을 요청하셨습니다.
			이 이메일은 카카오 로그인으로 가입한 계정이라 재설정할 비밀번호가 없습니다.
			로그인 화면에서 카카오 로그인을 이용해 주세요.

			직접 요청하지 않았다면 이 메일을 무시해 주세요.
			""");
	}

	private void send(String to, String text) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(to);
		message.setSubject(SUBJECT);
		message.setText(text);
		mailSender.send(message);
	}
}
