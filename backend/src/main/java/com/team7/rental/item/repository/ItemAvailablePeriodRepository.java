package com.team7.rental.item.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team7.rental.item.entity.ItemAvailablePeriod;

public interface ItemAvailablePeriodRepository extends JpaRepository<ItemAvailablePeriod, Long> {

	List<ItemAvailablePeriod> findAllByItemIdOrderByStartDate(Long itemId);
}
