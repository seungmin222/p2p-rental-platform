package com.team7.rental.item.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team7.rental.item.entity.ItemImage;

public interface ItemImageRepository extends JpaRepository<ItemImage, Long> {

	/** 대표 이미지(첫 장) */
	Optional<ItemImage> findFirstByItemIdOrderBySortOrder(Long itemId);
}
