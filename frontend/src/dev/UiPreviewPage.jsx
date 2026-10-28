import { useState } from 'react';

import FloatingActionButton from '../app/layout/FloatingActionButton';
import PageHeader from '../app/layout/PageHeader';
import { ITEM_IMAGES } from '../features/item/constants';
import {
  INSPECTION_IMAGES,
  RENTAL_DAY_STATUS,
  RENTAL_PERIOD,
  RENTAL_STATUS,
} from '../features/rental/constants';
import { addDays, compareDate } from '../shared/lib/date';
import { formatDailyPrice, formatDeposit, formatElapsed } from '../shared/lib/format';
import {
  Badge,
  BottomSheet,
  Button,
  ButtonLink,
  Calendar,
  Chip,
  ConfirmModal,
  EmptyState,
  ErrorState,
  ImageUploader,
  Modal,
  Skeleton,
  Spinner,
  TextField,
  toast,
} from '../shared/ui';

// 가짜 캘린더 응답 두 달(10·11월)을 합친 days. 호출부가 캐시에서 합쳐 넘기는 모양.
const BOOKED_DATES = ['2026-10-03', '2026-10-04', '2026-10-14'];
const FAKE_DAYS = (() => {
  const days = [];
  for (let d = '2026-10-01'; compareDate(d, '2026-11-30') <= 0; d = addDays(d, 1)) {
    let status = 'AVAILABLE';
    if (BOOKED_DATES.includes(d)) {
      status = 'BOOKED';
    } else if (compareDate(d, '2026-10-02') <= 0 || compareDate(d, '2026-11-26') >= 0) {
      status = 'UNAVAILABLE'; // 지난 날짜·가능 기간 밖
    }
    days.push({ date: d, status });
  }
  return days;
})();

// 서버에서 받은 기존 이미지 흉내(물품 수정 화면). 색 면 SVG.
function fakeServerImage(id, color) {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 4 4"><rect width="4" height="4" fill="${color}"/></svg>`;
  return { id, url: `data:image/svg+xml,${encodeURIComponent(svg)}` };
}

