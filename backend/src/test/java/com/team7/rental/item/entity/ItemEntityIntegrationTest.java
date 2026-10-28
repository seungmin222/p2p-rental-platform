package com.team7.rental.item.entity;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.team7.rental.item.repository.CategoryRepository;
import com.team7.rental.item.repository.ItemAvailablePeriodRepository;
import com.team7.rental.item.repository.ItemImageRepository;
import com.team7.rental.item.repository.ItemRepository;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture;
import com.team7.rental.support.MemberItemFixture.ItemWithPeriod;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

class ItemEntityIntegrationTest extends IntegrationTestSupport {

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private ItemRepository itemRepository;

	@Autowired
	private ItemImageRepository itemImageRepository;

	@Autowired
	private ItemAvailablePeriodRepository itemAvailablePeriodRepository;

	@Test
	@DisplayName("초기 카테고리 8개가 정책 순서대로 활성 상태로 들어 있다")
	void seedCategories() {
		// when
		List<Category> categories = categoryRepository.findAll().stream()
			.sorted(Comparator.comparingInt(Category::getSortOrder))
			.toList();

		// then
		assertThat(categories).extracting(Category::getCode)
			.containsExactly("CAMPING", "TOOL", "CAMERA", "BABY", "PARTY", "HOBBY", "APPLIANCE", "ETC");
		assertThat(categories).extracting(Category::getName)
			.containsExactly("캠핑·아웃도어", "공구·DIY", "촬영·영상 장비", "유아용품", "파티·행사용품", "취미·시즌 용품", "생활가전", "기타");
		assertThat(categories).allSatisfy(category -> {
			assertThat(category.isActive()).isTrue();
			assertThat(category.getParentId()).isNull();
		});
	}

	@Test
	@DisplayName("물품과 가능 기간을 저장하고 다시 읽을 수 있다")
	void saveAndFindItem() {
		// given
		LocalDateTime now = LocalDateTime.of(2026, 10, 28, 10, 0);
		testClock.fixAt(now);
		VerifiedMember owner = fixture.createVerifiedMember();
		ItemWithPeriod saved = fixture.createItem(owner, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10));

		// when
		Item found = itemRepository.findById(saved.item().getId()).orElseThrow();
		ItemAvailablePeriod foundPeriod = itemAvailablePeriodRepository.findById(saved.period().getId())
			.orElseThrow();

		// then
		assertThat(found.getOwnerId()).isEqualTo(owner.member().getId());
		assertThat(found.getRegionId()).isEqualTo(owner.region().getId());
		assertThat(found.getCategoryId()).isNotNull();
		assertThat(found.getName()).isEqualTo("테스트 물품");
		assertThat(found.getDescription()).isEqualTo("테스트 설명");
		assertThat(found.getItemCondition()).isEqualTo(ItemCondition.LIKE_NEW);
		assertThat(found.getPricePerDay()).isEqualTo(MemberItemFixture.PRICE_PER_DAY);
		assertThat(found.getDeposit()).isEqualTo(MemberItemFixture.DEPOSIT);
		assertThat(found.getItemPrice()).isEqualTo(MemberItemFixture.ITEM_PRICE);
		assertThat(found.getAddress()).isEqualTo("서울 강남구 테헤란로 152");
		assertThat(found.getLatitude()).isEqualTo(37.5);
		assertThat(found.getLongitude()).isEqualTo(127.03);
		assertThat(found.getStatus()).isEqualTo(ItemStatus.AVAILABLE);
		assertThat(found.getViewCount()).isZero();
		assertThat(found.getBumpedAt()).isEqualTo(now).isEqualTo(found.getCreatedAt());
		assertThat(foundPeriod.getItemId()).isEqualTo(found.getId());
		assertThat(foundPeriod.getStartDate()).isEqualTo(LocalDate.of(2026, 11, 1));
		assertThat(foundPeriod.getEndDate()).isEqualTo(LocalDate.of(2026, 11, 10));
	}

	@Test
	@DisplayName("물품 이미지를 저장하고 다시 읽을 수 있다")
	void saveAndFindItemImage() {
		// given
		VerifiedMember owner = fixture.createVerifiedMember();
		Item item = fixture.createItem(owner, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10)).item();
		ItemImage saved = itemImageRepository.save(
			ItemImage.create(item.getId(), "/files/items/a.jpg", "/files/items/a_thumb.jpg", 0));

		// when
		ItemImage found = itemImageRepository.findById(saved.getId()).orElseThrow();

		// then
		assertThat(found.getItemId()).isEqualTo(item.getId());
		assertThat(found.getUrl()).isEqualTo("/files/items/a.jpg");
		assertThat(found.getThumbnailUrl()).isEqualTo("/files/items/a_thumb.jpg");
		assertThat(found.getSortOrder()).isZero();
	}

	@Test
	@DisplayName("픽스처의 회원은 대표 동네가 지금 기준으로 인증돼 있다")
	void fixtureMemberIsVerified() {
		// given
		testClock.fixAt(LocalDateTime.of(2026, 10, 28, 10, 0));

		// when
		VerifiedMember member = fixture.createVerifiedMember();

		// then
		assertThat(member.memberRegion().isPrimary()).isTrue();
		assertThat(member.memberRegion().isVerified(LocalDateTime.of(2026, 10, 28, 10, 0), 30)).isTrue();
	}
}
