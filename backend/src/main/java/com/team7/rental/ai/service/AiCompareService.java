package com.team7.rental.ai.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.team7.rental.ai.client.AiClient;
import com.team7.rental.ai.client.AiClientException;
import com.team7.rental.ai.client.AiImage;
import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.global.storage.FileStorage;
import com.team7.rental.global.storage.ImageProcessor;
import com.team7.rental.global.storage.ScaledImage;
import com.team7.rental.rental.entity.AiCompareRecord;
import com.team7.rental.rental.entity.AiCompareResult;
import com.team7.rental.rental.entity.AiCompareStatus;
import com.team7.rental.rental.service.RentalAiCompareService;
import com.team7.rental.rental.service.RentalAiCompareService.CompareTarget;
import com.team7.rental.rental.service.RentalAiCompareService.Participants;
import com.team7.rental.rental.service.RentalNotificationPort;
import com.team7.rental.rental.service.RentalNotificationPort.TargetType;
import com.team7.rental.rental.service.RentalNotificationPort.Type;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AI 물품 상태 비교 워커 본체 (REQ-040). 트랜잭션을 열지 않는다: 맡기·저장은 {@link RentalAiCompareService}의 짧은 트랜잭션이고 사진 읽기·Claude
 * 호출은 그 사이에 한다.
 * <ol>
 *   <li>맡기: 반납 기록이 비어 있을 때만 PROCESSING (대여 건당 1회, 같은 메시지가 두 번 와도 한 번만 부른다)</li>
 *   <li>사진 읽기 + 축소 (ai.compare.image-max-long-side)</li>
 *   <li>호출마다 월 한도를 먼저 잡는다(재시도 포함). 넘으면 부르지 않고 LIMIT_EXCEEDED</li>
 *   <li>응답 형식이 틀리면 ai.compare.max-retry번까지 다시 부른다. 그래도 틀리면 FAILED. 호출 실패(키 없음·API 오류·거절)는 다시 부르지 않고 FAILED</li>
 *   <li>저장 후 DONE이면 양측에 AI_COMPARE_DONE (FAILED·LIMIT_EXCEEDED는 알리지 않는다, open-issues #48)</li>
 * </ol>
 * 어디서 실패해도 대여 상태는 건드리지 않는다. 맡은 뒤 서버가 죽으면 PROCESSING으로 남고 응답에서 일정 시간 뒤 FAILED로 보인다(open-issues #46).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiCompareService {

	private final RentalAiCompareService rentalAiCompareService;
	private final RentalNotificationPort rentalNotificationPort;
	private final AiClient aiClient;
	private final AiCompareParser aiCompareParser;
	private final AiUsageCounter aiUsageCounter;
	private final FileStorage fileStorage;
	private final ImageProcessor imageProcessor;
	private final PolicyProperties policyProperties;
	private final Clock clock;

	/** 한 대여를 비교한다. 이미 맡았거나 대상이 아니면 아무것도 하지 않는다 */
	public void compare(Long rentalId) {
		Optional<CompareTarget> target = rentalAiCompareService.start(rentalId);
		if (target.isEmpty()) {
			return;
		}
		AiCompareRecord finished;
		try {
			finished = run(target.get());
		} catch (RuntimeException e) {
			log.warn("AI compare failed: rentalId={}, error={}", rentalId, e.toString());
			finished = target.get().processing().failed(aiClient.model(), LocalDateTime.now(clock));
		}
		Optional<Participants> participants = rentalAiCompareService.finish(rentalId, finished);
		log.info("AI compare finished: rentalId={}, status={}", rentalId, finished.status());
		if (finished.status() == AiCompareStatus.DONE && participants.isPresent()) {
			String eventId = RentalNotificationPort.rentalEventId(Type.AI_COMPARE_DONE, rentalId);
			rentalNotificationPort.send(participants.get().renterId(), Type.AI_COMPARE_DONE, TargetType.RENTAL,
				rentalId, eventId);
			rentalNotificationPort.send(participants.get().ownerId(), Type.AI_COMPARE_DONE, TargetType.RENTAL,
				rentalId, eventId);
		}
	}

	private AiCompareRecord run(CompareTarget target) {
		List<AiImage> handoverImages = load(target.handoverImageUrls());
		List<AiImage> returnImages = load(target.returnImageUrls());
		String model = aiClient.model();
		int attempts = 1 + policyProperties.ai().compare().maxRetry();
		for (int attempt = 1; attempt <= attempts; attempt++) {
			if (!aiUsageCounter.tryAcquire()) {
				log.info("AI monthly limit exceeded: rentalId={}", target.rentalId());
				return target.processing().limitExceeded(model, LocalDateTime.now(clock));
			}
			String text;
			try {
				text = aiClient.compare(handoverImages, returnImages);
			} catch (AiClientException e) {
				log.warn("AI client error: rentalId={}, attempt={}, error={}", target.rentalId(), attempt,
					e.getMessage());
				return target.processing().failed(model, LocalDateTime.now(clock));
			}
			Optional<AiCompareResult> result = aiCompareParser.parse(text, returnImages.size());
			if (result.isPresent()) {
				return target.processing().done(result.get(), model, LocalDateTime.now(clock));
			}
			log.info("AI response invalid: rentalId={}, attempt={}/{}", target.rentalId(), attempt, attempts);
		}
		return target.processing().failed(model, LocalDateTime.now(clock));
	}

	private List<AiImage> load(List<String> urls) {
		int maxLongSide = policyProperties.ai().compare().imageMaxLongSide();
		return urls.stream()
			.map(url -> {
				ScaledImage scaled = imageProcessor.downscale(fileStorage.read(fileStorage.keyOf(url)), maxLongSide);
				return new AiImage(scaled.format().getContentType(), scaled.content());
			})
			.toList();
	}
}