const CATEGORIES = ['전체', '캠핑', '공구', '촬영 장비', '유아용품', '스포츠·레저'];

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
  const [calendarMonth, setCalendarMonth] = useState('2026-10');
  const [period, setPeriod] = useState(null);
  const [isCalendarLoading, setIsCalendarLoading] = useState(false);
  const [itemImages, setItemImages] = useState(() => [
    fakeServerImage(101, '#c9e2d6'),
    fakeServerImage(102, '#f0dcbf'),
  ]);
  const [handoverImages, setHandoverImages] = useState([]);
  const [sheetImages, setSheetImages] = useState([]);
  const [price, setPrice] = useState('15,000');
  const [memo, setMemo] = useState('');
  const [category, setCategory] = useState('전체');

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
        <ButtonLink to="/" disabled>
          비활성 링크
        </ButtonLink>
        <Badge tone="unknown">없는 tone(gray)</Badge>
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

      <Section title="Calendar (10~11월 days 합침 · 10/14 예약됨 · 11/26~ 예약 불가)">
        <div className="flex w-full max-w-sm flex-col gap-3">
          <p className="text-caption text-ink-muted">
            확인: 10/12→10/16 중간 예약됨 거부 · 10/21→11/20 31일 거부(11/19는 30일 허용) ·
            10/30→11/2 달 넘김 · 10/3 누르면 예약됨 안내 · 같은 날 두 번 = 1일
          </p>
          <Calendar
            title={<h3 className="text-price">기간 선택</h3>}
            month={calendarMonth}
            days={FAKE_DAYS}
            value={period}
            onChange={setPeriod}
            onMonthChange={setCalendarMonth}
            minDays={RENTAL_PERIOD.MIN_DAYS}
            maxDays={RENTAL_PERIOD.MAX_DAYS}
            statusLabels={RENTAL_DAY_STATUS}
            minMonth="2026-10"
            maxMonth="2026-11"
            isLoading={isCalendarLoading}
          />
          <div className="flex flex-wrap gap-2">
            <Button
              variant="secondary"
              size="sm"
              onClick={() => setIsCalendarLoading((value) => !value)}
            >
              로딩 {isCalendarLoading ? '끄기' : '켜기'}
            </Button>
            <Button variant="secondary" size="sm" onClick={() => setPeriod(null)}>
              선택 초기화
            </Button>
          </div>
          <code className="text-xs text-ink-muted">value: {JSON.stringify(period)}</code>
        </div>
      </Section>

      <Section title="ImageUploader">
        <div className="flex w-full max-w-md flex-col gap-6">
          <ImageUploader
            label="물품 사진 (기존 2장 + 새 사진)"
            hint="첫 장이 대표 사진이에요. JPG·PNG, 장당 10MB"
            value={itemImages}
            onChange={setItemImages}
            maxCount={ITEM_IMAGES.MAX_COUNT}
            maxFileSize={ITEM_IMAGES.MAX_FILE_SIZE}
            accept={ITEM_IMAGES.ACCEPT}
            onError={(rejected) => console.info('ImageUploader onError', rejected)}
          />
          <code className="text-xs break-all text-ink-muted">
            순서: {JSON.stringify(itemImages.map((image) => image.id))}
          </code>
          <ImageUploader
            label="인계 사진"
            value={handoverImages}
            onChange={setHandoverImages}
            maxCount={INSPECTION_IMAGES.MAX_COUNT}
            maxFileSize={INSPECTION_IMAGES.MAX_FILE_SIZE}
            accept={INSPECTION_IMAGES.ACCEPT}
          />
          <Button variant="secondary" onClick={() => setOpenDialog('upload-sheet')}>
            바텀시트 안의 인계 사진
          </Button>
        </div>
      </Section>

      <Section title="TextField">
        <div className="flex w-full max-w-xs flex-col gap-5">
          <TextField
            label="일 대여 가격"
            inputMode="numeric"
            value={price}
            onChange={(event) => setPrice(event.target.value)}
            hint="권장 보증금 300,000 ~ 500,000원 (캠핑 · 물품가 100만 원 기준)"
            required
          />
          <TextField label="물품 이름" defaultValue="텐" error="2자 이상 입력해 주세요" required />
          {/* 글자 수 예시: policies.md inspection.handover-memo.max */}
          <TextField
            label="인계 메모"
            multiline
            maxLength={300}
            value={memo}
            onChange={(event) => setMemo(event.target.value)}
            placeholder="물품 상태를 적어 주세요"
          />
          <TextField label="비제어 + 글자 수" maxLength={20} defaultValue="기본값" />
          <TextField label="비활성" defaultValue="수정할 수 없어요" disabled />
        </div>
      </Section>

      <Section title="Chip">
        <div className="flex w-full flex-wrap gap-x-2 gap-y-2.5">
          {CATEGORIES.map((name) => (
            <Chip key={name} selected={category === name} onClick={() => setCategory(name)}>
              {name}
            </Chip>
          ))}
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

      <BottomSheet
        open={openDialog === 'upload-sheet'}
        onClose={closeDialog}
        title="인계 확인"
        footer={
          <Button fullWidth disabled={sheetImages.length === 0} onClick={closeDialog}>
            인계 완료
          </Button>
        }
      >
        <ImageUploader
          label="인계 사진"
          hint="HEIC나 10MB 넘는 사진을 골라 시트 안에 안내가 보이는지 확인"
          value={sheetImages}
          onChange={setSheetImages}
          maxCount={INSPECTION_IMAGES.MAX_COUNT}
          maxFileSize={INSPECTION_IMAGES.MAX_FILE_SIZE}
          accept={INSPECTION_IMAGES.ACCEPT}
        />
      </BottomSheet>

      {showFab && <FloatingActionButton to="/dev/ui" label="물품 등록" />}
    </>
  );
}
