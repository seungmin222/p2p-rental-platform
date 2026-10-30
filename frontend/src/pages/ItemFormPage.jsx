import { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router';

import PageHeader from '../app/layout/PageHeader';
import { useMe } from '../features/auth/queries';
import ItemForm from '../features/item/components/ItemForm';
import { getErrorMessage } from '../features/item/errorMessages';
import { createItemFormValues, toCreatePayload } from '../features/item/itemForm';
import { useCreateItem } from '../features/item/queries';
import { todayInSeoul } from '../shared/lib/date';
import { ButtonLink, toast } from '../shared/ui';

/** 대표 동네 인증 안내. 입력을 막지 않고, 인증하러 가면 입력이 사라진다고 미리 알린다 */
function RegionVerifyNotice({ ref }) {
  return (
    <div
      ref={ref}
      role="status"
      className="mb-4 flex flex-col gap-2 rounded-xl border border-line bg-surface p-4"
    >
      <p className="text-caption">
        대표 동네를 인증해야 물품을 등록할 수 있어요. 인증하러 가면 지금 입력한 내용은 사라져요.
      </p>
      <ButtonLink to="/regions" variant="secondary" size="sm" className="self-start">
        동네 인증하러 가기
      </ButtonLink>
    </div>
  );
}

/** 등록(REQ-006). 진입할 때 인증이 없으면 안내하고, 서버 거부(REGION_NOT_VERIFIED)도 같은 안내로 받는다 */
function ItemCreate() {
  const navigate = useNavigate();
  const mutation = useCreateItem();
  const { data: me } = useMe();
  const [initialValues] = useState(createItemFormValues);
  const [today] = useState(todayInSeoul);
  const [isRejected, setIsRejected] = useState(false);
  const noticeRef = useRef(null);
  const showNotice = isRejected || (me != null && me.primaryRegion?.isVerified !== true);

  // 아래쪽 제출 버튼에서 거부되면 맨 위 안내가 보이게 옮긴다
  useEffect(() => {
    if (isRejected) {
      noticeRef.current?.scrollIntoView({ block: 'center' });
    }
  }, [isRejected]);

  function handleServerError(error) {
    if (error.code !== 'REGION_NOT_VERIFIED') {
      return false;
    }
    toast.show({ message: getErrorMessage(error), tone: 'danger' });
    setIsRejected(true);
    return true;
  }

  function handleSave(values, onError) {
    mutation.mutate(toCreatePayload(values), {
      onSuccess: ({ itemId }) => {
        toast.show({ message: '물품을 등록했어요' });
        navigate(`/items/${itemId}`, { replace: true });
      },
      onError,
    });
  }

  return (
    <>
      <PageHeader title="물품 등록" />
      {showNotice && <RegionVerifyNotice ref={noticeRef} />}
      <ItemForm
        initialValues={initialValues}
        today={today}
        isPending={mutation.isPending}
        submitLabel="등록하기"
        onSave={handleSave}
        onServerError={handleServerError}
      />
    </>
  );
}

/** SCR-06 물품 등록·수정 */
export default function ItemFormPage() {
  const { id } = useParams();
  return id === undefined ? <ItemCreate /> : null;
}
