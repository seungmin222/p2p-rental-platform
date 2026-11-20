package com.team7.rental.member.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.member.client.PasswordResetMailDispatcher;
import com.team7.rental.member.client.PasswordResetMailSender;
import com.team7.rental.member.dto.PasswordResetConfirmRequest;
import com.team7.rental.member.entity.Member;
import com.team7.rental.member.entity.MemberStatus;
import com.team7.rental.member.repository.MemberRepository;
import com.team7.rental.member.repository.PasswordResetTokenRepository;
import com.team7.rental.member.repository.RefreshTokenRepository;

/**
 * 비밀번호 재설정 (REQ-036). 메일 요청은 가입 여부·요청 제한·발송 실패와 상관없이 같은 응답이고, 메일은 백그라운드에서 보낸다. 토큰은 1회용(Redis GETDEL)이고
 * 재설정하면 모든 기기에서 로그아웃된다.
 */
@Service
@Transactional(readOnly = true)
public class PasswordResetService {

	/** 토큰 바이트 수. Base64URL로 43자 */
	private static final int TOKEN_BYTES = 32;
	private static final SecureRandom RANDOM = new SecureRandom();

	private final MemberRepository memberRepository;
	private final PasswordResetTokenRepository tokenRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final PasswordResetMailSender mailSender;
	private final PasswordResetMailDispatcher mailDispatcher;
	private final PolicyProperties policyProperties;
	private final String frontendBaseUrl;

	public PasswordResetService(MemberRepository memberRepository, PasswordResetTokenRepository tokenRepository,
		RefreshTokenRepository refreshTokenRepository, PasswordEncoder passwordEncoder,
		PasswordResetMailSender mailSender, PasswordResetMailDispatcher mailDispatcher,
		PolicyProperties policyProperties, @Value("${app.frontend.base-url}")
		String frontendBaseUrl) {
		this.memberRepository = memberRepository;
		this.tokenRepository = tokenRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.passwordEncoder = passwordEncoder;
		this.mailSender = mailSender;
		this.mailDispatcher = mailDispatcher;
		this.policyProperties = policyProperties;
		this.frontendBaseUrl = frontendBaseUrl;
	}

	/**
	 * 재설정 메일 요청. 같은 이메일은 password-reset.rate-limit에 한 번만 처리한다(가입하지 않은 이메일도 같이 센다). 활성 회원이면 토큰을 만들어 링크 메일을,
	 * 소셜 전용 계정(비밀번호 없음)이면 카카오 로그인 안내 메일을 보낸다. 탈퇴 회원·없는 이메일은 아무것도 보내지 않는다.
	 */
	public void requestReset(String email) {
		String normalized = email.strip().toLowerCase(Locale.ROOT);
		PolicyProperties.PasswordReset policy = policyProperties.passwordReset();
		if (!tokenRepository.acquireRateLimit(normalized, policy.rateLimit())) {
			return;
		}
		Member member = memberRepository.findByEmail(normalized)
			.filter(value -> value.getStatus() != MemberStatus.WITHDRAWN)
			.orElse(null);
		if (member == null) {
			return;
		}
		String to = member.getEmail();
		if (member.getPassword() == null) {
			mailDispatcher.dispatch(() -> mailSender.sendSocialAccountNotice(to));
			return;
		}
		String token = newToken();
		Duration ttl = policy.tokenTtl();
		tokenRepository.save(token, member.getId(), ttl);
		String resetUrl = frontendBaseUrl + "/password-reset?token=" + token;
		mailDispatcher.dispatch(() -> mailSender.sendResetLink(to, resetUrl, ttl.toMinutes()));
	}

	/**
	 * 재설정. 토큰을 먼저 지워(1회용) DB 저장이 실패해도 같은 토큰은 다시 쓸 수 없다(다시 요청한다).
	 *
	 * @throws BusinessException AUTH_RESET_TOKEN_INVALID 토큰이 없거나(만료·재사용·위조) 회원이 탈퇴했거나 비밀번호가 없는 소셜 전용 계정일 때
	 */
	@Transactional
	public void confirm(PasswordResetConfirmRequest request) {
		Long memberId = tokenRepository.consume(request.token())
			.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_RESET_TOKEN_INVALID));
		Member member = memberRepository.findForUpdate(memberId)
			.filter(value -> value.getStatus() != MemberStatus.WITHDRAWN && value.getPassword() != null)
			.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_RESET_TOKEN_INVALID));
		member.changePassword(passwordEncoder.encode(request.newPassword()));
		memberRepository.flush();
		// 비밀번호 변경(REQ-005)과 같이 Redis 실패 시 DB도 롤백한다
		refreshTokenRepository.deleteAllFamilies(memberId);
	}

	private static String newToken() {
		byte[] bytes = new byte[TOKEN_BYTES];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
