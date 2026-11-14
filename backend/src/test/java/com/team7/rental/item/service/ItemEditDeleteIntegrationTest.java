package com.team7.rental.item.service;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.team7.rental.global.storage.FileStorage;
import com.team7.rental.global.storage.LocalStorageProperties;
import com.team7.rental.item.entity.Item;
import com.team7.rental.item.entity.ItemImage;
import com.team7.rental.item.entity.ItemStatus;
import com.team7.rental.item.repository.ItemAvailablePeriodRepository;
import com.team7.rental.item.repository.ItemImageRepository;
import com.team7.rental.item.repository.ItemRepository;
import com.team7.rental.member.entity.Member;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.support.IntegrationTestSupport;
import com.team7.rental.support.MemberItemFixture.VerifiedMember;

/** 물품 수정 화면 조회·수정(REQ-007)·삭제(REQ-008) */
class ItemEditDeleteIntegrationTest extends IntegrationTestSupport {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 12, 0);

	@Autowired
	private ItemRepository items;
	@Autowired
	private ItemImageRepository images;
	@Autowired
	private ItemAvailablePeriodRepository periods;
	@Autowired
	private FileStorage storage;
	@Autowired
	private LocalStorageProperties storageProperties;

	private final List<String> uploadedUrls = new ArrayList<>();
	private VerifiedMember owner;
	private Member renter;
	private Item item;
	private ItemImage first;
	private ItemImage second;

	@BeforeEach
	void setUp() {
		testClock.fixAt(NOW);
		owner = fixture.createVerifiedMember();
		renter = fixture.createVerifiedMember().member();
		item = fixture.createItem(owner, LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 30)).item();
		first = saveImage(0);
		second = saveImage(1);
	}

	@AfterEach
	void deleteUploadedFiles() {
		for (var image : images.findAll()) {
			uploadedUrls.add(image.getUrl());
			uploadedUrls.add(image.getThumbnailUrl());
		}
		for (String url : uploadedUrls) {
			try {
				storage.delete(storage.keyOf(url));
			} catch (RuntimeException ignored) {
				// 이미 지워진 파일
			}
		}
	}

	@Test
	@DisplayName("수정 화면은 소유자에게 itemPrice·가능 기간·이미지 id를 sortOrder 순으로 준다")
	void editView() throws Exception {
		mockMvc.perform(get("/api/members/me/items/{itemId}", item.getId()).header(HttpHeaders.AUTHORIZATION,
			bearerToken(ownerId())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.itemPrice").value(item.getItemPrice()))
			.andExpect(jsonPath("$.data.availablePeriods[0].startDate").value("2026-11-03"))
			.andExpect(jsonPath("$.data.images[0].imageId").value(first.getId()))
			.andExpect(jsonPath("$.data.images[1].imageId").value(second.getId()))
			.andExpect(jsonPath("$.data.status").value("AVAILABLE"));
	}

	@Test
	@DisplayName("남의 물품 수정 화면·수정·삭제는 403 ITEM_NOT_OWNER")
	void notOwner() throws Exception {
		String token = bearerToken(renter.getId());
		mockMvc.perform(get("/api/members/me/items/{itemId}", item.getId()).header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ITEM_NOT_OWNER"));
		mockMvc.perform(update("{\"name\":\"남의 물품\"}").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ITEM_NOT_OWNER"));
		mockMvc.perform(delete("/api/items/{itemId}", item.getId()).header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("ITEM_NOT_OWNER"));
		assertThat(items.findById(item.getId()).orElseThrow().getName()).isEqualTo("테스트 물품");
	}

	@Test
	@DisplayName("없는 물품은 수정 화면·수정·삭제 모두 404 ITEM_NOT_FOUND")
	void notFound() throws Exception {
		String token = bearerToken(ownerId());
		mockMvc.perform(get("/api/members/me/items/{itemId}", Long.MAX_VALUE).header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("ITEM_NOT_FOUND"));
		mockMvc.perform(multipart(HttpMethod.PATCH, "/api/items/{itemId}", Long.MAX_VALUE)
			.file(request("{\"name\":\"없음\"}")).header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("ITEM_NOT_FOUND"));
		mockMvc.perform(delete("/api/items/{itemId}", Long.MAX_VALUE).header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("ITEM_NOT_FOUND"));
	}

	@Test
	@DisplayName("보낸 항목만 바꾸고 나머지는 그대로 두며 응답은 수정 화면과 같은 형태다")
	void partialUpdate() throws Exception {
		mockMvc.perform(update("{\"name\":\"새 이름\",\"pricePerDay\":20000}")
			.header(HttpHeaders.AUTHORIZATION, bearerToken(ownerId())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.name").value("새 이름"))
			.andExpect(jsonPath("$.data.pricePerDay").value(20000))
			.andExpect(jsonPath("$.data.itemPrice").value(item.getItemPrice()))
			.andExpect(jsonPath("$.data.images.length()").value(2));
		Item updated = items.findById(item.getId()).orElseThrow();
		assertThat(updated.getName()).isEqualTo("새 이름");
		assertThat(updated.getPricePerDay()).isEqualTo(20000);
		assertThat(updated.getDeposit()).isEqualTo(item.getDeposit());
		assertThat(updated.getDescription()).isEqualTo(item.getDescription());
		assertThat(periods.findAllByItemIdOrderByStartDate(item.getId())).hasSize(1);
	}

	@Test
	@DisplayName("imageOrder로 기존 이미지 순서를 바꾸고 새 이미지를 넣으면 빠진 이미지는 행과 파일이 지워진다")
	void replaceImages() throws Exception {
		String json = "{\"imageOrder\":[{\"imageId\":%d},{\"newImageIndex\":0}]}".formatted(second.getId());
		mockMvc.perform(update(json).file(image()).header(HttpHeaders.AUTHORIZATION, bearerToken(ownerId())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.images.length()").value(2))
			.andExpect(jsonPath("$.data.images[0].imageId").value(second.getId()));

		List<ItemImage> saved = images.findAllByItemIdOrderBySortOrderAscIdAsc(item.getId());
		assertThat(saved).extracting(ItemImage::getSortOrder).containsExactly(0, 1);
		assertThat(saved.get(0).getId()).isEqualTo(second.getId());
		assertThat(saved.get(1).getId()).isNotEqualTo(first.getId());
		assertThat(images.findById(first.getId())).isEmpty();
		assertThatThrownBy(() -> storage.read(storage.keyOf(first.getUrl()))).isInstanceOf(RuntimeException.class);
		assertThatThrownBy(() -> storage.read(storage.keyOf(first.getThumbnailUrl())))
			.isInstanceOf(RuntimeException.class);
		assertThat(storage.read(storage.keyOf(second.getUrl()))).isNotEmpty();
	}

	@Test
	@DisplayName("imageOrder 없이 파일을 보내면 400 COMMON_INVALID_INPUT(details imageOrder)")
	void filesWithoutImageOrder() throws Exception {
		expectImageOrderInvalid(update("{\"name\":\"이름\"}").file(image()));
	}

	@Test
	@DisplayName("없는 newImageIndex는 400 COMMON_INVALID_INPUT(details imageOrder)")
	void unknownNewImageIndex() throws Exception {
		expectImageOrderInvalid(update("{\"imageOrder\":[{\"newImageIndex\":1}]}").file(image()));
	}

	@Test
	@DisplayName("imageOrder가 가리키지 않는 파일이 있으면 400 COMMON_INVALID_INPUT(details imageOrder)")
	void unreferencedFile() throws Exception {
		expectImageOrderInvalid(
			update("{\"imageOrder\":[{\"imageId\":%d}]}".formatted(first.getId())).file(image()));
	}

	@Test
	@DisplayName("다른 물품의 imageId는 400 COMMON_INVALID_INPUT(details imageOrder)")
	void foreignImageId() throws Exception {
		Item other = fixture.createItem(owner, LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 30)).item();
		ItemImage foreign = images.save(ItemImage.create(other.getId(), "/files/items/x.png",
			"/files/items/thumbnails/x.png", 0));
		expectImageOrderInvalid(update("{\"imageOrder\":[{\"imageId\":%d}]}".formatted(foreign.getId())));
	}

	@Test
	@DisplayName("최종 이미지가 0장이면 400 IMAGE_COUNT_INVALID이고 이미지는 그대로다")
	void emptyImageOrder() throws Exception {
		mockMvc.perform(update("{\"imageOrder\":[]}").header(HttpHeaders.AUTHORIZATION, bearerToken(ownerId())))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("IMAGE_COUNT_INVALID"));
		assertThat(images.findAllByItemIdOrderBySortOrderAscIdAsc(item.getId())).hasSize(2);
	}

	@Test
	@DisplayName("예약이 차지한 날짜를 가능 기간에서 빼면 409 ITEM_PERIOD_CONFLICT와 충돌 기간, 아무것도 바뀌지 않고 새 파일은 지운다")
	void periodConflict() throws Exception {
		rentalFixture.createRental(item, renter, RentalStatus.RESERVED, LocalDate.of(2026, 11, 10),
			LocalDate.of(2026, 11, 12));
		long filesBefore = countUploadedFiles();
		String json = """
			{"name":"바뀌면 안 됨","availablePeriods":[{"startDate":"2026-11-03","endDate":"2026-11-10"}],
			"imageOrder":[{"imageId":%d},{"newImageIndex":0}]}""".formatted(first.getId());

		mockMvc.perform(update(json).file(image()).header(HttpHeaders.AUTHORIZATION, bearerToken(ownerId())))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("ITEM_PERIOD_CONFLICT"))
			.andExpect(jsonPath("$.error.details[0].field").value("availablePeriods"))
			.andExpect(jsonPath("$.error.details[0].reason").value("2026-11-10 ~ 2026-11-12"));

		assertThat(items.findById(item.getId()).orElseThrow().getName()).isEqualTo("테스트 물품");
		assertThat(periods.findAllByItemIdOrderByStartDate(item.getId())).singleElement()
			.satisfies(period -> assertThat(period.getEndDate()).isEqualTo(LocalDate.of(2026, 11, 30)));
		assertThat(images.findAllByItemIdOrderBySortOrderAscIdAsc(item.getId())).hasSize(2);
		assertThat(countUploadedFiles()).isEqualTo(filesBefore);
	}

	@Test
	@DisplayName("이어진 두 가능 기간이 예약 날짜를 모두 덮으면 기간을 바꿀 수 있다")
	void periodsCoveringRentalAcrossTwoPeriods() throws Exception {
		rentalFixture.createRental(item, renter, RentalStatus.RENTING, LocalDate.of(2026, 11, 10),
			LocalDate.of(2026, 11, 12));
		String json = """
			{"availablePeriods":[{"startDate":"2026-11-03","endDate":"2026-11-10"},
			{"startDate":"2026-11-11","endDate":"2026-11-15"}]}""";

		mockMvc.perform(update(json).header(HttpHeaders.AUTHORIZATION, bearerToken(ownerId())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.availablePeriods.length()").value(2));
		assertThat(periods.findAllByItemIdOrderByStartDate(item.getId())).hasSize(2);
	}

	@Test
	@DisplayName("만료된 결제 대기·취소된 대여는 기간 점유가 아니어서 기간을 뺄 수 있다")
	void expiredPendingAndCanceledDoNotConflict() throws Exception {
		rentalFixture.createPendingPayment(item, renter, LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12),
			NOW.minusMinutes(1));
		rentalFixture.createRental(item, renter, RentalStatus.CANCELED, LocalDate.of(2026, 11, 20),
			LocalDate.of(2026, 11, 22));

		mockMvc.perform(update("{\"availablePeriods\":[{\"startDate\":\"2026-11-03\",\"endDate\":\"2026-11-05\"}]}")
			.header(HttpHeaders.AUTHORIZATION, bearerToken(ownerId())))
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("삭제하면 status=DELETED이고 상세·수정 화면은 404, 지난 대여 상세에는 삭제된 물품으로 남는다")
	void deleteSoftly() throws Exception {
		Rental completed = rentalFixture.createRental(item, renter, RentalStatus.COMPLETED,
			LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3));
		rentalFixture.createRental(item, renter, RentalStatus.CANCELED, LocalDate.of(2026, 11, 10),
			LocalDate.of(2026, 11, 12));

		mockMvc.perform(delete("/api/items/{itemId}", item.getId()).header(HttpHeaders.AUTHORIZATION,
			bearerToken(ownerId())))
			.andExpect(status().isOk());

		assertThat(items.findById(item.getId()).orElseThrow().getStatus()).isEqualTo(ItemStatus.DELETED);
		mockMvc.perform(get("/api/items/{itemId}", item.getId()))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/members/me/items/{itemId}", item.getId()).header(HttpHeaders.AUTHORIZATION,
			bearerToken(ownerId())))
			.andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/items/{itemId}", item.getId()).header(HttpHeaders.AUTHORIZATION,
			bearerToken(ownerId())))
			.andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("ITEM_NOT_FOUND"));
		mockMvc.perform(get("/api/rentals/{rentalId}", completed.getId()).header(HttpHeaders.AUTHORIZATION,
			bearerToken(renter.getId())))
			.andExpect(status().isOk()).andExpect(jsonPath("$.data.item.isDeleted").value(true));
		assertThat(storage.read(storage.keyOf(first.getUrl()))).isNotEmpty();
	}

	@Test
	@DisplayName("삭제한 물품은 피드(GET /api/items/feed)에서 빠진다")
	void deletedItemLeavesFeed() throws Exception {
		String regionId = owner.region().getId().toString();
		mockMvc.perform(get("/api/items/feed").param("regionId", regionId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items[*].itemId", hasItem(item.getId().intValue())));

		mockMvc.perform(delete("/api/items/{itemId}", item.getId()).header(HttpHeaders.AUTHORIZATION,
			bearerToken(ownerId())))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/items/feed").param("regionId", regionId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items[*].itemId",
				not(hasItem(item.getId().intValue()))));
	}

	@ParameterizedTest
	@EnumSource(value = RentalStatus.class, names = {"RESERVED", "RENTING", "OVERDUE", "RETURNED"})
	@DisplayName("진행 중 대여가 있으면 삭제는 409 ITEM_HAS_ACTIVE_RENTAL이고 해당 건을 details에 담는다")
	void deleteBlockedByActiveRental(RentalStatus status) throws Exception {
		Rental rental = rentalFixture.createRental(item, renter, status, LocalDate.of(2026, 11, 10),
			LocalDate.of(2026, 11, 12));
		expectDeleteBlocked("2026-11-10 ~ 2026-11-12 " + status.displayName() + " (rentalId " + rental.getId()
			+ ")");
	}

	@Test
	@DisplayName("결제 대기 대여는 만료 시각이 지났어도 상태가 바뀌기 전까지 삭제를 막는다")
	void deleteBlockedByPendingPayment() throws Exception {
		Rental rental = rentalFixture.createPendingPayment(item, renter, LocalDate.of(2026, 11, 10),
			LocalDate.of(2026, 11, 12), NOW.minusMinutes(1));
		expectDeleteBlocked("2026-11-10 ~ 2026-11-12 결제 대기 (rentalId " + rental.getId() + ")");
	}

	private void expectDeleteBlocked(String reason) throws Exception {
		mockMvc.perform(delete("/api/items/{itemId}", item.getId()).header(HttpHeaders.AUTHORIZATION,
			bearerToken(ownerId())))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("ITEM_HAS_ACTIVE_RENTAL"))
			.andExpect(jsonPath("$.error.details[0].field").value("rentals"))
			.andExpect(jsonPath("$.error.details[0].reason").value(reason));
		assertThat(items.findById(item.getId()).orElseThrow().getStatus()).isEqualTo(ItemStatus.AVAILABLE);
	}

	private void expectImageOrderInvalid(MockMultipartHttpServletRequestBuilder builder) throws Exception {
		mockMvc.perform(builder.header(HttpHeaders.AUTHORIZATION, bearerToken(ownerId())))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"))
			.andExpect(jsonPath("$.error.details[0].field").value("imageOrder"));
		assertThat(images.findAllByItemIdOrderBySortOrderAscIdAsc(item.getId())).extracting(ItemImage::getId)
			.containsExactly(first.getId(), second.getId());
	}

	private Long ownerId() {
		return owner.member().getId();
	}

	private MockMultipartHttpServletRequestBuilder update(String json) {
		return multipart(HttpMethod.PATCH, "/api/items/{itemId}", item.getId()).file(request(json));
	}

	private ItemImage saveImage(int sortOrder) {
		byte[] content = png();
		var original = storage.upload("items", "png", "image/png", content);
		var thumbnail = storage.upload("items/thumbnails", "png", "image/png", content);
		return images.save(ItemImage.create(item.getId(), original.url(), thumbnail.url(), sortOrder));
	}

	private long countUploadedFiles() throws IOException {
		Path root = storageProperties.rootDir().resolve("items");
		if (!Files.exists(root)) {
			return 0;
		}
		try (Stream<Path> paths = Files.walk(root)) {
			return paths.filter(Files::isRegularFile).count();
		}
	}

	private static MockMultipartFile request(String json) {
		return new MockMultipartFile("request", "request.json", "application/json",
			json.getBytes(StandardCharsets.UTF_8));
	}

	private static MockMultipartFile image() {
		return new MockMultipartFile("images", "test.png", "image/png", png());
	}

	private static byte[] png() {
		try {
			var out = new ByteArrayOutputStream();
			ImageIO.write(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "png", out);
			return out.toByteArray();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}
}
