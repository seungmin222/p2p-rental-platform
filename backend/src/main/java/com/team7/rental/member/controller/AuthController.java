package com.team7.rental.member.controller;

import static com.team7.rental.global.error.ErrorCode.*;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team7.rental.global.error.ApiErrorCodes;
import com.team7.rental.global.response.ApiResponse;
import com.team7.rental.member.dto.AuthLoginRequest;
import com.team7.rental.member.dto.AuthTokenResponse;
import com.team7.rental.member.dto.PasswordResetConfirmRequest;
import com.team7.rental.member.dto.PasswordResetRequest;
import com.team7.rental.member.service.AuthService;
import com.team7.rental.member.service.PasswordResetService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 인증 API는 모두 공개(SecurityConfig /api/auth/**). 만료된 AT로도 재발급·로그아웃할 수 있어야 해서 */
@Tag(name = "Member", description = "회원·인증·동네")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	/** Refresh Token 쿠키 이름. 속성은 policies.md auth.refresh-cookie (HttpOnly, SameSite=Lax, Path=/api/auth, local 외 Secure) */
	public static final String REFRESH_TOKEN_COOKIE = "refreshToken";

	private static final String COOKIE_NOTE = "Refresh Token은 응답 바디에 없고 HttpOnly 쿠키 " + REFRESH_TOKEN_COOKIE
		+ "(Path=/api/auth, SameSite=Lax, local 외 Secure, 만료 policies.md auth.refresh-token-ttl)로만 주고받는다. "
		+ "브라우저가 자동으로 보내므로 프론트는 쿠키를 다루지 않는다(같은 출처 + withCredentials). ";

	/** Swagger Set-Cookie 헤더 설명: 새 RT 발급 (로그인·재발급) */
	private static final String SET_COOKIE_ISSUE = "새 Refresh Token. " + REFRESH_TOKEN_COOKIE
		+ "=<JWT>; Path=/api/auth; Max-Age=<policies.md auth.refresh-token-ttl 초>; HttpOnly; SameSite=Lax; Secure(local 제외)";

	/** Swagger Set-Cookie 헤더 설명: RT 쿠키 만료 (로그아웃) */
	private static final String SET_COOKIE_EXPIRE = "Refresh Token 쿠키 삭제. " + REFRESH_TOKEN_COOKIE
		+ "=; Path=/api/auth; Max-Age=0; HttpOnly; SameSite=Lax; Secure(local 제외)";

	private final AuthService authService;
	private final PasswordResetService passwordResetService;

	@Operation(summary = "로그인 (REQ-002)", description = "성공 시 바디에 Access Token, Set-Cookie로 Refresh Token. "
		+ COOKIE_NOTE + "로그인할 때마다 새 토큰 묶음(family)이 생겨 기기마다 따로 유지된다. "
		+ "이메일·비밀번호 중 무엇이 틀렸는지 구분하지 않고, 탈퇴 회원도 같은 401 AUTH_INVALID_CREDENTIALS다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공", headers = @Header(name = HttpHeaders.SET_COOKIE, description = SET_COOKIE_ISSUE, schema = @Schema(type = "string")))
	@ApiErrorCodes({COMMON_INVALID_INPUT, AUTH_INVALID_CREDENTIALS})
	@PostMapping("/login")
	public ApiResponse<AuthTokenResponse> login(@Valid @RequestBody
	AuthLoginRequest request, HttpServletResponse response) {
		return ApiResponse.ok(authService.login(request, response));
	}

	@Operation(summary = "로그아웃 (REQ-002)", description = "Refresh Token 쿠키의 family만 무효화하고 쿠키를 만료시킨다(Set-Cookie Max-Age=0). "
		+ "다른 기기는 로그인 상태가 유지된다. 쿠키가 없거나 이미 무효여도 성공(data null)이고, Access Token은 필요 없다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공", headers = @Header(name = HttpHeaders.SET_COOKIE, description = SET_COOKIE_EXPIRE, schema = @Schema(type = "string")))
	@ApiErrorCodes({})
	@PostMapping("/logout")
	public ApiResponse<Void> logout(
		@Parameter(hidden = true) @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false)
		String refreshToken, HttpServletResponse response) {
		authService.logout(refreshToken, response);
		return ApiResponse.ok();
	}

	@Operation(summary = "토큰 재발급 (REQ-003)", description = "Refresh Token 쿠키로 Access Token과 Refresh Token을 모두 새로 발급한다(Rotation, Set-Cookie로 교체). "
		+ COOKIE_NOTE + "401 AUTH_REFRESH_TOKEN_INVALID: 쿠키 없음, 서명 오류·만료, 로그아웃·강제 무효화된 family, "
		+ "이미 교체된 RT 재사용(탈취로 보고 그 family만 무효화, 다른 기기는 유지). 401이면 프론트는 로그인 화면으로 간다.")
	@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공", headers = @Header(name = HttpHeaders.SET_COOKIE, description = SET_COOKIE_ISSUE, schema = @Schema(type = "string")))
	@ApiErrorCodes({AUTH_REFRESH_TOKEN_INVALID})
	@PostMapping("/reissue")
	public ApiResponse<AuthTokenResponse> reissue(
		@Parameter(hidden = true) @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false)
		String refreshToken, HttpServletResponse response) {
		return ApiResponse.ok(authService.reissue(refreshToken, response));
	}

	@Operation(summary = "비밀번호 재설정 메일 (REQ-036)", description = "가입 여부와 상관없이 항상 성공(data null)이다. 가입한 이메일이면 재설정 링크 메일을 "
		+ "백그라운드에서 보낸다(링크 {프론트 주소}/password-reset?token=..., 유효 시간 policies.md password-reset.token-ttl, 1회용). "
		+ "같은 이메일은 password-reset.rate-limit에 한 번만 보내고 그 안의 재요청도 같은 응답이다. 카카오 로그인 전용 계정은 링크 대신 카카오 로그인 안내 메일을 보낸다. "
		+ "프론트는 \"메일이 오지 않으면 잠시 뒤 다시 요청하세요\"를 안내한다.")
	@ApiErrorCodes({COMMON_INVALID_INPUT})
	@PostMapping("/password-reset")
	public ApiResponse<Void> requestPasswordReset(@Valid @RequestBody
	PasswordResetRequest request) {
		passwordResetService.requestReset(request.email());
		return ApiResponse.ok();
	}

	@Operation(summary = "비밀번호 재설정 (REQ-036)", description = "메일 링크의 token과 새 비밀번호로 재설정한다. 새 비밀번호 규칙은 가입과 같다(policies.md member.password). "
		+ "성공하면 토큰은 사라지고 모든 기기에서 로그아웃된다(모든 Refresh Token family 삭제). "
		+ "토큰이 만료·재사용·위조됐으면 400 AUTH_RESET_TOKEN_INVALID → 프론트는 재설정 메일을 다시 요청하게 안내한다.")
	@ApiErrorCodes({COMMON_INVALID_INPUT, AUTH_RESET_TOKEN_INVALID})
	@PostMapping("/password-reset/confirm")
	public ApiResponse<Void> confirmPasswordReset(@Valid @RequestBody
	PasswordResetConfirmRequest request) {
		passwordResetService.confirm(request);
		return ApiResponse.ok();
	}
}
