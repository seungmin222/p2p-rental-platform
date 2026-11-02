package com.team7.rental.item.service;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;

import com.team7.rental.global.storage.FileStorage;
import com.team7.rental.item.repository.CategoryRepository;
import com.team7.rental.item.repository.ItemAvailablePeriodRepository;
import com.team7.rental.item.repository.ItemImageRepository;
import com.team7.rental.item.repository.ItemRepository;
import com.team7.rental.support.IntegrationTestSupport;

class ItemCreateIntegrationTest extends IntegrationTestSupport {
	@Autowired
	private ItemRepository items;
	@Autowired
	private ItemImageRepository images;
	@Autowired
	private ItemAvailablePeriodRepository periods;
	@Autowired
	private CategoryRepository categories;
	@Autowired
	private FileStorage storage;
	private final List<String> uploadedUrls = new ArrayList<>();
	private Long memberId;
	private Long regionId;
	private Long categoryId;

	@BeforeEach
	void setUp() {
		testClock.fixAt(LocalDateTime.of(2026, 11, 2, 12, 0));
		var owner = fixture.createVerifiedMember();
		memberId = owner.member().getId();
		regionId = owner.region().getId();
		categoryId = categories.findAll().getFirst().getId();
	}

	@AfterEach
	void deleteUploadedFiles() {
		for (String url : uploadedUrls) {
			storage.delete(storage.keyOf(url));
		}
	}

	@Test
	void createsAvailableItemWithImagesAndPeriods() throws Exception {
		mockMvc.perform(multipart("/api/items").file(request(validRequest())).file(image())
			.header(HttpHeaders.AUTHORIZATION, bearerToken(memberId)))
			.andExpect(status().isCreated()).andExpect(jsonPath("$.data.itemId").isNumber());
		var item = items.findAll().getFirst();
		for (var image : images.findAll()) {
			uploadedUrls.add(image.getUrl());
			uploadedUrls.add(image.getThumbnailUrl());
		}
		assertThat(item.getRegionId()).isEqualTo(regionId);
		assertThat(item.getOwnerId()).isEqualTo(memberId);
		assertThat(item.getStatus().name()).isEqualTo("AVAILABLE");
		assertThat(item.getItemPrice()).isEqualTo(200000);
		assertThat(images.findAll()).hasSize(1);
		assertThat(images.findAll().getFirst().getSortOrder()).isZero();
		assertThat(periods.findAllByItemIdOrderByStartDate(item.getId())).hasSize(2);
	}

	@Test
	void unverifiedMemberCannotRegister() throws Exception {
		var unverified = fixture.createMember();
		mockMvc.perform(multipart("/api/items").file(request(validRequest())).file(image())
			.header(HttpHeaders.AUTHORIZATION, bearerToken(unverified.getId())))
			.andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("REGION_NOT_VERIFIED"));
		assertThat(items.count()).isZero();
	}

	@Test
	void missingImageIsRejected() throws Exception {
		mockMvc.perform(multipart("/api/items").file(request(validRequest()))
			.header(HttpHeaders.AUTHORIZATION, bearerToken(memberId)))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("IMAGE_COUNT_INVALID"));
	}

	@Test
	void invalidImageIsRejected() throws Exception {
		mockMvc.perform(multipart("/api/items").file(request(validRequest()))
			.file(new MockMultipartFile("images", "fake.jpg", "image/jpeg", new byte[] {1, 2, 3}))
			.header(HttpHeaders.AUTHORIZATION, bearerToken(memberId)))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("IMAGE_INVALID_TYPE"));
		assertThat(items.count()).isZero();
	}

	@Test
	void overlappingInclusivePeriodsAreRejected() throws Exception {
		mockMvc.perform(multipart("/api/items").file(request(validRequest().replace("2026-11-06", "2026-11-05")))
			.file(image()).header(HttpHeaders.AUTHORIZATION, bearerToken(memberId)))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"));
		assertThat(items.count()).isZero();
	}

	@Test
	void unknownCategoryIsRejected() throws Exception {
		categoryId = Long.MAX_VALUE;
		mockMvc.perform(multipart("/api/items").file(request(validRequest())).file(image())
			.header(HttpHeaders.AUTHORIZATION, bearerToken(memberId)))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.details[0].field").value("categoryId"));
	}

	private String validRequest() {
		return """
			{"name":"테스트 텐트","description":"사진과 기간을 저장합니다","categoryId":%d,
			"condition":"LIKE_NEW","pricePerDay":10000,"itemPrice":200000,"deposit":50000,
			"availablePeriods":[{"startDate":"2026-11-03","endDate":"2026-11-05"},
			{"startDate":"2026-11-06","endDate":"2026-11-08"}],
			"address":"서울 강남구","latitude":37.5,"longitude":127.03}
			""".formatted(categoryId);
	}

	private MockMultipartFile request(String json) {
		return new MockMultipartFile("request", "request.json", "application/json",
			json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}

	private MockMultipartFile image() throws Exception {
		var out = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "png", out);
		return new MockMultipartFile("images", "test.png", "image/png", out.toByteArray());
	}
}
