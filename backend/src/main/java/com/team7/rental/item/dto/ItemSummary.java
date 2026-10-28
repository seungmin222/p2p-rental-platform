package com.team7.rental.item.dto;

/**
 * 다른 도메인 화면(대여 상세 등)에 보여 줄 물품 요약. 삭제된 물품도 반환한다.
 *
 * @param thumbnailUrl 대표 이미지 썸네일. 이미지가 없으면 null
 */
public record ItemSummary(Long itemId, String name, String thumbnailUrl, boolean isDeleted) {
}
