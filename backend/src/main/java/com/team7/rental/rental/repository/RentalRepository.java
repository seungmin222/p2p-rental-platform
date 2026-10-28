package com.team7.rental.rental.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team7.rental.rental.entity.Rental;

public interface RentalRepository extends JpaRepository<Rental, Long> {}
