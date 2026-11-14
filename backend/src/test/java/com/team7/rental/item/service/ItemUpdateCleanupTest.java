package com.team7.rental.item.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import com.team7.rental.item.dto.ItemUpdateRequest;
import com.team7.rental.member.service.MemberQueryService;

import jakarta.validation.Validator;

/** 물품 수정(REQ-007)에서 새로 올린 파일을 언제 지우는지 */
class ItemUpdateCleanupTest {
	private final FileStorage storage = mock(FileStorage.class);
	private final ImageProcessor processor = mock(ImageProcessor.class);
	private final ItemUpdateWriter updateWriter = mock(ItemUpdateWriter.class);
	private final RentalLockStrategy lock = mock(RentalLockStrategy.class);
	private ItemService service;
	private final ItemUpdateRequest request = new ItemUpdateRequest(null, null, null, null, null, null, null, null,
		List.of(new ItemUpdateRequest.ImageOrder(null, 0)), null, null, null);

	@BeforeEach
	void setUp() {
		var policies = mock(PolicyProperties.class);
		when(policies.item()).thenReturn(new PolicyProperties.Item(50, 2000,
			new PolicyProperties.Item.Amount(1000, 1000000), new PolicyProperties.Item.Amount(1000, 3000000),
			10000000, new PolicyProperties.Item.Images(1, 10, DataSize.ofMegabytes(10))));
		service = new ItemService(mock(MemberQueryService.class), mock(CategoryService.class), processor, storage,
			mock(ItemCreateWriter.class), updateWriter, mock(ItemDeleteWriter.class), mock(ItemQueryService.class),
			lock, mock(Validator.class), policies);
		when(processor.process(any(), any())).thenReturn(
			new ProcessedImage(ImageFormat.PNG, new byte[] {1}, new byte[] {2}));
		when(storage.upload(anyString(), anyString(), anyString(), any()))
			.thenReturn(new StoredFile("items/original.png", "/files/items/original.png"))
			.thenReturn(new StoredFile("items/thumb.png", "/files/items/thumb.png"));
	}

	@Test
	@DisplayName("writer(트랜잭션)가 실패하면 새로 올린 파일을 지운다")
	void writerFailureDeletesNewUploads() {
		var failure = new IllegalStateException("DB commit failed");
		when(lock.executeWithItemLock(eq(1L), any())).thenAnswer(inv -> ((Supplier<?>)inv.getArgument(1)).get());
		when(updateWriter.update(eq(1L), eq(1L), eq(request), any())).thenThrow(failure);

		assertThatThrownBy(this::update).isSameAs(failure);
		verify(storage).delete("items/original.png");
		verify(storage).delete("items/thumb.png");
	}

	@Test
	@DisplayName("커밋 뒤 락 해제가 실패해도 커밋된 행이 가리키는 새 파일은 지우지 않는다")
	void unlockFailureAfterCommitKeepsNewUploads() {
		var unlockFailure = new IllegalStateException("unlock failed");
		when(lock.executeWithItemLock(eq(1L), any())).thenAnswer(inv -> {
			((Supplier<?>)inv.getArgument(1)).get();
			throw unlockFailure;
		});
		when(updateWriter.update(eq(1L), eq(1L), eq(request), any()))
			.thenReturn(new ItemUpdateWriter.Result(null, List.of()));

		assertThatThrownBy(this::update).isSameAs(unlockFailure);
		verify(updateWriter).update(eq(1L), eq(1L), eq(request), any());
		verify(storage, never()).delete(anyString());
	}

	private void update() {
		service.update(1L, 1L, request,
			List.of(new MockMultipartFile("images", "test.png", "image/png", new byte[] {1})));
	}
}
