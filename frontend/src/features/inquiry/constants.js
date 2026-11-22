// 문의·분쟁(REQ-063) 표시 라벨과 정책 수치. Enum 코드는 docs/state-machines.md §5·docs/openapi.json InquiryResponse 그대로.

/** 문의 종류. 선언 순서 = 작성 화면 칩 순서 */
export const INQUIRY_TYPE = {
  GENERAL: { label: '일반 문의', tone: 'gray' },
  DISPUTE: { label: '분쟁', tone: 'red' },
};

/** 처리 상태(state-machines §5). 바꾸는 쪽은 관리자(REQ-057) */
export const INQUIRY_STATUS = {
  RECEIVED: { label: '접수됨', tone: 'gray' },
  IN_PROGRESS: { label: '처리 중', tone: 'amber' },
  RESOLVED: { label: '처리 완료', tone: 'green' },
};

/** policies.md inquiry.content-max — 코드 포인트로 센다(백엔드 InquiryService codePointCount) */
export const INQUIRY_CONTENT_MAX = 1000;
