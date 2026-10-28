package com.team7.rental.member.entity;

/** 동네 범위 (docs/state-machines.md §5). 거리 값은 policies.md region.range. REQ-042 전에는 NORMAL 고정. */
public enum RegionRange {
	NEAR, NORMAL, FAR
}
