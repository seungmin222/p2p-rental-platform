package com.team7.rental.global.storage;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import net.coobird.thumbnailator.Thumbnails;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.global.policy.PolicyProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * 업로드 이미지 검증·재인코딩. 처리 순서: 용량 → 매직 바이트(jpg/png) → 디코딩(EXIF orientation을 픽셀에 반영) →
 * 원본(긴 변 제한)·썸네일(가로 제한) 재인코딩. 재인코딩 결과에는 EXIF(GPS 포함) 등 메타데이터가 남지 않는다.
 *
 * <p>용량 한도는 용도마다 달라(profile.image, item.images, inspection.images) 호출하는 쪽이 정책 값을 넘긴다.
 * multipart 한도(spring.servlet.multipart)는 그중 가장 큰 값이다.
 */
@Slf4j
@Component
public class ImageProcessor {

	private final PolicyProperties.Image policy;

	public ImageProcessor(PolicyProperties policyProperties) {
		this.policy = policyProperties.image();
	}

	public ProcessedImage process(MultipartFile file, DataSize maxSize) {
		if (file == null || file.isEmpty()) {
			throw new BusinessException(ErrorCode.IMAGE_INVALID_TYPE);
		}
		if (file.getSize() > maxSize.toBytes()) {
			throw new BusinessException(ErrorCode.IMAGE_SIZE_EXCEEDED);
		}
		byte[] content = readBytes(file);
		ImageFormat format = ImageFormat.detect(content)
			.orElseThrow(() -> new BusinessException(ErrorCode.IMAGE_INVALID_TYPE));
		BufferedImage image = decode(content, format);

		int longSide = Math.max(image.getWidth(), image.getHeight());
		byte[] original = encode(image, format, scaleDownTo(policy.originalMaxLongSide(), longSide));
		byte[] thumbnail = encode(image, format, scaleDownTo(policy.thumbnailWidth(), image.getWidth()));
		return new ProcessedImage(format, original, thumbnail);
	}

	/** 작은 이미지는 키우지 않는다. */
	private static double scaleDownTo(int limit, int actual) {
		return Math.min(1.0, (double)limit / actual);
	}

	private static byte[] readBytes(MultipartFile file) {
		try {
			return file.getBytes();
		} catch (IOException e) {
			throw new UncheckedIOException("업로드 파일 읽기 실패", e);
		}
	}

	/** Thumbnailator는 InputStream에서 읽을 때 EXIF orientation을 픽셀에 반영한다(ImageProcessorTest로 확인). */
	private static BufferedImage decode(byte[] content, ImageFormat format) {
		try {
			return Thumbnails.of(new ByteArrayInputStream(content))
				.scale(1.0)
				.imageType(format.getImageType())
				.asBufferedImage();
		} catch (IOException | RuntimeException e) {
			// 매직 바이트는 맞지만 손상됐거나 디코딩할 수 없는 파일(CMYK JPEG 등)
			log.info("이미지 디코딩 실패: {}", e.toString());
			throw new BusinessException(ErrorCode.IMAGE_INVALID_TYPE);
		}
	}

	private static byte[] encode(BufferedImage image, ImageFormat format, double scale) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			Thumbnails.of(image)
				.scale(scale)
				.imageType(format.getImageType())
				.outputFormat(format.getExtension())
				.toOutputStream(out);
		} catch (IOException e) {
			throw new UncheckedIOException("이미지 인코딩 실패", e);
		}
		return out.toByteArray();
	}
}
