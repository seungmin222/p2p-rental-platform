import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router';

import { getSafeRedirect, withRedirect } from '../app/loginPath';
import { AuthPageLayout, FormRootError } from '../features/auth/components/AuthFormParts';
import { EMAIL_MAX, NICKNAME_LENGTH, PASSWORD_LENGTH } from '../features/auth/constants';
import { getErrorMessage } from '../features/auth/errorMessages';
import { useSignup } from '../features/auth/queries';
import { useAuthStore } from '../shared/api';
import { useSubmitErrorHandler } from '../shared/hooks/useSubmitErrorHandler';
import { validateEmail, validateNickname, validatePassword } from '../shared/lib/validators';
import { Button, TextField, toast } from '../shared/ui';

const FIELD_MAP = { email: 'email', password: 'password', nickname: 'nickname' };
const CODE_FIELDS = {
  MEMBER_EMAIL_DUPLICATED: 'email',
  MEMBER_NICKNAME_DUPLICATED: 'nickname',
};

function SignupForm({ redirect }) {
  const mutation = useSignup();
  const navigate = useNavigate();
  const {
    register,
    handleSubmit,
    setError,
    setFocus,
    formState: { errors },
  } = useForm({
    defaultValues: { email: '', password: '', passwordConfirm: '', nickname: '' },
    shouldFocusError: true,
  });
  const handleError = useSubmitErrorHandler({ setError, setFocus }, mutation.isPending, {
    fieldMap: FIELD_MAP,
    codeFields: CODE_FIELDS,
    getMessage: getErrorMessage,
  });

  function onSubmit(values) {
    const email = values.email.trim();
    // 비밀번호 확인은 프론트 전용이라 보내지 않는다
    mutation.mutate(
      { email, password: values.password, nickname: values.nickname },
      {
        onSuccess: () => {
          toast.show({ message: '가입이 완료됐어요. 로그인해 주세요' });
          // 이메일은 주소(기록)에 남기지 않게 state로 넘긴다
          navigate(withRedirect('/login', redirect), { replace: true, state: { email } });
        },
        onError: handleError,
      },
    );
  }

  return (
    <>
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
          <TextField
            label="비밀번호"
            required
            type="password"
            autoComplete="new-password"
            hint={`${PASSWORD_LENGTH.min}~${PASSWORD_LENGTH.max}자, 영문·숫자·기호를 각각 1개 이상`}
            error={errors.password?.message}
            {...register('password', {
              validate: (value) => validatePassword(value, PASSWORD_LENGTH),
            })}
          />
          <TextField
            label="비밀번호 확인"
            required
            type="password"
            autoComplete="new-password"
            error={errors.passwordConfirm?.message}
            {...register('passwordConfirm', {
              validate: (value, formValues) =>
                value === formValues.password || '비밀번호가 서로 달라요',
            })}
          />
          <TextField
            label="닉네임"
            required
            autoComplete="nickname"
            hint={`${NICKNAME_LENGTH.min}~${NICKNAME_LENGTH.max}자`}
            error={errors.nickname?.message}
            {...register('nickname', {
              validate: (value) => validateNickname(value, NICKNAME_LENGTH),
            })}
          />
        </fieldset>
        <FormRootError message={errors.root?.server?.message} />
        <Button type="submit" fullWidth isLoading={mutation.isPending}>
          가입하기
        </Button>
      </form>
      <p className="mt-6 flex items-center justify-center gap-1 text-caption text-ink-muted">
        이미 계정이 있나요?
        <Link
          to={withRedirect('/login', redirect)}
          className="inline-flex min-h-11 items-center px-1 font-semibold text-primary focus-visible:outline-2 focus-visible:outline-primary"
        >
          로그인
        </Link>
      </p>
    </>
  );
}

/** SCR-01 회원가입 (REQ-001). 약관 동의(REQ-037)는 2순위라 없다 */
export default function SignupPage() {
  const [searchParams] = useSearchParams();
  const redirect = getSafeRedirect(searchParams.get('redirect'));
  // LoginPage와 같이 들어올 때 한 번만 본다
  const [isAlreadyLoggedIn] = useState(() => useAuthStore.getState().accessToken !== null);

  if (isAlreadyLoggedIn) {
    return <Navigate to={redirect ?? '/'} replace />;
  }
  return (
    <AuthPageLayout title="회원가입">
      <SignupForm redirect={redirect} />
    </AuthPageLayout>
  );
}
