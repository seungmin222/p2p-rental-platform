package com.team7.rental.admin.entity;

/** 문의 처리 상태 (state-machines.md §5). 접수 시 RECEIVED. IN_PROGRESS·RESOLVED로는 관리자(REQ-057)가 바꾼다 */
public enum InquiryStatus {
	RECEIVED, IN_PROGRESS, RESOLVED
}
