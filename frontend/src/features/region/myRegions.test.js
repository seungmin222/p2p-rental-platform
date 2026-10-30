import { describe, expect, it } from 'vitest';

import {
  getDeleteNotice,
  getVerifyActionLabel,
  isRegionAdded,
  isRegionLimitReached,
  replaceMemberRegion,
} from './myRegions';

const PRIMARY = { memberRegionId: 1, regionId: 1101, isPrimary: true, verifiedAt: null };
const SECOND = {
  memberRegionId: 2,
  regionId: 1103,
  isPrimary: false,
  verifiedAt: '2026-09-20T10:00:00',
};

describe('replaceMemberRegion', () => {
  it('같은 memberRegionId 항목만 바꾸고 순서는 그대로', () => {
    const updated = { ...SECOND, verifiedAt: '2026-10-30T10:00:00' };
    expect(replaceMemberRegion([PRIMARY, SECOND], updated)).toEqual([PRIMARY, updated]);
  });

  it('목록이 없거나 그 동네가 없으면 null', () => {
    expect(replaceMemberRegion(undefined, SECOND)).toBeNull();
    expect(replaceMemberRegion([PRIMARY], SECOND)).toBeNull();
  });
});

describe('isRegionAdded·isRegionLimitReached', () => {
  it('regionId로 추가 여부를 본다', () => {
    expect(isRegionAdded([PRIMARY], 1101)).toBe(true);
    expect(isRegionAdded([PRIMARY], 1103)).toBe(false);
    expect(isRegionAdded(undefined, 1101)).toBe(false);
  });

  it('region.max-per-member(2)개면 더 추가할 수 없다', () => {
    expect(isRegionLimitReached([PRIMARY])).toBe(false);
    expect(isRegionLimitReached([PRIMARY, SECOND])).toBe(true);
    expect(isRegionLimitReached([PRIMARY, SECOND], 3)).toBe(false);
  });
});

describe('getVerifyActionLabel', () => {
  it('인증한 적 없으면 인증하기, 있으면 다시 인증', () => {
    expect(getVerifyActionLabel(PRIMARY)).toBe('인증하기');
    expect(getVerifyActionLabel(SECOND)).toBe('다시 인증');
  });
});

describe('getDeleteNotice', () => {
  it('대표를 지우고 남은 동네가 있을 때만 승계 안내', () => {
    expect(getDeleteNotice([PRIMARY, SECOND], PRIMARY)).toContain('남은 동네가 대표 동네가 돼요');
    expect(getDeleteNotice([PRIMARY, SECOND], SECOND)).not.toContain('대표');
    expect(getDeleteNotice([PRIMARY], PRIMARY)).not.toContain('대표');
  });
});
