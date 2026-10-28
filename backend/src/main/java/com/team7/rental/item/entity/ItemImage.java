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

@Getter
@Entity
@Table(name = "item_images")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemImage extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long itemId;

	@Column(nullable = false, length = 500)
	private String url;

	@Column(nullable = false, length = 500)
	private String thumbnailUrl;

	/** 0이 대표 이미지 */
	@Column(nullable = false)
	private int sortOrder;

	public static ItemImage create(Long itemId, String url, String thumbnailUrl, int sortOrder) {
		ItemImage image = new ItemImage();
		image.itemId = itemId;
		image.url = url;
		image.thumbnailUrl = thumbnailUrl;
		image.sortOrder = sortOrder;
		return image;
	}
}
