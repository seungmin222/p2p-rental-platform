// ImageUploader의 목록 조작. 화면과 분리해 node 환경에서 테스트한다.
// 항목 모양: 기존 이미지 { id: 서버 id, url }, 새 이미지 { id: 'local-n', file }.
// 미리보기 objectURL은 여기에 넣지 않는다(썸네일 컴포넌트가 만들고 해제한다).

export const IMAGE_REJECT_REASON = {
  TOO_MANY_FILES: 'TOO_MANY_FILES',
  FILE_TOO_LARGE: 'FILE_TOO_LARGE',
  UNSUPPORTED_TYPE: 'UNSUPPORTED_TYPE',
};

const LOCAL_ID_PREFIX = 'local-';
let localSeq = 0;

/** 서버 id와 겹치지 않는 로컬 id. 모듈 카운터라 한 페이지 안에서 유일하다. */
export function createLocalId() {
  localSeq += 1;
  return `${LOCAL_ID_PREFIX}${localSeq}`;
}

/** 새로 고른(아직 업로드하지 않은) 이미지인지 */
export function isLocalImage(image) {
  return image.file != null;
}

/**
 * 고른 파일을 검사해 통과한 것만 뒤에 붙인다. 형식·용량을 먼저 보고, 통과한 파일만 남은 장수를 쓴다.
 * 용량은 정확히 maxFileSize까지 허용. 형식은 file.type(MIME) 기준이라 HEIC 등은 거부된다.
 *
 * @returns {{images: object[], rejected: {file: File, reason: string}[]}}
 */
export function addImages(images, files, { maxCount, maxFileSize, accept }) {
  const added = [];
  const rejected = [];
  for (const file of files) {
    if (!accept.includes(file.type)) {
      rejected.push({ file, reason: IMAGE_REJECT_REASON.UNSUPPORTED_TYPE });
    } else if (file.size > maxFileSize) {
      rejected.push({ file, reason: IMAGE_REJECT_REASON.FILE_TOO_LARGE });
    } else if (images.length + added.length >= maxCount) {
      rejected.push({ file, reason: IMAGE_REJECT_REASON.TOO_MANY_FILES });
    } else {
      added.push({ id: createLocalId(), file });
    }
  }
  return { images: added.length > 0 ? [...images, ...added] : images, rejected };
}

export function removeImage(images, id) {
  return images.filter((image) => image.id !== id);
}

/** offset -1 = 앞으로, +1 = 뒤로. 범위를 벗어나면 그대로 돌려준다. */
export function moveImage(images, id, offset) {
  const from = images.findIndex((image) => image.id === id);
  const to = from + offset;
  if (from < 0 || to < 0 || to >= images.length) {
    return images;
  }
  const next = [...images];
  [next[from], next[to]] = [next[to], next[from]];
  return next;
}

/** 대표(첫 장)로. 나머지 순서는 유지한다. */
export function makeCover(images, id) {
  const target = images.find((image) => image.id === id);
  if (!target || images[0] === target) {
    return images;
  }
  return [target, ...images.filter((image) => image !== target)];
}

function formatTypes(accept) {
  const names = accept.map((type) => {
    const sub = type.split('/')[1]?.toUpperCase() ?? type;
    return sub === 'JPEG' ? 'JPG' : sub;
  });
  return [...new Set(names)].join(', ');
}

function formatSize(bytes) {
  const mb = bytes / (1024 * 1024);
  return Number.isInteger(mb) ? `${mb}MB` : `${Math.round(bytes / 1024)}KB`;
}

/** 거부 사유별 안내 문구(사유당 한 줄, 발생 순서대로) */
export function describeRejections(rejected, { maxCount, maxFileSize, accept }) {
  const counts = new Map();
  for (const { reason } of rejected) {
    counts.set(reason, (counts.get(reason) ?? 0) + 1);
  }
  return [...counts].map(([reason, count]) => {
    switch (reason) {
      case IMAGE_REJECT_REASON.TOO_MANY_FILES:
        return `사진은 최대 ${maxCount}장까지 올릴 수 있어요(${count}장 제외)`;
      case IMAGE_REJECT_REASON.FILE_TOO_LARGE:
        return `${formatSize(maxFileSize)}보다 큰 사진은 올릴 수 없어요(${count}장 제외)`;
      case IMAGE_REJECT_REASON.UNSUPPORTED_TYPE:
        return `${formatTypes(accept)} 사진만 올릴 수 있어요(${count}장 제외)`;
      default:
        return '';
    }
  });
}
