package com.team7.rental.item.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.team7.rental.item.dto.ItemCreateRequest;
import com.team7.rental.item.dto.ItemCreateResponse;
import com.team7.rental.item.dto.ItemEditResponse;
import com.team7.rental.item.dto.ItemUpdateRequest;

import lombok.RequiredArgsConstructor;

/**
 * 물품 쓰기: 등록(REQ-006), 수정(REQ-007), 삭제(REQ-008). 미구현: 응답 형태만 확정.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ItemService {

	/** 대표 동네 인증 필요. 이미지 업로드는 트랜잭션 밖에서 */
	public ItemCreateResponse create(Long memberId, ItemCreateRequest request, List<MultipartFile> images) {
		throw new UnsupportedOperationException("미구현: feat/item-crud");
	}

	/** 대여 가능 기간 변경은 예약과 같은 락(lock:item:{itemId}) 안에서 검사·저장 */
	public ItemEditResponse update(Long itemId, Long memberId, ItemUpdateRequest request,
		List<MultipartFile> images) {
		throw new UnsupportedOperationException("미구현: feat/item-crud");
	}

	/** 소프트 삭제(status=DELETED) */
	@Transactional
	public void delete(Long itemId, Long memberId) {
		throw new UnsupportedOperationException("미구현: feat/item-crud");
	}
}
