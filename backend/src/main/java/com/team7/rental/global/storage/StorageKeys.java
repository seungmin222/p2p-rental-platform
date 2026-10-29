package com.team7.rental.global.storage;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 저장 키 생성·검증. 키가 저장 경로 밖으로 나가지 못하게(../ 등) 허용 문자를 제한한다.
 * 용도별 디렉터리(FileStorage.upload의 directory) 상수는 여기에 모은다. 생성·검증 메서드는 저장소 구현만 쓴다.
 */
public final class StorageKeys {

	/** 인계·반납·판정 사진 (REQ-017·018) */
	public static final String INSPECTIONS = "inspections";

	private static final Pattern DIRECTORY = Pattern.compile("[a-z0-9-]+(/[a-z0-9-]+)*");
	private static final Pattern EXTENSION = Pattern.compile("[a-z0-9]{1,5}");
	private static final Pattern KEY = Pattern.compile("[a-z0-9-]+(/[a-z0-9-]+)*/[a-z0-9-]+\\.[a-z0-9]{1,5}");

	private StorageKeys() {}

	static String newKey(String directory, String extension) {
		if (directory == null || !DIRECTORY.matcher(directory).matches()) {
			throw new IllegalArgumentException("허용되지 않는 디렉터리: " + directory);
		}
		if (extension == null || !EXTENSION.matcher(extension).matches()) {
			throw new IllegalArgumentException("허용되지 않는 확장자: " + extension);
		}
		return directory + "/" + UUID.randomUUID() + "." + extension;
	}

	static String validate(String key) {
		if (key == null || !KEY.matcher(key).matches()) {
			throw new IllegalArgumentException("허용되지 않는 키: " + key);
		}
		return key;
	}

	/** publicPrefix + "/" + key 형태의 URL에서 키를 꺼낸다. */
	static String fromUrl(String url, String publicPrefix) {
		String prefix = publicPrefix + "/";
		if (url == null || !url.startsWith(prefix)) {
			throw new IllegalArgumentException("이 저장소의 URL이 아닙니다: " + url);
		}
		return validate(url.substring(prefix.length()));
	}
}
