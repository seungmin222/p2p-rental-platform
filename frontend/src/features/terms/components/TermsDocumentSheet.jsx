import { isInitialLoadError } from '../../../shared/lib/queryState';
import { BottomSheet, Button, ErrorState, Skeleton } from '../../../shared/ui';
import { findTermsDocumentByType } from '../constants';
import { getErrorMessage } from '../errorMessages';
import { useTermsDocuments } from '../queries';
import TermsDocumentView from './TermsDocumentView';

/** 가입 화면 "보기": 문서 하나를 바텀시트로. 열릴 때만 문서를 부른다(documentType이 null이면 닫힘) */
export default function TermsDocumentSheet({ documentType, onClose }) {
  const open = documentType != null;
  const query = useTermsDocuments({ enabled: open });
  const meta = findTermsDocumentByType(documentType);
  const document = query.data?.find((item) => item.type === documentType);

  let body;
  if (isInitialLoadError(query)) {
    body = <ErrorState message={getErrorMessage(query.error)} onRetry={query.refetch} />;
  } else if (!document) {
    body = (
      <div aria-busy="true" className="flex flex-col gap-2 py-2">
        <Skeleton className="h-4 w-1/2" />
        <Skeleton className="h-4 w-full" />
        <Skeleton className="h-4 w-5/6" />
      </div>
    );
  } else {
    body = <TermsDocumentView document={document} hideTitle />;
  }

  return (
    <BottomSheet
      open={open}
      onClose={onClose}
      title={document?.title ?? meta?.title ?? ''}
      footer={
        <Button fullWidth onClick={onClose}>
          확인
        </Button>
      }
    >
      <div className="max-h-[55dvh] overflow-y-auto pr-1">{body}</div>
    </BottomSheet>
  );
}
