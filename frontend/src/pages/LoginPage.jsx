import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, Navigate, useLocation, useNavigate, useSearchParams } from 'react-router';

import { getSafeRedirect, withRedirect } from '../app/loginPath';
import { AuthPageLayout, FormRootError } from '../features/auth/components/AuthFormParts';
import { getErrorMessage } from '../features/auth/errorMessages';
import { fetchPostLoginDestination, useLogin } from '../features/auth/queries';
import { useAuthStore } from '../shared/api';
import { useSubmitErrorHandler } from '../shared/hooks/useSubmitErrorHandler';
import { validateRequired } from '../shared/lib/validators';
import { Button, TextField } from '../shared/ui';

const FIELD_MAP = { email: 'email', password: 'password' };

function LoginForm({ redirect, defaultEmail }) {
  const mutation = useLogin();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  // 로그인 성공 뒤 갈 곳(내 정보 조회)을 정하는 동안에도 버튼을 막는다
  const [isMovingOn, setIsMovingOn] = useState(false);
  const {
    register,
    handleSubmit,
    setError,
    setFocus,
    formState: { errors },
  } = useForm({ defaultValues: { email: defaultEmail, password: '' }, shouldFocusError: true });
  const handleError = useSubmitErrorHandler({ setError, setFocus }, mutation.isPending, {
    fieldMap: FIELD_MAP,
    getMessage: getErrorMessage,
  });
  const isBusy = mutation.isPending || isMovingOn;

  // 가입 직후(이메일이 채워짐)면 비밀번호 칸부터
  useEffect(() => {
    if (defaultEmail) {
      setFocus('password');
    }
  }, [defaultEmail, setFocus]);

  function onSubmit(values) {
    mutation.mutate(
      { email: values.email.trim(), password: values.password },
      {
        onSuccess: async () => {
          if (redirect) {
            navigate(redirect, { replace: true });
            return;
          }
          setIsMovingOn(true);
          const destination = await fetchPostLoginDestination(queryClient);
          navigate(destination.to, { replace: true, state: destination.state });
        },
        onError: handleError,
      },
    );
  }

  return (
    <>
      {/* 로그인은 빈 값만 검사한다(백엔드 AuthLoginRequest도 형식 검사 없음, 규칙이 바뀌기 전 계정도 로그인해야 함) */}
      <form noValidate onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-5">
        <fieldset disabled={isBusy} className="flex flex-col gap-4">
          <TextField
            label="이메일"
            type="email"
            autoComplete="email"
            inputMode="email"
            autoCapitalize="none"
            spellCheck={false}
            error={errors.email?.message}
            {...register('email', {
              validate: (value) => validateRequired(value, '이메일을 입력해 주세요'),
            })}
          />
          <TextField
            label="비밀번호"
            type="password"
            autoComplete="current-password"
            error={errors.password?.message}
            {...register('password', {
              validate: (value) => validateRequired(value, '비밀번호를 입력해 주세요'),
            })}
          />
        </fieldset>
        <FormRootError message={errors.root?.server?.message} />
        <Button type="submit" fullWidth isLoading={isBusy}>
          로그인
        </Button>
      </form>
      <p className="mt-6 flex items-center justify-center gap-1 text-caption text-ink-muted">
        아직 회원이 아니신가요?
        <Link
          to={withRedirect('/signup', redirect)}
          className="inline-flex min-h-11 items-center px-1 font-semibold text-primary focus-visible:outline-2 focus-visible:outline-primary"
        >
          회원가입
        </Link>
      </p>
    </>
  );
}

/** SCR-01 로그인 (REQ-002) */
export default function LoginPage() {
  const [searchParams] = useSearchParams();
  const location = useLocation();
  const redirect = getSafeRedirect(searchParams.get('redirect'));
  // 들어올 때 한 번만 본다. 제출 성공으로 토큰이 생겨 다시 그려질 때 여기서 '/'로 보내면
  // 폼이 정한 곳(대표 동네가 없으면 /regions)보다 먼저 이동해 버리기 때문
  const [isAlreadyLoggedIn] = useState(() => useAuthStore.getState().accessToken !== null);

  if (isAlreadyLoggedIn) {
    return <Navigate to={redirect ?? '/'} replace />;
  }
  return (
    <AuthPageLayout title="로그인">
      <LoginForm redirect={redirect} defaultEmail={location.state?.email ?? ''} />
    </AuthPageLayout>
  );
}
