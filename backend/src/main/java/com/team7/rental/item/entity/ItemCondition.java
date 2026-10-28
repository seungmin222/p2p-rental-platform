package com.team7.rental.item.entity;

/** 물품 상태 등급 (docs/state-machines.md §5). 컬럼명은 예약어를 피해 item_condition. */
public enum ItemCondition {
	LIKE_NEW, LIGHTLY_USED, USED
}
