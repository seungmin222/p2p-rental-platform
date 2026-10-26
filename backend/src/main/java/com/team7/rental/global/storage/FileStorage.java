package com.team7.rental.global.storage;

/**
 * 파일 저장소. 운영은 S3(prod 프로필), 로컬·테스트는 디스크(local·test 프로필)로 교체된다.
 * 외부 저장소 호출이므로 트랜잭션 안에서 부르지 않는다(backend/CLAUDE.md "트랜잭션·동시성").
 */
public interface FileStorage {

	/**
	 * 새 키({directory}/{UUID}.{extension})로 저장한다. 기존 파일을 덮어쓰지 않는다.
	 *
	 * @param directory 영문 소문자·숫자·하이픈·슬래시만(예: items, profiles)
	 * @param extension 점 없이(예: jpg)
	 */
	StoredFile upload(String directory, String extension, String contentType, byte[] content);

	/** 없는 키면 아무 일도 하지 않는다. */
	void delete(String key);

	/** DB에는 URL만 저장하므로, 삭제할 때 URL에서 키를 구한다. 이 저장소의 URL이 아니면 IllegalArgumentException. */
	String keyOf(String url);
}
