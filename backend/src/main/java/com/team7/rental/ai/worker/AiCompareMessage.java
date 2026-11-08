package com.team7.rental.ai.worker;

/** ai.compare 큐 메시지 (docs/domains/ai.md REQ-040: {@code {rentalId}}) */
public record AiCompareMessage(Long rentalId) {
}
