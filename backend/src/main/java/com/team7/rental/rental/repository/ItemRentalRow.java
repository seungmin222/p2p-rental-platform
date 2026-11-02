package com.team7.rental.rental.repository;

import java.time.LocalDate;

import com.team7.rental.rental.entity.RentalStatus;

public record ItemRentalRow(Long rentalId, RentalStatus status, Long renterId, LocalDate startDate, LocalDate endDate) {
}
