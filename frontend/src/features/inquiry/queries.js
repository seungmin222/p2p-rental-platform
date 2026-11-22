import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';

import { flattenPages, getNextPageParam } from '../../shared/lib/listParams';
import { paymentHistoryKeys } from '../payment/keys';
import { rentalKeys } from '../rental/queries';

import { createInquiry, fetchMyInquiries } from './api';
import { isDisputeNotAllowedError } from './errorMessages';

export const inquiryKeys = {
  mine: () => ['inquiries', 'me'],
};

/** 내 문의(page/hasNext, size 없음 — inquiry.page 고정). data는 지금까지 받은 InquiryResponse 배열 */
export function useMyInquiries() {
  return useInfiniteQuery({
    queryKey: inquiryKeys.mine(),
    queryFn: ({ pageParam }) => fetchMyInquiries(pageParam),
    initialPageParam: 0,
    getNextPageParam,
    select: flattenPages,
  });
}

/**
 * 문의 접수 뮤테이션 옵션(테스트에서 MutationObserver로 쓴다). variables: InquiryCreateRequest.
 * 성공: 내 문의 무효화, 분쟁이면 그 대여의 정산이 보류(HELD)되므로 정산 내역도.
 * 대여 상세는 응답 필드가 바뀌지 않아 그대로 둔다.
 * 결제 대기라 거절되면(화면이 본 상태와 다름) 대여 상세를 다시 불러온다.
 */
export function createInquiryMutationOptions(queryClient) {
  return {
    mutationFn: (body) => createInquiry(body),
    onSuccess: (inquiry) => {
      queryClient.invalidateQueries({ queryKey: inquiryKeys.mine() });
      if (inquiry.type === 'DISPUTE') {
        queryClient.invalidateQueries({ queryKey: paymentHistoryKeys.settlements() });
      }
    },
    onError: (error, body) => {
      if (isDisputeNotAllowedError(error) && body?.rentalId != null) {
        queryClient.invalidateQueries({ queryKey: rentalKeys.detail(body.rentalId) });
      }
    },
  };
}

export function useCreateInquiry() {
  const queryClient = useQueryClient();
  return useMutation(createInquiryMutationOptions(queryClient));
}
