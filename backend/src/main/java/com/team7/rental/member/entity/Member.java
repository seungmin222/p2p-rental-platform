package com.team7.rental.member.entity;

import java.math.BigDecimal;

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

@Getter
@Entity
@Table(name = "members")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String email;

	/** BCrypt 해시. 소셜 전용 계정이면 null */
	@Column(length = 100)
	private String password;

	@Column(nullable = false, length = 10)
	private String nickname;

	@Column(length = 500)
	private String profileImageUrl;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private AuthProvider provider;

	/** 집계 캐시. 갱신은 REQ-054(3순위) 구현 시 */
	@Column(nullable = false, precision = 4, scale = 1)
	private BigDecimal trustScore;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private MemberRole role;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private MemberStatus status;

	/**
	 * 이메일 가입 회원(REQ-001). 형식·중복 검증은 호출하는 서비스가 한다.
	 *
	 * @param encodedPassword BCrypt로 인코딩한 비밀번호
	 * @param initialTrustScore 초기 신뢰 점수 (PolicyProperties trust.initial-score)
	 */
	public static Member createLocal(String email, String encodedPassword, String nickname,
		BigDecimal initialTrustScore) {
		Member member = new Member();
		member.email = email;
		member.password = encodedPassword;
		member.nickname = nickname;
		member.provider = AuthProvider.LOCAL;
		member.trustScore = initialTrustScore;
		member.role = MemberRole.USER;
		member.status = MemberStatus.ACTIVE;
		return member;
	}

	public void updateProfile(String nickname, String imageUrl) {
		if (nickname != null) {
			this.nickname = nickname;
		}
		if (imageUrl != null) {
			this.profileImageUrl = imageUrl;
		}
	}

	public void changePassword(String encodedPassword) {
		this.password = encodedPassword;
	}
}
