package com.team7.rental.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.amqp.autoconfigure.RabbitConnectionDetails;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.team7.rental.global.security.StompAuthChannelInterceptor;

/**
 * WebSocket + STOMP (docs/domains/chat.md 구조, open-issues #14).
 * <ul>
 *   <li>엔드포인트 {@code /ws}. 같은 출처만 허용(기본값. CORS를 쓰지 않는 것과 같다)</li>
 *   <li>앱 목적지 {@code /pub/**} → {@code @MessageMapping}. 개인 큐 {@code /user/queue/*}</li>
 *   <li>브로커: RabbitMQ STOMP 브로커 릴레이({@code rabbitmq_stomp} 플러그인). RabbitMQ는 {@code /topic/}·{@code /queue/} 등
 *   정해진 접두사만 받으므로 방 구독은 {@code /topic/chat-rooms.{roomId}}다(라우팅 키 구분자가 점)</li>
 *   <li>서버가 여러 대일 때 다른 서버에 붙은 회원의 {@code /user} 목적지·접속 정보를 브로커로 공유한다</li>
 * </ul>
 * 릴레이의 호스트·계정·vhost는 AMQP와 같은 RabbitMQ 접속 정보(spring.rabbitmq.*)를 쓰고, 포트만 {@code app.stomp.relay-port}로 따로 둔다.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	public static final String ENDPOINT = "/ws";
	public static final String APP_PREFIX = "/pub";
	public static final String USER_PREFIX = "/user";

	private final RabbitConnectionDetails rabbitConnectionDetails;
	private final StompAuthChannelInterceptor stompAuthChannelInterceptor;
	private final int relayPort;

	public WebSocketConfig(RabbitConnectionDetails rabbitConnectionDetails,
		StompAuthChannelInterceptor stompAuthChannelInterceptor, @Value("${app.stomp.relay-port}")
		int relayPort) {
		this.rabbitConnectionDetails = rabbitConnectionDetails;
		this.stompAuthChannelInterceptor = stompAuthChannelInterceptor;
		this.relayPort = relayPort;
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint(ENDPOINT);
		// 한 세션이 보낸 프레임을 받은 순서대로 처리한다(기본은 스레드 풀이라 연속 SEND의 저장 순서가 바뀔 수 있다)
		registry.setPreserveReceiveOrder(true);
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.setApplicationDestinationPrefixes(APP_PREFIX);
		registry.setUserDestinationPrefix(USER_PREFIX);
		// 한 세션으로 나가는 메시지도 발행 순서를 지킨다
		registry.setPreservePublishOrder(true);
		RabbitConnectionDetails.Address address = rabbitConnectionDetails.getFirstAddress();
		registry.enableStompBrokerRelay("/topic", "/queue")
			.setRelayHost(address.host())
			.setRelayPort(relayPort)
			.setClientLogin(rabbitConnectionDetails.getUsername())
			.setClientPasscode(rabbitConnectionDetails.getPassword())
			.setSystemLogin(rabbitConnectionDetails.getUsername())
			.setSystemPasscode(rabbitConnectionDetails.getPassword())
			.setVirtualHost(rabbitConnectionDetails.getVirtualHost())
			.setUserDestinationBroadcast("/topic/unresolved-user-destination")
			.setUserRegistryBroadcast("/topic/simp-user-registry");
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(stompAuthChannelInterceptor);
	}
}
