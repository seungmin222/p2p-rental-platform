package com.team7.rental.item.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.item.dto.ItemCreateRequest;
import com.team7.rental.item.dto.ItemCreateResponse;
import com.team7.rental.item.entity.Item;
import com.team7.rental.item.entity.ItemAvailablePeriod;
import com.team7.rental.item.entity.ItemImage;
import com.team7.rental.item.repository.ItemAvailablePeriodRepository;
import com.team7.rental.item.repository.ItemImageRepository;
import com.team7.rental.item.repository.ItemRepository;
import com.team7.rental.member.service.MemberQueryService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ItemCreateWriter {
	private final ItemRepository items;
	private final ItemImageRepository images;
	private final ItemAvailablePeriodRepository periods;
	private final MemberQueryService members;
	private final CategoryService categories;
	private final Clock clock;

	public record UploadedImage(String url, String thumbnailUrl) {
	}

	/** 파일 업로드가 끝난 뒤 한 트랜잭션에서 등록한다. 실패하면 호출자가 업로드 파일을 정리한다. */
	@Transactional
	public ItemCreateResponse create(Long memberId, ItemCreateRequest request, List<UploadedImage> uploaded) {
		Long regionId = members.lockVerifiedPrimaryRegion(memberId);
		categories.requireActive(request.categoryId());
		Item item = items.saveAndFlush(Item.create(memberId, regionId, request.categoryId(), request.name(),
			request.description(), request.condition(), request.pricePerDay(), request.deposit(), request.itemPrice(),
			request.address(), request.latitude(), request.longitude(), LocalDateTime.now(clock)));
		for (int i = 0; i < uploaded.size(); i++) {
			var image = uploaded.get(i);
			images.save(ItemImage.create(item.getId(), image.url(), image.thumbnailUrl(), i));
		}
		for (var period : request.availablePeriods()) {
			periods.save(ItemAvailablePeriod.create(item.getId(), period.startDate(), period.endDate()));
		}
		return new ItemCreateResponse(item.getId());
	}
}
