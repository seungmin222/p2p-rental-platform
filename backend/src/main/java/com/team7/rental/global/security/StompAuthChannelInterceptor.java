package com.team7.rental.global.security;

import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import com.team7.rental.chat.service.ChatDestinations;
import com.team7.rental.chat.service.ChatRoomService;
import com.team7.rental.global.config.WebSocketConfig;
import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;

import lombok.extern.slf4j.Slf4j;

/**
 * STOMP 인증·인가 (docs/domains/chat.md 구조, docs/architecture.md 인증).
 * <ul>
 *   <li>CONNECT: {@code Authorization: Bearer <AT>} 헤더를 REST와 같은 규칙으로 검증한다. Principal 이름 = memberId.
 *   AT 만료는 CONNECT 때만 본다(연결 중 만료돼도 끊지 않는다)</li>
 *   <li>SUBSCRIBE: 방 토픽은 참여자만, 그 밖에는 정해진 개인 큐만 허용한다</li>
 *   <li>SEND: 앱 목적지({@code /pub/**})만 허용한다. 브로커 목적지로 바로 보내 서버 검증을 건너뛰지 못하게 한다</li>
 * </ul>
 * 거부하면 그 프레임을 버리고 ERROR 프레임({@code message} 헤더 = 에러 코드, 예: {@code CHAT_NOT_PARTICIPANT})을 보낸다.
 * ERROR를 보내면 서버가 연결을 닫는다. 예외를 던지지 않고 직접 보내는 이유: 수신 순서 보존(WebSocketConfig)을 켜면 인바운드 처리가
 * 비동기라 인터셉터 예외가 클라이언트에 전달되지 않는다.
 */
@Slf4j
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

	private static final String BEARER_PREFIX = "Bearer ";
	private static final Set<String> USER_QUEUES = Set.of(
		WebSocketConfig.USER_PREFIX + ChatDestinations.ROOM_UPDATES_QUEUE,
		WebSocketConfig.USER_PREFIX + ChatDestinations.READS_QUEUE,
		WebSocketConfig.USER_PREFIX + ChatDestinations.ERRORS_QUEUE,
		WebSocketConfig.USER_PREFIX + ChatDestinations.NOTIFICATIONS_QUEUE);

	private final JwtProvider jwtProvider;
	private final ChatRoomService chatRoomService;
	private final MessageChannel clientOutboundChannel;

	/** 메시징 설정(WebSocketConfig)이 만들어질 때 채팅 도메인 빈·아웃바운드 채널을 앞당겨 만들지 않도록 지연 주입한다 */
	public StompAuthChannelInterceptor(JwtProvider jwtProvider, @Lazy
	ChatRoomService chatRoomService, @Lazy @Qualifier("clientOutboundChannel")
	MessageChannel clientOutboundChannel) {
		this.jwtProvider = jwtProvider;
		this.chatRoomService = chatRoomService;
		this.clientOutboundChannel = clientOutboundChannel;
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		if (accessor == null || accessor.getCommand() == null) {
			return message;
		}
		try {
			check(accessor);
			return message;
		} catch (BusinessException e) {
			log.info("STOMP {} rejected: {}", accessor.getCommand(), e.getErrorCode().getCode());
			sendError(accessor, e.getErrorCode());
			return null;
		}
	}

	private void check(StompHeaderAccessor accessor) {
		StompCommand command = accessor.getCommand();
		if (command == StompCommand.CONNECT) {
			accessor.setUser(authenticate(accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION)));
		} else if (command == StompCommand.SUBSCRIBE) {
			authorizeSubscribe(requireMemberId(accessor), accessor.getDestination());
		} else if (command == StompCommand.SEND) {
			requireMemberId(accessor);
			authorizeSend(accessor.getDestination());
		}
	}

	private void sendError(StompHeaderAccessor rejected, ErrorCode errorCode) {
		StompHeaderAccessor error = StompHeaderAccessor.create(StompCommand.ERROR);
		error.setMessage(errorCode.getCode());
		error.setSessionId(rejected.getSessionId());
		if (rejected.getReceipt() != null) {
			error.setReceiptId(rejected.getReceipt());
		}
		error.setLeaveMutable(true);
		clientOutboundChannel.send(MessageBuilder.createMessage(new byte[0], error.getMessageHeaders()));
	}

	private UsernamePasswordAuthenticationToken authenticate(String header) {
		if (header == null || !header.startsWith(BEARER_PREFIX)) {
			throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
		}
		AccessTokenClaims claims = jwtProvider.parseAccessToken(header.substring(BEARER_PREFIX.length()));
		var authentication = UsernamePasswordAuthenticationToken.authenticated(claims.memberId(), null,
			List.of(new SimpleGrantedAuthority("ROLE_" + claims.role())));
		authentication.setDetails(claims);
		return authentication;
	}

	private Long requireMemberId(StompHeaderAccessor accessor) {
		if (accessor.getUser() == null) {
			throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
		}
		return Long.valueOf(accessor.getUser().getName());
	}

	private void authorizeSubscribe(Long memberId, String destination) {
		if (destination == null) {
			throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
		}
		Long roomId = ChatDestinations.parseRoomTopic(destination);
		if (roomId != null) {
			chatRoomService.validateParticipant(roomId, memberId);
			return;
		}
		if (!USER_QUEUES.contains(destination)) {
			throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
		}
	}

	private void authorizeSend(String destination) {
		if (destination == null || !destination.startsWith(WebSocketConfig.APP_PREFIX + "/")) {
			throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
		}
	}
}
