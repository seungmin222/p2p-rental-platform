package com.team7.rental.item.entity;

/**
 * 내 물품 관리 탭 (REQ-020, state-machines.md UI 탭 매핑). RENTING = RENTING·OVERDUE 대여가 있는 물품, AVAILABLE = 그 외 공개 물품,
 * HIDDEN = 숨김 물품(숨기기 REQ-044 전에는 항상 빈 목록). DELETED 물품은 어느 탭에도 없다.
 */
public enum MyItemTab {
	AVAILABLE, RENTING, HIDDEN
}
