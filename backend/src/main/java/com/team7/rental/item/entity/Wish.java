package com.team7.rental.item.entity;

import com.team7.rental.global.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 관심(찜) (REQ-022). (회원, 물품)당 1개(UK member_id+item_id). 추가 시각은 createdAt이다 */
@Getter
@Entity
@Table(name = "wishes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Wish extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long memberId;

	@Column(nullable = false)
	private Long itemId;

	public static Wish of(Long memberId, Long itemId) {
		Wish wish = new Wish();
		wish.memberId = memberId;
		wish.itemId = itemId;
		return wish;
	}
}
