package com.team7.rental.member.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.team7.rental.member.dto.MemberPasswordChangeRequest;
import com.team7.rental.member.dto.MemberProfileUpdateRequest;
import com.team7.rental.member.dto.MemberResponse;
import com.team7.rental.member.dto.MemberSignupRequest;
import com.team7.rental.member.dto.MemberSignupResponse;

import lombok.RequiredArgsConstructor;

/**
 * 회원 쓰기: 가입(REQ-001), 프로필·비밀번호 변경(REQ-005). 미구현: 응답 형태만 확정.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MemberService {

	@Transactional
	public MemberSignupResponse signup(MemberSignupRequest request) {
		throw new UnsupportedOperationException("미구현: feat/member-auth");
	}

	/**
	 * @param request 생략 가능(닉네임 변경 없음)
	 * @param profileImage 생략 가능(이미지 변경 없음). jpg/png, policies.md profile.image
	 */
	@Transactional
	public MemberResponse updateProfile(Long memberId, MemberProfileUpdateRequest request,
		MultipartFile profileImage) {
		throw new UnsupportedOperationException("미구현: feat/member-region");
	}

	/** 현재 기기를 뺀 다른 기기 RT family를 지운다 */
	@Transactional
	public void changePassword(Long memberId, MemberPasswordChangeRequest request) {
		throw new UnsupportedOperationException("미구현: feat/member-region");
	}
}
