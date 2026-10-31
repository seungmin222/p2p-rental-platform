package com.team7.rental.global.storage;

import static org.assertj.core.api.Assertions.*;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.global.policy.PolicyProperties;

class ImageProcessorTest {

	private static final DataSize MAX_SIZE = DataSize.ofMegabytes(5);

	private static final long MAX_PIXELS = 60_000_000L;

	private final ImageProcessor imageProcessor = new ImageProcessor(
		new PolicyProperties(new PolicyProperties.Image(1600, 400, MAX_PIXELS), null, null, null, null, null, null,
			null, null, null, null, null, null, null, null, null, null));

	@Test
	@DisplayName("픽셀 수가 60MP를 넘으면 디코딩하기 전에 IMAGE_SIZE_EXCEEDED다")
	void rejectsTooManyPixels() {
		// given: 8000 x 7501 = 60,008,000px. 헤더만 있어 디코딩까지 가면 IMAGE_INVALID_TYPE이 된다
		MockMultipartFile file = new MockMultipartFile("image", "big.png", "image/png",
			TestImages.pngHeaderOnly(8000, 7501));

		// when, then
		assertErrorCode(file, ErrorCode.IMAGE_SIZE_EXCEEDED);
	}

	@Test
	@DisplayName("픽셀 수가 정확히 60MP면 픽셀 검사를 통과한다")
	void allowsExactlyMaxPixels() {
		// given: 8000 x 7500 = 60,000,000px. 검사를 통과하면 디코딩 단계에서 헤더만 있는 파일이라 IMAGE_INVALID_TYPE
		MockMultipartFile file = new MockMultipartFile("image", "big.png", "image/png",
			TestImages.pngHeaderOnly(8000, 7500));

		// when, then
		assertErrorCode(file, ErrorCode.IMAGE_INVALID_TYPE);
	}

	@Test
	@DisplayName("픽스처 확인: 일반 ImageIO는 EXIF orientation을 무시하고 저장된 픽셀 그대로(가로) 읽는다")
	void fixtureHasOrientation() {
		// given
		byte[] fixture = TestImages.jpegWithExif(2000, 1000, 6);

		// when
		BufferedImage raw = TestImages.read(fixture);

		// then
		assertThat(TestImages.hasExif(fixture)).isTrue();
		assertThat(raw.getWidth()).isEqualTo(2000);
		assertThat(raw.getHeight()).isEqualTo(1000);
	}

	@Test
	@DisplayName("orientation=6 JPEG는 시계 방향 90도 회전을 픽셀에 반영한 뒤 줄인다")
	void appliesExifOrientation() {
		// given: 저장 2000x1000(왼쪽 빨강) → 표시 1000x2000(위쪽 빨강)
		MockMultipartFile file = jpegFile(TestImages.jpegWithExif(2000, 1000, 6));

		// when
		ProcessedImage result = imageProcessor.process(file, MAX_SIZE);

		// then: 긴 변 1600 → 800x1600, 썸네일 가로 400 → 400x800
		BufferedImage original = TestImages.read(result.original());
		BufferedImage thumbnail = TestImages.read(result.thumbnail());
		assertThat(result.format()).isEqualTo(ImageFormat.JPEG);
		assertThat(original.getWidth()).isEqualTo(800);
		assertThat(original.getHeight()).isEqualTo(1600);
		assertThat(thumbnail.getWidth()).isEqualTo(400);
		assertThat(thumbnail.getHeight()).isEqualTo(800);
		assertThat(isReddish(original.getRGB(400, 100))).as("위쪽이 빨강").isTrue();
		assertThat(isReddish(original.getRGB(400, 1500))).as("아래쪽은 빨강 아님").isFalse();
	}

	@Test
	@DisplayName("재인코딩한 원본·썸네일에는 EXIF(GPS 포함)가 남지 않는다")
	void removesExif() {
		// given
		MockMultipartFile file = jpegFile(TestImages.jpegWithExif(2000, 1000, 6));

		// when
		ProcessedImage result = imageProcessor.process(file, MAX_SIZE);

		// then
		assertThat(TestImages.hasExif(result.original())).isFalse();
		assertThat(TestImages.hasExif(result.thumbnail())).isFalse();
	}

	@Test
	@DisplayName("가로 사진도 긴 변을 1600px로 줄이고, 썸네일은 가로 400px이다")
	void resizesLandscape() {
		// given
		MockMultipartFile file = jpegFile(TestImages.jpeg(3200, 1000));

		// when
		ProcessedImage result = imageProcessor.process(file, MAX_SIZE);

		// then
		BufferedImage original = TestImages.read(result.original());
		BufferedImage thumbnail = TestImages.read(result.thumbnail());
		assertThat(original.getWidth()).isEqualTo(1600);
		assertThat(original.getHeight()).isEqualTo(500);
		assertThat(thumbnail.getWidth()).isEqualTo(400);
		assertThat(thumbnail.getHeight()).isEqualTo(125);
	}

