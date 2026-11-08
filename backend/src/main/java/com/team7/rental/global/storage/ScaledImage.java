package com.team7.rental.global.storage;

/** {@link ImageProcessor#downscale} 결과. 원래 형식 그대로 다시 인코딩한(또는 작아서 그대로 둔) 바이트 */
public record ScaledImage(ImageFormat format, byte[] content) {
}
