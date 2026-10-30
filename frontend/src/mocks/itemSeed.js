// 피드·검색 확인용 물품 시드(itemId 201~). 기존 물품(101~108, item.js)과 함께 동네별로 나뉜다.
// 동네는 행정동 시드(memberSeed.js MOCK_REGION_MASTER)의 regionId를 쓴다.
//
// | 동네 | regionId | 물품 | 쓰임 |
// | --- | --- | --- | --- |
// | 송도1동 | 1101 | 30개(101의 103·104·107·108 + 26개) | 피드 2페이지(20 + 10), 같은 시각 등록이 여럿인 커서 경계, 무료(0원)·보증금 없음, 대여 중 배지, 검색 정렬·필터 |
// | 송도2동 | 1102 | 7개 | 1페이지만(마지막 페이지에서 더 부르지 않음) |
// | 연수1동 | 1103 | 12개(101·102·105 + 9개) | 카테고리 칩 필터(캠핑·촬영 등) |
// | 동춘1동 | 1104 | 5개(삭제된 106은 제외) | |
// | 옥련1동 | 1105 | 3개 | |
// | 청학동 | 1106 | 0개 | 빈 피드·검색 결과 없음 |
//
// 등록 시각은 지금 기준 분 단위로 벌려 "n분 전·n시간 전·n일 전"이 섞여 보이게 한다(새로고침하면 시드 기준 시각이 다시 잡힌다).

const MINUTE_MS = 60 * 1000;
const SEOUL_OFFSET_MS = 9 * 60 * MINUTE_MS;

function serverTimeMinutesAgo(minutes, now) {
  return new Date(now - minutes * MINUTE_MS + SEOUL_OFFSET_MS).toISOString().slice(0, 19);
}

// [이름, categoryId(V3 순서), 일 대여료]
const POOL = [
  ['원터치 텐트', 1, 12000],
  ['캠핑 테이블', 1, 5000],
  ['화로대', 1, 7000],
  ['침낭 2개', 1, 6000],
  ['캠핑 의자 4개', 1, 8000],
  ['전동 드라이버', 2, 4000],
  ['사다리 3단', 2, 3000],
  ['공구 세트', 2, 5000],
  ['미러리스 카메라', 3, 30000],
  ['삼각대', 3, 5000],
  ['짐벌', 3, 15000],
  ['액션캠', 3, 10000],
  ['유모차', 4, 8000],
  ['아기 욕조', 4, 2000],
  ['카시트', 4, 6000],
  ['파티 조명', 5, 10000],
  ['빔 스크린', 5, 9000],
  ['테이블 천막', 5, 12000],
  ['스키 장비 세트', 6, 20000],
  ['서핑보드', 6, 18000],
  ['공기청정기', 7, 7000],
  ['스팀 청소기', 7, 6000],
  ['제습기', 7, 8000],
  ['캐리어 28인치', 8, 4000],
  ['접이식 카트', 8, 3000],
  ['보드게임 모음', 6, 3000],
  ['무료 나눔 동화책 세트', 8, 0],
];

const CONDITIONS = ['LIKE_NEW', 'LIGHTLY_USED', 'USED'];
// 이 시드의 제공자: 캠핑조아(2)·텐트초보(3)·이웃(4). 내 물품(회원 1)은 기존 시드(103·104·107·108)만 쓴다
const OWNERS = [2, 3, 4];
const COLORS = ['#E8F1EC', '#E6EDF8', '#FBF1E2', '#F6F5F1', '#FBEAE5'];

/** 동네별 개수(위 표). 송도1동 26개는 기존 4개와 합쳐 30개 → 20 + 10 */
const REGION_COUNTS = [
  { regionId: 1101, count: 26 },
  { regionId: 1102, count: 7 },
  { regionId: 1103, count: 9 },
  { regionId: 1104, count: 5 },
  { regionId: 1105, count: 3 },
];

/** 대여 중 배지를 보여 줄 물품(대여 목 시드와 무관한 표시용). 대여 목의 isRenting은 대여 DB로 계산하고 이것과 합친다 */
const RENTING_ITEM_IDS = [203, 211];

/**
 * 시드 생성. now는 테스트에서 바꿔 넣는다.
 * 5개마다 직전 물품과 같은 시각에 등록해(커서 경계) id 순서로만 구분되게 한다.
 */
export function createFeedSeed(now = Date.now()) {
  const items = [];
  let itemId = 200;
  let minutesAgo = 4;
  REGION_COUNTS.forEach(({ regionId, count }, regionIndex) => {
    for (let index = 0; index < count; index += 1) {
      itemId += 1;
      const [name, categoryId, pricePerDay] = POOL[(index + regionIndex * 7) % POOL.length];
      if (index % 5 !== 4) {
        minutesAgo += 37 + ((itemId * 13) % 90);
      }
      items.push({
        itemId,
        name,
        ownerId: OWNERS[itemId % OWNERS.length],
        regionId,
        pricePerDay,
        deposit: pricePerDay === 0 || index % 6 === 5 ? 0 : pricePerDay * 5,
        availablePeriods: [[0, 60]],
        color: COLORS[itemId % COLORS.length],
        categoryId,
        condition: CONDITIONS[itemId % CONDITIONS.length],
        description: `${name} 대여해요. 깨끗하게 관리했어요.`,
        bumpedAt: serverTimeMinutesAgo(minutesAgo, now),
        isRentingMock: RENTING_ITEM_IDS.includes(itemId),
      });
    }
  });
  return items;
}
