package com.team7.rental.item.dto;

import com.team7.rental.item.entity.MyItemTab;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 내 물품 관리 쿼리 파라미터 (REQ-020). 페이지 크기는 policies.md my-items.page로 고정이고 size를 받지 않는다.
 *
 * @param tab 생략하면 AVAILABLE
 * @param page 생략하면 0
 */
public record MyItemRequest(
	@Schema(description = "탭. 생략하면 AVAILABLE. HIDDEN은 숨기기(REQ-044) 전에는 항상 빈 목록", defaultValue = "AVAILABLE")
	MyItemTab tab,

	@Schema(description = "페이지 번호 (0부터). 생략하면 0", defaultValue = "0", example = "0") @PositiveOrZero
	Integer page) {

	public MyItemRequest {
		if (tab == null) {
			tab = MyItemTab.AVAILABLE;
		}
		if (page == null) {
			page = 0;
		}
	}
}
