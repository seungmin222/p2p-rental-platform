import PageHeader from '../../../app/layout/PageHeader';

/** 로그인·가입 화면 틀. AppLayout 밖(하단 탭 없음)이라 폭·여백을 여기서 잡는다 */
export function AuthPageLayout({ title, children }) {
  return (
    <div className="mx-auto flex min-h-dvh w-full max-w-md flex-col px-4 pb-10">
      <PageHeader title={title} />
      <main className="flex flex-col pt-6">{children}</main>
    </div>
  );
}

/** 필드에 붙이지 못한 제출 오류(root.server). 로그인 불일치처럼 폼 전체에 대한 문구 */
export function FormRootError({ message }) {
  if (!message) {
    return null;
  }
  return (
    <p role="alert" className="text-caption text-danger">
      {message}
    </p>
  );
}
