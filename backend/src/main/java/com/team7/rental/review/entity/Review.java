package com.team7.rental.review.entity;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

import com.team7.rental.global.entity.BaseTimeEntity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 거래 후기 (REQ-034). (rental_id, writer_id)당 1건(유니크). 작성 뒤 수정·삭제하지 않는다. 신뢰 점수(REQ-054)는 이 행의 satisfaction·칭찬 항목으로
 * 다시 계산할 수 있다(source_id = id).
 */
@Getter
@Entity
@Table(name = "reviews")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long rentalId;

	@Column(nullable = false)
	private Long writerId;

	@Column(nullable = false)
	private Long targetId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private Satisfaction satisfaction;

	@Column(length = 300)
	private String content;

	@ElementCollection
	@CollectionTable(name = "review_compliments", joinColumns = @JoinColumn(name = "review_id"))
	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false, length = 20)
	private Set<ComplimentType> compliments = EnumSet.noneOf(ComplimentType.class);

	/**
	 * @param compliments 중복은 하나로 합친다. 비어 있어도 된다
	 * @param content 없으면 null (공백만이면 호출하는 쪽이 null로 바꿔 넘긴다)
	 */
	public static Review write(Long rentalId, Long writerId, Long targetId, Satisfaction satisfaction,
		Collection<ComplimentType> compliments, String content) {
		Review review = new Review();
		review.rentalId = rentalId;
		review.writerId = writerId;
		review.targetId = targetId;
		review.satisfaction = satisfaction;
		review.content = content;
		if (!compliments.isEmpty()) {
			review.compliments = EnumSet.copyOf(compliments);
		}
		return review;
	}
}
