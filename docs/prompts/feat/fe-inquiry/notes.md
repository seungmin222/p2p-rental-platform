# feat/fe-inquiry 결정 기록

## 요약
- 백엔드 변경 없음. 프론트 연동: `POST /api/inquiries`(일반 문의·분쟁 접수), `GET /api/inquiries`(내 문의). 정산 `heldTotal`·HELD 표시는 `feat/fe-payment-settlement` 것을 그대로 쓴다.
- 화면: SCR-15 `/me/inquiries`(`pages/MyInquiriesPage.jsx`)·`/me/inquiries/new?rentalId=&type=`(`pages/InquiryWritePage.jsx`), 진입은 `/me` "문의 내역"·SCR-08 맨 아래 링크.
- 새 파일: `features/inquiry/`(api·queries·constants·errorMessages·inquiryForm(+테스트)·components/InquiryForm·InquiryCard), 목 `mocks/inquiry.js`·`inquiryLogic.js`(+테스트)·`inquirySeed.js`. `rentalInspectionsQueryOptions` 분리 + AI 비교 5초 재조회 테스트(`features/rental/queries.test.js`).
- 열린 분쟁 표시는 상세 응답에 필드가 없어 409로만 안내한다(open-issues #60: `hasOpenDispute`·문의 응답 물품 요약 제안).
- 남은 일: 관리자 처리·답변·분쟁 종료(057), 처리 결과 알림(057). 브라우저 확인은 MSW 서비스 워커 등록 실패(앞 브랜치와 같음)로 못 했고, msw/node로 목 흐름(목록·409 두 종·403·400·분쟁 → 정산 HELD)만 확인했다.

## 결정
- 화면 위치는 SCR-15를 넓혀 `/me/inquiries` 아래에 둔다: admin.md "화면: SCR-15"이고, 로그인이 필요해 `/terms/*`(비회원 공개)와 갈라 `/me` 아래(RequireAuth)에 둔다. `/me`의 약관 링크 줄에 "문의 내역"을 붙였다(메뉴 목록이 아니라 고객센터 성격).
- SCR-08 진입은 상태별 버튼 표가 아니라 본문 맨 아래 텍스트 링크 하나, 대여자·제공자·모든 상태: 일반 문의는 결제 대기여도 대여를 연결할 수 있고, 분쟁 가능 여부는 작성 화면이 상세를 보고 정한다. 하단 바를 분쟁 버튼으로 어지럽히지 않으려고.
- 대여 상세에서 들어가도 분쟁을 미리 고르지 않는다(기본 일반 문의): 분쟁은 정산을 보류시키므로 사용자가 직접 고르게 한다.
- 열린 분쟁은 미리 막지 않고 409 `INQUIRY_DISPUTE_ALREADY_OPEN`을 폼 안 안내 + "내 문의 보기"로: 상세 응답에 필드가 없고, 내 문의 첫 페이지(20건)로 판단하면 놓칠 수 있다(open-issues #60).
- `INQUIRY_DISPUTE_NOT_ALLOWED`는 대여 상세를 무효화한다: 화면이 본 상태(결제 대기 아님)와 서버가 다르다는 뜻이라 다시 받으면 분쟁 칩이 막힌다.
- 대여 선택 UI는 만들지 않는다: `/me`에서 시작한 일반 문의는 대여 없이 보내고, 대여 연결은 SCR-08에서 들어올 때만. 3순위라 범위를 줄였다.
- 내용은 코드 포인트로 세고(백엔드 `codePointCount`) 원문 그대로 보낸다(백엔드도 trim하지 않음). 브라우저 maxLength는 쓰지 않는다(UTF-16 단위라 이모지에서 어긋남).
- 분쟁 접수 뒤 대여 상세는 무효화하지 않는다: 응답에 정산·분쟁 필드가 없어 바뀌는 것이 없다. 정산 내역만 무효화.
- 상태 라벨: RECEIVED "접수됨"(gray)·IN_PROGRESS "처리 중"(amber)·RESOLVED "처리 완료"(green), 분쟁 종류 배지는 red. 라벨은 UI용(state-machines §5에 한글 라벨이 없음).
- 목 정산 보류는 대여 기준(접수한 회원과 무관): state-machines §3처럼 대여자가 접수해도 제공자 정산이 보류된다. 기존 시드 33 HELD를 설명하려고 상대방(회원 3)의 분쟁을 시드에 넣었다.
- 재조회 테스트는 query-core `QueryObserver` + 가짜 타이머로 한다: node 환경에서 query-core는 window가 없으면 서버로 보고 refetchInterval을 걸지 않아 `vi.hoisted`로 빈 window를 둔다. 옵션은 `rentalInspectionsQueryOptions`로 분리해 훅과 테스트가 같은 값을 쓴다.
