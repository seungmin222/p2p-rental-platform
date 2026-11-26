// 시나리오 C: 같은 물품·같은 기간 경합. ROUNDS번 반복하며, 매 라운드 VUS명이 같은 시각에 같은 물품·같은 기간을 예약한다.
// 기대: 라운드마다 201은 정확히 1건, 나머지는 409(RENTAL_PERIOD_CONFLICT 또는 락 대기 초과 RENTAL_LOCK_TIMEOUT).
// 끝나면 check-duplicates.sql로 DB의 기간 겹침(중복 예약)이 0건인지 확인한다.
// 실행: docker compose -f infra/docker-compose.load.yml run --rm k6 run -e VUS=100 /scripts/reserve-contention.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { Counter, Rate } from 'k6/metrics';
import { BASE, ITEM_COUNT, SUMMARY_TREND_STATS, auth, errorCode, isListed, kstDate, loginMany, makeSummary, ownerOf } from './lib.js';

const VUS = Number(__ENV.VUS || 100);
const ROUNDS = Number(__ENV.ROUNDS || 20);
// 라운드 간격(ms). 락 대기(rental.lock-wait 3초)보다 길게 둬 라운드끼리 섞이지 않게 한다
const GAP_MS = Number(__ENV.GAP_MS || 5000);
// 실행마다 다른 물품을 쓰도록 시작 물품을 바꿀 수 있다(기본: 뒤쪽 물품부터 내려온다)
const ITEM_FROM = Number(__ENV.ITEM_FROM || ITEM_COUNT);

const created = new Counter('reserve_created');
const conflict = new Counter('reserve_conflict');
const lockTimeout = new Counter('reserve_lock_timeout');
const failed = new Rate('reserve_failed');

export const options = {
	setupTimeout: '5m',
	summaryTrendStats: SUMMARY_TREND_STATS,
	scenarios: {
		contention: {
			executor: 'per-vu-iterations',
			vus: VUS,
			iterations: ROUNDS,
			maxDuration: `${Math.ceil((ROUNDS * GAP_MS) / 1000) + 120}s`,
		},
	},
	thresholds: {
		reserve_failed: ['rate<0.01'],
		reserve_created: [`count==${ROUNDS}`],
		// 요청 수·응답 시간을 요약에 나오게 하려는 것(판정 아님)
		'http_reqs{api:reserve}': ['count>=0'],
		'http_req_duration{api:reserve}': ['max>=0'],
	},
};

export function setup() {
	const tokens = loginMany(Array.from({ length: VUS }, (_, i) => i + 1));
	// 대상 물품: 목록에 보이고, 소유자가 이번 가상 사용자(회원 1..VUS)가 아닌 물품
	const items = [];
	for (let id = ITEM_FROM; items.length < ROUNDS && id > 0; id--) {
		if (isListed(id) && ownerOf(id) > VUS) {
			items.push(id);
		}
	}
	// 시나리오 B(오늘+1~152일)·시드 예약(오늘+200일~)과 겹치지 않는 기간
	return { tokens, items, startDate: kstDate(160), endDate: kstDate(162), t0: Date.now() + 3000 };
}

export default function (data) {
	const round = exec.vu.iterationInScenario;
	const wait = data.t0 + round * GAP_MS - Date.now();
	if (wait > 0) {
		sleep(wait / 1000);
	}
	const res = http.post(`${BASE}/api/rentals`,
		JSON.stringify({ itemId: data.items[round], startDate: data.startDate, endDate: data.endDate }),
		{ headers: { ...auth(data.tokens[__VU]), 'Content-Type': 'application/json' }, tags: { api: 'reserve' } });

	const code = res.status === 201 ? null : errorCode(res);
	if (res.status === 201) {
		created.add(1);
	} else if (code === 'RENTAL_PERIOD_CONFLICT') {
		conflict.add(1);
	} else if (code === 'RENTAL_LOCK_TIMEOUT') {
		lockTimeout.add(1);
	}
	const ok = res.status === 201 || (res.status === 409 && (code === 'RENTAL_PERIOD_CONFLICT' || code === 'RENTAL_LOCK_TIMEOUT'));
	check(res, { '201 또는 예상된 409': () => ok });
	failed.add(!ok);
	if (!ok) {
		console.warn(`round ${round}: ${res.status} ${res.body && res.body.slice(0, 200)}`);
	}
}

export const handleSummary = makeSummary(`reserve-contention-vus${VUS}`);
