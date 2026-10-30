import { formatMonthDay, todayInSeoul } from '../../shared/lib/date';
import { parseServerDateTime } from '../../shared/lib/format';

/**
 * 동네 인증 상태 표시(REQ-004). MemberRegionResponse·MemberResponse.primaryRegion 둘 다 받는다.
 * 판정은 서버의 isVerified를 그대로 따른다(프론트에서 날짜로 다시 판정하지 않는다). 날짜는 표시에만 쓴다.
 * tone은 shared/ui Badge의 tone.
 * - isVerified true → "인증 완료 · M월 D일까지"(verifyExpiresAt, 서울 날짜. 해석할 수 없으면 "인증 완료")
 * - false이고 verifiedAt 있음 → "인증 만료"
 * - verifiedAt null → "미인증"
 */
export function getVerifyStatus(region) {
  if (region?.isVerified === true) {
    const expiresAt = parseServerDateTime(region.verifyExpiresAt);
    return {
      code: 'VERIFIED',
      label: expiresAt ? `인증 완료 · ${formatMonthDay(todayInSeoul(expiresAt))}까지` : '인증 완료',
      tone: 'green',
    };
  }
  if (region?.verifiedAt) {
    return { code: 'EXPIRED', label: '인증 만료', tone: 'amber' };
  }
  return { code: 'UNVERIFIED', label: '미인증', tone: 'gray' };
}
