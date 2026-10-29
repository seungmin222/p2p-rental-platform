package com.team7.rental.rental.service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.global.storage.FileStorage;
import com.team7.rental.global.storage.ImageProcessor;
import com.team7.rental.global.storage.ProcessedImage;
import com.team7.rental.global.storage.StorageKeys;
import com.team7.rental.global.storage.StoredFile;
import com.team7.rental.rental.dto.RentalDetailResponse;
import com.team7.rental.rental.dto.RentalHandoverRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 인계·반납 기록 쓰기 (REQ-017·018). 트랜잭션을 열지 않고 사진을 다룬다.
 * <ol>
 *   <li>사전 검사(읽기 트랜잭션): 거절될 요청이면 사진을 처리하지 않는다</li>
 *   <li>장수 검사 + 전부 처리(검증·재인코딩): 여기서 실패하면 업로드한 파일이 없다</li>
 *   <li>업로드(원본만, 썸네일은 쓰지 않음)</li>
 *   <li>{@link RentalInspectionTxService} 호출. 예외(검증·409·커밋 실패)면 업로드한 파일을 지우고 다시 던진다</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RentalInspectionService {

	private final RentalInspectionTxService rentalInspectionTxService;
	private final ImageProcessor imageProcessor;
	private final FileStorage fileStorage;
	private final PolicyProperties policyProperties;

	/**
	 * 인계 확인 (REQ-017).
	 *
	 * @param request 생략하면 null(메모 없음)
	 * @throws BusinessException 사전 검사·트랜잭션의 대여 오류, IMAGE_COUNT_INVALID, IMAGE_INVALID_TYPE, IMAGE_SIZE_EXCEEDED
	 */
	public RentalDetailResponse handover(Long rentalId, Long memberId, RentalHandoverRequest request,
		List<MultipartFile> images) {
		rentalInspectionTxService.validateHandover(rentalId, memberId);
		List<ProcessedImage> processed = processImages(images, policyProperties.inspection().images().min());
		String memo = request == null ? null : request.normalizedMemo();
		return withUploaded(processed, urls -> rentalInspectionTxService.handover(rentalId, memberId, memo, urls));
	}

	/**
	 * min ~ inspection.images.max장을 검사하고 전부 처리한다. null은 0장.
	 *
	 * @param min 최소 장수(판정 '정상'은 0, 그 외 inspection.images.min)
	 */
	private List<ProcessedImage> processImages(List<MultipartFile> images, int min) {
		PolicyProperties.Inspection.Images policy = policyProperties.inspection().images();
		List<MultipartFile> files = images == null ? List.of() : images;
		if (files.size() < min || files.size() > policy.max()) {
			throw new BusinessException(ErrorCode.IMAGE_COUNT_INVALID);
		}
		return files.stream().map(file -> imageProcessor.process(file, policy.maxFileSize())).toList();
	}

	private <T> T withUploaded(List<ProcessedImage> images, Function<List<String>, T> action) {
		List<StoredFile> stored = new ArrayList<>();
		try {
			for (ProcessedImage image : images) {
				stored.add(fileStorage.upload(StorageKeys.INSPECTIONS, image.format().getExtension(),
					image.format().getContentType(), image.original()));
			}
			return action.apply(stored.stream().map(StoredFile::url).toList());
		} catch (RuntimeException e) {
			deleteQuietly(stored);
			throw e;
		}
	}

	// 정리 실패가 원래 예외를 가리지 않게 한다. 남은 파일은 DB가 참조하지 않는 고아 파일이다
	private void deleteQuietly(List<StoredFile> stored) {
		for (StoredFile file : stored) {
			try {
				fileStorage.delete(file.key());
			} catch (RuntimeException e) {
				log.warn("Failed to delete uploaded file: {}", file.key(), e);
			}
		}
	}
}
