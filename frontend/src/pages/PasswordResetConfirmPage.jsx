import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useLocation, useNavigate, useSearchParams } from 'react-router';

import { AuthPageLayout, FormRootError } from '../features/auth/components/AuthFormParts';
import { PASSWORD_LENGTH } from '../features/auth/constants';
import { getErrorMessage } from '../features/auth/errorMessages';
import { isResetTokenError, readResetToken } from '../features/auth/passwordReset';
import { usePasswordResetConfirm } from '../features/auth/queries';
import { useSubmitErrorHandler } from '../shared/hooks/useSubmitErrorHandler';
import { validatePassword } from '../shared/lib/validators';
import { Button, ButtonLink, TextField, toast } from '../shared/ui';

const FIELD_MAP = { newPassword: 'newPassword' };

function InvalidLink({ message }) {
  return (
    <div className="flex flex-col gap-5">
      <div role="alert" className="rounded-xl bg-surface px-4 py-4 text-body">
        {message ?? '링크가 올바르지 않거나 만료됐어요. 재설정 메일을 다시 요청해 주세요'}
      </div>
      <ButtonLink to="/password-reset/request" fullWidth replace>
        재설정 메일 다시 요청
      </ButtonLink>
      <ButtonLink to="/login" variant="secondary" fullWidth replace>
        로그인으로
      </ButtonLink>
    </div>
  );
}

function NewPasswordForm({ token, onTokenInvalid }) {
  const mutation = usePasswordResetConfirm();
  const navigate = useNavigate();
  const {
    register,
    handleSubmit,
    setError,
    setFocus,
    formState: { errors },
  } = useForm({
    defaultValues: { newPassword: '', newPasswordConfirm: '' },
    shouldFocusError: true,
  });
  const handleError = useSubmitErrorHandler({ setError, setFocus }, mutation.isPending, {
    fieldMap: FIELD_MAP,
    getMessage: getErrorMessage,
  });

  function onSubmit(values) {
    // 비밀번호 확인은 프론트 전용이라 보내지 않는다
    mutation.mutate(
      { token, newPassword: values.newPassword },
      {
        onSuccess: () => {
          toast.show({ message: '비밀번호를 바꿨어요. 새 비밀번호로 로그인해 주세요' });
          navigate('/login', { replace: true });
        },
        onError: (error) => {
          if (isResetTokenError(error)) {
            onTokenInvalid(getErrorMessage({ code: 'AUTH_RESET_TOKEN_INVALID' }));
            return;
          }
          handleError(error);
        },
      },
    );
  }

  return (
    <>
      <p className="mb-5 text-body text-ink-muted">
        새 비밀번호를 정해 주세요. 바꾸면 모든 기기에서 로그아웃돼요.
      </p>
      <form noValidate onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-5">
        <fieldset disabled={mutation.isPending} className="flex flex-col gap-4">
          <TextField
            label="새 비밀번호"
            required
            type="password"
            autoComplete="new-password"
            hint={`${PASSWORD_LENGTH.min}~${PASSWORD_LENGTH.max}자, 영문·숫자·기호를 각각 1개 이상`}
            error={errors.newPassword?.message}
            {...register('newPassword', {
              validate: (value) => validatePassword(value, PASSWORD_LENGTH),
            })}
          />
          <TextField
            label="새 비밀번호 확인"
            required
            type="password"
            autoComplete="new-password"
            error={errors.newPasswordConfirm?.message}
            {...register('newPasswordConfirm', {
              validate: (value, formValues) =>
                value === formValues.newPassword || '비밀번호가 서로 달라요',
            })}
          />
        </fieldset>
        <FormRootError message={errors.root?.server?.message} />
        <Button type="submit" fullWidth isLoading={mutation.isPending}>
          비밀번호 바꾸기
        </Button>
      </form>
    </>
  );
}

/**
 * SCR-01 새 비밀번호 설정(REQ-036). 메일 링크 /password-reset?token=.
 * 토큰은 들어올 때 한 번 읽어 화면 상태에만 두고 주소에서 지운다(기록·공유 화면에 남지 않게).
 * 그래서 새로고침하면 토큰이 없어 "다시 요청" 안내가 보인다. 형식이 틀린 토큰은 요청하지 않는다.
 */
export default function PasswordResetConfirmPage() {
  const [searchParams] = useSearchParams();
  const location = useLocation();
  const navigate = useNavigate();
  const [token] = useState(() => readResetToken(searchParams.get('token')));
  const [invalidMessage, setInvalidMessage] = useState(null);
  const hasTokenInUrl = searchParams.has('token');

  useEffect(() => {
    if (hasTokenInUrl) {
      navigate(location.pathname, { replace: true });
    }
  }, [hasTokenInUrl, location.pathname, navigate]);

  return (
    <AuthPageLayout title="비밀번호 재설정">
      {token && invalidMessage === null ? (
        <NewPasswordForm token={token} onTokenInvalid={setInvalidMessage} />
      ) : (
        <InvalidLink message={invalidMessage} />
      )}
    </AuthPageLayout>
  );
}
