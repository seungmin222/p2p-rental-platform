package com.team7.rental.rental.batch;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import com.team7.rental.rental.service.RentalCancelService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 양측 동의 취소 요청 만료 (REQ-015, policies.md 배치 주기 10분). 진행 중 판단은 expires_at으로 하므로 이 배치가 늦어도 요청·응답은
 * 바르게 동작하고, 배치는 상태값을 정리할 뿐이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CancelAgreementExpireJob {

	private final RentalCancelService rentalCancelService;
	private final Clock clock;

	@Scheduled(cron = "${app.batch.cancel-agreement-expire-cron}")
	@SchedulerLock(name = "rental.cancel-agreement-expire", lockAtMostFor = "5m", lockAtLeastFor = "30s")
	public void run() {
		int expired = rentalCancelService.expireOverdue(LocalDateTime.now(clock));
		if (expired > 0) {
			log.info("Expired cancel agreements: {}", expired);
		}
	}
}
