# feat/member-extra 결정 기록

## 요약
- API: `GET /api/members/{id}`(프로필), `GET /api/members/{id}/items`(등록 물품, `profile-items.page`), `GET /api/terms`, `POST /api/members/me/terms-agreements`, `POST /api/auth/password-reset`, `POST /api/auth/password-reset/confirm`. 가입 요청에 `agreements` 필수, 로그인·재발급 응답에 `requiresTermsAgreement`. 탈퇴 회원 프로필은 `isWithdrawn`만, 받은 후기·등록 물품은 빈 목록.
- 주요 파일: `member/service/MemberProfileQueryService`·`TermsService`·`PasswordResetService`, `member/client/PasswordResetMailDispatcher`(백그라운드 발송)·`SmtpPasswordResetMailSender`, `rental/service/RentalCountReader`, `item/controller/MemberItemController`, `V19__create_terms_agreements.sql`, `resources/terms/*.md`(초안). 새 ErrorCode `AUTH_RESET_TOKEN_INVALID`.
- 남은 일: 약관 본문 정식 검토, 거부 시 서버 이용 제한(open-issues #56), 차단 시 물품 숨김(056), 신뢰 점수 표시(054). 탈퇴(043)는 `RefreshTokenRepository.deleteAllFamilies`를 쓰고 #55 counterpart를 정한다.
- 프론트: 이 브랜치 머지 후 가입은 agreements 없이는 400 → SCR-01 약관 체크박스·`/password-reset?token=` 화면, SCR-13 프로필, SCR-15 약관 화면이 필요하다.

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

## REQ-036 비밀번호 재설정
- **메일은 백그라운드 발송(사용자 결정 (a)).** "가입 여부와 무관하게 같은 응답"을 우선했다. 동기로 보내면 가입한 이메일에서만 응답 시간·SMTP 실패가 달라 가입 여부가 드러난다. 발송 실패는 로그만(이메일 주소는 남기지 않음).
- **실행기는 빈으로 내놓지 않고 `PasswordResetMailDispatcher`가 직접 가진다.** `Executor` 빈이 생기면 Boot 기본 `applicationTaskExecutor`가 빠져 다른 비동기 처리에 영향이 갈 수 있어서. `@EnableAsync`도 쓰지 않았다. 테스트는 `app.mail.async=false`(SyncTaskExecutor)로 결정적으로 검증한다.
- **메일은 `PasswordResetMailSender` 인터페이스 뒤 SMTP 구현, 테스트는 `FakePasswordResetMailSender`(@Primary).** 외부 연동은 인터페이스 뒤에 두는 규칙. 의존성 `spring-boot-starter-mail` 추가(버전은 Boot BOM).
- **링크 주소는 `app.frontend.base-url`(로컬 기본 http://localhost:5173, 운영 `FRONTEND_BASE_URL` 필수), 발신 주소 `app.mail.from`(운영 `MAIL_FROM` 필수).** `.env.example`에는 비운 값이 기본값을 덮지 않게 주석으로 넣었다.
- **요청 제한 키는 이메일을 소문자로 바꿔 쓴다.** members.email 조회는 MySQL 대소문자 무시 정렬이라, 대소문자만 바꿔 제한을 피하지 못하게.
- **토큰은 먼저 GETDEL로 지운 뒤 DB를 바꾼다.** 1회용을 원자적으로 보장하려고. DB 저장이 실패하면 그 토큰은 버려지고 다시 요청한다.
- **소셜 전용 계정(비밀번호 없음)은 링크 대신 카카오 로그인 안내 메일(사용자 결정).** 041 미구현이라 지금은 그런 계정이 없다. confirm은 이런 계정·탈퇴 회원이면 AUTH_RESET_TOKEN_INVALID.
- **같은 회원의 이전 토큰 무효화 키(pwreset:member:*)는 두지 않는다(사용자 결정).** 1회용이고 재설정하면 전체 기기 로그아웃이라.
- `RefreshTokenRepository.deleteAllFamilies`를 추가했다(탈퇴 REQ-043·이용 정지도 이걸 쓰면 된다).

