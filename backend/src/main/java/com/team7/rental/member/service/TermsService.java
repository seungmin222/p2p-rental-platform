package com.team7.rental.member.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.member.dto.TermsAgreementStatusResponse;
import com.team7.rental.member.dto.TermsDocumentResponse;
import com.team7.rental.member.dto.TermsDocumentType;
import com.team7.rental.member.entity.MemberStatus;
import com.team7.rental.member.entity.TermsAgreement;
import com.team7.rental.member.entity.TermsAgreementType;
import com.team7.rental.member.repository.MemberRepository;
import com.team7.rental.member.repository.TermsAgreementRepository;

/**
 * 약관 동의 및 정책 안내 (REQ-037). 본문은 기동 시 resources/terms/*.md에서 한 번 읽는다(파일이 없으면 기동 실패). 동의 항목은 셋 다 필수이고, 각 항목의 현재
 * 버전({@link TermsProperties})에 동의한 기록이 없으면 재동의가 필요하다.
 */
@Service
@Transactional(readOnly = true)
public class TermsService {

	private final TermsAgreementRepository termsAgreementRepository;
	private final MemberRepository memberRepository;
	private final TermsProperties termsProperties;
	private final Clock clock;
	private final List<TermsDocumentResponse> documents;

	public TermsService(TermsAgreementRepository termsAgreementRepository, MemberRepository memberRepository,
		TermsProperties termsProperties, Clock clock) {
		this.termsAgreementRepository = termsAgreementRepository;
		this.memberRepository = memberRepository;
		this.termsProperties = termsProperties;
		this.clock = clock;
		this.documents = Arrays.stream(TermsDocumentType.values())
			.map(type -> new TermsDocumentResponse(type, type.getTitle(), termsProperties.versionOf(type),
				read(type.getResourcePath())))
			.toList();
	}

	/** 약관·정책 문서 전부 (GET /api/terms). 쿼리 없음 */
	public List<TermsDocumentResponse> getDocuments() {
		return documents;
	}

	/** 로그인·재발급 응답의 requiresTermsAgreement. 쿼리 1개 */
	public boolean requiresAgreement(Long memberId) {
		return hasMissing(agreedVersions(memberId));
	}

	/**
	 * 가입 시 필수 항목 전부를 현재 버전으로 기록한다. 가입 트랜잭션에 참여해 회원과 함께 커밋된다. 동의 여부 검사는 요청 DTO가 한다.
	 */
	@Transactional
	public void agreeAllOnSignup(Long memberId) {
		LocalDateTime now = LocalDateTime.now(clock);
		termsAgreementRepository.saveAll(Arrays.stream(TermsAgreementType.values())
			.map(type -> TermsAgreement.agree(memberId, type, termsProperties.versionOf(type), now))
			.toList());
	}

	/**
	 * 재동의 (POST /api/members/me/terms-agreements). 회원 행 락으로 같은 회원의 동시 요청을 줄 세워 유니크 충돌을 막는다. 이미 현재 버전에 동의한 항목은
	 * 건너뛴다.
	 *
	 * @throws BusinessException AUTH_UNAUTHORIZED 회원이 없거나 탈퇴했을 때
	 */
	@Transactional
	public TermsAgreementStatusResponse reagree(Long memberId, Collection<TermsAgreementType> types) {
		memberRepository.findForUpdate(memberId)
			.filter(member -> member.getStatus() != MemberStatus.WITHDRAWN)
			.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED));
		Map<TermsAgreementType, Set<String>> agreed = agreedVersions(memberId);
		LocalDateTime now = LocalDateTime.now(clock);
		for (TermsAgreementType type : types) {
			String version = termsProperties.versionOf(type);
			if (!agreed.getOrDefault(type, Set.of()).contains(version)) {
				termsAgreementRepository.save(TermsAgreement.agree(memberId, type, version, now));
				agreed.computeIfAbsent(type, key -> new HashSet<>()).add(version);
			}
		}
		return new TermsAgreementStatusResponse(hasMissing(agreed));
	}

	/** 현재 버전에 동의하지 않은 필수 항목이 있는가 */
	private boolean hasMissing(Map<TermsAgreementType, Set<String>> agreed) {
		return Arrays.stream(TermsAgreementType.values())
			.anyMatch(type -> !agreed.getOrDefault(type, Set.of()).contains(termsProperties.versionOf(type)));
	}

	private Map<TermsAgreementType, Set<String>> agreedVersions(Long memberId) {
		return termsAgreementRepository.findByMemberId(memberId).stream()
			.collect(Collectors.groupingBy(TermsAgreement::getTermsType,
				() -> new EnumMap<>(TermsAgreementType.class),
				Collectors.mapping(TermsAgreement::getTermsVersion, Collectors.toCollection(HashSet::new))));
	}

	private static String read(String path) {
		try (var in = new ClassPathResource(path).getInputStream()) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException("약관 본문을 읽지 못했습니다: " + path, e);
		}
	}
}
