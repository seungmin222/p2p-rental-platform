package com.team7.rental.admin.entity;

import com.team7.rental.global.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 문의·분쟁 (REQ-063). 처리(답변·상태 변경)는 관리자 기능(REQ-057)에서 메서드를 더한다 */
@Getter
@Entity
@Table(name = "inquiries")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inquiry extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long memberId;

	private Long rentalId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private InquiryType type;

	@Column(nullable = false, length = 1000)
	private String content;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private InquiryStatus status;

	/** 접수(RECEIVED). 분쟁의 rentalId 필수 검사는 서비스가 한다 */
	public static Inquiry receive(Long memberId, Long rentalId, InquiryType type, String content) {
		Inquiry inquiry = new Inquiry();
		inquiry.memberId = memberId;
		inquiry.rentalId = rentalId;
		inquiry.type = type;
		inquiry.content = content;
		inquiry.status = InquiryStatus.RECEIVED;
		return inquiry;
	}
}
