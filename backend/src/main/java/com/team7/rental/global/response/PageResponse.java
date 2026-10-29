package com.team7.rental.global.response;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 페이지 기반 목록 응답 (docs/architecture.md 페이지네이션). 검색·목록 API가 같이 쓴다. 피드처럼 커서 기반이면 CursorResponse를 쓴다.
 */
public record PageResponse<T>(
	@Schema(description = "이 페이지의 항목")
	List<T> items,

	@Schema(description = "페이지 번호 (0부터)", example = "0")
	int page,

	@Schema(description = "페이지 크기", example = "20")
	int size,

	@Schema(description = "전체 항목 수", example = "21")
	long totalElements,

	@Schema(description = "다음 페이지가 있으면 true")
	boolean hasNext) {

	public static <T> PageResponse<T> of(Page<T> page) {
		return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
			page.hasNext());
	}

	/** 조회 결과(행)를 응답 항목으로 바꿔 담는다 */
	public static <S, T> PageResponse<T> of(Page<S> page, Function<? super S, ? extends T> mapper) {
		return of(page.map(mapper));
	}
}
