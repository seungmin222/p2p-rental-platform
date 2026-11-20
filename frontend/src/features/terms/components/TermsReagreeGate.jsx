import { Link, useLocation } from 'react-router';

import { useAuthStore } from '../../../shared/api';
import { Button, Modal, toast } from '../../../shared/ui';
import { TERMS_DEFER_NOTICE } from '../constants';
import { getErrorMessage } from '../errorMessages';
import { useAgreeTerms } from '../queries';
import { shouldShowTermsPrompt } from '../termsPrompt';

const LINK =
  'font-semibold text-primary underline focus-visible:outline-2 focus-visible:outline-primary';

/**
 * 약관 재동의 모달(REQ-037). 로그인·재발급 응답 requiresTermsAgreement가 true면 AppLayout 어느 화면에서든 띄운다.
 * "나중에"(닫기 포함)는 새로고침·다시 로그인할 때까지 묻지 않고 이용 제한 안내만 한다(open-issues #56: 서버는 막지 않음).
 */
export default function TermsReagreeGate() {
  const { pathname } = useLocation();
  const open = useAuthStore((state) => shouldShowTermsPrompt(state, pathname));
  const mutation = useAgreeTerms();

  function defer() {
    useAuthStore.getState().dismissTermsPrompt();
    mutation.reset();
    toast.show({ message: TERMS_DEFER_NOTICE });
  }

  function agree() {
    mutation.mutate(undefined, {
      onSuccess: ({ requiresTermsAgreement }) => {
        if (!requiresTermsAgreement) {
          toast.show({ message: '약관에 동의했어요' });
        }
      },
    });
  }

  return (
    <Modal
      open={open}
      onClose={defer}
      title="약관이 바뀌었어요"
      dismissible={!mutation.isPending}
      footer={
        <>
          <Button variant="secondary" onClick={defer} disabled={mutation.isPending}>
            나중에
          </Button>
          <Button onClick={agree} isLoading={mutation.isPending}>
            동의하고 계속하기
          </Button>
        </>
      }
    >
      <p>
        계속 이용하려면 바뀐{' '}
        <Link to="/terms/service" className={LINK}>
          이용약관
        </Link>
        과{' '}
        <Link to="/terms/privacy" className={LINK}>
          개인정보처리방침
        </Link>
        을 확인하고 필수 항목(이용약관, 개인정보 수집·이용, 만 14세 이상)에 다시 동의해 주세요.
      </p>
      <p className="mt-2 text-caption text-ink-muted">{TERMS_DEFER_NOTICE}</p>
      {mutation.isError && (
        <p role="alert" className="mt-2 text-caption text-danger">
          {getErrorMessage(mutation.error)}
        </p>
      )}
    </Modal>
  );
}
