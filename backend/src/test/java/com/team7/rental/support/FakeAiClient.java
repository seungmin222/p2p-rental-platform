package com.team7.rental.support;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Primary;

import com.team7.rental.ai.client.AiClient;
import com.team7.rental.ai.client.AiImage;
import com.team7.rental.ai.client.CategoryOption;

/**
 * Claude 대신 쓰는 AI 클라이언트 (REQ-040·067). 넣어 둔 응답을 순서대로 돌려주고, 비면 {@link #VALID}(등록 보조는 {@link #DRAFT_ETC}).
 * 응답 대신 예외를 넣으면 그 예외를 던진다. 상태 비교와 등록 보조는 응답·호출 기록을 따로 둔다.
 * 각 테스트가 끝나면 {@link IntegrationTestSupport}가 비운다.
 */
@Primary
@TestComponent
public class FakeAiClient implements AiClient {

	public static final String MODEL = "fake-model";

	/** 고정 스키마에 맞는 응답: 반납 사진 0번에 흠집 */
	public static final String VALID = """
		{"changed": true, "changeTypes": ["SCRATCH"], "confidence": 0.8,
		 "description": "반납 사진 왼쪽 하단 모서리에 흠집이 보입니다", "regions": [{"imageIndex": 0, "note": "왼쪽 하단 모서리"}]}""";

	/** 스키마에 맞지 않는 응답 (허용하지 않는 changeTypes) */
	public static final String INVALID = """
		{"changed": true, "changeTypes": ["DENT"], "confidence": 0.8, "description": "찌그러짐", "regions": []}""";

	/** 등록 보조 기본 응답: 기타, 후보 없음, 나머지 초안 없음 */
	public static final String DRAFT_ETC = """
		{"categoryCode": "ETC", "suggestedName": null, "title": null, "description": null,
		 "itemPrice": null, "pricePerDay": null}""";

	public record Call(List<AiImage> handoverImages, List<AiImage> returnImages) {
	}

	public record DraftCall(List<AiImage> images, List<CategoryOption> categories, int titleMax, int descriptionMax,
		Duration timeout) {
	}

	private final Deque<Object> responses = new ArrayDeque<>();
	private final List<Call> calls = new ArrayList<>();
	private final Deque<Object> draftResponses = new ArrayDeque<>();
	private final List<DraftCall> draftCalls = new ArrayList<>();

	@Override
	public synchronized String compare(List<AiImage> handoverImages, List<AiImage> returnImages) {
		calls.add(new Call(List.copyOf(handoverImages), List.copyOf(returnImages)));
		Object next = responses.isEmpty() ? VALID : responses.poll();
		if (next instanceof RuntimeException exception) {
			throw exception;
		}
		return (String)next;
	}

	@Override
	public synchronized String draft(List<AiImage> images, List<CategoryOption> categories, int titleMax,
		int descriptionMax, Duration timeout) {
		draftCalls.add(new DraftCall(List.copyOf(images), List.copyOf(categories), titleMax, descriptionMax, timeout));
		Object next = draftResponses.isEmpty() ? DRAFT_ETC : draftResponses.poll();
		if (next instanceof RuntimeException exception) {
			throw exception;
		}
		return (String)next;
	}

	@Override
	public String model() {
		return MODEL;
	}

	/** 다음 호출들의 응답 (순서대로) */
	public synchronized void respond(String... texts) {
		responses.addAll(List.of(texts));
	}

	/** 다음 호출에서 던질 예외 */
	public synchronized void fail(RuntimeException exception) {
		responses.add(exception);
	}

	public synchronized List<Call> calls() {
		return List.copyOf(calls);
	}

	/** 다음 등록 보조 호출들의 응답 (순서대로) */
	public synchronized void respondDraft(String... texts) {
		draftResponses.addAll(List.of(texts));
	}

	/** 다음 등록 보조 호출에서 던질 예외 */
	public synchronized void failDraft(RuntimeException exception) {
		draftResponses.add(exception);
	}

	public synchronized List<DraftCall> draftCalls() {
		return List.copyOf(draftCalls);
	}

	public synchronized void reset() {
		responses.clear();
		calls.clear();
		draftResponses.clear();
		draftCalls.clear();
	}
}
