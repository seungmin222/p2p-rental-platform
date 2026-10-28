import { describe, expect, it } from 'vitest';

import {
  addImages,
  createLocalId,
  describeRejections,
  IMAGE_REJECT_REASON,
  isLocalImage,
  makeCover,
  moveImage,
  removeImage,
} from './imageList';

const MB = 1024 * 1024;
const OPTIONS = { maxCount: 3, maxFileSize: 10 * MB, accept: ['image/jpeg', 'image/png'] };

// node 환경이라 File 대신 같은 필드를 가진 객체를 쓴다
const file = (name, { type = 'image/jpeg', size = MB } = {}) => ({ name, type, size });
const ids = (images) => images.map((image) => image.id);

// 기존(서버) 이미지와 새 이미지가 섞인 목록
const mixed = [
  { id: 101, url: '/files/a.jpg' },
  { id: 'local-a', file: file('b.jpg') },
  { id: 102, url: '/files/c.jpg' },
];

describe('createLocalId', () => {
  it('접두사가 붙고 호출마다 다르다', () => {
    const a = createLocalId();
    const b = createLocalId();
    expect(a).toMatch(/^local-\d+$/);
    expect(a).not.toBe(b);
  });
});

describe('addImages', () => {
  it('정확히 max까지 넣는다', () => {
    const { images, rejected } = addImages([], [file('1'), file('2'), file('3')], OPTIONS);
    expect(images).toHaveLength(3);
    expect(rejected).toEqual([]);
    expect(images.every(isLocalImage)).toBe(true);
    expect(new Set(ids(images)).size).toBe(3);
  });

  it('max+1번째부터 TOO_MANY_FILES, 통과한 것은 넣는다', () => {
    const extra = file('4');
    const { images, rejected } = addImages([], [file('1'), file('2'), file('3'), extra], OPTIONS);
    expect(images).toHaveLength(3);
    expect(rejected).toEqual([{ file: extra, reason: IMAGE_REJECT_REASON.TOO_MANY_FILES }]);
  });

  it('기존 이미지를 포함해 남은 장수만큼만', () => {
    const { images, rejected } = addImages(mixed, [file('x')], OPTIONS);
    expect(images).toBe(mixed);
    expect(rejected.map((r) => r.reason)).toEqual([IMAGE_REJECT_REASON.TOO_MANY_FILES]);
  });

  it('용량은 정확히 max까지 허용', () => {
    const exact = file('exact', { size: 10 * MB });
    const over = file('over', { size: 10 * MB + 1 });
    const { images, rejected } = addImages([], [exact, over], OPTIONS);
    expect(images.map((image) => image.file)).toEqual([exact]);
    expect(rejected).toEqual([{ file: over, reason: IMAGE_REJECT_REASON.FILE_TOO_LARGE }]);
  });

  it('허용하지 않은 형식(HEIC, 형식 없음)은 거부', () => {
    const heic = file('a.heic', { type: 'image/heic' });
    const unknown = file('a', { type: '' });
    const png = file('b.png', { type: 'image/png' });
    const { images, rejected } = addImages([], [heic, unknown, png], OPTIONS);
    expect(images.map((image) => image.file)).toEqual([png]);
    expect(rejected.map((r) => r.reason)).toEqual([
      IMAGE_REJECT_REASON.UNSUPPORTED_TYPE,
      IMAGE_REJECT_REASON.UNSUPPORTED_TYPE,
    ]);
  });

  it('거부된 파일은 남은 장수를 쓰지 않는다', () => {
    const big = file('big', { size: 11 * MB });
    const { images, rejected } = addImages(
      [{ id: 1, url: '/a' }],
      [big, file('1'), file('2')],
      OPTIONS,
    );
    expect(images).toHaveLength(3);
    expect(rejected.map((r) => r.reason)).toEqual([IMAGE_REJECT_REASON.FILE_TOO_LARGE]);
  });
});

describe('removeImage', () => {
  it('id로 지운다(기존·새 이미지 모두)', () => {
    expect(ids(removeImage(mixed, 'local-a'))).toEqual([101, 102]);
    expect(ids(removeImage(mixed, 101))).toEqual(['local-a', 102]);
  });
});

describe('moveImage', () => {
  it('앞으로·뒤로', () => {
    expect(ids(moveImage(mixed, 'local-a', -1))).toEqual(['local-a', 101, 102]);
    expect(ids(moveImage(mixed, 'local-a', 1))).toEqual([101, 102, 'local-a']);
  });

  it('처음에서 앞으로, 끝에서 뒤로는 그대로', () => {
    expect(moveImage(mixed, 101, -1)).toBe(mixed);
    expect(moveImage(mixed, 102, 1)).toBe(mixed);
    expect(moveImage(mixed, 'none', 1)).toBe(mixed);
  });
});

describe('makeCover', () => {
  it('첫 장으로 옮기고 나머지 순서는 유지', () => {
    expect(ids(makeCover(mixed, 102))).toEqual([102, 101, 'local-a']);
  });

  it('이미 대표면 그대로', () => {
    expect(makeCover(mixed, 101)).toBe(mixed);
  });
});

describe('describeRejections', () => {
  it('사유별로 한 줄', () => {
    const rejected = [
      { reason: IMAGE_REJECT_REASON.UNSUPPORTED_TYPE },
      { reason: IMAGE_REJECT_REASON.TOO_MANY_FILES },
      { reason: IMAGE_REJECT_REASON.UNSUPPORTED_TYPE },
      { reason: IMAGE_REJECT_REASON.FILE_TOO_LARGE },
    ];
    expect(describeRejections(rejected, OPTIONS)).toEqual([
      'JPG, PNG 사진만 올릴 수 있어요(2장 제외)',
      '사진은 최대 3장까지 올릴 수 있어요(1장 제외)',
      '10MB보다 큰 사진은 올릴 수 없어요(1장 제외)',
    ]);
  });
});
