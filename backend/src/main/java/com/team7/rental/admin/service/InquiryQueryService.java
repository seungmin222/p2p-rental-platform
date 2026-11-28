package com.team7.rental.admin.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.admin.dto.AdminInquiryListRequest;
import com.team7.rental.admin.dto.AdminInquiryResponse;
import com.team7.rental.admin.dto.InquiryResponse;
import com.team7.rental.admin.repository.InquiryRepository;
import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.global.response.PageResponse;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class InquiryQueryService {

	private final InquiryRepository inquiryRepository;
	private final PolicyProperties policyProperties;

	/** 내 문의 목록 (REQ-063). 최신 접수 순, 처리 상태 포함 */
	public PageResponse<InquiryResponse> getMyInquiries(Long memberId, int page) {
		return PageResponse.of(inquiryRepository.findByMemberIdOrderByIdDesc(memberId,
			PageRequest.of(page, policyProperties.inquiry().page())), InquiryResponse::of);
	}

	/** 관리자 문의 목록 (REQ-057). 접수 순, 처리 상태·종류 조건 선택, 페이지 크기 inquiry.page */
	public PageResponse<AdminInquiryResponse> getForAdmin(AdminInquiryListRequest request) {
		return PageResponse.of(inquiryRepository.searchForAdmin(request.status(), request.type(),
			PageRequest.of(request.page(), policyProperties.inquiry().page())), AdminInquiryResponse::of);
	}
}
