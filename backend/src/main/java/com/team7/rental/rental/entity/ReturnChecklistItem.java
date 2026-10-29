package com.team7.rental.rental.entity;

/** 반납 체크리스트 항목 (docs/state-machines.md §5, REQ-018). 반납 요청은 항목마다 true/false를 모두 보낸다 */
public enum ReturnChecklistItem {
	NO_EXTERIOR_DAMAGE, ALL_PARTS_INCLUDED, NO_CONTAMINATION, WORKS_NORMALLY, CONSUMABLES_RESTORED
}
