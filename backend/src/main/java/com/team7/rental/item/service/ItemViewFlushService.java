package com.team7.rental.item.service;

import org.springframework.stereotype.Service;

import com.team7.rental.item.repository.ItemViewRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ItemViewFlushService {
	private final ItemViewRepository views;
	private final ItemViewWriter writer;

	public void flushPending() {
		try (var cursor = views.pendingItems()) {
			while (cursor.hasNext()) {
				Long itemId = Long.valueOf(cursor.next());
				try {
					Integer count = views.snapshot(itemId);
					if (count != null) {
						writer.flush(itemId, count);
						views.acknowledge(itemId, count);
					}
				} catch (RuntimeException failure) {
					log.warn("조회수 DB 반영 실패: itemId={}", itemId, failure);
				}
			}
		}
	}
}
