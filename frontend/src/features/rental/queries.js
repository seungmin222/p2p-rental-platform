import {
  useInfiniteQuery,
  useMutation,
  useQueries,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query';

import { flattenPages, getNextPageParam } from '../../shared/lib/listParams';
import { itemKeys } from '../item/queries';
import { paymentHistoryKeys } from '../payment/keys';

import {
  actOnDeposit,
  cancelRental,
  confirmReturn,
  createRental,
  fetchCalendar,
  fetchDeductionPolicy,
  fetchInspections,
  fetchItemRentals,
  fetchRental,
  fetchRentalHistory,
  handoverRental,
  reportLost,
  requestCancelAgreement,
  requestExtension,
  requestReturn,
  respondCancelAgreement,
  toItemRental,
} from './api';
import { isAiComparePending } from './aiCompare';
import { isExtensionRefetchError, isStaleStateError } from './errorMessages';
import { findInspection, isPaymentExpired, mergeCalendarDays } from './rentalData';
import { isExtensionPaymentExpired, readMaxEndDate } from './rentalExtension';

// 상태가 바뀌는 화면이라 짧게(frontend/CLAUDE.md "API 연동 규칙")
const SHORT_STALE_TIME = 5_000;

export const rentalKeys = {
  all: ['rentals'],
  detail: (rentalId) => ['rentals', rentalId],
  inspections: (rentalId) => ['rentals', rentalId, 'inspections'],
  mine: () => ['rentals', 'me'],
  // mine()의 하위라 상태 변경 뒤 mine() 무효화가 그대로 걸린다
  history: (tab) => ['rentals', 'me', { role: 'renter', tab }],
  calendar: (itemId) => ['items', itemId, 'calendar'],
  calendarMonth: (itemId, month) => ['items', itemId, 'calendar', month],
  itemRentals: (itemId) => ['items', itemId, 'rentals'],
  // detail의 하위라 상세 무효화(refetchIfStale)에 함께 걸린다
  deductionPolicy: (rentalId, grade) => ['rentals', rentalId, 'deposit-policy', grade],
};

export function useRental(rentalId) {
  return useQuery({
    queryKey: rentalKeys.detail(rentalId),
    queryFn: () => fetchRental(rentalId),
    staleTime: SHORT_STALE_TIME,
    // 경로의 id가 숫자가 아니면 부르지 않는다(화면이 바로 "볼 수 없어요")
    enabled: rentalId != null,
    // 결제 시간(대여 결제·연장 결제)이 지나면 만료 배치가 정리할 때까지 다시 불러온다
    refetchInterval: ({ state }) =>
      state.data &&
      (isPaymentExpired(state.data, Date.now()) ||
        isExtensionPaymentExpired(state.data, Date.now()))
        ? SHORT_STALE_TIME
        : false,
  });
}

export function useRentalInspections(rentalId, { enabled }) {
  return useQuery(rentalInspectionsQueryOptions(rentalId, { enabled }));
}

/** 검사 기록 조회 옵션. 재조회 주기가 실제로 걸리는지 queries.test.js가 QueryObserver로 확인한다 */
export function rentalInspectionsQueryOptions(rentalId, { enabled }) {
  return {
    queryKey: rentalKeys.inspections(rentalId),
    queryFn: () => fetchInspections(rentalId),
    staleTime: SHORT_STALE_TIME,
    enabled,
    // AI 상태 비교(REQ-040)를 기다리는 동안 다시 불러온다. DONE은 AI_COMPARE_DONE 알림도 ['rentals', id]
    // 무효화로 이 키를 다시 받게 하지만, FAILED·LIMIT_EXCEEDED는 알림이 없다(open-issues #48)
    refetchInterval: ({ state }) =>
      isAiComparePending(findInspection(state.data, 'RETURN'), Date.now())
        ? SHORT_STALE_TIME
        : false,
  };
}

// 모듈 함수라 참조가 바뀌지 않아, 결과가 그대로면 TanStack Query가 이전 값을 돌려준다(days가 렌더마다 새 배열이 아님)
function combineCalendar(results) {
  return { days: mergeCalendarDays(results.map((result) => result.data)), results };
}

/**
 * 연 달들의 캘린더를 불러와 Calendar의 days 하나로 합친다(달을 넘는 예약의 범위 검사용).
 * results는 months 순서와 같다.
 */
export function useCalendarDays(itemId, months) {
  return useQueries({
    queries: months.map((month) => ({
      queryKey: rentalKeys.calendarMonth(itemId, month),
      queryFn: () => fetchCalendar(itemId, month),
      staleTime: SHORT_STALE_TIME,
      enabled: Number.isInteger(itemId),
    })),
    combine: combineCalendar,
  });
}

/** REQ-019 빌린 내역(탭별, page/hasNext). data는 지금까지 받은 페이지를 합친 RentalHistoryResponse 배열 */
export function useRentalHistory(tab) {
  return useInfiniteQuery({
    queryKey: rentalKeys.history(tab),
    queryFn: ({ pageParam }) => fetchRentalHistory({ tab, page: pageParam }),
    initialPageParam: 0,
    getNextPageParam,
    select: flattenPages,
  });
}

function selectItemRentals(data) {
  return flattenPages(data, toItemRental);
}

/** REQ-020 물품별 예약 현황(제공자 본인만). 열린 동안만 렌더하는 예약 현황 시트가 부른다 */
export function useItemRentals(itemId) {
  return useInfiniteQuery({
    queryKey: rentalKeys.itemRentals(itemId),
    queryFn: ({ pageParam }) => fetchItemRentals(itemId, pageParam),
    initialPageParam: 0,
    getNextPageParam,
    select: selectItemRentals,
    staleTime: SHORT_STALE_TIME,
  });
}

/**
 * 상태 변경 응답(RentalDetailResponse)을 캐시에 넣고 관련 목록을 무효화한다.
 * 내 물품 목록도 무효화한다(대여 중 탭 소속·다가오는 예약 수가 바뀐다).
 * 기간 점유가 바뀌는 경우(예약·취소·동의 수락·반납 요청)는 물품 캘린더·예약 현황도, 기록이 생기는 경우는 inspections도.
 * 돈이 움직이는 경우(취소 환불·연체료 확정·보증금 반환·보류·차감)는 결제·정산 내역(REQ-033·039)도.
 */
function applyRentalDetail(
  queryClient,
  rental,
  { hasPeriodChanged = false, hasInspectionsChanged = false, hasPaymentChanged = false },
) {
  queryClient.setQueryData(rentalKeys.detail(rental.rentalId), rental);
  queryClient.invalidateQueries({ queryKey: rentalKeys.mine() });
  queryClient.invalidateQueries({ queryKey: itemKeys.mine() });
  if (hasPeriodChanged) {
    queryClient.invalidateQueries({ queryKey: rentalKeys.calendar(rental.item.itemId) });
    queryClient.invalidateQueries({ queryKey: rentalKeys.itemRentals(rental.item.itemId) });
  }
  if (hasInspectionsChanged) {
    queryClient.invalidateQueries({ queryKey: rentalKeys.inspections(rental.rentalId) });
  }
  if (hasPaymentChanged) {
    invalidatePaymentHistory(queryClient);
  }
}

/** 결제·보증금 내역과 정산 내역 */
function invalidatePaymentHistory(queryClient) {
  queryClient.invalidateQueries({ queryKey: paymentHistoryKeys.all });
  queryClient.invalidateQueries({ queryKey: paymentHistoryKeys.settlements() });
}

/**
 * 보던 대여가 이미 바뀌었으면(errorMessages.isStaleStateError) 상세와 inspections를 다시 불러온다.
 * 다른 곳에서 바뀐 상태라 빌린 내역·내 물품 목록도 낡았으므로 함께 무효화한다.
 */
function refetchIfStale(queryClient, rentalId, error) {
  if (isStaleStateError(error)) {
    // ['rentals', id]는 inspections 키의 앞부분이라 둘 다 무효화된다
    queryClient.invalidateQueries({ queryKey: rentalKeys.detail(rentalId) });
    queryClient.invalidateQueries({ queryKey: rentalKeys.mine() });
    queryClient.invalidateQueries({ queryKey: itemKeys.mine() });
  }
}

/** 대여 상세의 상태 변경 뮤테이션 공통 모양 */
function useRentalMutation(rentalId, mutationFn, applyOptions) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: (rental) => applyRentalDetail(queryClient, rental, applyOptions),
    onError: (error) => refetchIfStale(queryClient, rentalId, error),
  });
}

