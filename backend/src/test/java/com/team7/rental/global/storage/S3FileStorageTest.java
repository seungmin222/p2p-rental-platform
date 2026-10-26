package com.team7.rental.global.storage;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class S3FileStorageTest {

	private static final String PUBLIC_URL = "https://bucket.s3.ap-northeast-2.amazonaws.com";

	private final S3Client s3Client = mock(S3Client.class);
	private final S3FileStorage storage = new S3FileStorage(s3Client,
		new S3StorageProperties("ap-northeast-2", "bucket", PUBLIC_URL));

	@Test
	@DisplayName("버킷·새 키·Content-Type으로 업로드하고 공개 URL + 키를 반환한다")
	void upload() {
		// when
		StoredFile stored = storage.upload("items", "png", "image/png", new byte[] {1, 2});

		// then
		ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
		verify(s3Client).putObject(request.capture(), any(RequestBody.class));
		assertThat(request.getValue().bucket()).isEqualTo("bucket");
		assertThat(request.getValue().key()).isEqualTo(stored.key()).matches("items/[0-9a-f-]{36}\\.png");
		assertThat(request.getValue().contentType()).isEqualTo("image/png");
		assertThat(stored.url()).isEqualTo(PUBLIC_URL + "/" + stored.key());
		assertThat(storage.keyOf(stored.url())).isEqualTo(stored.key());
	}

	@Test
	@DisplayName("키로 객체를 삭제한다")
	void delete() {
		// when
		storage.delete("items/abc.jpg");

		// then
		verify(s3Client).deleteObject(DeleteObjectRequest.builder().bucket("bucket").key("items/abc.jpg").build());
	}
}
