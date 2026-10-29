package com.team7.rental.item.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.team7.rental.item.dto.ItemCreateRequest;
import com.team7.rental.item.dto.ItemCreateResponse;
import com.team7.rental.item.dto.ItemEditResponse;
import com.team7.rental.item.dto.ItemUpdateRequest;

import lombok.RequiredArgsConstructor;

/**
 * 물품 쓰기: 등록(REQ-006), 수정(REQ-007), 삭제(REQ-008). 미구현: 응답 형태만 확정.
 * 세 메서드 모두 외부 호출(이미지 업로드) 또는 물품 락(lock:item:{itemId})을 먼저 거치므로 클래스에 트랜잭션을 걸지 않는다.
 * 저장은 락·외부 호출 뒤 별도 빈의 트랜잭션에서 한다(backend/CLAUDE.md 트랜잭션·동시성).
 */
@Service
@RequiredArgsConstructor
public class ItemService {

	/** 대표 동네 인증 필요 */
	// 외부 호출(이미지 업로드) 후 별도 트랜잭션에서 저장
	public ItemCreateResponse create(Long memberId, ItemCreateRequest request, List<MultipartFile> images) {
		throw new UnsupportedOperationException("미구현: feat/item-crud");
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