export function useCreateRental() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createRental,
    onSuccess: (rental) => applyRentalDetail(queryClient, rental, { hasPeriodChanged: true }),
  });
}

/** variables: { reason, reasonText? } */
export function useCancelRental(rentalId) {
  return useRentalMutation(rentalId, (body) => cancelRental(rentalId, body), {
    hasPeriodChanged: true,
    hasPaymentChanged: true,
  });
}

/** variables: { reason, reasonText? }. 응답이 상세가 아니라(CancelAgreementResponse) 상세를 다시 불러온다. */
export function useRequestCancelAgreement(rentalId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body) => requestCancelAgreement(rentalId, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: rentalKeys.detail(rentalId) }),
    onError: (error) => refetchIfStale(queryClient, rentalId, error),
  });
}

/** variables: { agreementId, action: 'ACCEPT' | 'REJECT' } */
export function useRespondCancelAgreement(rentalId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ agreementId, action }) => respondCancelAgreement(rentalId, agreementId, action),
    onSuccess: (rental, { action }) =>
      applyRentalDetail(queryClient, rental, {
        hasPeriodChanged: action === 'ACCEPT',
        hasPaymentChanged: action === 'ACCEPT',
      }),
    onError: (error) => refetchIfStale(queryClient, rentalId, error),
  });
}

