package com.team7.rental.item.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team7.rental.item.entity.Item;

public interface ItemRepository extends JpaRepository<Item, Long> {}
