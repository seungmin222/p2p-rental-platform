import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  cancelRental,
  confirmReturn,
  createRental,
  fetchCalendar,
  fetchInspections,
  fetchRental,
  handoverRental,
  requestCancelAgreement,
  requestReturn,
  respondCancelAgreement,
} from './api';
import { isStaleStateError } from './errorMessages';
import { mergeCalendarDays } from './rentalData';

// 상태가 바뀌는 화면이라 짧게(frontend/CLAUDE.md "API 연동 규칙")
const SHORT_STALE_TIME = 5_000;

export const rentalKeys = {
  all: ['rentals'],
  detail: (rentalId) => ['rentals', rentalId],
  inspections: (rentalId) => ['rentals', rentalId, 'inspections'],
  mine: () => ['rentals', 'me'],
  calendar: (itemId) => ['items', itemId, 'calendar'],
  calendarMonth: (itemId, month) => ['items', itemId, 'calendar', month],
  itemRentals: (itemId) => ['items', itemId, 'rentals'],
};

export function useRental(rentalId) {
  return useQuery({
    queryKey: rentalKeys.detail(rentalId),
    queryFn: () => fetchRental(rentalId),
    staleTime: SHORT_STALE_TIME,
  });
}

export function useRentalInspections(rentalId, { enabled }) {
  return useQuery({
    queryKey: rentalKeys.inspections(rentalId),
    queryFn: () => fetchInspections(rentalId),
    staleTime: SHORT_STALE_TIME,
    enabled,
  });
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
    })),
    combine: combineCalendar,
  });
}

/**
 * 상태 변경 응답(RentalDetailResponse)을 캐시에 넣고 관련 목록을 무효화한다.
 * 기간 점유가 바뀌는 경우(예약·취소)는 물품 캘린더·예약 현황도, 기록이 생기는 경우는 inspections도.
 */
function applyRentalDetail(
  queryClient,
  rental,
  { periodChanged = false, inspectionsChanged = false },
) {
  queryClient.setQueryData(rentalKeys.detail(rental.rentalId), rental);
  queryClient.invalidateQueries({ queryKey: rentalKeys.mine() });
  if (periodChanged) {
    queryClient.invalidateQueries({ queryKey: rentalKeys.calendar(rental.item.itemId) });
    queryClient.invalidateQueries({ queryKey: rentalKeys.itemRentals(rental.item.itemId) });
  }
  if (inspectionsChanged) {
    queryClient.invalidateQueries({ queryKey: rentalKeys.inspections(rental.rentalId) });
  }
}

/** 보던 대여가 이미 바뀌었으면(errorMessages.isStaleStateError) 상세와 inspections를 다시 불러온다. */
function refetchIfStale(queryClient, rentalId, error) {
  if (isStaleStateError(error)) {
    // ['rentals', id]는 inspections 키의 앞부분이라 둘 다 무효화된다
    queryClient.invalidateQueries({ queryKey: rentalKeys.detail(rentalId) });
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
    onSuccess: (rental) => applyRentalDetail(queryClient, rental, { periodChanged: true }),
  });
}

/** variables: { reason, reasonText? } */
export function useCancelRental(rentalId) {
  return useRentalMutation(rentalId, (body) => cancelRental(rentalId, body), {
    periodChanged: true,
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
      applyRentalDetail(queryClient, rental, { periodChanged: action === 'ACCEPT' }),
    onError: (error) => refetchIfStale(queryClient, rentalId, error),
  });
}

/** variables: { request: { memo }, images: File[] } */
export function useHandover(rentalId) {
  return useRentalMutation(
    rentalId,
    ({ request, images }) => handoverRental(rentalId, request, images),
    { inspectionsChanged: true },
  );
}

/** variables: { request: { checklist, note }, images: File[] } */
export function useRequestReturn(rentalId) {
  return useRentalMutation(
    rentalId,
    ({ request, images }) => requestReturn(rentalId, request, images),
    { inspectionsChanged: true },
  );
}

/** variables: { request: { result, resultNote }, images: File[] } */
export function useConfirmReturn(rentalId) {
  return useRentalMutation(
    rentalId,
    ({ request, images }) => confirmReturn(rentalId, request, images),
    { inspectionsChanged: true },
  );
}
