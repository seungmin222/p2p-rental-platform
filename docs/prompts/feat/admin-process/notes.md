# feat/admin-process 결정 기록 (REQ-057 문의 처리·분쟁 종료)

## 요약
- API: `GET /api/admin/inquiries?status=&type=&page=`(관리자 문의 목록), `PATCH /api/admin/inquiries/{id}`(처리·답변). 내 문의 목록 응답에 `answer`·`answeredAt` 추가. Flyway `V23__add_answer_to_inquiries.sql`.
- 분쟁을 RESOLVED로 바꾸면 대여 행 락 안에서 남은 열린 분쟁이 없을 때 `SettlementTxService.releaseForDispute`로 정산 HELD → SCHEDULED.
- 주요 파일: `admin/controller/AdminInquiryController`, `admin/service/InquiryAdminService`, `admin/repository/InquiryAdminSearchRepositoryImpl`·`InquiryRef`, `Inquiry.process`, 테스트 `AdminInquiryIntegrationTest`·`InquiryTest`.
- 남은 일: 관리자 화면(SCR-15·16)과 프론트 답변 표시, 처리와 분쟁 접수·지급 배치가 겹치는 동시성 테스트(성능 실험 때), 057의 나머지(회원·물품·신고·카테고리·증빙 조회).

## API·스키마 (2026-11-28 사용자 승인한 설계안)
- **D-admin-process-01** [사용자] 관리자 문의 API는 `GET /api/admin/inquiries`(status·type 조건, `inquiry.page` 20건 고정)와 `PATCH /api/admin/inquiries/{id}`(status·answer)로 하고, api.md의 `/api/admin/disputes`를 `/inquiries`로 바꾼다. 관리자가 다루는 대상이 분쟁만이 아니라 일반 문의도 포함해서다.
- **D-admin-process-02** [사용자] 전이는 RECEIVED → IN_PROGRESS, RECEIVED·IN_PROGRESS → RESOLVED만 허용하고 RESOLVED는 되돌리지 않는다. RESOLVED에는 답변이 필요하다. 끝난 분쟁을 다시 열면 정산 보류가 다시 걸려야 하는데, 그건 재접수(REQ-063)로 이미 된다.
- **D-admin-process-03** [사용자] `inquiries`에 `answer` VARCHAR(1000) NULL·`answered_at`을 추가(V23)하고 `inquiry.answer-max` 1,000자를 새로 둔다. 내 문의 목록 응답에도 답변을 넣는다. 사용자가 처리 결과를 확인할 곳이 그 목록뿐이어서다.
- **D-admin-process-04** [사용자] 분쟁을 RESOLVED로 바꿀 때, 그 대여에 RESOLVED가 아닌 분쟁(상대방 것 포함)이 없으면 정산 HELD → SCHEDULED로 푼다. open-issues #9·state-machines §3의 종료 조건 그대로다.
- **D-admin-process-05** [사용자] 처리 결과 알림은 보내지 않는다. NotificationType 추가는 프론트 알림 화면까지 바꿔야 해서 뒤로 미룬다.

## 처리 세부
- **D-admin-process-06** [에이전트] 분쟁 처리는 접수와 같은 순서로 락을 잡는다: 종류·대여를 projection(`findRefById`)으로 읽고 → 대여 행 락 → 문의 행 FOR UPDATE. 상대방 분쟁 접수·대여 완료(정산 생성)와 한 줄로 서서, 남은 분쟁 확인과 보류 해제 사이에 새 분쟁이 끼지 않는다. 엔티티를 먼저 일반 읽기로 올리면 락 쿼리가 옛 상태를 돌려줘서 projection을 쓴다(063과 같은 까닭).
  - 남은 분쟁 확인은 바뀐 상태를 flush한 뒤 기존 잠금 읽기(`findOpenDisputeIdsForShare`)로 한다. 정산 id 조회·행 락은 `holdForDispute`와 같다.
- **D-admin-process-07** [에이전트] 답변을 주면 덮어쓰고 `answeredAt`을 그때로 바꾼다. 처리 중에 남긴 답변이 있으면 RESOLVED 때 답변을 생략할 수 있다. 처리 중 안내를 남긴 뒤 같은 글로 끝내는 경우를 막지 않으려고서다. 답변은 공백만 불가(400 details answer).
- **D-admin-process-08** [에이전트] 같은 상태로의 변경(IN_PROGRESS → IN_PROGRESS)도 409 `INQUIRY_INVALID_TRANSITION`이다. 다른 상태 머신(대여)과 같이 전이만 받는다. 그래서 처리 중 답변 수정은 RESOLVED로 바꿀 때 한 번 더 줄 수 있을 뿐이다.
- **D-admin-process-09** [에이전트] 새 에러 코드는 404 `INQUIRY_NOT_FOUND`, 409 `INQUIRY_INVALID_TRANSITION` 두 개다. 답변 누락·형식은 기존 400 `COMMON_INVALID_INPUT`(details answer)으로 한다.
- **D-admin-process-10** [에이전트] 관리자 목록은 접수 순(오래된 것 먼저, id 오름차순)이고 QueryDSL(`InquiryAdminSearchRepository`)로 조건을 조합한다. 처리 대기 건을 먼저 보게 하려고서다. 관리자 화면 하나가 쓰는 조회라 전용 인덱스는 두지 않았다.
- **D-admin-process-11** [에이전트] 처리한 관리자 id는 컬럼에 남기지 않고 로그(`adminId`)로만 남긴다. 승인된 스키마 범위(answer·answered_at) 밖이라서다. 감사 기록이 필요하면 057 증빙 조회 때 정한다.
