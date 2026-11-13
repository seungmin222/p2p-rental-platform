package com.team7.rental.support;

import java.lang.reflect.Type;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * 통합 테스트용 STOMP 클라이언트 (REQ-027). 실제 서버 포트(/ws)에 붙고, 서버는 RabbitMQ STOMP 브로커 릴레이를 거친다.
 * 구독은 브로커의 RECEIPT를 받은 뒤 돌려주므로, 구독 직후 보낸 메시지를 놓치지 않는다.
 */
public final class StompTestClient {

	public static final long TIMEOUT_SECONDS = 10;

	/** RECEIPT 추적용 (클라이언트 공용) */
	private static final ThreadPoolTaskScheduler SCHEDULER = createScheduler();

	private static ThreadPoolTaskScheduler createScheduler() {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setThreadNamePrefix("stomp-test-");
		scheduler.setDaemon(true);
		scheduler.initialize();
		return scheduler;
	}

	private StompTestClient() {}

	/** {@code authorization}이 null이면 헤더 없이 CONNECT한다. 거부되면 {@link Connection#errors()}에 에러 코드가 들어온다 */
	public static Connection connect(int port, String authorization) {
		WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
		client.setMessageConverter(new JacksonJsonMessageConverter());
		client.setTaskScheduler(SCHEDULER);
		StompHeaders connectHeaders = new StompHeaders();
		if (authorization != null) {
			connectHeaders.add("Authorization", authorization);
		}
		Connection connection = new Connection();
		CompletableFuture<StompSession> future = client.connectAsync("ws://localhost:" + port + "/ws",
			new WebSocketHttpHeaders(), connectHeaders, connection.handler);
		connection.sessionFuture = future;
		return connection;
	}

	public static final class Connection {

		private final BlockingQueue<String> errors = new LinkedBlockingQueue<>();
		private final StompSessionHandlerAdapter handler = new StompSessionHandlerAdapter() {
			@Override
			public void handleFrame(StompHeaders headers, Object payload) {
				errors.add(String.valueOf(headers.getFirst("message")));
			}

			@Override
			public Type getPayloadType(StompHeaders headers) {
				return byte[].class;
			}
		};
		private CompletableFuture<StompSession> sessionFuture;

		/** ERROR 프레임의 message 헤더(에러 코드) */
		public BlockingQueue<String> errors() {
			return errors;
		}

		public StompSession session() {
			try {
				return sessionFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
			} catch (Exception e) {
				throw new IllegalStateException("STOMP 연결 실패", e);
			}
		}

		/** 연결이 실패(거부)했는지. ERROR를 받으면 서버가 연결을 닫는다 */
		public boolean failedToConnect() {
			try {
				sessionFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
				return false;
			} catch (Exception e) {
				return true;
			}
		}

		/** 구독하고 브로커 RECEIPT까지 기다린다 */
		public <T> BlockingQueue<T> subscribe(String destination, Class<T> type) {
			BlockingQueue<T> received = new LinkedBlockingQueue<>();
			StompSession.Subscription subscription = subscribeAsync(destination, type, received);
			CompletableFuture<Void> receipt = new CompletableFuture<>();
			subscription.addReceiptTask(() -> receipt.complete(null));
			subscription
				.addReceiptLostTask(() -> receipt.completeExceptionally(new IllegalStateException("receipt lost")));
			try {
				receipt.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
			} catch (Exception e) {
				throw new IllegalStateException("구독 RECEIPT를 받지 못함: " + destination, e);
			}
			return received;
		}

		/** RECEIPT를 기다리지 않는 구독 (거부되는 구독 검증용) */
		public <T> StompSession.Subscription subscribeAsync(String destination, Class<T> type,
			BlockingQueue<T> received) {
			StompSession session = session();
			session.setAutoReceipt(true);
			StompHeaders headers = new StompHeaders();
			headers.setDestination(destination);
			return session.subscribe(headers, new StompFrameHandler() {
				@Override
				public Type getPayloadType(StompHeaders headers) {
					return type;
				}

				@Override
				@SuppressWarnings("unchecked")
				public void handleFrame(StompHeaders headers, Object payload) {
					received.add((T)payload);
				}
			});
		}

		public void send(String destination, Object payload) {
			session().send(destination, payload);
		}

		public boolean isConnected() {
			return sessionFuture.isDone() && !sessionFuture.isCompletedExceptionally()
				&& sessionFuture.join().isConnected();
		}

		public void disconnect() {
			if (isConnected()) {
				sessionFuture.join().disconnect();
			}
		}
	}
}
