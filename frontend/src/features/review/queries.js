import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';

import { flattenPages, getNextPageParam } from '../../shared/lib/listParams';
import { rentalKeys } from '../rental/queries';
import { fetchReceivedReviews, writeReview } from './api';
import { isReviewBlockedError } from './errorMessages';

export const reviewKeys = {
  // 받은 후기(REQ-034). ['members', 'me']와 겹치지 않게 회원 id를 둘째 자리에 둔다
  received: (memberId) => ['members', memberId, 'reviews'],
};

/**
 * 후기 작성 뒤에는 대여 상세(canWriteReview·reviewWritten)와 빌린 내역(canWriteReview)이 바뀐다.
 * 응답이 상세가 아니라(ReviewResponse) 둘 다 무효화한다.
 */
function invalidateRental(queryClient, rentalId) {
  queryClient.invalidateQueries({ queryKey: rentalKeys.detail(rentalId) });
  queryClient.invalidateQueries({ queryKey: rentalKeys.mine() });
}

/** variables: ReviewCreateRequest */
export function useWriteReview(rentalId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body) => writeReview(rentalId, body),
    // 끝나기를 기다리지 않는다(기다리면 다시 받은 상세가 "이미 보냈어요"로 먼저 바뀐 뒤 이동한다)
    onSuccess: (review) => {
      invalidateRental(queryClient, rentalId);
      queryClient.invalidateQueries({ queryKey: reviewKeys.received(review.targetId) });
    },
    // 이미 썼거나 기한이 지난 경우 등: 상세를 다시 받아 화면이 "쓸 수 없음"으로 바뀐다
    onError: (error) => {
      if (isReviewBlockedError(error)) {
        invalidateRental(queryClient, rentalId);
      }
    },
  });
}

/** 받은 후기(page/hasNext). data는 지금까지 받은 페이지를 합친 ReceivedReviewResponse 배열 */
export function useReceivedReviews(memberId) {
  return useInfiniteQuery({
    queryKey: reviewKeys.received(memberId),
    queryFn: ({ pageParam }) => fetchReceivedReviews(memberId, pageParam),
    initialPageParam: 0,
    getNextPageParam,
    select: flattenPages,
    enabled: memberId != null,
  });
}
