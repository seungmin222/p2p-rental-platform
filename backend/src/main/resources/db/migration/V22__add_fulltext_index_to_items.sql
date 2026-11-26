-- 물품 키워드 검색(REQ-010)을 LIKE '%키워드%' 전체 훑기에서 FULLTEXT(ngram) 구문 검색으로 바꾼다.
-- 실험 결과와 채택 이유: docs/experiments/query-index.md (도전 과제 ④)
-- ngram_token_size·불용어는 서버 기본값(2·켜짐)을 그대로 쓴다(서버 파라미터를 바꾸지 않는다). 앱은 한글·숫자 2~100글자
-- 검색어만 이 인덱스로 찾고(불용어는 영문이라 영향이 없다), 그 밖은 LIKE로 찾는다(ItemListingRepositoryImpl.keywordMatches).
ALTER TABLE items ADD FULLTEXT INDEX ft_items_name_description (name, description) WITH PARSER ngram;
