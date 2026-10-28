package com.team7.rental.item.entity;

/** 물품 상태 (docs/state-machines.md §5). "대여 중"은 상태가 아니라 대여 건으로 파생한다. */
public enum ItemStatus {
	AVAILABLE, HIDDEN, DELETED
}
