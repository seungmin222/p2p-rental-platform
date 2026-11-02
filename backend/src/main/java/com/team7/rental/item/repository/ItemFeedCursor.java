package com.team7.rental.item.repository;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.global.response.FieldErrorDetail;

/** bumpedAt과 ID를 모두 사용해 시각이 같은 물품도 빠짐없이 넘긴다. */
public record ItemFeedCursor(LocalDateTime bumpedAt, Long itemId) {
	public String encode() {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(
			(bumpedAt + "|" + itemId).getBytes(StandardCharsets.UTF_8));
	}

	public static ItemFeedCursor decode(String value) {
		if (value == null) {
			return null;
		}
		try {
			String[] parts = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8).split("\\|", -1);
			if (parts.length != 2) {
				throw new IllegalArgumentException();
			}
			var cursor = new ItemFeedCursor(LocalDateTime.parse(parts[0]), Long.valueOf(parts[1]));
			if (cursor.itemId() <= 0 || cursor.bumpedAt().getYear() < 1000 || cursor.bumpedAt().getYear() > 9999) {
				throw new IllegalArgumentException();
			}
			return cursor;
		} catch (RuntimeException failure) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT,
				List.of(new FieldErrorDetail("cursor", "올바른 목록 커서를 보내 주세요.")));
		}
	}
}
