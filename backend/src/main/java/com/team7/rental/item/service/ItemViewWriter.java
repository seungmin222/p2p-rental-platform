package com.team7.rental.item.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.item.repository.ItemRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ItemViewWriter {
	private final ItemRepository items;

	@Transactional
	public void flush(Long itemId, int count) {
		items.advanceViewCount(itemId, count);
	}
}
