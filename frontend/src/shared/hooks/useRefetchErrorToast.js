import { useEffect, useRef } from 'react';

import { getRefetchErrorKey } from '../lib/queryState';
import { toast } from '../ui';

const MESSAGE = '최신 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요';

/**
 * 데이터가 있는 화면에서 다시 불러오기가 실패하면 화면은 그대로 두고 토스트를 한 번 띄운다.
 * 실패 시각(getRefetchErrorKey)이 바뀔 때만 띄우므로 같은 실패로 반복되지 않는다.
 * 열린 모달·시트가 있으면 토스트가 그 아래(top layer 밖)에 그려져 가려진다.
 */
export function useRefetchErrorToast(queries) {
  const errorKey = getRefetchErrorKey(queries);
  const shownKey = useRef(null);

  useEffect(() => {
    if (errorKey != null && errorKey !== shownKey.current) {
      shownKey.current = errorKey;
      toast.show({ message: MESSAGE, tone: 'danger' });
    }
  }, [errorKey]);
}
