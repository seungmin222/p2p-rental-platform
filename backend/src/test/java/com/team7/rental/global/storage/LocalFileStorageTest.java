package com.team7.rental.global.storage;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileStorageTest {

	@TempDir
	Path rootDir;

	@Test
	@DisplayName("디렉터리 아래 새 키로 저장하고 URL은 서빙 경로 + 키다")
	void upload() throws Exception {
		// given
		LocalFileStorage storage = storage();
		byte[] content = {1, 2, 3};

		// when
		StoredFile stored = storage.upload("items", "jpg", "image/jpeg", content);

		// then
		assertThat(stored.key()).matches("items/[0-9a-f-]{36}\\.jpg");
		assertThat(stored.url()).isEqualTo("/files/" + stored.key());
		assertThat(Files.readAllBytes(rootDir.resolve(stored.key()))).isEqualTo(content);
	}

	@Test
	@DisplayName("삭제하면 파일이 없어지고, 없는 키를 삭제해도 예외가 없다")
	void delete() {
		// given
		LocalFileStorage storage = storage();
		StoredFile stored = storage.upload("profiles", "png", "image/png", new byte[] {1});

		// when
		storage.delete(stored.key());

		// then
		assertThat(rootDir.resolve(stored.key())).doesNotExist();
		assertThatCode(() -> storage.delete(stored.key())).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("URL에서 키를 구한다. 이 저장소의 URL이 아니면 예외다")
	void keyOf() {
		// given
		LocalFileStorage storage = storage();
		StoredFile stored = storage.upload("items", "jpg", "image/jpeg", new byte[] {1});

		// when, then
		assertThat(storage.keyOf(stored.url())).isEqualTo(stored.key());
		assertThatThrownBy(() -> storage.keyOf("https://example.com/items/a.jpg"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("저장 경로 밖을 가리키는 디렉터리·키는 거부한다")
	void rejectsPathTraversal() {
		// given
		LocalFileStorage storage = storage();

		// when, then
		assertThatThrownBy(() -> storage.upload("../etc", "jpg", "image/jpeg", new byte[] {1}))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> storage.delete("items/../../secret.txt"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> storage.keyOf("/files/../application.yml"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	private LocalFileStorage storage() {
		return new LocalFileStorage(new LocalStorageProperties(rootDir, "/files"));
	}
}
