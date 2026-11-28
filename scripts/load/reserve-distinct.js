// 시나리오 B: 예약, 서로 다른 물품. 가상 사용자마다 자기 회원으로 무작위 물품·기간(오늘+1~150일 시작, 1~3일)을 예약한다.
// 무작위가 겹쳐 이미 예약된 기간이면 409 RENTAL_PERIOD_CONFLICT가 정상 응답이다(오류로 세지 않는다).
// 실행: docker compose -f infra/docker-compose.load.yml run --rm k6 run -e VUS=100 /scripts/reserve-distinct.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';
import { BASE, SUMMARY_TREND_STATS, auth, errorCode, kstDate, loginMany, makeSummary, randInt, randomListedItem } from './lib.js';

const VUS = Number(__ENV.VUS || 100);
const THINK = Number(__ENV.THINK ?? 1);

const created = new Counter('reserve_created');
const conflict = new Counter('reserve_conflict');
const lockTimeout = new Counter('reserve_lock_timeout');
const concurrentUpdate = new Counter('reserve_concurrent_update');
// 결과 파일 이름에 붙일 구성 이름(락 전략 비교, 예: RC-REDISSON)
const LABEL = __ENV.LABEL ? `-${__ENV.LABEL}` : '';
const failed = new Rate('reserve_failed');

export const options = {
	setupTimeout: '5m',
	summaryTrendStats: SUMMARY_TREND_STATS,
	scenarios: {
		reserve: {
			executor: 'ramping-vus',
			startVUs: 0,
			stages: [
				{ duration: __ENV.RAMP || '2m', target: VUS },
				{ duration: __ENV.HOLD || '5m', target: VUS },
				{ duration: '30s', target: 0 },
			],
			gracefulRampDown: '10s',
		},
	},
	thresholds: {
		// 목표(docs/architecture.md §7): 예약 p95 ≤ 1s
		'http_req_duration{api:reserve}': ['p(95)<1000'],
		reserve_failed: ['rate<0.01'],
		// 요청 수를 요약에 나오게 하려는 것(판정 아님)
		'http_reqs{api:reserve}': ['count>=0'],
	},
};

export function setup() {
	// 회원 1..VUS 가 각 가상 사용자. 시나리오 C(contention)와 같은 회원을 쓰지만 날짜 구간이 다르다
	return { tokens: loginMany(Array.from({ length: VUS }, (_, i) => i + 1)) };
}

export default function (data) {
	const memberId = ((__VU - 1) % VUS) + 1;
	const itemId = randomListedItem(memberId);
	const start = randInt(1, 150);
	const res = http.post(`${BASE}/api/rentals`,
		JSON.stringify({ itemId, startDate: kstDate(start), endDate: kstDate(start + randInt(0, 2)) }),
		{ headers: { ...auth(data.tokens[memberId]), 'Content-Type': 'application/json' }, tags: { api: 'reserve' } });

	const code = res.status === 201 ? null : errorCode(res);
	if (res.status === 201) {
		created.add(1);
	} else if (code === 'RENTAL_PERIOD_CONFLICT') {
		conflict.add(1);
	} else if (code === 'RENTAL_LOCK_TIMEOUT') {
		lockTimeout.add(1);
	} else if (code === 'COMMON_CONCURRENT_UPDATE') {
		concurrentUpdate.add(1);
	}
	// COMMON_CONCURRENT_UPDATE는 락 전략 비교의 실험 전략(낙관적 락 충돌·교착)에서만 나온다
	const ok = res.status === 201 || code === 'RENTAL_PERIOD_CONFLICT' || code === 'COMMON_CONCURRENT_UPDATE';
	check(res, { 'reserve 201 또는 기간 충돌 409': () => ok });
	failed.add(!ok);
	if (!ok && __ENV.DEBUG) {
		console.warn(`${res.status} ${res.body && res.body.slice(0, 200)}`);
	}

	if (THINK > 0) {
		sleep(THINK * (0.5 + Math.random()));
	}
}

export const handleSummary = makeSummary(`reserve-distinct-vus${VUS}${LABEL}`);
