import { useState } from 'react';
import { useSearchParams } from 'react-router';

import PageHeader from '../app/layout/PageHeader';
import HistoryRangeSheet from '../features/payment/components/HistoryRangeSheet';
import PaymentHistoryCard from '../features/payment/components/PaymentHistoryCard';
import SettlementCard from '../features/payment/components/SettlementCard';
import SettlementSummary from '../features/payment/components/SettlementSummary';
import {
  PAYMENT_HISTORY_DEFAULT_RANGE_MONTHS,
  PAYMENT_PAGE_TABS,
} from '../features/payment/constants';
import { getErrorMessage, isRangeError } from '../features/payment/errorMessages';
import {
  getRangeLabel,
  HISTORY_RANGE_ORDER_ERROR,
  PAYMENT_HISTORY_TYPES,
  readHistoryFilter,
  writeHistoryFilter,
} from '../features/payment/paymentHistory';
import { usePaymentHistory, useSettlements } from '../features/payment/queries';
import { PAYMENT_ENABLED } from '../features/rental/constants';
import { useRefetchErrorToast } from '../shared/hooks/useRefetchErrorToast';
import { useTabParam } from '../shared/hooks/useTabParam';
import { isInitialLoadError } from '../shared/lib/queryState';
import {
  Button,
  ButtonLink,
  Chip,
  EmptyState,
  ErrorState,
  InfiniteListFooter,
  Skeleton,
  Tabs,
} from '../shared/ui';

const DEFAULT_TAB = 'payments';
const TAB_ITEMS = Object.entries(PAYMENT_PAGE_TABS).map(([value, { label }]) => ({
  value,
  label,
}));
const TYPE_ITEMS = Object.entries(PAYMENT_HISTORY_TYPES);

function ListSkeleton() {
  return (
    <ul aria-busy="true" aria-label="불러오는 중">
      {[0, 1, 2, 3].map((key) => (
        <li key={key} className="flex gap-3.5 border-b border-divider py-3.5">
          <Skeleton className="size-16 rounded-[10px]" />
          <div className="flex flex-1 flex-col gap-2 py-1">
            <Skeleton className="h-4 w-2/3" />
            <Skeleton className="h-3 w-full" />
            <Skeleton className="h-3 w-1/3" />
          </div>
        </li>
      ))}
    </ul>
  );
}

function PaymentHistoryList({ filter, onResetRange }) {
  const query = usePaymentHistory(filter);
  // 이미 보던 목록은 재조회가 실패해도 그대로 둔다
  useRefetchErrorToast([query]);

  if (isInitialLoadError(query)) {
    // 기간 400은 다시 불러도 같으므로 기본 기간으로 돌린다
    return isRangeError(query.error) ? (
      <ErrorState
        message={HISTORY_RANGE_ORDER_ERROR}
        onRetry={onResetRange}
        retryLabel={`최근 ${PAYMENT_HISTORY_DEFAULT_RANGE_MONTHS}개월로 보기`}
      />
    ) : (
      <ErrorState message={getErrorMessage(query.error)} onRetry={query.refetch} />
    );
  }
  if (!query.data) {
    return <ListSkeleton />;
  }
  if (query.data.length === 0) {
    return <EmptyState title="결제 내역이 없어요" description="기간이나 구분을 바꿔 보세요" />;
  }
  return (
    <>
      <ul>
        {query.data.map((payment) => (
          <li key={payment.paymentId} className="border-b border-divider">
            <PaymentHistoryCard payment={payment} />
          </li>
        ))}
      </ul>
      <InfiniteListFooter query={query} />
    </>
  );
}

