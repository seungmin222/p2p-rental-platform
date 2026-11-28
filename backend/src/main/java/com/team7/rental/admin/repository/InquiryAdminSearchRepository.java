package com.team7.rental.admin.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.team7.rental.admin.entity.Inquiry;
import com.team7.rental.admin.entity.InquiryStatus;
import com.team7.rental.admin.entity.InquiryType;

/** 관리자 문의 목록 (REQ-057). 처리 상태·종류 조건이 선택적으로 조합된다 */
public interface InquiryAdminSearchRepository {

	/** 접수 순(id 오름차순). status·type이 null이면 조건을 빼고 모두 */
	Page<Inquiry> searchForAdmin(InquiryStatus status, InquiryType type, Pageable pageable);
}
