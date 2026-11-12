package com.team7.rental.admin.entity;

/** 문의 종류 (state-machines.md §5). DISPUTE는 대여에 연결해야 하고 접수 시 그 대여의 정산을 보류한다 */
public enum InquiryType {
	GENERAL, DISPUTE
}
