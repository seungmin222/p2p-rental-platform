package com.team7.rental.notification.repository;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.global.response.FieldErrorDetail;

/**
 * 알림 목록 커서. 최신순은 id 내림차순이라 마지막 항목의 id 하나로 충분하다(id는 저장 순서대로 커진다).
 * 클라이언트는 해석하지 않으므로 피드 커서처럼 Base64로 감싼다.
 */
public record NotificationCursor(Long notificationId) {

	public String encode() {
		return Base64.getUrlEncoder().withoutPadding()
			.encodeToString(String.valueOf(notificationId).getBytes(StandardCharsets.UTF_8));
	}

	/** @return cursor가 null이면 null(첫 페이지) */
	public static NotificationCursor decode(String value) {
		if (value == null) {
			return null;
		}
		try {
			long id = Long.parseLong(new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8));
			if (id <= 0) {
				throw new IllegalArgumentException();
			}
			return new NotificationCursor(id);
		} catch (RuntimeException failure) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT,
				List.of(new FieldErrorDetail("cursor", "올바른 목록 커서를 보내 주세요.")));
		}
	}
}
