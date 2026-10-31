import { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router';

import PageHeader from '../app/layout/PageHeader';
import { useMe } from '../features/auth/queries';
import { toItemEditForm } from '../features/item/api';
import ItemForm from '../features/item/components/ItemForm';
import { getErrorMessage } from '../features/item/errorMessages';
import { createItemFormValues, toCreatePayload, toUpdatePayload } from '../features/item/itemForm';
import { parseItemId } from '../features/item/itemId';
import { useCreateItem, useItemForEdit, useUpdateItem } from '../features/item/queries';
import { todayInSeoul } from '../shared/lib/date';
import { isInitialLoadError } from '../shared/lib/queryState';
import { ButtonLink, ErrorState, Skeleton, toast } from '../shared/ui';

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

/** 수정 폼. 처음 받은 값을 기준으로 바뀐 필드만 보낸다(이후 재조회로 입력이 덮이지 않게 한 번만 잡는다) */
function ItemEditForm({ itemId, item }) {
  const navigate = useNavigate();
  const mutation = useUpdateItem(itemId);
  const [initialValues] = useState(() => toItemEditForm(item));

  function handleSave(values, onError) {
    const payload = toUpdatePayload(initialValues, values);
    if (!payload) {
      toast.show({ message: '바뀐 내용이 없어요' });
      navigate(`/items/${itemId}`, { replace: true });
      return;
    }
    mutation.mutate(payload, {
      onSuccess: () => {
        toast.show({ message: '물품을 수정했어요' });
        navigate(`/items/${itemId}`, { replace: true });
      },
      onError,
    });
  }

  return (
    <ItemForm
      initialValues={initialValues}
      isEdit
      isPending={mutation.isPending}
      submitLabel="수정하기"
      onSave={handleSave}
    />
  );
}

function FormSkeleton() {
  return (
    <div aria-busy="true" aria-label="불러오는 중" className="flex flex-col gap-4 pt-2">
      <Skeleton className="h-32 w-full" />
      <Skeleton className="h-12 w-full" />
      <Skeleton className="h-24 w-full" />
      <Skeleton className="h-12 w-full" />
    </div>
  );
}

/** 수정(REQ-007). 남의 물품 403 ITEM_NOT_OWNER, 없음·삭제 404 ITEM_NOT_FOUND, 잘못된 id는 요청하지 않는다 */
function ItemEdit({ id }) {
  const itemId = parseItemId(id);
  const query = useItemForEdit(itemId);

  let content;
  if (itemId == null || (isInitialLoadError(query) && query.error.code === 'ITEM_NOT_FOUND')) {
    content = (
      <>
        <ErrorState title="물품을 찾을 수 없어요" message="삭제되었거나 없는 물품이에요." />
        <div className="flex justify-center">
          <ButtonLink to="/" variant="secondary">
            홈으로
          </ButtonLink>
        </div>
      </>
    );
  } else if (isInitialLoadError(query) && query.error.code === 'ITEM_NOT_OWNER') {
    content = (
      <>
        <ErrorState title="수정할 수 없어요" message="내 물품만 수정할 수 있어요." />
        <div className="flex justify-center">
          <ButtonLink to={`/items/${itemId}`} variant="secondary">
            물품 보기
          </ButtonLink>
        </div>
      </>
    );
  } else if (isInitialLoadError(query)) {
    content = <ErrorState message={getErrorMessage(query.error)} onRetry={query.refetch} />;
  } else if (!query.data) {
    content = <FormSkeleton />;
  } else {
    content = <ItemEditForm itemId={itemId} item={query.data} />;
  }
  return (
    <>
      <PageHeader title="물품 수정" />
      {content}
    </>
  );
}

/** SCR-06 물품 등록·수정 */
export default function ItemFormPage() {
  const { id } = useParams();
  return id === undefined ? <ItemCreate /> : <ItemEdit key={id} id={id} />;
}
