# 회원·인증·동네 (member)

담당: 신수정(B) · 화면: SCR-01, 02, 12, 13, 15 · 엔티티: members, regions, member_regions, terms_agreements

## REQ-001 회원가입 [1]
`POST /api/members`
- 입력: email(형식, 중복 불가), password(8~20자, 영문·숫자·특수문자 모두 포함), nickname(2~10자, 중복 불가)
- 처리: 중복 검사 → BCrypt 저장 → provider=LOCAL, role=USER, status=ACTIVE, trustScore=50
- 완료: 저장 후 프론트는 로그인 화면으로 이동
- 예외: 중복 → 409 `MEMBER_EMAIL_DUPLICATED` / `MEMBER_NICKNAME_DUPLICATED`, 형식 → 400 + 필드별 details
- 2순위 연결: REQ-037 약관 동의 필드가 추가된다(필수 3개 미동의 시 400). 1순위에서는 받지 않아도 되나 요청 DTO 확장이 쉽게 둔다.

## REQ-002 로그인·로그아웃 [1]
`POST /api/auth/login`, `POST /api/auth/logout`
- Spring Security 인증. 성공 시 Access Token(JWT 30분, 응답 바디) + Refresh Token(14일, HttpOnly 쿠키, Redis `rt:{memberId}`)
- 로그아웃: Redis RT 삭제, 쿠키 만료
- 예외: 계정 불일치 401 `AUTH_INVALID_CREDENTIALS` (이메일/비번 중 무엇이 틀렸는지 구분 금지), 탈퇴 회원 401 동일 코드
- JWT claim: `sub=memberId`, `role`. 닉네임 등 변경 가능한 값은 넣지 않는다.

## REQ-003 토큰 재발급 [1]
`POST /api/auth/reissue`
- RT 쿠키를 Redis 값과 비교 → 일치 시 AT·RT 모두 새로 발급, Redis 교체 (Rotation)
- **재사용 감지**: 이미 교체된 RT가 들어오면 탈취로 간주 → 해당 회원 RT 삭제(전체 무효화) 후 401
- 만료·불일치 401 → 프론트는 로그인 화면으로
- 프론트: 401 수신 시 재발급 후 원 요청 **1회만** 재시도 (동시 401 여러 개면 재발급은 한 번만 — frontend/CLAUDE.md 참고)
- 구현 힌트: RT를 단순 문자열 비교로만 하면 "이미 교체된 토큰"과 "위조 토큰"을 구분 못 한다. RT에 `jti`/family id를 넣고 Redis에 현재 jti 저장 → 서명은 유효한데 jti 불일치면 재사용으로 판단.

## REQ-004 동네 설정 및 인증 [1]
- 동네 검색: `GET /api/regions?keyword=` (행정동 마스터 LIKE)
- 현재 위치: 브라우저 Geolocation → `GET /api/regions/by-coord` (서버가 카카오 로컬 API로 좌표→행정동 코드 변환)
- 추가: `POST /api/members/me/regions` (최대 2개, 3번째 시도 → 409 `REGION_LIMIT_EXCEEDED` "기존 동네를 삭제하세요")
- 인증: `PATCH /api/members/me/regions/{id}/verify` body `{lat, lng}` → 서버가 좌표의 행정동 == 해당 region이면 `verified_at=now`. **좌표는 저장하지 않는다.**
- 유효기간 30일. 대표 동네 1개가 피드·검색 기준.
- **물품 등록·예약은 대표 동네가 인증(유효기간 내)된 경우에만** → 아니면 403 `REGION_NOT_VERIFIED`
- 예외: 위치 권한 거부 → 프론트에서 안내 후 검색만 허용(인증 미완료 상태로 저장 가능), 불일치 → 400 `REGION_VERIFY_MISMATCH`
- 비회원: 임시 동네 선택은 브라우저 localStorage에만 보관, 서버 저장 없음
- 첫 동네 추가 시 자동으로 대표 동네. 대표 동네 삭제 시 남은 동네를 대표로.

