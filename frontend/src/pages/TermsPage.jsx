import { NavLink, useParams } from 'react-router';

import PageHeader from '../app/layout/PageHeader';
import TermsDocumentView from '../features/terms/components/TermsDocumentView';
import { findTermsDocumentBySlug, TERMS_DOCUMENTS } from '../features/terms/constants';
import { getErrorMessage } from '../features/terms/errorMessages';
import { useTermsDocuments } from '../features/terms/queries';
import { isInitialLoadError } from '../shared/lib/queryState';
import { ButtonLink, ErrorState, Skeleton } from '../shared/ui';

// 문서 이동은 링크라 Chip(button) 대신 같은 겉모양(34px + 터치 영역 44px)을 NavLink에 입힌다
function chipClassName({ isActive }) {
  return `relative inline-flex h-8.5 shrink-0 items-center rounded-full border px-3.5 text-caption whitespace-nowrap before:absolute before:inset-x-0 before:-inset-y-1.5 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary ${
    isActive
      ? 'border-primary bg-primary font-semibold text-white'
      : 'border-line bg-white font-medium text-ink hover:bg-surface'
  }`;
}

function DocumentSkeleton() {
  return (
    <div aria-busy="true" aria-label="불러오는 중" className="flex flex-col gap-3 py-2">
      <Skeleton className="h-6 w-1/3" />
      <Skeleton className="h-4 w-full" />
      <Skeleton className="h-4 w-5/6" />
      <Skeleton className="h-4 w-2/3" />
    </div>
  );
}

/**
 * SCR-15 약관·정책(REQ-037): 이용약관 · 개인정보처리방침 · 대여 금지 품목. 비회원도 볼 수 있다.
 * 문의·분쟁 접수(REQ-063)는 이 브랜치 범위 밖이다. 이 화면에서는 재동의 모달을 띄우지 않는다(termsPrompt.js).
 */
export default function TermsPage() {
  const { slug } = useParams();
  const meta = findTermsDocumentBySlug(slug);
  const query = useTermsDocuments({ enabled: meta != null });

  if (!meta) {
    return (
      <>
        <PageHeader title="약관·정책" />
        <ErrorState title="문서를 찾을 수 없어요" />
        <div className="flex justify-center">
          <ButtonLink to="/terms/service" variant="secondary" size="sm" replace>
            이용약관 보기
          </ButtonLink>
        </div>
      </>
    );
  }

  const document = query.data?.find((item) => item.type === meta.type);
  let body;
  if (isInitialLoadError(query)) {
    body = <ErrorState message={getErrorMessage(query.error)} onRetry={query.refetch} />;
  } else if (!document) {
    body = query.data ? <ErrorState title="문서를 찾을 수 없어요" /> : <DocumentSkeleton />;
  } else {
    body = <TermsDocumentView document={document} hideTitle />;
  }

  return (
    <>
      <PageHeader title={document?.title ?? meta.title} />
      <nav aria-label="약관·정책 문서" className="-mx-4 flex gap-2 overflow-x-auto px-4 py-3">
        {TERMS_DOCUMENTS.map((item) => (
          <NavLink key={item.slug} to={`/terms/${item.slug}`} replace className={chipClassName}>
            {item.title}
          </NavLink>
        ))}
      </nav>
      <div className="pb-8">{body}</div>
    </>
  );
}