function PaymentsTab() {
  const [searchParams, setSearchParams] = useSearchParams();
  const filter = readHistoryFilter(searchParams);
  const [isRangeOpen, setRangeOpen] = useState(false);
  const typeDescription = PAYMENT_HISTORY_TYPES[filter.type].description;

  function updateFilter(next) {
    setSearchParams((params) => writeHistoryFilter(params, { ...filter, ...next }), {
      replace: true,
    });
  }

  return (
    <>
      <div
        role="group"
        aria-label="구분"
        className="-mx-4 mt-3 flex gap-2 overflow-x-auto px-4 py-1.5"
      >
        {TYPE_ITEMS.map(([value, { label }]) => (
          <Chip
            key={value}
            selected={filter.type === value}
            onClick={() => updateFilter({ type: value })}
          >
            {label}
          </Chip>
        ))}
      </div>
      <div className="mt-2 flex items-center justify-between gap-2 border-b border-divider pb-3">
        <div className="min-w-0">
          <p className="text-caption font-semibold">{getRangeLabel(filter)}</p>
          {typeDescription && <p className="text-xs text-ink-muted">{typeDescription}</p>}
        </div>
        <Button variant="secondary" size="sm" onClick={() => setRangeOpen(true)}>
          기간 변경
        </Button>
      </div>
      {isRangeOpen && (
        <HistoryRangeSheet
          open
          onClose={() => setRangeOpen(false)}
          range={filter}
          onApply={(range) => {
            setRangeOpen(false);
            updateFilter(range);
          }}
        />
      )}
      {/* 조건마다 새 목록(스켈레톤·빈 상태·오류가 조건별로) */}
      <PaymentHistoryList
        key={`${filter.type}:${filter.from}:${filter.to}`}
        filter={filter}
        onResetRange={() => updateFilter({ from: null, to: null })}
      />
    </>
  );
}

function SettlementsTab() {
  const query = useSettlements();
  useRefetchErrorToast([query]);

  if (isInitialLoadError(query)) {
    return <ErrorState message={getErrorMessage(query.error)} onRetry={query.refetch} />;
  }
  if (!query.data) {
    return (
      <>
        <Skeleton className="mt-4 h-24 w-full rounded-xl" />
        <ListSkeleton />
      </>
    );
  }
  return (
    <>
      <SettlementSummary totals={query.data.totals} />
      {query.data.items.length === 0 ? (
        <EmptyState
          title="정산 내역이 없어요"
          description="빌려준 물건의 거래가 끝나면 여기에 정산돼요"
        />
      ) : (
        <>
          <ul className="mt-2">
            {query.data.items.map((settlement) => (
              <li key={settlement.settlementId} className="border-b border-divider">
                <SettlementCard settlement={settlement} />
              </li>
            ))}
          </ul>
          <InfiniteListFooter query={query} />
        </>
      )}
    </>
  );
}

/**
 * SCR-12 결제·정산 내역(REQ-033 대여자 결제·보증금, REQ-039 제공자 정산). 탭은 URL ?tab=, 결제 내역 조건은
 * ?type=&from=&to=(대여 상세에서 뒤로 오면 같은 조건). 결제가 꺼져 있으면 API를 부르지 않고 안내만 보인다.
 */
export default function MyPaymentsPage() {
  const [tab, setTab] = useTabParam(PAYMENT_PAGE_TABS, DEFAULT_TAB);
  if (!PAYMENT_ENABLED) {
    return (
      <>
        <PageHeader title="결제·정산 내역" />
        <EmptyState
          title="결제 기능이 꺼져 있어요"
          description="결제를 쓰지 않는 동안에는 결제·정산 내역이 없어요"
          action={
            <ButtonLink to="/me" variant="secondary">
              나의 대여로
            </ButtonLink>
          }
        />
      </>
    );
  }
  return (
    <>
      <PageHeader title="결제·정산 내역" />
      <Tabs
        label="결제·정산 내역 탭"
        items={TAB_ITEMS}
        value={tab}
        onChange={setTab}
        listClassName="sticky top-14 z-10 -mx-4 bg-white px-4"
      >
        {tab === 'payments' ? <PaymentsTab /> : <SettlementsTab />}
      </Tabs>
    </>
  );
}
