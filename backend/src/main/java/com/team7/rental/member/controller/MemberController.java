package com.team7.rental.member.controller;

import static com.team7.rental.global.error.ErrorCode.*;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.team7.rental.global.config.OpenApiConfig;
import com.team7.rental.global.error.ApiErrorCodes;
import com.team7.rental.global.response.ApiResponse;
import com.team7.rental.global.security.LoginMember;
import com.team7.rental.member.dto.MemberPasswordChangeRequest;
import com.team7.rental.member.dto.MemberProfileResponse;
import com.team7.rental.member.dto.MemberProfileUpdateRequest;
import com.team7.rental.member.dto.MemberResponse;
import com.team7.rental.member.dto.MemberSignupRequest;
import com.team7.rental.member.dto.MemberSignupResponse;
import com.team7.rental.member.service.MemberProfileQueryService;
import com.team7.rental.member.service.MemberQueryService;
import com.team7.rental.member.service.MemberService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Member", description = "회원·인증·동네")
@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

	private final MemberService memberService;
	private final MemberQueryService memberQueryService;
	private final MemberProfileQueryService memberProfileQueryService;

	@Operation(summary = "회원가입 (REQ-001)", description = "글자 수 규칙은 policies.md member.email.max·member.password·member.nickname, "
		+ "비밀번호는 공백 없는 ASCII 출력 문자만, 영문·숫자·기호를 각각 1개 이상 포함한다. 형식 오류는 400 + details(필드별), 중복은 409. 가입 후 로그인 화면으로 이동한다.")
	@ApiErrorCodes({COMMON_INVALID_INPUT, MEMBER_EMAIL_DUPLICATED, MEMBER_NICKNAME_DUPLICATED})
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<MemberSignupResponse> signup(@Valid @RequestBody
	MemberSignupRequest request) {
		return ApiResponse.ok(memberService.signup(request));
	}

	@Operation(summary = "내 정보 조회 (REQ-005)", description = "대표 동네 요약(인증 여부·만료 시각)을 포함한다.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({AUTH_UNAUTHORIZED})
	@GetMapping("/me")
	public ApiResponse<MemberResponse> getMe(@LoginMember
	Long memberId) {
		return ApiResponse.ok(memberQueryService.getMe(memberId));
	}

	@Operation(summary = "사용자 프로필 (REQ-021)", description = "비회원도 볼 수 있다. 이메일 등 개인정보는 주지 않는다. "
		+ "거래 횟수 = 대여자·제공자로 거래 완료(COMPLETED)된 대여 수. 받은 후기는 /api/members/{memberId}/reviews, "
		+ "등록 물품은 /api/members/{memberId}/items로 따로 조회한다. 탈퇴 회원은 200에 isWithdrawn=true이고 나머지 필드는 null(\"탈퇴한 사용자\", 상세 비공개).")
	@ApiErrorCodes({COMMON_INVALID_INPUT, MEMBER_NOT_FOUND})
	@GetMapping("/{memberId}")
	public ApiResponse<MemberProfileResponse> getProfile(@LoginMember(required = false)
	Long viewerId, @PathVariable
	Long memberId) {
		return ApiResponse.ok(memberProfileQueryService.getProfile(memberId, viewerId));
	}

	@Operation(summary = "프로필 수정 (REQ-005)", description = "보낸 항목만 바꾼다. "
		+ "multipart: request part(Content-Type application/json, 생략 가능) { nickname } + profileImage 파일(생략 가능). "
		+ "이미지는 jpg/png, 용량 policies.md profile.image. 닉네임 글자 수 member.nickname. "
		+ "검사 순서: request part 검증 400 COMMON_INVALID_INPUT(part의 Content-Type이 json이 아니면 415) → 409 MEMBER_NICKNAME_DUPLICATED → "
		+ "이미지 400(IMAGE_INVALID_TYPE, IMAGE_SIZE_EXCEEDED). 응답은 내 정보와 같다.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({COMMON_INVALID_INPUT, COMMON_UNSUPPORTED_MEDIA_TYPE, AUTH_UNAUTHORIZED, MEMBER_NICKNAME_DUPLICATED,
		IMAGE_INVALID_TYPE, IMAGE_SIZE_EXCEEDED})
	@PatchMapping(value = "/me", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ApiResponse<MemberResponse> updateProfile(@LoginMember
	Long memberId,
		@Valid @RequestPart(value = "request", required = false)
		MemberProfileUpdateRequest request,
		@RequestPart(value = "profileImage", required = false)
		MultipartFile profileImage) {
		return ApiResponse.ok(memberService.updateProfile(memberId, request, profileImage));
	}

	@Operation(summary = "비밀번호 변경 (REQ-005)", description = "현재 비밀번호가 틀리면 400 MEMBER_PASSWORD_MISMATCH. "
		+ "새 비밀번호 규칙은 가입과 같다. 변경하면 현재 기기를 뺀 다른 기기는 로그아웃된다.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({COMMON_INVALID_INPUT, AUTH_UNAUTHORIZED, MEMBER_PASSWORD_MISMATCH})
	@PatchMapping("/me/password")
	public ApiResponse<Void> changePassword(@LoginMember
	Long memberId,
		@Valid @RequestBody
		MemberPasswordChangeRequest request) {
		memberService.changePassword(memberId, request);
		return ApiResponse.ok();
	}
}
