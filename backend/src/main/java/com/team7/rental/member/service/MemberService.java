package com.team7.rental.member.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.global.security.AccessTokenClaims;
import com.team7.rental.global.storage.FileStorage;
import com.team7.rental.global.storage.ImageProcessor;
import com.team7.rental.global.storage.StoredFile;
import com.team7.rental.member.dto.MemberPasswordChangeRequest;
import com.team7.rental.member.dto.MemberProfileUpdateRequest;
import com.team7.rental.member.dto.MemberResponse;
import com.team7.rental.member.dto.MemberSignupRequest;
import com.team7.rental.member.dto.MemberSignupResponse;
import com.team7.rental.member.entity.Member;
import com.team7.rental.member.entity.MemberStatus;
import com.team7.rental.member.repository.MemberRepository;
import com.team7.rental.member.repository.RefreshTokenRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MemberService {
	private final MemberRepository memberRepository;
	private final PasswordEncoder passwordEncoder;
	private final PolicyProperties policyProperties;
	private final MemberQueryService memberQueryService;
	private final MemberProfileWriter memberProfileWriter;
	private final RefreshTokenRepository refreshTokenRepository;
	private final ImageProcessor imageProcessor;
	private final FileStorage fileStorage;

	@Value("${app.policy.profile.image-max-size}")
	private String profileImageMaxSize;

	@Transactional
	public MemberSignupResponse signup(MemberSignupRequest request) {
		if (memberRepository.existsByEmail(request.email())) {
			throw new BusinessException(ErrorCode.MEMBER_EMAIL_DUPLICATED);
		}
		if (memberRepository.existsByNickname(request.nickname())) {
			throw new BusinessException(ErrorCode.MEMBER_NICKNAME_DUPLICATED);
		}
		Member member = Member.createLocal(request.email(), passwordEncoder.encode(request.password()),
			request.nickname(), policyProperties.trust().initialScore());
		try {
			return new MemberSignupResponse(memberRepository.saveAndFlush(member).getId());
		} catch (DataIntegrityViolationException e) {
			throw mapDuplicate(e);
		}
	}

	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public MemberResponse updateProfile(Long memberId, MemberProfileUpdateRequest request,
		MultipartFile profileImage) {
		memberQueryService.getMe(memberId);
		String nickname = request == null ? null : request.nickname();
		if (nickname != null && memberRepository.existsByNicknameAndIdNot(nickname, memberId)) {
			throw new BusinessException(ErrorCode.MEMBER_NICKNAME_DUPLICATED);
		}
		StoredFile uploaded = null;
		if (profileImage != null) {
			var image = imageProcessor.process(profileImage, DataSize.parse(profileImageMaxSize));
			uploaded = fileStorage.upload("profiles/" + memberId, image.format().getExtension(),
				image.format().getContentType(), image.original());
		}
		String previous;
		try {
			previous = memberProfileWriter.update(memberId, nickname, uploaded == null ? null : uploaded.url());
		} catch (RuntimeException e) {
			if (uploaded != null) {
				deleteQuietly(uploaded.key());
			}
			if (e instanceof DataIntegrityViolationException duplicate) {
				throw mapDuplicate(duplicate);
			}
			throw e;
		}
		if (uploaded != null && previous != null) {
			try {
				deleteQuietly(fileStorage.keyOf(previous));
			} catch (IllegalArgumentException e) {
				log.warn("이전 프로필 이미지가 현재 저장소에 속하지 않아 삭제하지 않았습니다");
			}
		}
		return memberQueryService.getMe(memberId);
	}

	@Transactional
	public void changePassword(Long memberId, MemberPasswordChangeRequest request) {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getDetails() instanceof AccessTokenClaims claims)
			|| !memberId.equals(claims.memberId()) || claims.familyId() == null || claims.familyId().isBlank()) {
			throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
		}
		Member member = memberRepository.findForUpdate(memberId)
			.filter(value -> value.getStatus() != MemberStatus.WITHDRAWN)
			.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED));
		if (member.getPassword() == null || !passwordEncoder.matches(request.currentPassword(), member.getPassword())) {
			throw new BusinessException(ErrorCode.MEMBER_PASSWORD_MISMATCH);
		}
		member.changePassword(passwordEncoder.encode(request.newPassword()));
		memberRepository.flush();
		// Redis 실패 시 DB도 롤백한다. DB 커밋 실패 시에는 다른 기기가 다시 로그인해야 할 수 있다.
		refreshTokenRepository.deleteOtherFamilies(memberId, claims.familyId());
	}

	private RuntimeException mapDuplicate(DataIntegrityViolationException exception) {
		for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
			String message = cause.getMessage();
			if (message != null && message.contains("uk_members_email")) {
				return new BusinessException(ErrorCode.MEMBER_EMAIL_DUPLICATED);
			}
			if (message != null && message.contains("uk_members_nickname")) {
				return new BusinessException(ErrorCode.MEMBER_NICKNAME_DUPLICATED);
			}
		}
		return exception;
	}

	private void deleteQuietly(String key) {
		try {
			fileStorage.delete(key);
		} catch (RuntimeException e) {
			log.warn("프로필 이미지 정리에 실패했습니다: {}", e.getClass().getSimpleName());
		}
	}
}
