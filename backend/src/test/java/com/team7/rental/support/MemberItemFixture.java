package com.team7.rental.support;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.test.util.ReflectionTestUtils;

import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.item.entity.Category;
import com.team7.rental.item.entity.Item;
import com.team7.rental.item.entity.ItemAvailablePeriod;
import com.team7.rental.item.entity.ItemCondition;
import com.team7.rental.item.repository.CategoryRepository;
import com.team7.rental.item.repository.ItemAvailablePeriodRepository;
import com.team7.rental.item.repository.ItemRepository;
import com.team7.rental.member.entity.Member;
import com.team7.rental.member.entity.MemberRegion;
import com.team7.rental.member.entity.Region;
import com.team7.rental.member.repository.MemberRegionRepository;
import com.team7.rental.member.repository.MemberRepository;
import com.team7.rental.member.repository.RegionRepository;

/**
 * 대여 테스트용 회원·동네·물품 데이터. 각 메서드가 바로 저장(커밋)한다.
 * 예: {@code var owner = fixture.createVerifiedMember(); var item = fixture.createItem(owner, start, end);}
 */
@TestComponent
public class MemberItemFixture {

	public static final int PRICE_PER_DAY = 10_000;
	public static final int DEPOSIT = 50_000;
	public static final int ITEM_PRICE = 200_000;

	// 테스트 클래스 사이에 테이블은 비워지지만 UK 충돌을 피하려고 JVM 전체에서 번호를 이어 쓴다
	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	private final MemberRepository memberRepository;
	private final RegionRepository regionRepository;
	private final MemberRegionRepository memberRegionRepository;
	private final CategoryRepository categoryRepository;
	private final ItemRepository itemRepository;
	private final ItemAvailablePeriodRepository itemAvailablePeriodRepository;
	private final PolicyProperties policyProperties;
	private final Clock clock;

	MemberItemFixture(MemberRepository memberRepository, RegionRepository regionRepository,
		MemberRegionRepository memberRegionRepository, CategoryRepository categoryRepository,
		ItemRepository itemRepository, ItemAvailablePeriodRepository itemAvailablePeriodRepository,
		PolicyProperties policyProperties, Clock clock) {
		this.memberRepository = memberRepository;
		this.regionRepository = regionRepository;
		this.memberRegionRepository = memberRegionRepository;
		this.categoryRepository = categoryRepository;
		this.itemRepository = itemRepository;
		this.itemAvailablePeriodRepository = itemAvailablePeriodRepository;
		this.policyProperties = policyProperties;
		this.clock = clock;
	}

	/** 동네가 없는 회원 */
	public Member createMember() {
		int n = SEQUENCE.incrementAndGet();
		return memberRepository.save(Member.createLocal("user" + n + "@test.com", "{bcrypt}encoded", "user" + n,
			policyProperties.trust().initialScore()));
	}

	public Region createRegion() {
		int n = SEQUENCE.incrementAndGet();
		return regionRepository.save(
			Region.create(String.format("%010d", n), "역삼" + n + "동", "서울특별시", "강남구", 37.5, 127.03));
	}

	/** 새 동네를 대표 동네로 두고 지금(Clock 기준) 인증한 회원 */
	public VerifiedMember createVerifiedMember() {
		Member member = createMember();
		Region region = createRegion();
		MemberRegion memberRegion = addPrimaryRegion(member, region, LocalDateTime.now(clock));
		return new VerifiedMember(member, region, memberRegion);
	}

	/**
	 * 대표 동네를 추가한다.
	 *
	 * @param verifiedAt 인증 시각. null이면 인증 전
	 */
	public MemberRegion addPrimaryRegion(Member member, Region region, LocalDateTime verifiedAt) {
		MemberRegion memberRegion = MemberRegion.create(member.getId(), region.getId(), true);
		// 인증 처리(REQ-004)는 B 담당이라 아직 엔티티 메서드가 없다. 생기면 그 메서드로 바꾼다
		ReflectionTestUtils.setField(memberRegion, "verifiedAt", verifiedAt);
		return memberRegionRepository.save(memberRegion);
	}

	/** owner의 대표 동네에 AVAILABLE 물품과 가능 기간 [start, end] 하나를 만든다 */
	public ItemWithPeriod createItem(VerifiedMember owner, LocalDate start, LocalDate end) {
		Category category = categoryRepository.findAll().getFirst();
		Item item = itemRepository.save(Item.create(owner.member().getId(), owner.region().getId(),
			category.getId(), "테스트 물품", "테스트 설명", ItemCondition.LIKE_NEW, PRICE_PER_DAY, DEPOSIT, ITEM_PRICE,
			"서울 강남구 테헤란로 152", 37.5, 127.03, LocalDateTime.now(clock)));
		ItemAvailablePeriod period = itemAvailablePeriodRepository.save(
			ItemAvailablePeriod.create(item.getId(), start, end));
		return new ItemWithPeriod(item, period);
	}

	public record VerifiedMember(Member member, Region region, MemberRegion memberRegion) {
	}

	public record ItemWithPeriod(Item item, ItemAvailablePeriod period) {
	}
}
