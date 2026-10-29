package com.team7.rental.item.service;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.team7.rental.item.dto.ItemSummary;
import com.team7.rental.item.entity.Item;
import com.team7.rental.item.entity.ItemImage;
import com.team7.rental.item.repository.ItemImageRepository;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

import jakarta.persistence.EntityManagerFactory;

/** ItemQueryService.getSummaries (REQ-019 빌린 내역의 물품 일괄 조회) */
class ItemQueryServiceIntegrationTest extends IntegrationTestSupport {

	private static final LocalDate START = LocalDate.of(2026, 11, 1);
	private static final LocalDate END = LocalDate.of(2026, 11, 30);

	@Autowired
	private ItemQueryService itemQueryService;

	@Autowired
	private ItemImageRepository itemImageRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	private VerifiedMember owner;

	@BeforeEach
	void setUp() {
		owner = fixture.createVerifiedMember();
	}

	@Test
	@DisplayName("빈 목록이면 쿼리 없이 빈 Map이다")
	void empty() {
		// given
		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();

		// when
		Map<Long, ItemSummary> summaries = itemQueryService.getSummaries(List.of());

		// then
		assertThat(summaries).isEmpty();
		assertThat(statistics.getPrepareStatementCount()).isZero();
	}

	@Test
	@DisplayName("없는 ID는 결과에 담지 않는다")
	void missingId() {
		// given
		Item item = fixture.createItem(owner, START, END).item();

		// when
		Map<Long, ItemSummary> summaries = itemQueryService.getSummaries(List.of(item.getId(), item.getId() + 999));

		// then
		assertThat(summaries).containsOnlyKeys(item.getId());
	}

	@Test
	@DisplayName("대표 이미지는 sort_order가 가장 작은 것이고, 동률이면 id가 작은 것이다. 이미지가 없으면 thumbnailUrl null")
	void thumbnail() {
		// given
		Item withImages = fixture.createItem(owner, START, END).item();
		Item tied = fixture.createItem(owner, START, END).item();
		Item noImage = fixture.createItem(owner, START, END).item();
		itemImageRepository.save(ItemImage.create(withImages.getId(), "/files/a1.jpg", "/files/a1_t.jpg", 1));
		itemImageRepository.save(ItemImage.create(withImages.getId(), "/files/a0.jpg", "/files/a0_t.jpg", 0));
		itemImageRepository.save(ItemImage.create(tied.getId(), "/files/b-first.jpg", "/files/b-first_t.jpg", 0));
		itemImageRepository.save(ItemImage.create(tied.getId(), "/files/b-second.jpg", "/files/b-second_t.jpg", 0));

		// when
		Map<Long, ItemSummary> summaries = itemQueryService.getSummaries(
			List.of(withImages.getId(), tied.getId(), noImage.getId()));

		// then
		assertThat(summaries.get(withImages.getId()).thumbnailUrl()).isEqualTo("/files/a0_t.jpg");
		assertThat(summaries.get(tied.getId()).thumbnailUrl()).isEqualTo("/files/b-first_t.jpg");
		assertThat(summaries.get(noImage.getId()).thumbnailUrl()).isNull();
	}

	@Test
	@DisplayName("삭제된 물품도 결과에 담고 이름을 유지한 채 isDeleted=true다 (getSummary와 같은 규칙)")
	void deletedItem() {
		// given
		Item deleted = fixture.createItem(owner, START, END).item();
		Item available = fixture.createItem(owner, START, END).item();
		jdbcTemplate.update("UPDATE items SET status = 'DELETED' WHERE id = ?", deleted.getId());

		// when
		Map<Long, ItemSummary> summaries = itemQueryService.getSummaries(List.of(deleted.getId(), available.getId()));

		// then
		assertThat(summaries.get(deleted.getId()))
			.isEqualTo(new ItemSummary(deleted.getId(), deleted.getName(), null, true));
		assertThat(summaries.get(available.getId()).isDeleted()).isFalse();
		assertThat(summaries.get(deleted.getId())).isEqualTo(itemQueryService.getSummary(deleted.getId()));
	}

	@Test
	@DisplayName("물품 수와 상관없이 쿼리 2개(물품·대표 이미지)다")
	void queryCount() {
		// given
		List<Long> itemIds = List.of(fixture.createItem(owner, START, END).item().getId(),
			fixture.createItem(owner, START, END).item().getId(),
			fixture.createItem(owner, START, END).item().getId());
		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();

		// when
		itemQueryService.getSummaries(itemIds);

		// then
		assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
	}
}
