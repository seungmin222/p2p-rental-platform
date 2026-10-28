import { useState } from 'react';

import BottomActionBar from '../app/layout/BottomActionBar';
import PageHeader from '../app/layout/PageHeader';
import { formatDailyPrice, formatDeposit } from '../shared/lib/format';
import { Button } from '../shared/ui';

const LAYOUTS = {
  row: '가로(Detail.html)',
  stack: '세로 쌓기(Booking.html)',
};

/** 개발 서버 전용(/dev/ui/action-bar, 하단 탭 숨김). BottomActionBar의 두 배치와 본문 길이별 동작을 확인한다. */
export default function ActionBarPreviewPage() {
  const [layout, setLayout] = useState('row');
  const [isLong, setIsLong] = useState(true);

  return (
    <>
      <PageHeader title="하단 고정 바 미리보기" />

      <div className="flex flex-wrap gap-2 py-4">
        {Object.entries(LAYOUTS).map(([key, label]) => (
          <Button
            key={key}
            size="sm"
            variant={layout === key ? 'primary' : 'secondary'}
            onClick={() => setLayout(key)}
          >
            {label}
          </Button>
        ))}
        <Button size="sm" variant="secondary" onClick={() => setIsLong((value) => !value)}>
          본문 {isLong ? '짧게' : '길게'}
        </Button>
      </div>

      <p className="text-caption text-ink-muted">
        본문이 길면 스크롤해도 바가 화면 아래에 붙어 있고, 맨 끝까지 내리면 마지막 줄이 바 위에
        보여야 합니다. 본문이 짧아도 바는 화면 맨 아래에 있어야 합니다.
      </p>
      {isLong &&
        Array.from({ length: 30 }, (_, index) => (
          <p key={index} className="border-b border-divider py-3">
            본문 {index + 1}번째 줄
          </p>
        ))}
      <p className="py-3 font-semibold">본문 마지막 줄</p>

      {layout === 'row' ? (
        <BottomActionBar>
          <div className="min-w-0 flex-1">
            <p className="text-[17px] font-bold">{formatDailyPrice(15000)}</p>
            <p className="text-xs font-semibold text-accent-strong">{formatDeposit(50000)}</p>
          </div>
          <Button variant="outline" className="px-3.5">
            채팅하기
          </Button>
          <Button>예약하기</Button>
        </BottomActionBar>
      ) : (
        <BottomActionBar className="flex flex-col gap-2">
          <p className="text-center text-xs text-ink-muted">
            예약 후 <b className="text-danger">15분 안에</b> 결제하지 않으면 자동 취소돼요
          </p>
          <Button fullWidth className="min-h-13 text-base font-bold">
            95,000원 결제하기
          </Button>
        </BottomActionBar>
      )}
    </>
  );
}
