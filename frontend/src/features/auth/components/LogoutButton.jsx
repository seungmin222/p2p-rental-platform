import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router';

import { Button, ConfirmModal, toast } from '../../../shared/ui';
import { useLogout } from '../queries';

/**
 * /me 맨 아래 로그아웃. 확인 → POST /api/auth/logout → 홈으로 이동 → AT·캐시 삭제(useLogout onSuccess).
 * 이동을 먼저 끝내는(flushSync로 새 화면까지 커밋) 이유: /me는 RequireAuth 안이라 AT를 먼저 지우면
 * /login?redirect=/me로 보내 버린다. 실패(네트워크·5xx)면 RT 쿠키가 남아 다음 시작 때 복원되므로 로컬도 지우지 않는다.
 */
export default function LogoutButton() {
  const [isOpen, setIsOpen] = useState(false);
  const navigate = useNavigate();
  // 화면이 떠 있는 동안만 떠나는 함수를 둔다. 요청 중에 화면을 벗어나면 null이라 훅이 이동 없이 정리만 한다
  const leaveRef = useRef(null);
  const mutation = useLogout(leaveRef);

  useEffect(() => {
    leaveRef.current = () => navigate('/', { replace: true, flushSync: true });
    return () => {
      leaveRef.current = null;
    };
  }, [navigate]);

  function handleConfirm() {
    mutation.mutate(undefined, {
      onError: () => {
        setIsOpen(false);
        toast.show({
          message: '로그아웃하지 못했어요. 잠시 후 다시 시도해 주세요',
          tone: 'danger',
        });
      },
    });
  }

  return (
    <>
      <Button variant="secondary" fullWidth onClick={() => setIsOpen(true)}>
        로그아웃
      </Button>
      {isOpen && (
        <ConfirmModal
          open
          onClose={() => setIsOpen(false)}
          onConfirm={handleConfirm}
          title="로그아웃할까요?"
          confirmLabel="로그아웃"
          isLoading={mutation.isPending}
        >
          <p className="text-body text-ink-muted">이 기기에서만 로그아웃돼요.</p>
        </ConfirmModal>
      )}
    </>
  );
}
