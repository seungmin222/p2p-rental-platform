package com.team7.rental.rental.dto;

/** 확정된 연장과 알림 받을 당사자 (REQ-038, 커밋 뒤 RENTAL_EXTENDED 발행용) */
public record RentalExtensionConfirmed(Long rentalId, Long extensionId, Long renterId, Long ownerId) {
}
