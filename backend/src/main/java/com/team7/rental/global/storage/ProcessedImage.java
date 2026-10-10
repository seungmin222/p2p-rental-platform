package com.team7.rental.global.storage;

/**
 * 업로드 이미지 처리 결과. 둘 다 EXIF 회전을 픽셀에 반영하고 메타데이터(GPS 포함) 없이 재인코딩한 것이다.
 * FileStorage.upload(directory, format.getExtension(), format.getContentType(), original/thumbnail)로 저장한다.
 *
 * @param original 긴 변 image.original-max-long-side 이하
 * @param thumbnail 가로 thumbnail.width 이하
 */
public record ProcessedImage(ImageFormat format, byte[] original, byte[] thumbnail) {
}
