-- 피드(REQ-009)·검색(REQ-010) 조회 인덱스 개선. 실험 결과와 채택 이유: docs/experiments/query-index.md (도전 과제 ④)

-- 1) 피드 커서 인덱스에 위경도를 붙인다. 정렬(bumped_at DESC, id DESC)은 그대로 인덱스 순서로 읽고, bounding box·거리 조건을
--    인덱스 안에서 걸러(Index Condition Pushdown) 범위 밖 물품의 행을 읽지 않는다. 앞 세 컬럼이 같아 기존 인덱스는 지운다.
ALTER TABLE items
    DROP INDEX idx_items_status_bumped_at_id,
    ADD INDEX idx_items_status_bumped_at_id_latitude_longitude (status, bumped_at DESC, id DESC, latitude, longitude);

-- 2) 키워드 검색을 LIKE '%키워드%' 전체 훑기에서 FULLTEXT(ngram) 구문 검색으로 바꾼다.
--    ngram_token_size·불용어는 서버 기본값(2·켜짐)을 그대로 쓴다(서버 파라미터를 바꾸지 않는다). 앱은 한글·숫자 2~100글자
--    검색어만 이 인덱스로 찾고(불용어는 영문이라 영향이 없다), 그 밖은 LIKE로 찾는다(ItemListingRepositoryImpl.keywordMatches).
ALTER TABLE items ADD FULLTEXT INDEX idx_items_name_description (name, description) WITH PARSER ngram;
