package com.team7.rental.payment.client;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestHeadersSpec;
import org.springframework.web.client.RestClientException;

import com.team7.rental.payment.client.PgResult.PgPayment;

import lombok.extern.slf4j.Slf4j;

/**
 * 토스페이먼츠 코어 API (결제창, API 개별 연동). 결과를 알 수 없는 응답(연결·읽기 실패, 5xx, 처리 중, 이미 처리됨)은 Unknown으로 돌려주고,
 * 호출하는 쪽이 같은 키 재요청이나 조회로 확인한다.
 */
@Slf4j
@Component
public class TossPaymentGateway implements PaymentGateway {

	/** 같은 키의 이전 요청이 아직 처리 중 */
	private static final String IDEMPOTENT_REQUEST_PROCESSING = "IDEMPOTENT_REQUEST_PROCESSING";
	/** 승인이 이미 끝난 결제. 어떤 결과인지는 조회해야 안다 */
	private static final String ALREADY_PROCESSED_PAYMENT = "ALREADY_PROCESSED_PAYMENT";

	private final RestClient restClient;

	@Autowired
	public TossPaymentGateway(TossPaymentProperties properties, @Value("${app.feature.payment-enabled}")
	boolean paymentEnabled) {
		this(builderOf(properties), properties, paymentEnabled);
	}

	TossPaymentGateway(RestClient.Builder builder, TossPaymentProperties properties, boolean paymentEnabled) {
		if (paymentEnabled && !StringUtils.hasText(properties.secretKey())) {
			throw new IllegalStateException("결제를 켜려면 TOSS_SECRET_KEY가 필요합니다");
		}
		this.restClient = builder.baseUrl(properties.baseUrl())
			.defaultHeader(HttpHeaders.AUTHORIZATION, basicAuth(properties.secretKey()))
			.build();
	}

	private static RestClient.Builder builderOf(TossPaymentProperties properties) {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(properties.connectTimeout());
		factory.setReadTimeout(properties.readTimeout());
		return RestClient.builder().requestFactory(factory);
	}

	// 시크릿 키를 사용자 ID로, 비밀번호는 비운다(콜론 필수)
	private static String basicAuth(String secretKey) {
		String credentials = (secretKey == null ? "" : secretKey) + ":";
		return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
	}

	@Override
	public PgResult confirm(String paymentKey, String orderId, int amount, String idempotencyKey) {
		return call(restClient.post().uri("/v1/payments/confirm")
			.header("Idempotency-Key", idempotencyKey)
			.contentType(MediaType.APPLICATION_JSON)
			.body(new ConfirmBody(paymentKey, orderId, amount)));
	}

	@Override
	public PgResult cancel(String paymentKey, int cancelAmount, String cancelReason, String idempotencyKey) {
		return call(restClient.post().uri("/v1/payments/{paymentKey}/cancel", paymentKey)
			.header("Idempotency-Key", idempotencyKey)
			.contentType(MediaType.APPLICATION_JSON)
			.body(new CancelBody(cancelReason, cancelAmount)));
	}

	@Override
	public PgResult findByOrderId(String orderId) {
		return call(restClient.get().uri("/v1/payments/orders/{orderId}", orderId));
	}

	private PgResult call(RequestHeadersSpec<?> request) {
		try {
			return request.exchange((req, res) -> {
				if (res.getStatusCode().is2xxSuccessful()) {
					PaymentBody body = res.bodyTo(PaymentBody.class);
					return new PgResult.Success(new PgPayment(body.paymentKey(), body.orderId(), body.status(),
						body.totalAmount(), body.balanceAmount()));
				}
				if (res.getStatusCode().is5xxServerError()) {
					return new PgResult.Unknown("HTTP " + res.getStatusCode().value());
				}
				ErrorBody error = res.bodyTo(ErrorBody.class);
				if (IDEMPOTENT_REQUEST_PROCESSING.equals(error.code())
					|| ALREADY_PROCESSED_PAYMENT.equals(error.code())) {
					return new PgResult.Unknown(error.code());
				}
				return new PgResult.Failure(error.code(), error.message());
			});
		} catch (RestClientException e) {
			log.warn("Toss API call failed: {}", e.getClass().getSimpleName());
			return new PgResult.Unknown(e.getClass().getSimpleName());
		}
	}

	private record ConfirmBody(String paymentKey, String orderId, int amount) {
	}

	private record CancelBody(String cancelReason, int cancelAmount) {
	}

	private record PaymentBody(String paymentKey, String orderId, String status, int totalAmount, int balanceAmount) {
	}

	private record ErrorBody(String code, String message) {
	}
}
