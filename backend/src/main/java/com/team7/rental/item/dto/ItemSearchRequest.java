package com.team7.rental.item.dto;

import java.time.LocalDate;

import com.team7.rental.item.entity.ItemSortType;
import com.team7.rental.member.entity.RegionRange;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 물품 검색 쿼리 파라미터 (REQ-010). 페이지 크기는 policies.md search.page로 고정이고 size를 받지 않는다.
 * 기간 규칙(둘 다 보내거나 둘 다 생략, 시작일 ≤ 종료일)은 {@link ValidItemInput}.
 *
 * @param sort 생략하면 ACCURACY
 * @param page 생략하면 0
 */
@ValidItemInput
public record ItemSearchRequest(
	@Schema(description = "기준 행정동 ID. 생략하면 로그인 회원의 대표 동네(비회원·대표 동네 없음이면 400)", example = "1234")
	Long regionId,

	@Schema(description = "동네 범위(거리는 policies.md region.range). 동네 범위 설정(REQ-042) 전에는 무엇을 보내도 NORMAL로 처리한다")
	RegionRange range,

	@Schema(description = "검색어 (이름·설명)", example = "텐트")
	String keyword,

	@Schema(description = "카테고리 ID", example = "1")
	Long categoryId,

	@Schema(description = "일 대여료 최소(원)", example = "0") @PositiveOrZero
	Integer minPrice,

	@Schema(description = "일 대여료 최대(원)", example = "20000") @PositiveOrZero
	Integer maxPrice,

	@Schema(description = "대여 희망 시작일. 보내면 그 기간 전체가 가능 기간 하나에 들어가고 예약과 겹치지 않는 물품만", example = "2026-11-03")
	LocalDate startDate,

	@Schema(description = "대여 희망 종료일 (포함)", example = "2026-11-05")
	LocalDate endDate,

	@Schema(description = "대여 가능한 물품만. 기간을 보냈으면 그 기간 기준, 없으면 오늘 이후 가능 기간이 남아 있고 오늘이 예약되지 않은 물품 (open-issues #6)")
	Boolean availableOnly,

	@Schema(description = "정렬. 생략하면 ACCURACY(이름 일치 > 설명 일치 > 최신순)", defaultValue = "ACCURACY")
	ItemSortType sort,

	@Schema(description = "페이지 번호 (0부터). 생략하면 0", defaultValue = "0", example = "0") @PositiveOrZero
	Integer page) {

	public ItemSearchRequest {
		if (sort == null) {
			sort = ItemSortType.ACCURACY;
		}
		if (page == null) {
			page = 0;
		}
	}
}
