package com.team7.rental.support;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Primary;

import com.team7.rental.member.client.PasswordResetMailSender;

/**
 * SMTP 대신 쓰는 재설정 메일 발송 (REQ-036). 보낸 메일을 기록하고, {@link #failNext}면 다음 한 번은 예외를 던진다. 테스트는 app.mail.async=false라 요청
 * 스레드에서 바로 불린다. 각 테스트가 끝나면 {@link IntegrationTestSupport}가 비운다.
 */
@Primary
@TestComponent
public class FakePasswordResetMailSender implements PasswordResetMailSender {

	public enum Kind {
		RESET_LINK, SOCIAL_NOTICE
	}

	/** @param resetUrl 안내 메일이면 null */
	public record Mail(Kind kind, String to, String resetUrl, long validMinutes) {
	}

	private final List<Mail> sent = new ArrayList<>();
	private boolean failNext;

	@Override
	public synchronized void sendResetLink(String to, String resetUrl, long validMinutes) {
		failIfRequested();
		sent.add(new Mail(Kind.RESET_LINK, to, resetUrl, validMinutes));
	}

	@Override
	public synchronized void sendSocialAccountNotice(String to) {
		failIfRequested();
		sent.add(new Mail(Kind.SOCIAL_NOTICE, to, null, 0));
	}

	public synchronized List<Mail> sent() {
		return List.copyOf(sent);
	}

	public synchronized void failNext() {
		failNext = true;
	}

	public synchronized void reset() {
		sent.clear();
		failNext = false;
	}

	private void failIfRequested() {
		if (failNext) {
			failNext = false;
			throw new IllegalStateException("메일 서버 장애(테스트)");
		}
	}
}
