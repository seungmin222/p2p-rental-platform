import { Link } from 'react-router';

export default function NotFoundPage() {
  return (
    <section className="py-16 text-center">
      <h1 className="text-lg font-bold">페이지를 찾을 수 없습니다</h1>
      <Link
        to="/"
        className="mt-4 inline-flex min-h-11 items-center px-4 text-sm text-gray-700 underline"
      >
        홈으로
      </Link>
    </section>
  );
}