	@Test
	@DisplayName("기준보다 작은 이미지는 키우지 않는다")
	void doesNotUpscale() {
		// given
		MockMultipartFile file = jpegFile(TestImages.jpeg(200, 100));

		// when
		ProcessedImage result = imageProcessor.process(file, MAX_SIZE);

		// then
		BufferedImage original = TestImages.read(result.original());
		BufferedImage thumbnail = TestImages.read(result.thumbnail());
		assertThat(original.getWidth()).isEqualTo(200);
		assertThat(original.getHeight()).isEqualTo(100);
		assertThat(thumbnail.getWidth()).isEqualTo(200);
		assertThat(thumbnail.getHeight()).isEqualTo(100);
	}

	@Test
	@DisplayName("PNG는 PNG로 저장하고 투명도를 유지한다")
	void keepsPngTransparency() {
		// given: 왼쪽 절반 투명, 오른쪽 절반 불투명
		MockMultipartFile file = new MockMultipartFile("image", "a.png", "image/png",
			TestImages.pngWithTransparency(800, 400));

		// when
		ProcessedImage result = imageProcessor.process(file, MAX_SIZE);

		// then
		assertThat(result.format()).isEqualTo(ImageFormat.PNG);
		for (byte[] content : new byte[][] {result.original(), result.thumbnail()}) {
			BufferedImage image = TestImages.read(content);
			assertThat(image.getColorModel().hasAlpha()).isTrue();
			assertThat(alpha(image.getRGB(10, 10))).isZero();
			assertThat(alpha(image.getRGB(image.getWidth() - 10, 10))).isEqualTo(255);
		}
		assertThat(TestImages.read(result.thumbnail()).getWidth()).isEqualTo(400);
	}

	@Test
	@DisplayName("jpg·png가 아닌 파일은 확장자·Content-Type과 관계없이 IMAGE_INVALID_TYPE이다")
	void rejectsInvalidType() {
		// given
		MockMultipartFile gif = new MockMultipartFile("image", "a.jpg", "image/jpeg",
			"GIF89a....".getBytes(StandardCharsets.US_ASCII));
		MockMultipartFile text = new MockMultipartFile("image", "a.png", "image/png",
			"hello".getBytes(StandardCharsets.US_ASCII));
		MockMultipartFile empty = new MockMultipartFile("image", "a.jpg", "image/jpeg", new byte[0]);

		// when, then
		assertErrorCode(gif, ErrorCode.IMAGE_INVALID_TYPE);
		assertErrorCode(text, ErrorCode.IMAGE_INVALID_TYPE);
		assertErrorCode(empty, ErrorCode.IMAGE_INVALID_TYPE);
	}

	@Test
	@DisplayName("매직 바이트는 PNG지만 디코딩할 수 없는 파일은 IMAGE_INVALID_TYPE이다")
	void rejectsCorruptedImage() {
		// given
		byte[] corrupted = new byte[100];
		System.arraycopy(ImageFormat.PNG.getMagic(), 0, corrupted, 0, ImageFormat.PNG.getMagic().length);
		MockMultipartFile file = new MockMultipartFile("image", "a.png", "image/png", corrupted);

		// when, then
		assertErrorCode(file, ErrorCode.IMAGE_INVALID_TYPE);
	}

	@Test
	@DisplayName("호출자가 넘긴 용량 한도를 넘으면 IMAGE_SIZE_EXCEEDED다")
	void rejectsOversizedFile() {
		// given
		MockMultipartFile file = jpegFile(new byte[(int)MAX_SIZE.toBytes() + 1]);

		// when, then
		assertErrorCode(file, ErrorCode.IMAGE_SIZE_EXCEEDED);
	}

	private void assertErrorCode(MockMultipartFile file, ErrorCode errorCode) {
		assertThatThrownBy(() -> imageProcessor.process(file, MAX_SIZE))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(errorCode);
	}

	private static MockMultipartFile jpegFile(byte[] content) {
		return new MockMultipartFile("image", "photo.jpg", "image/jpeg", content);
	}

	private static boolean isReddish(int rgb) {
		Color color = new Color(rgb);
		return color.getRed() > 200 && color.getBlue() < 60;
	}

	private static int alpha(int argb) {
		return (argb >>> 24) & 0xFF;
	}
}
