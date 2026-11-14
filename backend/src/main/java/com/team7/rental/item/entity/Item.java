package com.team7.rental.item.entity;

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

/**
 * 대여 물품. 이미지(ItemImage)와 대여 가능 기간(ItemAvailablePeriod)은 itemId로 따로 저장한다.
 */
@Getter
@Entity
@Table(name = "items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Item extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long ownerId;

	/** 등록 시 소유자의 대표 동네 */
	@Column(nullable = false)
	private Long regionId;

	@Column(nullable = false)
	private Long categoryId;

	@Column(nullable = false, length = 50)
	private String name;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ItemCondition itemCondition;

	@Column(nullable = false)
	private int pricePerDay;

	@Column(nullable = false)
	private int deposit;

	/** 대여자에게 비공개. 권장 보증금 계산·D 등급 판단용(REQ-006) */
	@Column(nullable = false)
	private int itemPrice;

	/** 거래 장소 */
	@Column(nullable = false, length = 200)
	private String address;

	@Column(nullable = false)
	private double latitude;

	@Column(nullable = false)
	private double longitude;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ItemStatus status;

	@Column(nullable = false)
	private int viewCount;

	@Column(nullable = false)
	private int wishCount;

	@Column(nullable = false)
	private int chatCount;

	/** 피드 정렬 키 */
	@Column(nullable = false)
	private LocalDateTime bumpedAt;

	/**
	 * 등록(REQ-006). 입력 검증은 호출하는 서비스가 한다.
	 *
	 * @param now 등록 시각(Clock 기준). bumpedAt을 created_at과 같게 둔다
	 */
	public static Item create(Long ownerId, Long regionId, Long categoryId, String name, String description,
		ItemCondition itemCondition, int pricePerDay, int deposit, int itemPrice, String address, double latitude,
		double longitude, LocalDateTime now) {
		Item item = new Item();
		item.ownerId = ownerId;
		item.regionId = regionId;
		item.categoryId = categoryId;
		item.name = name;
		item.description = description;
		item.itemCondition = itemCondition;
		item.pricePerDay = pricePerDay;
		item.deposit = deposit;
		item.itemPrice = itemPrice;
		item.address = address;
		item.latitude = latitude;
		item.longitude = longitude;
		item.status = ItemStatus.AVAILABLE;
		item.bumpedAt = now;
		return item;
	}

	public boolean isOwnedBy(Long memberId) {
		return ownerId.equals(memberId);
	}

	public boolean isDeleted() {
		return status == ItemStatus.DELETED;
	}

	/**
	 * 수정(REQ-007). null인 인자는 바꾸지 않는다. 입력 검증은 호출하는 서비스가 한다. 가격·보증금은 대여 건에 스냅샷이 있어 이후 신규 예약부터
	 * 적용된다.
	 */
	public void update(String name, String description, Long categoryId, ItemCondition itemCondition,
		Integer pricePerDay, Integer deposit, Integer itemPrice, String address, Double latitude, Double longitude) {
		if (name != null) {
			this.name = name;
		}
		if (description != null) {
			this.description = description;
		}
		if (categoryId != null) {
			this.categoryId = categoryId;
		}
		if (itemCondition != null) {
			this.itemCondition = itemCondition;
		}
		if (pricePerDay != null) {
			this.pricePerDay = pricePerDay;
		}
		if (deposit != null) {
			this.deposit = deposit;
		}
		if (itemPrice != null) {
			this.itemPrice = itemPrice;
		}
		if (address != null) {
			this.address = address;
		}
		if (latitude != null && longitude != null) {
			this.latitude = latitude;
			this.longitude = longitude;
		}
	}

	/** 소프트 삭제(REQ-008). 진행 중 대여 검사는 호출하는 쪽이 물품 락 안에서 한다 */
	public void delete() {
		this.status = ItemStatus.DELETED;
	}
}
