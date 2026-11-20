package com.team7.rental.member.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team7.rental.member.entity.TermsAgreement;

public interface TermsAgreementRepository extends JpaRepository<TermsAgreement, Long> {

	/** 회원의 동의 이력 전부(재동의 필요 판정 REQ-037). 유니크 (member_id, …) 사용. 회원당 몇 행이라 종류로 거르지 않는다 */
	List<TermsAgreement> findByMemberId(Long memberId);
}
