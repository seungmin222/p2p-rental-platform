package com.team7.rental.member.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.member.entity.Member;
import com.team7.rental.member.entity.MemberRegion;
import com.team7.rental.member.entity.Region;
import com.team7.rental.member.repository.MemberRegionRepository;
import com.team7.rental.member.repository.MemberRepository;
import com.team7.rental.member.repository.RegionRepository;

class MemberRegionVerificationWriterTest {
	private final MemberRepository members = mock(MemberRepository.class);
	private final MemberRegionRepository memberRegions = mock(MemberRegionRepository.class);
	private final RegionRepository regions = mock(RegionRepository.class);
	private final MemberRegionMapper mapper = mock(MemberRegionMapper.class);
	private final Clock clock = Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC);
	private final MemberRegionVerificationWriter writer = new MemberRegionVerificationWriter(members, memberRegions,
		regions, mapper, clock);

	@Test
	void matchingAdministrativeCodeRecordsInjectedTime() {
		var target = arrangeOwnedRegion();
		writer.verify(1L, 9L, "1111051500");
		assertThat(target.getVerifiedAt()).isEqualTo(LocalDateTime.now(clock));
	}

	@Test
	void differentAdministrativeCodeDoesNotVerify() {
		var target = arrangeOwnedRegion();
		assertThatThrownBy(() -> writer.verify(1L, 9L, "1111053000"))
			.isInstanceOf(BusinessException.class);
		assertThat(target.getVerifiedAt()).isNull();
		verifyNoInteractions(mapper);
	}

	@Test
	void otherMembersRegionCannotBeVerified() {
		when(members.findForUpdate(1L)).thenReturn(Optional.of(mock(Member.class)));
		when(memberRegions.findByIdAndMemberId(9L, 1L)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> writer.verify(1L, 9L, "1111051500"))
			.isInstanceOf(BusinessException.class);
		verifyNoInteractions(regions, mapper);
	}

	private MemberRegion arrangeOwnedRegion() {
		var target = MemberRegion.create(1L, 2L, true);
		var region = Region.create("1111051500", "테스트동", "테스트시", "테스트구", 37, 127);
		when(members.findForUpdate(1L)).thenReturn(Optional.of(mock(Member.class)));
		when(memberRegions.findByIdAndMemberId(9L, 1L)).thenReturn(Optional.of(target));
		when(regions.findById(2L)).thenReturn(Optional.of(region));
		return target;
	}
}
