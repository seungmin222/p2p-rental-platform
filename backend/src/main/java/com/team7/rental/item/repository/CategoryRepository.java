package com.team7.rental.item.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team7.rental.item.entity.Category;

public interface CategoryRepository extends JpaRepository<Category, Long> {}
