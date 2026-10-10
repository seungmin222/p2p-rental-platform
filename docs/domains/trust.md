# 후기·신뢰 점수·신고·차단·페널티 (trust)

담당: 신수정(B) · 화면: SCR-13, 14 · 엔티티: reviews, review_compliments, reports, blocks, trust_score_events(제안)

## REQ-034 거래 후기 [2 필수]
`POST /api/rentals/{rentalId}/reviews { satisfaction, compliments[], content? }`
- satisfaction: `BAD`/`GOOD`/`BEST` 필수, compliments 다중 선택, content ≤300자
- 가드: rental COMPLETED, 작성자가 당사자(대여자→제공자, 제공자→대여자), `completed_at` + 7일 이내, (rental, writer) 1회
- 예외: 당사자 아님 403, 이미 작성 409 `REVIEW_ALREADY_WRITTEN`, 기한 경과 400 `REVIEW_DEADLINE_PASSED`
- 상대에게 `REVIEW_RECEIVED` 알림. 상대 프로필 후기 목록에 노출
- 칭찬 항목 초기값(`ComplimentType`): 시간 약속을 잘 지켜요(ON_TIME), 물품 상태가 설명과 같아요(AS_DESCRIBED), 깨끗하게 사용했어요(CLEAN_USE). 대여자용/제공자용 구분이 필요하면 착수 시 결정

## REQ-054 신뢰 점수 [3] (후기와 함께 설계)
- 초기값·범위·가감 규칙은 `policies.md` `trust.*`가 기준이다. 소수 0.5 단위 → `DECIMAL(4,1)`
- 연체(1일당), 노쇼, 신고 확정, 제공자 사유 취소(`policies.md` §2 취소 수수료 표)를 반영한다
- 변화마다 `trust_score_events`에 기록 → 점수 = 초기값 + Σdelta (범위 클램프). 재계산·감사 가능하게
- 2순위 단계에서는 후기 데이터만 쌓고 점수 표시는 054 착수 시

## REQ-055 신고 [3]
물품·사용자·채팅 대상, 사유 선택. 관리자 처리(기각/경고/이용 정지), 확정 시 신뢰 점수 반영.

## REQ-056 차단 [3]
차단한 사용자의 물품(피드·검색·추천·프로필)과 채팅 숨김, 채팅방 생성·전송 불가. 피드·검색 쿼리에 `owner_id NOT IN (차단 목록)` 조건이 들어갈 자리를 미리 고려해 둔다.

## REQ-062 노쇼·취소 페널티 [3]
- 대여자 사유 취소, 노쇼(약속 시각+30분, 신고 + 채팅·약속 기록 확인)를 기록
- 최근 30일 대여자 사유 취소 3회 또는 노쇼 2회 이상 → 14일 예약 제한(예약 API에서 403)
- "대여자 사유 취소" = `cancel_type = RENTER`인 결제 후 취소(결제 대기 중 취소는 제외). 제공자 사유 취소(`OWNER`)는 제공자 신뢰 점수에만 반영

## REQ-066 금지어·부적절 콘텐츠 필터 [3]
물품 제목·설명, 채팅 메시지에 금지어 필터. 대여 금지 품목(`policies.md` §2) 등록 차단.
