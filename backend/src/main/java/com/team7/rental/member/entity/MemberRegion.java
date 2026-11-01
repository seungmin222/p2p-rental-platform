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
 * 회원의 동네. 회원당 최대 2개·대표 1개 검증은 REQ-004 서비스가 한다.
 */
@Getter
@Entity
@Table(name = "member_regions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberRegion extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long memberId;

	@Column(nullable = false)
	private Long regionId;

	@Column(nullable = false)
	private boolean isPrimary;

	/** 마지막 인증 시각. 인증 전이면 null. 좌표는 저장하지 않는다(REQ-004) */
	private LocalDateTime verifiedAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private RegionRange regionRange;

	/** 인증 전 상태로 만든다. 범위는 REQ-042 전까지 NORMAL 고정 */
	public static MemberRegion create(Long memberId, Long regionId, boolean isPrimary) {
		MemberRegion memberRegion = new MemberRegion();
		memberRegion.memberId = memberId;
		memberRegion.regionId = regionId;
		memberRegion.isPrimary = isPrimary;
		memberRegion.regionRange = RegionRange.NORMAL;
		return memberRegion;
	}

	/**
	 * 인증이 유효한지. 유효 = verifiedAt + validDays > now (경과 일수가 validDays가 되는 시각부터 만료).
	 *
	 * @param validDays 인증 유효 일수 (PolicyProperties region.verify-valid-days)
	 */
	public boolean isVerified(LocalDateTime now, int validDays) {
		return verifiedAt != null && verifiedAt.plusDays(validDays).isAfter(now);
	}

	public void verify(LocalDateTime verifiedAt) {
		this.verifiedAt = verifiedAt;
	}

	public void changePrimary(boolean primary) {
		this.isPrimary = primary;
	}
}
