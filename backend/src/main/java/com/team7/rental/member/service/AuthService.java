package com.team7.rental.member.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.member.dto.AuthLoginRequest;
import com.team7.rental.member.dto.AuthTokenResponse;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * 로그인·로그아웃·재발급 (REQ-002·003). RT는 HttpOnly 쿠키(policies.md auth.refresh-cookie)로만 주고받는다.
 * 미구현: 응답 형태만 확정. 로직은 feat/member-auth에서 채운다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AuthService {

	/** 새 토큰 묶음(family)을 만들고 RT 쿠키를 응답에 싣는다 */
	public AuthTokenResponse login(AuthLoginRequest request, HttpServletResponse response) {
		throw new UnsupportedOperationException("미구현: feat/member-auth");
	}

	/** RT 쿠키의 family만 지우고 쿠키를 만료시킨다. RT가 없거나 무효여도 성공으로 응답한다 */
	public void logout(String refreshToken, HttpServletResponse response) {
		throw new UnsupportedOperationException("미구현: feat/member-auth");
	}

	/** RT Rotation. 이미 교체된 RT를 재사용하면 그 family만 지운다 */
	public AuthTokenResponse reissue(String refreshToken, HttpServletResponse response) {
		throw new UnsupportedOperationException("미구현: feat/member-auth");
	}
}
