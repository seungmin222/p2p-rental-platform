package com.team7.rental.item.batch;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import com.team7.rental.item.service.ItemViewFlushService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ItemViewCountJob {
	private final ItemViewFlushService flushService;

	@Scheduled(cron = "${app.batch.item-view-count-cron}")
	@SchedulerLock(name = "item.view-count", lockAtMostFor = "4m", lockAtLeastFor = "1s")
	public void run() {
		flushService.flushPending();
	}
}
