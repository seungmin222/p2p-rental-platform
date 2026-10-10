package com.team7.rental.global.storage;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Optional;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 업로드를 허용하는 이미지 형식(jpg/png). 확장자·Content-Type이 아니라 파일 앞부분의 매직 바이트로 판별한다. */
@Getter
@RequiredArgsConstructor
public enum ImageFormat {

	JPEG("jpg", "image/jpeg", BufferedImage.TYPE_INT_RGB, new byte[] {(byte)0xFF, (byte)0xD8, (byte)0xFF}),
	PNG("png", "image/png", BufferedImage.TYPE_INT_ARGB,
		new byte[] {(byte)0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});

	/** 저장 확장자이자 Thumbnailator 출력 형식 이름 */
	private final String extension;
	private final String contentType;
	/** 처리용 픽셀 형식. PNG는 투명도를 유지하도록 알파 채널 포함 */
	private final int imageType;
	private final byte[] magic;

	public static Optional<ImageFormat> detect(byte[] content) {
		return Arrays.stream(values())
			.filter(format -> startsWith(content, format.magic))
			.findFirst();
	}

	private static boolean startsWith(byte[] content, byte[] prefix) {
		return content.length >= prefix.length && Arrays.equals(content, 0, prefix.length, prefix, 0, prefix.length);
	}
}
