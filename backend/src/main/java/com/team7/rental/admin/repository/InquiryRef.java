package com.team7.rental.admin.repository;

import com.team7.rental.admin.entity.InquiryType;

/** 처리 전 락 순서를 정하는 데 쓰는 문의의 바뀌지 않는 값 (REQ-057) */
public record InquiryRef(Long inquiryId, InquiryType type, Long rentalId) {

	public boolean isDispute() {
		return type == InquiryType.DISPUTE;
	}
}
