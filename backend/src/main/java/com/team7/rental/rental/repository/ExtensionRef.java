package com.team7.rental.rental.repository;

/** 연장과 그 대여 ID. 배치가 대여 행 락부터 잡을 수 있게 같이 읽는다 */
public record ExtensionRef(Long extensionId, Long rentalId) {
}
