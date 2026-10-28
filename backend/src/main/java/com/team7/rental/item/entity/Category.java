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

/**
 * 카테고리. 초기 데이터는 V3__insert_categories.sql(docs/policies.md §3). 삭제 대신 isActive=false.
 */
@Getter
@Entity
@Table(name = "categories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** AI 분류 선택지로도 쓰는 코드 (예: CAMPING) */
	@Column(nullable = false, length = 30)
	private String code;

	@Column(nullable = false, length = 30)
	private String name;

	/** 미사용(하위 카테고리 확장용) */
	private Long parentId;

	@Column(nullable = false)
	private int sortOrder;

	@Column(nullable = false)
	private boolean isActive;
}
