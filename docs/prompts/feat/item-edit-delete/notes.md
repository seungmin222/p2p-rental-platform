## 결정
- 수정 가능 범위: 가격·보증금·거래 장소는 진행 중·예정 대여가 있어도 바꿀 수 있다. 가격·보증금은 대여 건에 스냅샷이 있고, 대여·채팅 응답은 물품 주소를 쓰지 않기 때문이다. 막는 것은 점유 날짜를 빼는 가능 기간 수정뿐이다.
- 기간 충돌 판정: 점유 기간(RentalOccupancyReader, 만료 전 결제 대기·결제 대기 연장 포함)의 날짜를 새 가능 기간들의 합집합이 모두 덮어야 한다. 이미 시작된 대여(과거 날짜)도 검사한다. 수정 DTO가 시작일 ≥ 오늘을 검사하지 않는 것과 맞춘다.
- 수정은 기간을 보내지 않아도 물품 락을 잡는다. 같은 물품의 수정을 직렬화하고, 이미지 순서·삭제가 섞이지 않게 하기 위해서다.
- 404·403·이미지 검사는 락 밖에서 먼저 하고 락 안 트랜잭션(ItemUpdateWriter·ItemDeleteWriter)에서 다시 한다. 락을 기다리는 동안 삭제·다른 수정이 커밋됐을 수 있다.
- 새 이미지는 락 전에 올리고, 실패하면 등록과 같은 방식으로 지운다. 빠진 기존 이미지 파일은 락 블록이 끝난 뒤(커밋 뒤) 지우고, 실패하면 로그만 남긴다(DB는 이미 정상).
- 삭제(REQ-008)는 이미지 파일을 지우지 않는다. 지난 대여·채팅이 삭제된 물품의 대표 이미지를 계속 보여 준다.
- 삭제를 막는 상태는 RentalStatus.BLOCKING_ITEM_DELETION으로 둔다. 결제 대기는 명세 문구대로 만료 여부와 상관없이 막는다(만료 배치가 CANCELED로 바꾸면 풀린다). details reason의 상태 이름은 RentalStatus.displayName()(state-machines.md 한글명, RETURNED는 '상태 확인 대기').
- 예약 생성(RentalReservationTxService.create)이 락 안에서 물품 공개 여부(ITEM_NOT_FOUND)와 가능 기간(RENTAL_OUT_OF_AVAILABLE_PERIOD)을 다시 본다. 전에는 락 밖에서만 봐서, 락 안에서 삭제·기간 수정을 해도 그사이 대기 중이던 예약이 삭제된 물품이나 빠진 날짜에 저장될 수 있었다(사용자 승인, API 변경 없음).

## 검증
- ItemEditDeleteIntegrationTest(19개): 수정 화면, 403·404, 부분 수정, 이미지 교체·파일 삭제, imageOrder 400 4종·IMAGE_COUNT_INVALID, 기간 409(새 파일 정리 포함)·이어진 기간 허용·만료 결제 대기 무시, 삭제 성공과 삭제 뒤 노출, 상태별 삭제 거부.
- ItemEditDeleteConcurrencyTest(2개): 삭제·기간 축소와 예약 동시 요청을 10회씩 반복해 한쪽 결과만 남는지 확인.
- RentalReservationTxServiceTest에 락 안 재검사 2개 추가.
