package com.team7.rental.member.entity;

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
 * 행정동 마스터. 데이터는 REQ-004의 적재 마이그레이션으로 넣는다(출처는 docs/policies.md §2 동네 데이터).
 */
@Getter
@Entity
@Table(name = "regions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Region extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 행정동 코드(10자리) */
	@Column(nullable = false, length = 10)
	private String code;

	@Column(nullable = false, length = 30)
	private String name;

	@Column(nullable = false, length = 20)
	private String sido;

	@Column(nullable = false, length = 30)
	private String sigungu;

	@Column(nullable = false)
	private double centerLat;

	@Column(nullable = false)
	private double centerLng;

	public static Region create(String code, String name, String sido, String sigungu, double centerLat,
		double centerLng) {
		Region region = new Region();
		region.code = code;
		region.name = name;
		region.sido = sido;
		region.sigungu = sigungu;
		region.centerLat = centerLat;
		region.centerLng = centerLng;
		return region;
	}
}