## REQ-005 프로필 수정 [1]
`PATCH /api/members/me` (multipart: profileImage?, nickname?), `PATCH /api/members/me/password`
- 요청한 항목만 반영. 이미지 jpg/png 5MB → S3, URL만 저장
- 비밀번호 변경: 현재 비번 확인(불일치 400 `MEMBER_PASSWORD_MISMATCH`) → BCrypt → **다른 기기 RT 무효화**(Redis 삭제 후 현재 기기에 새 RT 발급)
- 닉네임 중복 409. 변경 내용은 프로필·물품·채팅에 반영(닉네임은 조인 조회, 비정규화 금지)

## REQ-021 사용자 프로필 조회 [2]
`GET /api/members/{id}`, `/reviews`, `/items`
- 표시: 프로필 사진, 닉네임, 대표 동네명, 가입일, 거래 횟수(대여+제공, COMPLETED 기준), 받은 후기, 등록 물품(AVAILABLE만)
- 신뢰 점수·칭찬 항목별 횟수는 REQ-054 구현 시. 채팅 응답률·평균 응답 시간은 보조 지표(선택)
- 이메일 등 개인정보 비노출. 비회원 조회 가능
- 탈퇴 회원 → "탈퇴한 사용자", 상세 비공개 / 없음 404 / 차단 관계면 등록 물품 숨김(REQ-056 이후)

## REQ-036 비밀번호 재설정 [2]
- `POST /api/auth/password-reset {email}`: 가입 이메일이면 1회용 토큰 → Redis 30분 → 링크 메일 발송. **가입 여부와 무관하게 같은 응답**. 같은 이메일 5분에 1회(초과도 같은 응답, 메일만 안 보냄)
- `POST /api/auth/password-reset/confirm {token, newPassword}`: 검증 → BCrypt → 토큰 삭제 + 해당 회원 RT 삭제
- 예외: 토큰 만료·재사용 400 `AUTH_RESET_TOKEN_INVALID`, 소셜 전용 계정은 카카오 로그인 안내, 메일 실패 시 재시도 안내
- 메일 발송: Spring Mail(SMTP). 로컬은 MailHog/로그 출력으로 대체

## REQ-037 약관 동의 및 정책 안내 [2]
- 가입 시 필수: 이용약관, 개인정보 수집·이용, 만 14세 이상 → 하나라도 false면 400
- `terms_agreements`에 (회원, 종류, 버전, 시각) 저장
- `GET /api/terms` 비회원 조회 가능: 이용약관, 개인정보처리방침, 대여 금지 품목(`policies.md` §2)
- 약관 개정 시 다음 로그인에서 재동의 요구(로그인 응답에 `requiresTermsAgreement: true`), 거부 시 이용 제한 안내
- 약관 본문은 마크다운 파일 또는 DB. 버전 문자열로 관리

## REQ-041 소셜 로그인 [3]
카카오 OAuth. provider=KAKAO, password NULL. 같은 이메일 LOCAL 계정 존재 시 처리 방침은 착수 시 결정.

## REQ-042 동네 범위 설정 [3]
member_regions.region_range = NEAR(1.5km)/NORMAL(3km)/FAR(6km). 구현 전에는 NORMAL 고정.

## REQ-043 회원 탈퇴 [3]
진행 중 대여(ACTIVE_FOR_PERIOD + RETURNED, 대여자·제공자 양쪽) 없을 때만. status=WITHDRAWN, 개인정보 마스킹, 물품 DELETED, RT 삭제.

## 테스트 포인트
- 비밀번호 규칙 경계값(7/8/20/21자, 특수문자 없음)
- RT Rotation: 정상 재발급, 구 RT 재사용 시 전체 무효화
- 동네 3개째 추가 거부, 대표 동네 삭제 시 승계, 인증 만료(30일) 후 물품 등록 거부
