package com.team7.rental.member.entity;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MemberRegionTest {

	private static final int VALID_DAYS = 30;
	private static final LocalDateTime VERIFIED_AT = LocalDateTime.of(2026, 10, 1, 10, 0);

	@Test
	@DisplayName("인증한 적 없으면 유효하지 않다")
	void notVerified() {
		// given
		MemberRegion memberRegion = MemberRegion.create(1L, 1L, true);

		// then
		assertThat(memberRegion.isVerified(VERIFIED_AT, VALID_DAYS)).isFalse();
	}

	@Test
	@DisplayName("인증 후 29일 경과는 유효하다")
	void validAfter29Days() {
		// given
		MemberRegion memberRegion = verifiedAt(VERIFIED_AT);

		// then
		assertThat(memberRegion.isVerified(VERIFIED_AT.plusDays(29), VALID_DAYS)).isTrue();
	}

	@Test
	@DisplayName("인증 후 30일 직전까지 유효하고, 정확히 30일이 되는 시각부터 만료된다")
	void expiresAt30Days() {
		// given
		MemberRegion memberRegion = verifiedAt(VERIFIED_AT);

		// then
		assertThat(memberRegion.isVerified(VERIFIED_AT.plusDays(30).minusNanos(1), VALID_DAYS)).isTrue();
		assertThat(memberRegion.isVerified(VERIFIED_AT.plusDays(30), VALID_DAYS)).isFalse();
	}

	@Test
	@DisplayName("인증 후 31일 경과는 만료다")
	void expiredAfter31Days() {
		// given
		MemberRegion memberRegion = verifiedAt(VERIFIED_AT);

		// then
		assertThat(memberRegion.isVerified(VERIFIED_AT.plusDays(31), VALID_DAYS)).isFalse();
	}

	@Test
	@DisplayName("새 동네는 인증 전이고 범위는 NORMAL이다")
	void createDefaults() {
		// when
		MemberRegion memberRegion = MemberRegion.create(1L, 2L, true);

		// then
		assertThat(memberRegion.getVerifiedAt()).isNull();
		assertThat(memberRegion.getRegionRange()).isEqualTo(RegionRange.NORMAL);
		assertThat(memberRegion.isPrimary()).isTrue();
	}

	// 인증 처리(REQ-004)는 B 담당이라 아직 엔티티 메서드가 없다
	private MemberRegion verifiedAt(LocalDateTime verifiedAt) {
		MemberRegion memberRegion = MemberRegion.create(1L, 1L, true);
		ReflectionTestUtils.setField(memberRegion, "verifiedAt", verifiedAt);
		return memberRegion;
	}
}
