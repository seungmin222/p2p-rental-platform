// 프로필 표시 문구(순수 함수).

export const WITHDRAWN_NICKNAME = '탈퇴한 사용자';

/** 가입 시각(오프셋 없는 서버 시각 'YYYY-MM-DDTHH:mm:ss') → "2026년 10월 가입". 형식이 다르면 null */
export function formatJoinedMonth(joinedAt) {
  const match = /^(\d{4})-(\d{2})/.exec(joinedAt ?? '');
  return match ? `${match[1]}년 ${Number(match[2])}월 가입` : null;
}

/** 프로필로 가는 경로. 탈퇴 회원·id 없음은 null(링크를 그리지 않는다 — 상세 비공개) */
export function getProfilePath(member) {
  if (!member || member.isWithdrawn || member.memberId == null) {
    return null;
  }
  return `/users/${member.memberId}`;
}
