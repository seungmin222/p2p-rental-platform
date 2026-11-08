package com.team7.rental.ai.client;

import java.util.Objects;

/**
 * AI에 넘길 이미지 한 장.
 *
 * @param mediaType image/jpeg 또는 image/png
 */
public record AiImage(String mediaType, byte[] data) {

	public AiImage {
		Objects.requireNonNull(mediaType, "mediaType");
		Objects.requireNonNull(data, "data");
	}
}
