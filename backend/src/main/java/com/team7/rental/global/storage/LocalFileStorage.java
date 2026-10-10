package com.team7.rental.global.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** 로컬 디스크 저장소(local·test 프로필). AWS 키 없이 동작한다. 파일은 {urlPrefix}/**로 서빙된다(LocalStorageConfig). */
public class LocalFileStorage implements FileStorage {

	private final Path rootDir;
	private final String urlPrefix;

	public LocalFileStorage(LocalStorageProperties properties) {
		this.rootDir = properties.rootDir().toAbsolutePath().normalize();
		this.urlPrefix = properties.urlPrefix();
	}

	@Override
	public StoredFile upload(String directory, String extension, String contentType, byte[] content) {
		String key = StorageKeys.newKey(directory, extension);
		Path path = resolve(key);
		try {
			Files.createDirectories(path.getParent());
			Files.write(path, content, StandardOpenOption.CREATE_NEW);
		} catch (IOException e) {
			throw new UncheckedIOException("파일 저장 실패: " + key, e);
		}
		return new StoredFile(key, urlPrefix + "/" + key);
	}

	@Override
	public void delete(String key) {
		try {
			Files.deleteIfExists(resolve(StorageKeys.validate(key)));
		} catch (IOException e) {
			throw new UncheckedIOException("파일 삭제 실패: " + key, e);
		}
	}

	@Override
	public String keyOf(String url) {
		return StorageKeys.fromUrl(url, urlPrefix);
	}

	private Path resolve(String key) {
		Path path = rootDir.resolve(key).normalize();
		if (!path.startsWith(rootDir)) {
			throw new IllegalArgumentException("저장 경로 밖의 키: " + key);
		}
		return path;
	}
}
