package com.team7.rental.admin.entity;

import java.time.LocalDateTime;

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

/** 문의·분쟁. 접수(REQ-063), 관리자 처리(답변·상태 변경, REQ-057) */
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

	@Column(length = 1000)
	private String answer;

	private LocalDateTime answeredAt;

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

	/**
	 * 관리자 처리 (REQ-057, state-machines §5): RECEIVED → IN_PROGRESS, RECEIVED·IN_PROGRESS → RESOLVED. RESOLVED는 끝이다.
	 * 답변을 주면 덮어쓰고 answeredAt을 now로 한다. 답변 글자 수와 RESOLVED의 답변 필수는 서비스가 먼저 검사한다.
	 *
	 * @throws IllegalStateException 허용하지 않는 전이이거나, RESOLVED로 바꾸는데 답변이 없을 때
	 */
	public void process(InquiryStatus target, String newAnswer, LocalDateTime now) {
		if (!canTransitTo(target)) {
			throw new IllegalStateException("처리할 수 없는 문의 상태 전이입니다: id=" + id + ", " + status + " → " + target);
		}
		if (newAnswer != null) {
			this.answer = newAnswer;
			this.answeredAt = now;
		}
		if (target == InquiryStatus.RESOLVED && this.answer == null) {
			throw new IllegalStateException("답변 없이 처리 완료할 수 없습니다: id=" + id);
		}
		this.status = target;
	}

	/** 허용 전이: RECEIVED → IN_PROGRESS·RESOLVED, IN_PROGRESS → RESOLVED. 같은 상태로는 바꾸지 않는다 */
	public boolean canTransitTo(InquiryStatus target) {
		return switch (status) {
			case RECEIVED -> target == InquiryStatus.IN_PROGRESS || target == InquiryStatus.RESOLVED;
			case IN_PROGRESS -> target == InquiryStatus.RESOLVED;
			case RESOLVED -> false;
		};
	}

	public boolean isDispute() {
		return type == InquiryType.DISPUTE;
	}
}
