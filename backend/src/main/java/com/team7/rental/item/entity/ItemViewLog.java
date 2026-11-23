package com.team7.rental.item.entity;

import java.time.LocalDateTime;

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

/**
 * 최근 본 상품 기록 (REQ-035). (회원, 물품)당 1개(UK member_id+item_id). 쓰기는 ItemViewLogRepository의 upsert 네이티브 쿼리로만 하고,
 * 이 엔티티는 스키마 검증(ddl-auto validate)과 조회에 쓴다.
 */
@Getter
@Entity
@Table(name = "item_view_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemViewLog extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long memberId;

	@Column(nullable = false)
	private Long itemId;

	@Column(nullable = false)
	private LocalDateTime viewedAt;
}
