package com.team7.rental.item.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.global.lock.RentalLockStrategy;
import com.team7.rental.global.response.FieldErrorDetail;
import com.team7.rental.global.storage.FileStorage;
import com.team7.rental.global.storage.ImageProcessor;
import com.team7.rental.global.storage.StoredFile;
import com.team7.rental.item.dto.ItemCreateRequest;
import com.team7.rental.item.dto.ItemCreateResponse;
import com.team7.rental.item.dto.ItemEditResponse;
import com.team7.rental.item.dto.ItemUpdateRequest;
import com.team7.rental.item.entity.ItemImage;
import com.team7.rental.member.service.MemberQueryService;

import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 물품 쓰기: 등록(REQ-006), 수정(REQ-007), 삭제(REQ-008).
 * 세 메서드 모두 외부 호출(이미지 업로드) 또는 물품 락(lock:item:{itemId})을 먼저 거치므로 클래스에 트랜잭션을 걸지 않는다.
 * 저장은 락·외부 호출 뒤 별도 빈의 트랜잭션에서 한다(backend/CLAUDE.md 트랜잭션·동시성).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ItemService {
	private final MemberQueryService members;
	private final CategoryService categories;
	private final ImageProcessor imageProcessor;
	private final FileStorage storage;
	private final ItemCreateWriter writer;
	private final ItemUpdateWriter updateWriter;
	private final ItemDeleteWriter deleteWriter;
	private final ItemQueryService itemQueryService;
	private final RentalLockStrategy rentalLockStrategy;
	private final Validator validator;
	private final com.team7.rental.global.policy.PolicyProperties policies;

	/** 대표 동네 인증 필요 */
	// 외부 호출(이미지 업로드) 후 별도 트랜잭션에서 저장
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public ItemCreateResponse create(Long memberId, ItemCreateRequest request, List<MultipartFile> images) {
		var violations = validator.validate(request);
		if (!violations.isEmpty()) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT, violations.stream()
				.map(v -> new FieldErrorDetail(v.getPropertyPath().toString(), v.getMessage())).toList());
		}
		var primary = members.getMe(memberId).primaryRegion();
		if (primary == null || !primary.isVerified()) {
			throw new BusinessException(ErrorCode.REGION_NOT_VERIFIED);
		}
		if (images == null || images.size() < policies.item().images().min()
			|| images.size() > policies.item().images().max()) {
			throw new BusinessException(ErrorCode.IMAGE_COUNT_INVALID);
		}
		categories.requireActive(request.categoryId());
		List<StoredFile> stored = new ArrayList<>();
		try {
			List<ItemCreateWriter.UploadedImage> uploaded = new ArrayList<>();
			for (MultipartFile file : images) {
				UploadedPair pair = upload(file, stored);
				uploaded.add(new ItemCreateWriter.UploadedImage(pair.original().url(), pair.thumbnail().url()));
			}
			return writer.create(memberId, request, uploaded);
		} catch (RuntimeException failure) {
			deleteQuietly(stored.stream().map(StoredFile::key).toList(), "물품 등록 실패 후 파일 정리 실패");
			throw failure;
		}
	}

	/**
	 * 검사 순서(컨트롤러 설명과 같다): request part 검증 400(컨트롤러) → 404 → 403 → 이미지 400 → 카테고리 400 → 업로드 → 락 → 409.
	 * 대여 가능 기간 변경은 예약과 같은 락(lock:item:{itemId}) 안에서 검사·저장한다. 기간을 보내지 않아도 락을 잡아 같은 물품의 수정을
	 * 직렬화한다. 새 이미지는 락 전에 올리고 실패하면 지운다. 빠진 기존 이미지 파일은 커밋 뒤 지우고, 실패하면 로그만 남긴다.
	 */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public ItemEditResponse update(Long itemId, Long memberId, ItemUpdateRequest request,
		List<MultipartFile> images) {
		List<MultipartFile> files = images == null ? List.of() : images;
		itemQueryService.getOwnedItem(itemId, memberId);
		validateImageParts(itemId, request.imageOrder(), files);
		if (request.categoryId() != null) {
			categories.requireActive(request.categoryId());
		}
		List<StoredFile> stored = new ArrayList<>();
		// writer가 반환했다면 커밋된 것이다. 그 뒤 락 해제가 실패해도 커밋된 행이 새 파일을 가리키므로 지우지 않는다.
		AtomicBoolean committed = new AtomicBoolean();
		ItemUpdateWriter.Result result;
		try {
			List<ItemUpdateWriter.UploadedImage> uploaded = new ArrayList<>();
			for (MultipartFile file : files) {
				UploadedPair pair = upload(file, stored);
				uploaded.add(new ItemUpdateWriter.UploadedImage(pair.original().url(), pair.thumbnail().url()));
			}
			result = rentalLockStrategy.executeWithItemLock(itemId, () -> {
				ItemUpdateWriter.Result written = updateWriter.update(itemId, memberId, request, uploaded);
				committed.set(true);
				return written;
			});
		} catch (RuntimeException failure) {
			if (!committed.get()) {
				deleteQuietly(stored.stream().map(StoredFile::key).toList(), "물품 수정 실패 후 파일 정리 실패");
			}
			throw failure;
		}
		List<String> removedKeys = new ArrayList<>();
		for (ItemImage image : result.removedImages()) {
			removedKeys.add(storage.keyOf(image.getUrl()));
			removedKeys.add(storage.keyOf(image.getThumbnailUrl()));
		}
		deleteQuietly(removedKeys, "물품 수정 후 빠진 이미지 파일 삭제 실패");
		return result.response();
	}

	/**
	 * 소프트 삭제(status=DELETED). 진행 중 대여 검사와 삭제를 예약과 같은 락(lock:item:{itemId}) 안에서 한다.
	 * 404·403은 락 밖에서 먼저 보고(락 점유를 줄이려고), 락 안 트랜잭션에서 다시 확인한다.
	 */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public void delete(Long itemId, Long memberId) {
		itemQueryService.getOwnedItem(itemId, memberId);
		rentalLockStrategy.executeWithItemLock(itemId, () -> {
			deleteWriter.delete(itemId, memberId);
			return null;
		});
	}

	/**
	 * imageOrder와 images 파트의 대응(open-issues #19). imageId가 이 물품 것인지는 여기서 한 번, 락 안에서 한 번 더 본다.
	 *
	 * @throws BusinessException COMMON_INVALID_INPUT(details imageOrder), IMAGE_COUNT_INVALID
	 */
	private void validateImageParts(Long itemId, List<ItemUpdateRequest.ImageOrder> imageOrder,
		List<MultipartFile> files) {
		if (imageOrder == null) {
			if (!files.isEmpty()) {
				throw invalidImageOrder("imageOrder 없이 이미지 파일을 보낼 수 없습니다.");
			}
			return;
		}
		Set<Integer> referenced = new HashSet<>();
		for (ItemUpdateRequest.ImageOrder slot : imageOrder) {
			if (slot.newImageIndex() != null) {
				if (slot.newImageIndex() >= files.size()) {
					throw invalidImageOrder("newImageIndex에 해당하는 이미지 파일이 없습니다.");
				}
				referenced.add(slot.newImageIndex());
			}
		}
		if (referenced.size() != files.size()) {
			throw invalidImageOrder("imageOrder가 가리키지 않는 이미지 파일이 있습니다.");
		}
		ItemUpdateWriter.validateImageIds(imageOrder, itemQueryService.getImages(itemId));
		if (imageOrder.size() < policies.item().images().min()
			|| imageOrder.size() > policies.item().images().max()) {
			throw new BusinessException(ErrorCode.IMAGE_COUNT_INVALID);
		}
	}

	private BusinessException invalidImageOrder(String reason) {
		return new BusinessException(ErrorCode.COMMON_INVALID_INPUT,
			List.of(new FieldErrorDetail("imageOrder", reason)));
	}

	private record UploadedPair(StoredFile original, StoredFile thumbnail) {
	}

	// 원본·썸네일을 올리고 올린 파일을 stored에 기록한다(실패하면 호출하는 쪽이 stored를 정리)
	private UploadedPair upload(MultipartFile file, List<StoredFile> stored) {
		var image = imageProcessor.process(file, policies.item().images().maxFileSize());
		var original = storage.upload("items", image.format().getExtension(), image.format().getContentType(),
			image.original());
		stored.add(original);
		var thumbnail = storage.upload("items/thumbnails", image.format().getExtension(),
			image.format().getContentType(), image.thumbnail());
		stored.add(thumbnail);
		return new UploadedPair(original, thumbnail);
	}

	// 파일 정리 실패는 로그만 남기고 원래 결과(또는 원래 예외)를 지킨다
	private void deleteQuietly(List<String> keys, String message) {
		for (String key : keys) {
			try {
				storage.delete(key);
			} catch (RuntimeException cleanupFailure) {
				log.warn("{}: key={}", message, key, cleanupFailure);
			}
		}
	}
}
