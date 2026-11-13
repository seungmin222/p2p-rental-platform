package com.team7.rental.chat.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import com.team7.rental.chat.dto.ChatErrorResponse;
import com.team7.rental.chat.dto.ChatMessageSendRequest;
import com.team7.rental.chat.dto.ChatReadRequest;
import com.team7.rental.chat.service.ChatDestinations;
import com.team7.rental.chat.service.ChatMessageService;
import com.team7.rental.chat.service.ChatReadService;
import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.global.response.ErrorResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * STOMP 메시지 송신 (REQ-027). SEND {@code /pub/chat-rooms/{roomId}} → 저장 → 커밋 뒤 {@code /topic/chat-rooms.{roomId}}로 전파.
 * 읽음 (REQ-029). SEND {@code /pub/chat-rooms/{roomId}/read} → 읽음 위치 갱신 → 커밋 뒤 상대의 {@code /user/queue/chat-reads}로 전파.
 * 거부는 연결을 끊지 않고 보낸 세션의 {@code /user/queue/errors}로 {@link ChatErrorResponse}를 회신한다.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatMessageController {

	private final ChatMessageService chatMessageService;
	private final ChatReadService chatReadService;

	@MessageMapping("/chat-rooms/{roomId}")
	public void send(@DestinationVariable
	Long roomId, @Payload
	ChatMessageSendRequest request, Principal principal) {
		try {
			chatMessageService.send(Long.valueOf(principal.getName()), roomId, request);
		} catch (BusinessException e) {
			throw new ChatSendException(request.clientMessageId(), e);
		}
	}

	@MessageMapping("/chat-rooms/{roomId}/read")
	public void read(@DestinationVariable
	Long roomId, @Payload
	ChatReadRequest request, Principal principal) {
		try {
			chatReadService.markRead(Long.valueOf(principal.getName()), roomId, request);
		} catch (BusinessException e) {
			throw new ChatSendException(null, e);
		}
	}

	@MessageExceptionHandler
	@SendToUser(destinations = ChatDestinations.ERRORS_QUEUE, broadcast = false)
	public ChatErrorResponse handleSendFailure(ChatSendException e) {
		BusinessException failure = e.failure;
		log.info("Chat send rejected: {}", failure.getErrorCode().getCode());
		return new ChatErrorResponse(e.clientMessageId, ErrorResponse.of(failure.getErrorCode(), failure.getDetails()));
	}

	/** 본문이 JSON이 아니거나 형식이 맞지 않음 (경로 변수 roomId가 숫자가 아닐 때 포함) */
	@MessageExceptionHandler({MessageConversionException.class,
		org.springframework.messaging.handler.annotation.support.MethodArgumentTypeMismatchException.class})
	@SendToUser(destinations = ChatDestinations.ERRORS_QUEUE, broadcast = false)
	public ChatErrorResponse handleInvalidFormat(Exception e) {
		log.info("Chat send malformed: {}", e.getClass().getSimpleName());
		return new ChatErrorResponse(null, ErrorResponse.of(ErrorCode.COMMON_INVALID_INPUT, List.of()));
	}

	/** 실패한 SEND의 임시 ID를 오류 회신까지 전달한다 */
	static class ChatSendException extends RuntimeException {

		private final String clientMessageId;
		private final BusinessException failure;

		ChatSendException(String clientMessageId, BusinessException cause) {
			super(cause.getMessage(), cause, false, false);
			this.clientMessageId = clientMessageId;
			this.failure = cause;
		}
	}
}
