// 조회 실패를 화면에서 어떻게 보여 줄지 정하는 순수 함수(TanStack Query 결과 객체를 받는다).
// 처음 불러오기 실패만 화면 전체 오류로 바꾸고, 이미 데이터가 있으면 화면은 그대로 두고 토스트로 알린다.

/** 보여 줄 데이터가 없는 실패(전체 ErrorState) */
export function isInitialLoadError(query) {
  return Boolean(query?.isError) && query.data === undefined;
}

/**
 * 데이터가 있는데 다시 불러오기만 실패한 query들 중 가장 최근 실패 시각(errorUpdatedAt). 없으면 null.
 * 실패마다 값이 바뀌고 다시 그려도 그대로라, 이 값이 바뀔 때만 토스트를 띄우면 같은 실패로 반복되지 않는다.
 */
export function getRefetchErrorKey(queries) {
  let latest = null;
  for (const query of queries) {
    if (query?.isError && query.data !== undefined && query.errorUpdatedAt > (latest ?? 0)) {
      latest = query.errorUpdatedAt;
    }
  }
  return latest;
}
