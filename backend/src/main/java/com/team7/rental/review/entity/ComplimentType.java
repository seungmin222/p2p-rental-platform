package com.team7.rental.review.entity;

/**
 * 후기 칭찬 항목 (docs/state-machines.md ComplimentType). 대여자→제공자, 제공자→대여자 모두 같은 목록을 쓴다(open-issues #15).
 * 화면 표시: ON_TIME 시간 약속을 잘 지켜요, AS_DESCRIBED 물품 상태가 설명과 같아요, CLEAN_USE 깨끗하게 사용했어요
 */
public enum ComplimentType {
	ON_TIME, AS_DESCRIBED, CLEAN_USE
}
