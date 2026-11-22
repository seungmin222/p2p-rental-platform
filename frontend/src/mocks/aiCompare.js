// AI 상태 비교 목(REQ-040). 백엔드 흐름 흉내: 반납 요청 → (큐) → 워커가 맡음(PROCESSING) → DONE + 양측 AI_COMPARE_DONE.
// 저장 형태는 반납 기록의 aiResult { status, startedAt, analyzedAt, result }(rentalSeed.js), 응답은 RentalAiResultResponse.
import { parseServerDateTime } from '../shared/lib/format';
import { mockNotificationControls } from './notification';
import { db, nowServer, saveDb } from './rentalStore';

// policies.md ai.compare.processing-timeout-minutes (open-issues #46). 목은 서버 흉내라 화면 상수를 가져오지 않는다
const PROCESSING_TIMEOUT_MINUTES = 10;
// 목 진행 시간(큐 대기 → 워커가 맡음 → 끝남)
const QUEUE_DELAY_MS = 2_000;
const ANALYZE_DELAY_MS = 8_000;

/** 저장 값 → RentalAiResultResponse. 오래된 PROCESSING은 응답에서 FAILED(DB 값은 그대로, #46) */
export function toAiResultResponse(aiResult, now = Date.now()) {
  if (!aiResult) {
    return null;
  }
  if (aiResult.status === 'PROCESSING') {
    const startedAt = parseServerDateTime(aiResult.startedAt)?.getTime() ?? now;
    if (now - startedAt > PROCESSING_TIMEOUT_MINUTES * 60 * 1000) {
      return { status: 'FAILED', analyzedAt: null, result: null };
    }
    return { status: 'PROCESSING', analyzedAt: null, result: null };
  }
  return {
    status: aiResult.status,
    analyzedAt: aiResult.analyzedAt ?? null,
    result: aiResult.status === 'DONE' ? aiResult.result : null,
  };
}

function findReturnRecord(inspectionId) {
  return db.inspections.find((inspection) => inspection.inspectionId === inspectionId);
}

/** 반납 사진 마지막 장에 작은 긁힘이 있다고 보는 가짜 결과 */
function fakeResult(imageCount) {
  return {
    changed: true,
    changeTypes: ['SCRATCH'],
    confidence: 0.7,
    description: '반납 사진에 인계 때 보이지 않던 작은 긁힘이 있어요.',
    regions: [{ imageIndex: Math.max(0, imageCount - 1), note: '아래쪽 가장자리' }],
  };
}

/**
 * 반납 요청 뒤 AI 비교를 흉내 낸다. 새로고침으로 타이머가 사라지면 aiResult가 null로 남는다(= 적재 실패, "AI 결과 없음").
 * @param {number} inspectionId 반납 기록
 * @param {{ renterId: number, ownerId: number, rentalId: number }} rental
 */
export function scheduleAiCompare(inspectionId, rental) {
  setTimeout(() => {
    const record = findReturnRecord(inspectionId);
    if (!record || record.aiResult) {
      return;
    }
    record.aiResult = {
      status: 'PROCESSING',
      startedAt: nowServer(),
      analyzedAt: null,
      result: null,
    };
    saveDb();
    setTimeout(() => {
      const current = findReturnRecord(inspectionId);
      if (!current || current.aiResult?.status !== 'PROCESSING') {
        return;
      }
      current.aiResult = {
        ...current.aiResult,
        status: 'DONE',
        analyzedAt: nowServer(),
        result: fakeResult(current.imageUrls.length),
      };
      saveDb();
      // DONE일 때만 양측에 알린다(#48)
      mockNotificationControls.push('AI_COMPARE_DONE', rental.rentalId, rental.renterId);
      mockNotificationControls.push('AI_COMPARE_DONE', rental.rentalId, rental.ownerId);
    }, ANALYZE_DELAY_MS);
  }, QUEUE_DELAY_MS);
}
