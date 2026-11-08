package com.team7.rental.ai.worker;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.team7.rental.rental.event.ReturnRequestedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 반납 요청 커밋 뒤 ai.compare 큐에 적재한다 (REQ-040). 롤백된 반납 요청은 적재하지 않는다.
 * 적재 실패는 삼키고 로그만 남긴다: 반납은 이미 커밋됐고 AI 결과 없이도 상태 확인을 할 수 있다(ai.md "AI가 실패해도 거래 흐름은 멈추지 않는다").
 * 그 대여의 ai_result는 비어 있는 채로 남고 응답은 "AI 결과 없음"이다. 다시 적재하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiCompareEnqueuer {

	private final AiCompareQueue aiCompareQueue;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onReturnRequested(ReturnRequestedEvent event) {
		try {
			aiCompareQueue.publish(event.rentalId());
		} catch (RuntimeException e) {
			log.warn("AI compare enqueue failed (return already committed): rentalId={}, error={}", event.rentalId(),
				e.toString());
		}
	}
}
