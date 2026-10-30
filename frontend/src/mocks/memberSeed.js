// 회원·동네 목의 시드와 메모리 저장소. 인증·회원 목(auth.js·member.js)이 함께 쓰고, 동네 API 목(feat/fe-region)도 이 db를 쓴다.
// 비밀번호는 개발용 가짜 값이다(frontend/CLAUDE.md "개발 방식"에 목 로그인 계정). 새로고침하면 시드로 돌아간다.
//
// | memberId | 이메일 | 닉네임 | 내 동네 | 쓰임 |
// | --- | --- | --- | --- | --- |
// | 1 | user1@example.com | 동네주민 | 송도1동(대표, 인증 D-5 → 유효) + 연수1동(인증 D-40 → 만료) | 대여·물품 목의 "나"(MOCK_ME). 개수 최대·만료 표시 |
// | 2 | user2@example.com | 캠핑조아 | 연수1동(대표, 인증 유효) | 닉네임 중복 확인 |
// | 3 | user3@example.com | 텐트초보 | 없음 | 로그인 후 /regions 이동·첫 동네 추가 |
// | 4 | user4@example.com | 이웃 | 동춘1동(대표, 미인증) | 미인증 표시 |
//
// 비밀번호는 모두 rental123!. 닉네임은 기존 대여·물품 목(item.js MOCK_MEMBERS)과 같다. 동네 이름은 행정동(송도1동 등)이라
// 물품 목의 동네명(송도동 등, 물품·제공자 표시용)과 다를 수 있다(물품 목은 바꾸지 않았다).
import { addDays, todayInSeoul } from '../shared/lib/date';

/** 행정동 마스터(regions). 중심 좌표는 대략값(좌표→동네 찾기 목이 쓴다, feat/fe-region) */
export const MOCK_REGION_MASTER = [
  {
    regionId: 1101,
    name: '송도1동',
    fullName: '인천광역시 연수구 송도1동',
    lat: 37.3925,
    lng: 126.639,
  },
  {
    regionId: 1102,
    name: '송도2동',
    fullName: '인천광역시 연수구 송도2동',
    lat: 37.38,
    lng: 126.657,
  },
  {
    regionId: 1103,
    name: '연수1동',
    fullName: '인천광역시 연수구 연수1동',
    lat: 37.418,
    lng: 126.679,
  },
  {
    regionId: 1104,
    name: '동춘1동',
    fullName: '인천광역시 연수구 동춘1동',
    lat: 37.405,
    lng: 126.67,
  },
  {
    regionId: 1105,
    name: '옥련1동',
    fullName: '인천광역시 연수구 옥련1동',
    lat: 37.423,
    lng: 126.647,
  },
  {
    regionId: 1106,
    name: '청학동',
    fullName: '인천광역시 연수구 청학동',
    lat: 37.428,
    lng: 126.664,
  },
];

const SEED_PASSWORD = 'rental123!';

/** 오늘 기준 days일 전 10:00(오프셋 없는 서울 시각, 다른 목 시각과 같은 형식) */
function daysAgoAt10(days) {
  return `${addDays(todayInSeoul(), -days)}T10:00:00`;
}

/** 새 저장소. 테스트는 이 함수로 매번 새로 만든다 */
export function createMemberDb() {
  return {
    members: [
      { memberId: 1, email: 'user1@example.com', nickname: '동네주민', joinedDaysAgo: 120 },
      { memberId: 2, email: 'user2@example.com', nickname: '캠핑조아', joinedDaysAgo: 200 },
      { memberId: 3, email: 'user3@example.com', nickname: '텐트초보', joinedDaysAgo: 30 },
      { memberId: 4, email: 'user4@example.com', nickname: '이웃', joinedDaysAgo: 60 },
    ].map(({ joinedDaysAgo, ...member }) => ({
      ...member,
      password: SEED_PASSWORD,
      profileImageUrl: null,
      role: 'USER',
      createdAt: daysAgoAt10(joinedDaysAgo),
    })),
    // member_regions. verifiedAt null이면 인증한 적 없음(만료 시각은 verifiedAt + region.verify-valid-days로 계산)
    memberRegions: [
      {
        memberRegionId: 1,
        memberId: 1,
        regionId: 1101,
        isPrimary: true,
        verifiedAt: daysAgoAt10(5),
      },
      {
        memberRegionId: 2,
        memberId: 1,
        regionId: 1103,
        isPrimary: false,
        verifiedAt: daysAgoAt10(40),
      },
      {
        memberRegionId: 3,
        memberId: 2,
        regionId: 1103,
        isPrimary: true,
        verifiedAt: daysAgoAt10(3),
      },
      { memberRegionId: 4, memberId: 4, regionId: 1104, isPrimary: true, verifiedAt: null },
    ],
    // 가입하면 이 번호부터(시드와 겹치지 않게)
    nextMemberId: 100,
    nextMemberRegionId: 100,
  };
}

/** 브라우저 목이 쓰는 저장소 */
export const memberDb = createMemberDb();
