package com.team7.rental.member.entity;

import java.time.LocalDateTime;

import com.team7.rental.global.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 약관 동의 이력 (REQ-037). 동의한 버전을 스냅샷으로 남기고 바꾸지 않는다. 약관이 개정되면 재동의 때 새 버전 행을 더한다. (member_id, terms_type,
 * terms_version) 유니크.
 */
@Getter
@Entity
@Table(name = "terms_agreements")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TermsAgreement extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long memberId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private TermsAgreementType termsType;

	@Column(nullable = false, length = 20)
	private String termsVersion;

	@Column(nullable = false)
	private LocalDateTime agreedAt;

	public static TermsAgreement agree(Long memberId, TermsAgreementType termsType, String termsVersion,
		LocalDateTime agreedAt) {
		TermsAgreement agreement = new TermsAgreement();
		agreement.memberId = memberId;
		agreement.termsType = termsType;
		agreement.termsVersion = termsVersion;
		agreement.agreedAt = agreedAt;
		return agreement;
	}
}
