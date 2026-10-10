package com.team7.rental.global.storage;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** S3 저장소(prod 프로필). 자격 증명은 AWS SDK 기본 체인(환경 변수 AWS_ACCESS_KEY_ID 등 또는 EC2 역할)으로 얻는다. */
public class S3FileStorage implements FileStorage {

	private final S3Client s3Client;
	private final String bucket;
	private final String publicUrl;

	public S3FileStorage(S3Client s3Client, S3StorageProperties properties) {
		this.s3Client = s3Client;
		this.bucket = properties.bucket();
		this.publicUrl = properties.publicUrl();
	}

	@Override
	public StoredFile upload(String directory, String extension, String contentType, byte[] content) {
		String key = StorageKeys.newKey(directory, extension);
		s3Client.putObject(PutObjectRequest.builder()
			.bucket(bucket)
			.key(key)
			.contentType(contentType)
			.build(), RequestBody.fromBytes(content));
		return new StoredFile(key, publicUrl + "/" + key);
	}

	@Override
	public void delete(String key) {
		// S3 DeleteObject는 없는 키여도 성공한다
		s3Client.deleteObject(DeleteObjectRequest.builder()
			.bucket(bucket)
			.key(StorageKeys.validate(key))
			.build());
	}

	@Override
	public String keyOf(String url) {
		return StorageKeys.fromUrl(url, publicUrl);
	}
}
