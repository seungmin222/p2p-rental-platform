package com.team7.rental.item.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import com.team7.rental.global.lock.RentalLockStrategy;
import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.global.storage.FileStorage;
import com.team7.rental.global.storage.ImageFormat;
import com.team7.rental.global.storage.ImageProcessor;
import com.team7.rental.global.storage.ProcessedImage;
import com.team7.rental.global.storage.StoredFile;
import com.team7.rental.item.dto.ItemCreateRequest;
import com.team7.rental.item.entity.ItemCondition;
import com.team7.rental.member.dto.MemberResponse;
import com.team7.rental.member.service.MemberQueryService;

import jakarta.validation.Validator;

class ItemCreateCleanupTest {
	private final FileStorage storage = mock(FileStorage.class);
	private final ImageProcessor processor = mock(ImageProcessor.class);
	private final MemberQueryService members = mock(MemberQueryService.class);
	private final CategoryService categories = mock(CategoryService.class);
	private final ItemCreateWriter writer = mock(ItemCreateWriter.class);
	private final Validator validator = mock(Validator.class);
	private ItemService service;
	private final ItemCreateRequest request = new ItemCreateRequest("텐트", "설명", 1L,
		ItemCondition.LIKE_NEW, 1000, 10000, 1000, List.of(), "주소", 37.5, 127.0);

	@BeforeEach
	void setUp() {
		var policies = mock(PolicyProperties.class);
		when(policies.item()).thenReturn(new PolicyProperties.Item(50, 2000,
			new PolicyProperties.Item.Amount(1000, 1000000), new PolicyProperties.Item.Amount(1000, 3000000),
			10000000, new PolicyProperties.Item.Images(1, 10, DataSize.ofMegabytes(10))));
		service = new ItemService(members, categories, processor, storage, writer, mock(ItemUpdateWriter.class),
			mock(ItemDeleteWriter.class), mock(ItemQueryService.class), mock(RentalLockStrategy.class), validator,
			policies);
		when(validator.validate(request)).thenReturn(Set.of());
		when(members.getMe(1L)).thenReturn(new MemberResponse(1L, "", "", null, null,
			new MemberResponse.PrimaryRegion(1L, 1L, "동네", true, null, null), null));
		when(processor.process(any(), any())).thenReturn(
			new ProcessedImage(ImageFormat.PNG, new byte[] {1}, new byte[] {2}));
	}

	@Test
	void thumbnailFailureDeletesAlreadyUploadedOriginal() {
		var failure = new IllegalStateException("thumbnail upload failed");
		when(storage.upload(anyString(), anyString(), anyString(), any()))
			.thenReturn(new StoredFile("items/original.png", "/files/items/original.png"))
			.thenThrow(failure);
		assertThatThrownBy(this::create).isSameAs(failure);
		verify(storage).delete("items/original.png");
		verifyNoInteractions(writer);
	}

	@Test
	void databaseFailureDeletesBothUploadsAndPreservesOriginalException() {
		var failure = new IllegalStateException("DB commit failed");
		when(storage.upload(anyString(), anyString(), anyString(), any()))
			.thenReturn(new StoredFile("items/original.png", "/files/items/original.png"))
			.thenReturn(new StoredFile("items/thumb.png", "/files/items/thumb.png"));
		when(writer.create(eq(1L), eq(request), any())).thenThrow(failure);
		doThrow(new IllegalStateException("cleanup failed")).when(storage).delete("items/original.png");
		assertThatThrownBy(this::create).isSameAs(failure);
		verify(storage).delete("items/original.png");
		verify(storage).delete("items/thumb.png");
	}

	private void create() {
		service.create(1L, request,
			List.of(new MockMultipartFile("images", "test.png", "image/png", new byte[] {1})));
	}
}
