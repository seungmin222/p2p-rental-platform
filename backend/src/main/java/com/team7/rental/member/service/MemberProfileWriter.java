package com.team7.rental.member.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.member.entity.MemberStatus;
import com.team7.rental.member.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MemberProfileWriter {
	private final MemberRepository memberRepository;

	/** 호출자에게 이전 URL을 반환한다. 메서드가 반환될 때 DB 커밋도 끝난다. */
	@Transactional
	public String update(Long memberId, String nickname, String newImageUrl) {
		var member = memberRepository.findForUpdate(memberId)
			.filter(value -> value.getStatus() != MemberStatus.WITHDRAWN)
			.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED));
		if (nickname != null && memberRepository.existsByNicknameAndIdNot(nickname, memberId)) {
			throw new BusinessException(ErrorCode.MEMBER_NICKNAME_DUPLICATED);
		}
		String previous = member.getProfileImageUrl();
		member.updateProfile(nickname, newImageUrl);
		memberRepository.flush();
		return previous;
	}
}
