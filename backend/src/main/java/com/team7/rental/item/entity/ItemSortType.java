package com.team7.rental.item.entity;

/** 물품 검색 정렬 (REQ-010). ACCURACY = 이름 일치 > 설명 일치 > 최신순. 거리순(REQ-065)은 3순위 */
public enum ItemSortType {
	ACCURACY, LATEST, PRICE_ASC
}