/** variables: { request: { memo }, images: File[] } */
export function useHandover(rentalId) {
  return useRentalMutation(
    rentalId,
    ({ request, images }) => handoverRental(rentalId, request, images),
    { hasInspectionsChanged: true },
  );
}

/** variables: { request: { checklist, note }, images: File[] }. RETURNED가 되면 기간 점유가 풀린다(대리 접수 포함). */
export function useRequestReturn(rentalId) {
  return useRentalMutation(
    rentalId,
    ({ request, images }) => requestReturn(rentalId, request, images),
    // 반납 때 연체료가 확정된다
    { hasPeriodChanged: true, hasInspectionsChanged: true, hasPaymentChanged: true },
  );
}

/** variables: { request: { result, resultNote }, images: File[] } */
export function useConfirmReturn(rentalId) {
  return useRentalMutation(
    rentalId,
    ({ request, images }) => confirmReturn(rentalId, request, images),
    { hasInspectionsChanged: true, hasPaymentChanged: true },
  );
}

/**
 * 대여 연장 요청(REQ-038). variables: { newEndDate }. 응답은 RentalExtensionResponse라 상세를 다시 불러온다.
 * 결제 대기면 그 기간을 선점하고, 바로 확정이면 종료일이 바뀌므로 어느 쪽이든 캘린더·목록도 무효화한다.
 * 거절이 보던 상세가 낡았다는 뜻이면(결제 대기 연장 있음·종료일 지남·상태 바뀜) 상세를 다시 불러오고,
 * 연장 기간 오류(maxEndDate)면 캘린더를 다시 불러온다.
 */
export function useRequestExtension(rental) {
  const queryClient = useQueryClient();
  const { rentalId } = rental;
  const itemId = rental.item.itemId;
  return useMutation({
    mutationFn: (body) => requestExtension(rentalId, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: rentalKeys.detail(rentalId) });
      queryClient.invalidateQueries({ queryKey: rentalKeys.mine() });
      queryClient.invalidateQueries({ queryKey: itemKeys.mine() });
      queryClient.invalidateQueries({ queryKey: rentalKeys.calendar(itemId) });
      queryClient.invalidateQueries({ queryKey: rentalKeys.itemRentals(itemId) });
    },
    onError: (error) => {
      refetchIfStale(queryClient, rentalId, error);
      if (isExtensionRefetchError(error)) {
        queryClient.invalidateQueries({ queryKey: rentalKeys.detail(rentalId) });
      }
      if (readMaxEndDate(error) !== undefined) {
        queryClient.invalidateQueries({ queryKey: rentalKeys.calendar(itemId) });
      }
    },
  });
}

/**
 * 미반납 신고(REQ-018, 제공자). variables: { note? }. 반납 완료가 되며 기간 점유가 풀리고 RETURN 기록이 생기고,
 * 연체료 확정·보증금 보류(또는 동결 보증금이 없으면 바로 거래 완료)로 결제·정산 내역이 바뀐다.
 */
export function useReportLost(rentalId) {
  return useRentalMutation(rentalId, (body) => reportLost(rentalId, body), {
    hasPeriodChanged: true,
    hasInspectionsChanged: true,
    hasPaymentChanged: true,
  });
}

/** 등급별 차감 상한(REQ-049). grade가 없으면 부르지 않는다 */
export function useDeductionPolicy(rentalId, grade) {
  return useQuery({
    queryKey: rentalKeys.deductionPolicy(rentalId, grade),
    queryFn: () => fetchDeductionPolicy(rentalId, grade),
    staleTime: SHORT_STALE_TIME,
    enabled: grade != null,
  });
}

/**
 * 보증금 차감 협의(REQ-049). variables: { request: DepositActionRequest, evidenceImages?, receiptImages? }.
 * 제안·거절은 보류가 이어지고 수락은 반환·거래 완료로 가므로 모두 결제·정산 내역을 무효화한다.
 * 상한 초과(DEDUCTION_EXCEEDS_CAP)면 그 등급 상한을 다시 불러온다.
 */
export function useDepositAction(rentalId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ request, evidenceImages, receiptImages }) =>
      actOnDeposit(rentalId, request, { evidenceImages, receiptImages }),
    onSuccess: (rental) => applyRentalDetail(queryClient, rental, { hasPaymentChanged: true }),
    onError: (error, { request }) => {
      refetchIfStale(queryClient, rentalId, error);
      if (error?.code === 'DEDUCTION_EXCEEDS_CAP' && request.grade) {
        queryClient.invalidateQueries({
          queryKey: rentalKeys.deductionPolicy(rentalId, request.grade),
        });
      }
    },
  });
}
