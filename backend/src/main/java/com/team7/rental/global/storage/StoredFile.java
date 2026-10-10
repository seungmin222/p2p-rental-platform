package com.team7.rental.global.storage;

/**
 * @param key 저장소 안의 경로(예: items/3f2a….jpg). 삭제할 때 쓴다
 * @param url 브라우저가 접근하는 주소. DB에는 이 값만 저장한다
 */
public record StoredFile(String key, String url) {
}
