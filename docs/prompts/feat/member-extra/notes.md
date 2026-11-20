# feat/member-extra 결정 기록

REQ-021 사용자 프로필, REQ-037 약관 동의, REQ-036 비밀번호 재설정(백엔드). 프론트 SCR-13 등은 범위 밖.

## REQ-021 사용자 프로필
- **거래 횟수는 rental의 조회 클래스 `RentalCountReader.countCompleted`로 센다(포트 아님).** member가 rental을 부르지만 새 `MemberProfileQueryService`에서만 부르므로, rental이 의존하는 `MemberQueryService`와 빈 순환이 없다. 대여자·제공자 인덱스를 각각 타도록 OR 대신 COUNT 2개로 나눴다.
- **등록 물품 페이지 크기는 새 키 `profile-items.page` = 20건(고정)으로 두었다(사용자 결정).** 다른 목록 키와 같은 값. `PolicyProperties`에는 위치 기반 생성자를 쓰는 단위 테스트를 덜 건드리려고 맨 끝에 붙였다.
- **등록 물품 정렬은 피드와 같은 bumpedAt·id 내림차순.** 카드에 bumpedAt을 보여 주므로 화면 순서와 맞춘다(내 물품 관리는 id 내림차순이라 다르다).
- **등록 물품 API는 item 도메인 `MemberItemController`에 둔다.** 내 물품(`MyItemController`)과 같이 경로는 회원 아래지만 물품 목록이라서. Swagger 태그는 api.md 섹션(회원·인증)을 따라 Member.
- **탈퇴 회원: 프로필 200 + `isWithdrawn=true`, 나머지 null / 받은 후기·등록 물품 200 빈 목록(사용자 결정, open-issues #55에 기록).** 후기 작성자 표시(`isWithdrawn`)처럼 문구는 프론트가 정한다. 받은 후기 404 판정용 `MemberQueryService.exists`는 `isWithdrawn`(없으면 MEMBER_NOT_FOUND)으로 바꿨다.
- **차단(REQ-056)은 확장 지점만**: 서비스가 조회자 ID(nullable)를 받고 아직 쓰지 않는다. 컨트롤러는 `@LoginMember(required = false)`.

## REQ-037 약관 동의
- **본문은 마크다운 리소스(`resources/terms/*.md`), 버전은 `application.yml` `app.terms.*-version`.** 관리자 편집(057)이 없어 개정은 파일 교체 + 버전 올림 + 배포로 충분하고, DB 테이블은 erd.md에 없는 테이블이 된다. 본문은 기동 시 한 번 읽고 없으면 기동 실패. 본문은 초안(사용자 결정)이라 정식 문구 검토가 남았다.
- **대여 금지 품목 문서도 마크다운 파일로 옮겼다(계획의 "코드로 만든다"에서 바꿈).** 목록이 문장이라 코드 상수로 두면 같은 사본이 코드에 생기는 건 마찬가지라, 다른 문서와 같은 방식으로 두고 policies.md §2에 함께 고치라고 적었다.
- **동의 항목 `TermsAgreementType`과 문서 `TermsDocumentType`을 나눴다.** 개인정보 "수집·이용 동의"(동의)와 "처리방침"(문서)이 다르고, 만 14세 확인은 문서가 없다. PRIVACY_COLLECTION 버전은 처리방침 문서 버전을 따른다.
- **가입 요청은 `agreements` 중첩 객체 + `Boolean` + `@NotNull @AssertTrue`(사용자 결정 (a) 바로 필수).** false거나 생략한 항목마다 400 details agreements.{필드}(primitive boolean이면 Jackson이 첫 누락 필드에서 역직렬화를 멈춰 details가 하나만 나와서 바꿨다), 객체를 빼면 details agreements. 현재 프론트 가입 화면은 체크박스가 없어 이 브랜치 머지 후 가입이 400이 된다 → 프론트 담당에게 알려야 한다.
- **requiresTermsAgreement는 로그인·재발급 응답 모두에 준다(쿼리 1개).** 같은 DTO이고, 재발급만 하는 프론트도 알 수 있게.
- **재동의 API `POST /api/members/me/terms-agreements {types}` 추가(사용자 결정).** 이미 현재 버전에 동의한 항목은 건너뛰고(멱등), 회원 행 락으로 동시 요청의 유니크 충돌을 막는다. 옛 버전 행은 지우지 않는다(스냅샷).
- **거부 시 이용 제한은 안내만(open-issues #56).** 서버 차단은 보안 필터 변경이라 범위 밖.
- 인덱스는 UK(member_id, terms_type, terms_version) 하나만 둔다. 앞부분이 member_id라 회원별 조회도 이걸 탄다.
