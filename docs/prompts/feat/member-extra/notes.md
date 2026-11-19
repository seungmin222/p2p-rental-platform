# feat/member-extra 결정 기록

REQ-021 사용자 프로필, REQ-037 약관 동의, REQ-036 비밀번호 재설정(백엔드). 프론트 SCR-13 등은 범위 밖.

## REQ-021 사용자 프로필
- **거래 횟수는 rental의 조회 클래스 `RentalCountReader.countCompleted`로 센다(포트 아님).** member가 rental을 부르지만 새 `MemberProfileQueryService`에서만 부르므로, rental이 의존하는 `MemberQueryService`와 빈 순환이 없다. 대여자·제공자 인덱스를 각각 타도록 OR 대신 COUNT 2개로 나눴다.
- **등록 물품 페이지 크기는 새 키 `profile-items.page` = 20건(고정)으로 두었다(사용자 결정).** 다른 목록 키와 같은 값. `PolicyProperties`에는 위치 기반 생성자를 쓰는 단위 테스트를 덜 건드리려고 맨 끝에 붙였다.
- **등록 물품 정렬은 피드와 같은 bumpedAt·id 내림차순.** 카드에 bumpedAt을 보여 주므로 화면 순서와 맞춘다(내 물품 관리는 id 내림차순이라 다르다).
- **등록 물품 API는 item 도메인 `MemberItemController`에 둔다.** 내 물품(`MyItemController`)과 같이 경로는 회원 아래지만 물품 목록이라서. Swagger 태그는 api.md 섹션(회원·인증)을 따라 Member.
- **탈퇴 회원: 프로필 200 + `isWithdrawn=true`, 나머지 null / 받은 후기·등록 물품 200 빈 목록(사용자 결정, open-issues #55에 기록).** 후기 작성자 표시(`isWithdrawn`)처럼 문구는 프론트가 정한다. 받은 후기 404 판정용 `MemberQueryService.exists`는 `isWithdrawn`(없으면 MEMBER_NOT_FOUND)으로 바꿨다.
- **차단(REQ-056)은 확장 지점만**: 서비스가 조회자 ID(nullable)를 받고 아직 쓰지 않는다. 컨트롤러는 `@LoginMember(required = false)`.
