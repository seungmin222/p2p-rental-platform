package com.team7.rental.member.entity;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import com.team7.rental.member.repository.MemberRegionRepository;
import com.team7.rental.member.repository.MemberRepository;
import com.team7.rental.member.repository.RegionRepository;
import com.team7.rental.support.IntegrationTestSupport;

class MemberEntityIntegrationTest extends IntegrationTestSupport {

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private RegionRepository regionRepository;

	@Autowired
	private MemberRegionRepository memberRegionRepository;

	@Test
	@DisplayName("회원을 저장하고 다시 읽으면 LOCAL·USER·ACTIVE, 신뢰 점수 50.0이다")
	void saveAndFindMember() {
		// given
		testClock.fixAt(LocalDateTime.of(2026, 10, 28, 10, 0));
		Member saved = memberRepository.save(
			Member.createLocal("a@test.com", "{bcrypt}encoded", "에이", new BigDecimal("50.0")));

		// when
		Member found = memberRepository.findById(saved.getId()).orElseThrow();

		// then
		assertThat(found.getEmail()).isEqualTo("a@test.com");
		assertThat(found.getPassword()).isEqualTo("{bcrypt}encoded");
		assertThat(found.getNickname()).isEqualTo("에이");
		assertThat(found.getProfileImageUrl()).isNull();
		assertThat(found.getProvider()).isEqualTo(AuthProvider.LOCAL);
		assertThat(found.getRole()).isEqualTo(MemberRole.USER);
		assertThat(found.getStatus()).isEqualTo(MemberStatus.ACTIVE);
		assertThat(found.getTrustScore()).isEqualByComparingTo("50.0");
		assertThat(found.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 28, 10, 0));
	}

	@Test
	@DisplayName("동네와 회원 동네를 저장하고 다시 읽을 수 있다")
	void saveAndFindRegions() {
		// given
		Member member = fixture.createMember();
		Region region = regionRepository.save(Region.create("1168064000", "역삼1동", "서울특별시", "강남구", 37.495, 127.033));
		LocalDateTime verifiedAt = LocalDateTime.of(2026, 10, 1, 9, 30, 15, 123_456_000);
		MemberRegion saved = fixture.addPrimaryRegion(member, region, verifiedAt);

		// when
		Region foundRegion = regionRepository.findById(region.getId()).orElseThrow();
		MemberRegion found = memberRegionRepository.findById(saved.getId()).orElseThrow();

		// then
		assertThat(foundRegion.getCode()).isEqualTo("1168064000");
		assertThat(foundRegion.getName()).isEqualTo("역삼1동");
		assertThat(foundRegion.getSido()).isEqualTo("서울특별시");
		assertThat(foundRegion.getSigungu()).isEqualTo("강남구");
		assertThat(foundRegion.getCenterLat()).isEqualTo(37.495);
		assertThat(foundRegion.getCenterLng()).isEqualTo(127.033);
		assertThat(found.getMemberId()).isEqualTo(member.getId());
		assertThat(found.getRegionId()).isEqualTo(region.getId());
		assertThat(found.isPrimary()).isTrue();
		assertThat(found.getVerifiedAt()).isEqualTo(verifiedAt);
		assertThat(found.getRegionRange()).isEqualTo(RegionRange.NORMAL);
	}

	@Test
	@DisplayName("이메일이 같은 회원은 저장되지 않는다")
	void duplicateEmail() {
		// given
		memberRepository.save(Member.createLocal("dup@test.com", "pw", "하나", new BigDecimal("50.0")));

		// then
		assertThatThrownBy(
			() -> memberRepository.save(Member.createLocal("dup@test.com", "pw", "둘", new BigDecimal("50.0"))))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("닉네임이 같은 회원은 저장되지 않는다")
	void duplicateNickname() {
		// given
		memberRepository.save(Member.createLocal("one@test.com", "pw", "같은닉", new BigDecimal("50.0")));

		// then
		assertThatThrownBy(
			() -> memberRepository.save(Member.createLocal("two@test.com", "pw", "같은닉", new BigDecimal("50.0"))))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("같은 회원에 같은 동네를 두 번 저장할 수 없다")
	void duplicateMemberRegion() {
		// given
		Member member = fixture.createMember();
		Region region = fixture.createRegion();
		memberRegionRepository.save(MemberRegion.create(member.getId(), region.getId(), true));

		// then
		assertThatThrownBy(
			() -> memberRegionRepository.save(MemberRegion.create(member.getId(), region.getId(), false)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}
}
