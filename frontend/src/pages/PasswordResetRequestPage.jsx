import { useForm } from 'react-hook-form';
import { Link, useLocation } from 'react-router';

import { AuthPageLayout, FormRootError } from '../features/auth/components/AuthFormParts';
import { EMAIL_MAX, PASSWORD_RESET } from '../features/auth/constants';
import { getErrorMessage } from '../features/auth/errorMessages';
import { usePasswordResetRequest } from '../features/auth/queries';
import { useSubmitErrorHandler } from '../shared/hooks/useSubmitErrorHandler';
import { validateEmail } from '../shared/lib/validators';
import { Button, ButtonLink, TextField } from '../shared/ui';

const FIELD_MAP = { email: 'email' };

/** 요청 뒤 안내. 응답이 가입 여부와 무관하게 같아(백엔드가 백그라운드로 보냄) 문구도 하나다 */
function SentNotice({ email, onRetry }) {
  return (
    <div className="flex flex-col gap-5">
      <div role="status" className="flex flex-col gap-2 rounded-xl bg-surface px-4 py-4 text-body">
        <p>
          <b className="break-all">{email}</b>이(가) 가입된 이메일이라면 비밀번호 재설정 링크를
          보냈어요.
        </p>
        <p className="text-caption text-ink-muted">
          링크는 {PASSWORD_RESET.TOKEN_TTL_MINUTES}분 동안 한 번만 쓸 수 있어요. 메일이 오지 않으면
          스팸함을 확인하거나 잠시 뒤 다시 요청해 주세요(같은 이메일은{' '}
          {PASSWORD_RESET.RATE_LIMIT_MINUTES}분에 한 번 보내요).
        </p>
      </div>
      <ButtonLink to="/login" fullWidth replace>
        로그인으로
      </ButtonLink>
      <Button variant="secondary" fullWidth onClick={onRetry}>
        다른 이메일로 다시 요청
      </Button>
    </div>
  );
}

/** SCR-01 비밀번호 재설정 메일 요청(REQ-036). 로그인 여부와 상관없이 쓸 수 있다 */
export default function PasswordResetRequestPage() {
  const location = useLocation();
  const mutation = usePasswordResetRequest();
  const {
    register,
    handleSubmit,
    setError,
    setFocus,
    reset,
    formState: { errors },
  } = useForm({
    defaultValues: { email: location.state?.email ?? '' },
    shouldFocusError: true,
  });
  const handleError = useSubmitErrorHandler({ setError, setFocus }, mutation.isPending, {
    fieldMap: FIELD_MAP,
    getMessage: getErrorMessage,
  });

  function onSubmit(values) {
    mutation.mutate({ email: values.email.trim() }, { onError: handleError });
  }

  function retry() {
    mutation.reset();
    reset({ email: '' });
  }

  return (
    <AuthPageLayout title="비밀번호 찾기">
      {mutation.isSuccess ? (
        <SentNotice email={mutation.variables.email} onRetry={retry} />
      ) : (
        <>
          <p className="mb-5 text-body text-ink-muted">
            가입한 이메일을 입력하면 비밀번호를 다시 설정할 수 있는 링크를 보내 드려요.
          </p>
          <form noValidate onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-5">
            <fieldset disabled={mutation.isPending} className="flex flex-col gap-4">
              <TextField
                label="이메일"
                required
                type="email"
                autoComplete="email"
                inputMode="email"
                autoCapitalize="none"
                spellCheck={false}
                error={errors.email?.message}
                {...register('email', {
                  validate: (value) => validateEmail(value.trim(), { max: EMAIL_MAX }),
                })}
              />
            </fieldset>
            <FormRootError message={errors.root?.server?.message} />
            <Button type="submit" fullWidth isLoading={mutation.isPending}>
              재설정 링크 보내기
            </Button>
          </form>
          <p className="mt-6 flex justify-center text-caption">
            <Link
              to="/login"
              replace
              className="inline-flex min-h-11 items-center px-1 font-semibold text-primary focus-visible:outline-2 focus-visible:outline-primary"
            >
              로그인으로 돌아가기
            </Link>
          </p>
        </>
      )}
    </AuthPageLayout>
  );
}
