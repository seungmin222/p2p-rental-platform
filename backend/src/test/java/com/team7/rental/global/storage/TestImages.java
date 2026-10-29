package com.team7.rental.global.storage;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;

/** 이미지 테스트 픽스처. 바이너리 파일을 커밋하지 않고 코드로 만든다. */
public final class TestImages {

	private TestImages() {}

	/** 왼쪽 절반 빨강, 오른쪽 절반 파랑인 JPEG (EXIF 없음) */
	public static byte[] jpeg(int width, int height) {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setColor(Color.RED);
		g.fillRect(0, 0, width / 2, height);
		g.setColor(Color.BLUE);
		g.fillRect(width / 2, 0, width - width / 2, height);
		g.dispose();
		return write(image, "jpg");
	}

	/**
	 * 휴대폰 사진처럼 EXIF Orientation과 GPS 정보가 든 JPEG. 저장된 픽셀은 width x height(왼쪽 빨강·오른쪽 파랑)이다.
	 * orientation=6이면 보는 쪽에서 시계 방향 90도 회전해 표시해야 한다(빨강이 위로).
	 */
	static byte[] jpegWithExif(int width, int height, int orientation) {
		byte[] jpeg = jpeg(width, height);
		byte[] app1 = exifSegment(orientation);
		// SOI(2바이트) + APP0(JFIF) 뒤에 APP1(Exif)을 끼워 넣는다
		int app0Length = ((jpeg[4] & 0xFF) << 8) | (jpeg[5] & 0xFF);
		int insertAt = 4 + app0Length;
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(jpeg, 0, insertAt);
		out.writeBytes(app1);
		out.write(jpeg, insertAt, jpeg.length - insertAt);
		return out.toByteArray();
	}

	/** 왼쪽 절반 투명, 오른쪽 절반 불투명 초록인 PNG */
	static byte[] pngWithTransparency(int width, int height) {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setColor(Color.GREEN);
		g.fillRect(width / 2, 0, width - width / 2, height);
		g.dispose();
		return write(image, "png");
	}

	/**
	 * 헤더(IHDR)만 있고 픽셀 데이터(IDAT)가 없는 PNG. 큰 해상도를 메모리 없이 흉내 낸다.
	 * 헤더로 가로·세로는 읽히지만 디코딩은 실패한다.
	 */
	static byte[] pngHeaderOnly(int width, int height) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.writeBytes(ImageFormat.PNG.getMagic());
		ByteBuffer ihdr = ByteBuffer.allocate(13).putInt(width).putInt(height)
			.put((byte)8).put((byte)6).put((byte)0).put((byte)0).put((byte)0);
		out.writeBytes(pngChunk("IHDR", ihdr.array()));
		out.writeBytes(pngChunk("IEND", new byte[0]));
		return out.toByteArray();
	}

	private static byte[] pngChunk(String type, byte[] data) {
		byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
		CRC32 crc = new CRC32();
		crc.update(typeBytes);
		crc.update(data);
		return ByteBuffer.allocate(12 + data.length)
			.putInt(data.length).put(typeBytes).put(data).putInt((int)crc.getValue())
			.array();
	}

	static BufferedImage read(byte[] content) {
		try {
			return ImageIO.read(new ByteArrayInputStream(content));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** JPEG 세그먼트를 SOS 전까지 훑어 Exif APP1이 있는지 확인한다. */
	static boolean hasExif(byte[] jpeg) {
		int i = 2;
		while (i + 4 <= jpeg.length && (jpeg[i] & 0xFF) == 0xFF) {
			int marker = jpeg[i + 1] & 0xFF;
			if (marker == 0xDA) {
				return false;
			}
			int length = ((jpeg[i + 2] & 0xFF) << 8) | (jpeg[i + 3] & 0xFF);
			if (marker == 0xE1 && new String(jpeg, i + 4, Math.min(4, length - 2), StandardCharsets.US_ASCII)
				.equals("Exif")) {
				return true;
			}
			i += 2 + length;
		}
		return false;
	}

	private static byte[] write(BufferedImage image, String format) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			ImageIO.write(image, format, out);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return out.toByteArray();
	}

	/** APP1: "Exif\0\0" + TIFF(빅 엔디언). IFD0 = Orientation + GPS IFD 포인터, GPS IFD = GPSLatitudeRef "N" */
	private static byte[] exifSegment(int orientation) {
		int gpsIfdOffset = 8 + 2 + 2 * 12 + 4;
		ByteBuffer tiff = ByteBuffer.allocate(gpsIfdOffset + 2 + 12 + 4);
		tiff.put((byte)'M').put((byte)'M').putShort((short)0x002A).putInt(8);
		tiff.putShort((short)2);
		tiff.putShort((short)0x0112).putShort((short)3).putInt(1).putShort((short)orientation).putShort((short)0);
		tiff.putShort((short)0x8825).putShort((short)4).putInt(1).putInt(gpsIfdOffset);
		tiff.putInt(0);
		tiff.putShort((short)1);
		tiff.putShort((short)0x0001).putShort((short)2).putInt(2).put((byte)'N').put((byte)0).putShort((short)0);
		tiff.putInt(0);

		byte[] header = "Exif\0\0".getBytes(StandardCharsets.US_ASCII);
		int length = 2 + header.length + tiff.capacity();
		ByteBuffer segment = ByteBuffer.allocate(2 + length);
		segment.put((byte)0xFF).put((byte)0xE1).putShort((short)length).put(header).put(tiff.array());
		return segment.array();
	}
}
