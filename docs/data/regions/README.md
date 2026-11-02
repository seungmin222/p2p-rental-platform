# 행정동 시드 범위와 출처

V9__insert_regions.sql: **3,565개**의 공식 코드·이름과 카카오 H 좌표가 일치한 행을 적재한다.
원본 3,630개 중 **65개 출장소는 미해결**이다. 전국 코드 전체 지원으로 표시하지 않는다.

- 코드·명칭: 행정안전부 「행정기관(행정동) 및 관할구역(법정동) 코드 변경내역 알림(2026.10.30. 기준)」, 첨부 jscode20261026.zip의 KIKcd_H.20261026.
- 원문: https://www.mois.go.kr/frt/bbs/type001/commonSelectBoardArticle.do?bbsId=BBSMSTR_000000000052&nttId=130674
- 원본 ZIP 해시·변환 기준: scripts/regions/source.json. 원본 CP949 고정 길이 300바이트; 읍면동명이 있고 말소일자가 없는 행만 추출. 시도·시군구 집계행 제외.
- 좌표: 사용자 PC에서 2026-11-02 카카오 REST API 실제 호출. 키는 infra/.env에서만 사용하고 결과 파일에 포함하지 않았다.
- API 계약: https://developers.kakao.com/docs/ko/kakaomap/rest-api
- 주소/장소 검색으로 얻은 후보를 coord2regioncode로 재조회한 뒤, `region_type=H`이며 공식 10자리 코드가 정확히 일치한 응답의 x/y를 중심 좌표로 사용. 이름만 같거나 법정동(B)인 결과는 사용하지 않는다.
- 원시 수집 결과: resolved-20261102.jsonl. 미해결 코드: unresolved-20261102.csv.
- 1차 3,563개, 검색어 제+숫자 정규화·주민센터 재조회로 상일제1동/상일제2동을 추가하여 3,565개.

## 미해결 코드의 처리

65개 모두 공식 명칭에 출장소가 포함된다. 이번 검색에서 해당 코드와 일치하는 H 응답을 얻지 못했다는 뜻이며, 카카오가 영구적으로 지원하지 않는다고 단정하지 않는다. 임의 좌표나 상위 읍면동 코드로 대체하지 않는다. 이 코드들은 검색 결과에 없으며 좌표 조회가 해당 코드를 반환하면 기존 계약대로 404 COMMON_NOT_FOUND다. 향후 별도 확인한 결과를 새 버전 마이그레이션으로 추가한다.

## DB 적용·재현

V9는 code 유니크 키 기준 upsert로 이름·시도·시군구·좌표를 갱신하고, **기존 id와 created_at은 유지**한다. 기존 member_regions/items 외래키는 유지된다. 자료에 없는 기존 지역을 삭제하지 않는다. 실제 적용은 Spring Boot 시작 시 Flyway가 수행한다.

```bash
python scripts/regions/build-seed.py docs/data/regions/resolved-20261102.jsonl docs/data/regions/unresolved-20261102.csv /tmp/regions-review.sql
```

생성기는 코드/이름 일치·유일성·좌표 범위·성공/미해결 목록의 완전한 분할을 검사한다. 일반 읍면동이 미해결이면 생성을 거부한다. 기존 출력 파일을 덮어쓰지 않는다. 적용된 V9 파일은 수정하지 않고 신규 번호를 예약하여 변경한다.

## 테스트 격리

실제 행정동 시드와 회원 회귀 테스트의 동일 코드 fixture가 충돌하지 않도록 IntegrationTestSupport는 각 테스트 **시작 전** 동적 테이블·Redis를 비운다. categories/damage_policies는 유지한다. RegionSeedIntegrationTest만 실제 V9 SQL을 다시 적재하여 시드·검색·중복 실행·ID/회원 참조 보존을 검증한다. 운영 데이터 정리와 무관한 테스트 전용 처리다.
