// 후기(REQ-034) 표시 라벨과 정책 수치. Enum 코드는 docs/openapi.json ReviewCreateRequest 그대로.

/** 만족도(필수, 하나). 라벨은 openapi.json 설명 그대로. 선언 순서 = 화면 순서 */
export const SATISFACTION = {
  BAD: { label: '별로예요' },
  GOOD: { label: '좋아요' },
  BEST: { label: '최고예요' },
};

/** 칭찬 항목(선택, 여러 개). 라벨은 trust.md REQ-034 그대로. 대여자·제공자 공통 목록(open-issues #15) */
export const COMPLIMENT = {
  ON_TIME: { label: '시간 약속을 잘 지켜요' },
  AS_DESCRIBED: { label: '물품 상태가 설명과 같아요' },
  CLEAN_USE: { label: '깨끗하게 사용했어요' },
};

/** policies.md review.content.max — 코드 포인트로 센다(백엔드 codePointCount) */
export const REVIEW_CONTENT_MAX = 300;

/** policies.md review.write-deadline-days — 안내 문구용. 작성 가능 여부는 서버 canWriteReview로 정한다 */
export const REVIEW_WRITE_DEADLINE_DAYS = 7;
