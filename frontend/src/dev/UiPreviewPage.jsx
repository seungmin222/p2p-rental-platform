import { useState } from 'react';

import FloatingActionButton from '../app/layout/FloatingActionButton';
import PageHeader from '../app/layout/PageHeader';
import { RENTAL_STATUS } from '../features/rental/constants';
import { formatDailyPrice, formatDeposit, formatElapsed } from '../shared/lib/format';
import {
  Badge,
  BottomSheet,
  Button,
  ButtonLink,
  ConfirmModal,
  EmptyState,
  ErrorState,
  Modal,
  Skeleton,
  Spinner,
  toast,
} from '../shared/ui';

function Section({ title, children }) {
  return (
    <section className="border-b border-divider py-6">
      <h2 className="mb-3 text-caption font-semibold text-ink-muted">{title}</h2>
      <div className="flex flex-wrap items-center gap-2">{children}</div>
    </section>
  );
}

/** 개발 서버 전용(/dev/ui). 공통 컴포넌트의 variant·상태를 한 화면에서 확인한다. */
export default function UiPreviewPage() {
  const [openDialog, setOpenDialog] = useState(null);
  const [isConfirming, setIsConfirming] = useState(false);
  const [showFab, setShowFab] = useState(false);

  function closeDialog() {
    setOpenDialog(null);
  }

  function handleConfirm() {
    setIsConfirming(true);
    setTimeout(() => {
      setIsConfirming(false);
      closeDialog();
      toast.show({ message: '취소했어요' });
    }, 1500);
  }

  return (
    <>
      <PageHeader
        title="UI 미리보기"
        action={
          <Button
            variant="secondary"
            size="sm"
            onClick={() => toast.show({ message: '헤더 액션' })}
          >
            액션
          </Button>
        }
      />

      <Section title="Typography">
        <div className="flex w-full flex-col gap-2">
          <span className="text-title">4인용 거실형 텐트</span>
          <span className="text-heading">대여 캘린더</span>
          <span className="text-body">주말 캠핑용으로 두 번 사용했어요.</span>
          <span className="text-price">{formatDailyPrice(15000)}</span>
          <span className="text-caption text-ink-muted">연수동 · 3분 전</span>
        </div>
      </Section>

      <Section title="Button (시안 5가지)">
        <Button>예약하기</Button>
        <Button variant="outline">채팅하기</Button>
        <Button variant="secondary">취소</Button>
        <Button variant="danger">이상 있음</Button>
        <Button disabled>비활성</Button>
      </Section>

      <Section title="Button 상태·크기">
        <Button isLoading>저장 중</Button>
        <Button variant="outline" isLoading>
          저장 중
        </Button>
        <Button size="sm">작은 버튼</Button>
        <Button variant="secondary" size="sm">
          작은 보조
        </Button>
        <ButtonLink to="/" variant="outline">
          링크 버튼
        </ButtonLink>
        <Button fullWidth>전체 폭</Button>
      </Section>

      <Section title="Badge (RENTAL_STATUS)">
        {Object.entries(RENTAL_STATUS).map(([code, { label, tone }]) => (
          <Badge key={code} tone={tone}>
            {label}
          </Badge>
        ))}
      </Section>

      <Section title="포맷">
        <span className="text-price">{formatDailyPrice(12000)}</span>
        <span className="text-xs font-semibold text-accent-strong">{formatDeposit(50000)}</span>
        <span className="text-xs font-semibold text-accent-strong">{formatDeposit(0)}</span>
        <span className="text-caption text-ink-muted">
          {formatElapsed('2026-10-07T15:27:00', new Date('2026-10-07T15:30:00+09:00'))}
        </span>
      </Section>

      <Section title="Spinner · Skeleton">
        <Spinner label="불러오는 중" />
        <Spinner size="lg" label="불러오는 중" className="text-primary" />
        <div className="flex w-full gap-3.5">
          <Skeleton className="size-26 rounded-[10px]" />
          <div className="flex flex-1 flex-col gap-2">
            <Skeleton className="h-4 w-3/4" />
            <Skeleton className="h-4 w-1/2" />
            <Skeleton className="h-4 w-1/3" />
          </div>
        </div>
      </Section>

      <Section title="EmptyState · ErrorState">
        <div className="w-full rounded-xl border border-line">
          <EmptyState
            title="빌린 물건이 없어요"
            description="동네 이웃의 물건을 둘러보세요."
            action={<ButtonLink to="/">홈으로</ButtonLink>}
          />
        </div>
        <div className="w-full rounded-xl border border-line">
          <ErrorState
            message="네트워크 연결을 확인해 주세요."
            onRetry={() => toast.show({ message: '다시 시도' })}
          />
        </div>
      </Section>

      <Section title="Toast">
        <Button variant="secondary" onClick={() => toast.show({ message: '저장했어요' })}>
          기본 토스트
        </Button>
        <Button
          variant="secondary"
          onClick={() => toast.show({ message: '이미 예약된 기간입니다', tone: 'danger' })}
        >
          위험 토스트
        </Button>
      </Section>

      <Section title="Modal · BottomSheet">
        <Button variant="secondary" onClick={() => setOpenDialog('modal')}>
          모달
        </Button>
        <Button variant="secondary" onClick={() => setOpenDialog('confirm')}>
          확인 모달
        </Button>
        <Button variant="secondary" onClick={() => setOpenDialog('sheet')}>
          바텀시트
        </Button>
      </Section>

      <Section title="하단 고정 바 · (+) 버튼">
        <ButtonLink to="/dev/ui/action-bar" variant="secondary">
          하단 고정 바 미리보기
        </ButtonLink>
        <Button variant="secondary" onClick={() => setShowFab((value) => !value)}>
          (+) 버튼 {showFab ? '끄기' : '켜기'}
        </Button>
      </Section>

      <div className="h-[60vh]" aria-hidden="true" />

      <Modal
        open={openDialog === 'modal'}
        onClose={closeDialog}
        title="모달 제목"
        footer={<Button onClick={closeDialog}>확인</Button>}
      >
        Esc·배경 클릭·닫기 버튼으로 닫힙니다. 닫힌 뒤 포커스는 연 버튼으로 돌아갑니다. 이 글자를
        드래그해 배경에서 놓아도 닫히지 않아야 합니다.
        <div className="mt-3">
          <Button variant="secondary" onClick={() => setOpenDialog('sheet')}>
            바텀시트로 바꾸기
          </Button>
        </div>
      </Modal>

      <ConfirmModal
        open={openDialog === 'confirm'}
        onClose={closeDialog}
        onConfirm={handleConfirm}
        title="예약을 취소할까요?"
        confirmLabel="예약 취소"
        danger
        isLoading={isConfirming}
      >
        처리 중에는 닫히지 않습니다.
      </ConfirmModal>

      <BottomSheet
        open={openDialog === 'sheet'}
        onClose={closeDialog}
        title="정렬"
        footer={
          <Button fullWidth onClick={closeDialog}>
            적용
          </Button>
        }
      >
        <ul className="flex flex-col">
          {['최신순', '가격 낮은순', '가격 높은순'].map((label) => (
            <li key={label} className="flex min-h-11 items-center">
              {label}
            </li>
          ))}
        </ul>
      </BottomSheet>

      {showFab && <FloatingActionButton to="/dev/ui" label="물품 등록" />}
    </>
  );
}
