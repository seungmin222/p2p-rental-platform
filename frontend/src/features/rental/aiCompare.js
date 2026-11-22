// AI 상태 비교(REQ-040) 표시. 반납 기록(GET /inspections의 RETURN)의 aiResult만 보고 정한다.
// AI 결과는 판정이 아니라 참고 자료다(ai.md) — 버튼·보증금 흐름은 이 결과를 보지 않는다.
import { parseServerDateTime } from '../../shared/lib/format';
import { AI_CHANGE_TYPE, AI_COMPARE_PROCESSING_TIMEOUT_MINUTES } from './constants';

/** 표시 종류 */
export const AI_VIEW = {
  WAITING: 'WAITING', // aiResult가 아직 없음(적재 직후, open-issues #59 임시)
  PROCESSING: 'PROCESSING',
  DONE: 'DONE',
  NONE: 'NONE', // AI 결과 없음
};

/** AI 결과 없음의 이유 */
export const AI_NONE_REASON = {
  FAILED: 'FAILED',
  LIMIT_EXCEEDED: 'LIMIT_EXCEEDED',
  NOT_RUN: 'NOT_RUN', // aiResult null이 시간창을 넘음(적재 실패 등)
};

const TIMEOUT_MS = AI_COMPARE_PROCESSING_TIMEOUT_MINUTES * 60 * 1000;

/**
 * aiResult가 null인 반납 기록이 "비교 준비 중"인지(open-issues #59 임시).
 * 반납 사진이 있고, 기록이 만들어진 지 processing-timeout 안이면 true.
 */
function isWaitingForAi(returnInspection, now) {
  const createdAt = parseServerDateTime(returnInspection.createdAt);
  if (!createdAt) {
    return false;
  }
  const elapsed = now - createdAt.getTime();
  return elapsed >= 0 ? elapsed < TIMEOUT_MS : true;
}

/** 미반납 신고 기록(사진 없는 반납 기록)에는 AI 비교가 없다 */
function hasReturnPhotos(returnInspection) {
  return (returnInspection.imageUrls ?? []).length > 0;
}

function toChangeTypes(codes) {
  return (codes ?? []).map((code) => ({ code, label: AI_CHANGE_TYPE[code]?.label ?? code }));
}

/** regions.imageIndex는 반납 사진 순서(0부터). 범위를 벗어나면 사진 없이 메모만 */
function toRegions(regions, imageUrls) {
  return (regions ?? []).map((region) => {
    const index = Number.isInteger(region.imageIndex) ? region.imageIndex : -1;
    const imageUrl = index >= 0 && index < imageUrls.length ? imageUrls[index] : null;
    return { imageIndex: index, imageUrl, note: region.note ?? '' };
  });
}

/**
 * 반납 기록 → AI 비교 표시. 그리지 않으면 null(반납 기록 없음·미반납 신고 건).
 * @param {object|null|undefined} returnInspection RentalInspectionResponse(RETURN)
 * @param {number} now 지금 시각(밀리초)
 */
export function getAiCompareView(returnInspection, now) {
  if (!returnInspection) {
    return null;
  }
  const aiResult = returnInspection.aiResult ?? null;
  if (!aiResult) {
    if (!hasReturnPhotos(returnInspection)) {
      return null;
    }
    return isWaitingForAi(returnInspection, now)
      ? { kind: AI_VIEW.WAITING }
      : { kind: AI_VIEW.NONE, reason: AI_NONE_REASON.NOT_RUN };
  }
  switch (aiResult.status) {
    case 'PROCESSING':
      return { kind: AI_VIEW.PROCESSING };
    case 'DONE': {
      const result = aiResult.result;
      if (!result) {
        return { kind: AI_VIEW.NONE, reason: AI_NONE_REASON.FAILED };
      }
      return {
        kind: AI_VIEW.DONE,
        changed: result.changed === true,
        changeTypes: toChangeTypes(result.changeTypes),
        description: result.description ?? '',
        regions: toRegions(result.regions, returnInspection.imageUrls ?? []),
      };
    }
    case 'LIMIT_EXCEEDED':
      return { kind: AI_VIEW.NONE, reason: AI_NONE_REASON.LIMIT_EXCEEDED };
    default:
      // FAILED, 모르는 값
      return { kind: AI_VIEW.NONE, reason: AI_NONE_REASON.FAILED };
  }
}

/** 결과를 기다리는 중이면 true → inspections를 다시 불러온다 */
export function isAiComparePending(returnInspection, now) {
  const view = getAiCompareView(returnInspection, now);
  return view?.kind === AI_VIEW.WAITING || view?.kind === AI_VIEW.PROCESSING;
}

/** 상태 문구(제목) */
export function getAiCompareHeadline(view) {
  switch (view.kind) {
    case AI_VIEW.WAITING:
      return 'AI가 사진 비교를 준비하고 있어요';
    case AI_VIEW.PROCESSING:
      return 'AI가 인계·반납 사진을 비교하고 있어요';
    case AI_VIEW.DONE:
      return view.changed ? 'AI가 달라진 점을 찾았어요' : 'AI가 눈에 띄는 변화를 찾지 못했어요';
    default:
      return 'AI 결과 없음';
  }
}

/** AI 결과 없음의 보조 문구 */
export function getAiNoneMessage(reason) {
  return reason === AI_NONE_REASON.LIMIT_EXCEEDED
    ? '이번 달 AI 이용 한도를 넘었어요. 사진을 직접 비교해 주세요.'
    : '비교하지 못했어요. 사진을 직접 비교해 주세요.';
}

/** 모든 상태 공통 고지(ai.md REQ-040 "조명·각도 차이로 오판 가능") */
export const AI_COMPARE_NOTICE =
  'AI 결과는 판정이 아니라 참고 자료예요. 조명·각도 차이로 잘못 볼 수 있어요.';
