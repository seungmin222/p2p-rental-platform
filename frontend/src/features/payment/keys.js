// 결제·정산 내역 queryKey. 대여 뮤테이션(features/rental/queries.js)도 무효화에 쓰므로 queries.js와 따로 둔다
// (payment/queries.js가 rental/queries.js를 가져오고 있어 거기서 가져오면 순환한다).
export const paymentHistoryKeys = {
  all: ['payments', 'me'],
  list: (filter) => ['payments', 'me', filter],
  settlements: () => ['settlements', 'me'],
};
