package com.team7.rental.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import com.team7.rental.global.storage.TestImages;

/** 인계·반납·상태 확인 multipart 요청 part (REQ-017·018) */
public final class InspectionParts {

	/** test 프로필 app.storage.local.root-dir + StorageKeys.INSPECTIONS */
	private static final Path INSPECTION_DIR = Path.of("build/test-uploads/inspections");

	private InspectionParts() {}

	/** 작은 JPEG 사진 part */
	public static MockMultipartFile image() {
		return new MockMultipartFile("images", "photo.jpg", MediaType.IMAGE_JPEG_VALUE, TestImages.jpeg(40, 30));
	}

	/** Content-Type application/json인 request part */
	public static MockMultipartFile json(String json) {
		return new MockMultipartFile("request", "", MediaType.APPLICATION_JSON_VALUE,
			json.getBytes(StandardCharsets.UTF_8));
	}

	/** 저장소에 남은 인계·반납 사진 수. 업로드 정리 검증용 */
	public static long storedImageCount() {
		if (!Files.isDirectory(INSPECTION_DIR)) {
			return 0;
		}
		try (Stream<Path> files = Files.list(INSPECTION_DIR)) {
			return files.count();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
