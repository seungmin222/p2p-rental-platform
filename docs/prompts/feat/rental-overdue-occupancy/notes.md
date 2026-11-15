## 요약
- API 경로·필드 변경 없음. 종료일 지난 RENTING·OVERDUE의 점유 끝을 max(종료일, 오늘)로 늘려 예약(POST /api/rentals)·캘린더·기간 검색(GET /api/items)·연장에 적용 (open-issues #53).
- 판정 식: Rental.occupiedEndDate(today). 쿼리: RentalRepository.findOccupying, ItemListingRepositoryImpl.occupied(두 곳을 ItemSearchIntegrationTest로 묶음).
- 물품 가능 기간 수정 검사는 RentalOccupancyReader.findBookedDates로 원래 대여 날짜만 본다(범위 밖 원래 날짜는 거름).
- 남은 일: 이미 잡힌 다음 예약과 연체가 겹치는 경우는 open-issues #7 영역으로 다루지 않았다.

## 결정
- 점유 끝 = Rental.occupiedEndDate(today): RENTING·OVERDUE면 max(endDate, today), 그 외 endDate (open-issues #53 사용자 결정). 오늘을 포함하는 예약만 막고 내일 시작 예약은 받는다(사용자 결정 A①). 내일까지 반납이 안 되면 #7 영역이라 이번에 다루지 않았다.
- 배치(00:05) 전에 종료일이 지난 RENTING도 같은 식에 넣었다. validateExtendable이 이미 이 경우를 연체로 보고, 종료일 이전 RENTING은 max가 endDate라 영향이 없다.
- 오늘 = 호출하는 쪽이 Clock(Asia/Seoul)으로 구한 now의 날짜. JPQL에는 :today 파라미터로 넘기고 DB CURRENT_DATE는 쓰지 않았다(테스트 Clock과 맞추기 위해).
- 판정 위치: 식의 정의는 Rental.occupiedEndDate 하나, 쿼리 표현은 RentalRepository.findOccupying과 ItemListingRepositoryImpl.occupied 두 곳("endDate >= from OR (RENTING·OVERDUE AND today >= from)"). 검색 쪽은 today >= from일 때만 상태 조건을 붙인다. QueryDSL 공통 Predicate로 합치려면 findOccupying을 커스텀 구현으로 옮겨야 해서 하지 않고, ItemSearchIntegrationTest.periodOccupancyMatchesRentalOccupancyReader로 두 판정이 같은지 묶었다. 결제 대기 연장 쿼리(findPendingOccupying)는 바뀌지 않았다.
- 물품 수정 가능 기간 검사(REQ-007)는 원래 대여 날짜 [start, end]만 본다(사용자 결정 B). 늘어난 구간은 원래 가능 기간 밖일 수 있어, 넣으면 연체 한 건으로 물품 수정이 모두 409가 되기 때문이다. RentalOccupancyReader.findBookedDates를 따로 두었다.
- findBookedDates는 쿼리 결과를 원래 날짜 [startDate, endDate]가 [from, to]와 겹치는 것만 한 번 더 거른다(리뷰 반영). 쿼리는 늘어난 구간으로 고르므로, 좁은 범위로 불리면 범위 밖 연체 건이 섞일 수 있어서다.
- 캘린더 응답 필드는 그대로. 지난 날짜는 원래 UNAVAILABLE이라 늘어난 구간 중 보이는 날은 오늘뿐이다(가능 기간 안이면 BOOKED).
- 연장(REQ-038)은 같은 reader를 써서 자동 적용된다. 다만 연장 구간은 (내 종료일 + 1)부터이고 연장 가능한 대여는 종료일 ≥ 오늘이라, 다른 대여의 늘어난 구간(오늘까지)과는 구조상 겹칠 수 없다. 그래서 연장 전용 테스트는 더하지 않았다.
- 기간 없는 availableOnly의 renting() 제외는 코드 그대로 두었다. OVERDUE 부분은 새 판정과 중복이지만 같은 함수가 피드 "대여 중" 배지·내 물품 탭 조건이다. 주석과 item.md 문구만 정리했다.
- API 경로·필드·@Schema 설명이 바뀌지 않아 openapi.json은 재생성하지 않았다.

## 검증
- RentalOccupancyReaderIntegrationTest: 종료일 지난 RENTING·OVERDUE는 오늘까지 점유·내일 미점유, findBookedDates는 원래 날짜, 종료일 전 RENTING·종료일 지난 RESERVED는 그대로.
- RentalCreateIntegrationTest: 종료일 지난 OVERDUE·RENTING에 오늘 시작 409·내일 시작 201, RETURNED·COMPLETED 후 오늘 예약 201, 종료일 전 RENTING은 기존 판정.
- RentalCalendarIntegrationTest: 연체 물품 오늘 BOOKED, 내일 AVAILABLE.
- ItemSearchIntegrationTest: 상태 4종 × 대여 기간 3종 × 검색 기간 4종에서 검색 결과 = reader 판정.
- ItemEditDeleteIntegrationTest: 연체 중 원래 날짜를 덮으면 오늘을 빼도 200, 원래 날짜를 빼면 409.
- OverdueReturnIntegrationTest: 연체 대여를 실제 반납 API로 RETURNED로 바꾸면 오늘 포함 예약이 409 → 201(리뷰 반영, 상태를 직접 넣는 returnedReleasesToday를 실제 전이로 보강).
- RentalOccupancyReaderIntegrationTest.bookedDatesExcludeOriginalDatesOutsideRange: 늘어난 구간만 걸치는 범위로 부르면 findBookedDates는 빈 목록(리뷰 반영).
- 기존 동시성 테스트(RentalConcurrencyTest, RentalExtensionConcurrencyTest, ItemEditDeleteConcurrencyTest)와 연장 통합 테스트 통과.
