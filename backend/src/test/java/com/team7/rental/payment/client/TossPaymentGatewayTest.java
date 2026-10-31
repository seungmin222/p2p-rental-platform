package com.team7.rental.payment.client;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TossPaymentGatewayTest {

	private static final String BASE_URL = "https://toss.test";
	private static final String PAYMENT_JSON = """
		{"paymentKey":"pk","orderId":"order-1","status":"DONE","totalAmount":15000,"balanceAmount":15000,"card":{"number":"1234"}}
		""";
	private static final TossPaymentProperties PROPERTIES = new TossPaymentProperties(BASE_URL, "test_sk",
		Duration.ofSeconds(1), Duration.ofSeconds(1));

	private MockRestServiceServer server;
	private TossPaymentGateway gateway;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		gateway = new TossPaymentGateway(builder, PROPERTIES, true);
	}

	@Test
	@DisplayName("승인은 Basic 인증(시크릿 키 + 콜론)과 멱등성 키를 보내고 결제 정보를 돌려준다")
	void confirmSuccess() {
		// given: "test_sk:"의 base64
		server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "Basic dGVzdF9zazo="))
			.andExpect(header("Idempotency-Key", "key-1"))
			.andExpect(jsonPath("$.paymentKey").value("pk"))
			.andExpect(jsonPath("$.amount").value(15000))
			.andRespond(withSuccess(PAYMENT_JSON, MediaType.APPLICATION_JSON));

		// when
		PgResult result = gateway.confirm("pk", "order-1", 15000, "key-1");

		// then
		assertThat(result).isInstanceOfSatisfying(PgResult.Success.class, success -> {
			assertThat(success.payment().isApproved()).isTrue();
			assertThat(success.payment().balanceAmount()).isEqualTo(15000);
			assertThat(success.toRecord().toString()).doesNotContain("1234");
		});
		server.verify();
	}

	@Test
	@DisplayName("취소는 사유·금액·멱등성 키를 보낸다")
	void cancelRequest() {
		// given
		server.expect(requestTo(BASE_URL + "/v1/payments/pk/cancel"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header("Idempotency-Key", "deposit-refund:1"))
			.andExpect(jsonPath("$.cancelReason").value("보증금 반환"))
			.andExpect(jsonPath("$.cancelAmount").value(5000))
			.andRespond(withSuccess(PAYMENT_JSON, MediaType.APPLICATION_JSON));

		// when
		PgResult result = gateway.cancel("pk", 5000, "보증금 반환", "deposit-refund:1");

		// then
		assertThat(result).isInstanceOf(PgResult.Success.class);
		server.verify();
	}

	@Test
	@DisplayName("4xx는 코드를 담은 Failure다. 조회의 없는 결제도 같다")
	void clientErrorIsFailure() {
		// given
		server.expect(requestTo(BASE_URL + "/v1/payments/orders/order-1"))
			.andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
				.body("{\"code\":\"NOT_FOUND_PAYMENT\",\"message\":\"없음\"}"));

		// when
		PgResult result = gateway.findByOrderId("order-1");

		// then
		assertThat(result).isEqualTo(new PgResult.Failure(PgResult.NOT_FOUND, "없음"));
	}

	@Test
	@DisplayName("처리 중·이미 처리됨은 결과를 모르므로 Unknown이다")
	void processingIsUnknown() {
		// given
		server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
			.andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
				.body("{\"code\":\"IDEMPOTENT_REQUEST_PROCESSING\",\"message\":\"처리 중\"}"));
		server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
			.andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
				.body("{\"code\":\"ALREADY_PROCESSED_PAYMENT\",\"message\":\"처리됨\"}"));

		// then
		assertThat(gateway.confirm("pk", "order-1", 1000, "key")).isInstanceOf(PgResult.Unknown.class);
		assertThat(gateway.confirm("pk", "order-1", 1000, "key")).isInstanceOf(PgResult.Unknown.class);
	}

	@Test
	@DisplayName("5xx와 타임아웃은 Unknown이다")
	void serverErrorAndTimeoutAreUnknown() {
		// given
		server.expect(requestTo(BASE_URL + "/v1/payments/confirm")).andRespond(withServerError());
		server.expect(requestTo(BASE_URL + "/v1/payments/confirm")).andRespond(request -> {
			throw (IOException)new SocketTimeoutException("read timed out");
		});

		// then
		assertThat(gateway.confirm("pk", "order-1", 1000, "key")).isInstanceOf(PgResult.Unknown.class);
		assertThat(gateway.confirm("pk", "order-1", 1000, "key")).isInstanceOf(PgResult.Unknown.class);
	}

	@Test
	@DisplayName("결제를 켰는데 시크릿 키가 비어 있으면 기동에 실패하고, 꺼져 있으면 괜찮다")
	void blankSecretKey() {
		// given
		TossPaymentProperties blank = new TossPaymentProperties(BASE_URL, "", Duration.ofSeconds(1),
			Duration.ofSeconds(1));

		// then
		assertThatThrownBy(() -> new TossPaymentGateway(RestClient.builder(), blank, true))
			.isInstanceOf(IllegalStateException.class);
		assertThatCode(() -> new TossPaymentGateway(RestClient.builder(), blank, false)).doesNotThrowAnyException();
	}
}
