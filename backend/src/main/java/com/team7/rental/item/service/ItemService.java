package com.team7.rental.item.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.global.response.FieldErrorDetail;
import com.team7.rental.global.storage.FileStorage;
import com.team7.rental.global.storage.ImageProcessor;
import com.team7.rental.global.storage.StoredFile;
import com.team7.rental.item.dto.ItemCreateRequest;
import com.team7.rental.item.dto.ItemCreateResponse;
import com.team7.rental.item.dto.ItemEditResponse;
import com.team7.rental.item.dto.ItemUpdateRequest;
import com.team7.rental.member.service.MemberQueryService;

import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 물품 쓰기: 등록(REQ-006), 수정(REQ-007), 삭제(REQ-008). 등록 구현. 수정·삭제는 다음 작업에서 구현.
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
		List<ItemCreateWriter.UploadedImage> uploaded = new ArrayList<>();
		try {
			for (MultipartFile file : images) {
				var image = imageProcessor.process(file, policies.item().images().maxFileSize());
				var original = storage.upload("items", image.format().getExtension(),
					image.format().getContentType(), image.original());
				stored.add(original);
				var thumbnail = storage.upload("items/thumbnails", image.format().getExtension(),
					image.format().getContentType(), image.thumbnail());
				stored.add(thumbnail);
				uploaded.add(new ItemCreateWriter.UploadedImage(original.url(), thumbnail.url()));
			}
			return writer.create(memberId, request, uploaded);
		} catch (RuntimeException failure) {
			for (StoredFile file : stored) {
				try {
					storage.delete(file.key());
				} catch (RuntimeException cleanupFailure) {
					log.warn("물품 등록 실패 후 파일 정리 실패: key={}", file.key(), cleanupFailure);
				}
			}
			throw failure;
		}
	}

	/** 대여 가능 기간 변경은 예약과 같은 락(lock:item:{itemId}) 안에서 검사·저장 */
	// 외부 호출(새 이미지 업로드) 후 락 → 별도 트랜잭션에서 저장
	public ItemEditResponse update(Long itemId, Long memberId, ItemUpdateRequest request,
		List<MultipartFile> images) {
		throw new UnsupportedOperationException("미구현: feat/item-crud");
	}

	/** 소프트 삭제(status=DELETED). 진행 중 대여 검사와 삭제를 예약과 같은 락(lock:item:{itemId}) 안에서 한다 */
	// 락 → 별도 트랜잭션에서 검사·저장
	public void delete(Long itemId, Long memberId) {
		throw new UnsupportedOperationException("미구현: feat/item-crud");
	}
}
