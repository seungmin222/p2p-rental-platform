package com.team7.rental.ai.client;

/** AI 호출 실패(키 없음, API 오류, 연결 실패, 거절). 응답 형식 오류와 달리 다시 부르지 않는다 */
public class AiClientException extends RuntimeException {

	public AiClientException(String message) {
		super(message);
	}

	public AiClientException(String message, Throwable cause) {
		super(message, cause);
	}
}
