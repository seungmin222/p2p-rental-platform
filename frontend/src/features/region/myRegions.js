// 내 동네 목록(MemberRegionResponse[]) 계산. 판정은 서버 값(isPrimary·isVerified·verifiedAt)을 그대로 쓴다.
import { REGION_MAX_PER_MEMBER } from './constants';

/** 인증 응답 하나를 목록에 반영한 새 배열. 목록에 그 동네가 없으면 null(호출부가 다시 불러온다) */
export function replaceMemberRegion(list, updated) {
  if (!Array.isArray(list) || !list.some((r) => r.memberRegionId === updated.memberRegionId)) {
    return null;
  }
  return list.map((r) => (r.memberRegionId === updated.memberRegionId ? updated : r));
}

/** 이미 추가한 행정동인지(regionId로 비교) */
export function isRegionAdded(list, regionId) {
  return (list ?? []).some((r) => r.regionId === regionId);
}

/** 더 추가할 수 없는지(policies.md region.max-per-member) */
export function isRegionLimitReached(list, max = REGION_MAX_PER_MEMBER) {
  return (list ?? []).length >= max;
}

/** 인증 버튼 라벨: 인증한 적 없으면 "인증하기", 있으면(유효·만료 모두) "다시 인증" */
export function getVerifyActionLabel(region) {
  return region.verifiedAt ? '다시 인증' : '인증하기';
}

/** 삭제 확인 안내. 대표 동네를 지우고 남은 동네가 있으면 승계 안내를 덧붙인다 */
export function getDeleteNotice(list, target) {
  const base = '삭제하면 이 동네의 인증 기록도 사라져요.';
  return target.isPrimary && list.length > 1 ? `${base} 남은 동네가 대표 동네가 돼요.` : base;
}
