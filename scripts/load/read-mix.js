// 시나리오 A: 조회 혼합 (피드 50% · 검색 30% · 상세 20%), 회원·비회원 반씩.
// 실행: docker compose -f infra/docker-compose.load.yml run --rm k6 run -e VUS=100 /scripts/read-mix.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';
import {
	BASE, CATEGORY_COUNT, ITEM_COUNT, KEYWORDS, SUMMARY_TREND_STATS, auth, isListed, kstDate, loginMany, makeSummary,
	pick, randInt,
} from './lib.js';

const VUS = Number(__ENV.VUS || 100);
const LOGIN_USERS = Number(__ENV.LOGIN_USERS || 200);
// 사용자 한 명이 요청 사이에 쉬는 시간(초). 0.5~1.5초 무작위(평균 THINK)
const THINK = Number(__ENV.THINK ?? 1);
// ONLY=feed|search면 그 API만 보낸다(조회 인덱스 실험에서 피드·검색을 따로 잰다). 비우면 피드 50%·검색 30%·상세 20%
const ONLY = __ENV.ONLY || '';
if (ONLY && !['feed', 'search'].includes(ONLY)) {
	throw new Error('ONLY는 feed 또는 search다');
}

const failed = new Rate('read_failed');

export const options = {
	setupTimeout: '5m',
	summaryTrendStats: SUMMARY_TREND_STATS,
	scenarios: {
		read: {
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
		// 목표(docs/architecture.md §7): 조회 p95 ≤ 500ms. 실패해도 중단하지 않고 결과에 표시만 한다
		'http_req_duration{api:feed}': ['p(95)<500'],
		'http_req_duration{api:search}': ['p(95)<500'],
		'http_req_duration{api:detail}': ['p(95)<500'],
		read_failed: ['rate<0.01'],
		// API별 요청 수를 요약에 나오게 하려는 것(판정 아님)
		'http_reqs{api:feed}': ['count>=0'],
		'http_reqs{api:search}': ['count>=0'],
		'http_reqs{api:detail}': ['count>=0'],
	},
};

export function setup() {
	const ids = Array.from({ length: LOGIN_USERS }, (_, i) => i + 1);
	const tokens = loginMany(ids);
	// 비회원 요청에 쓸 기준 동네: 회원들의 대표 동네 id
	const regionIds = new Set();
	for (const id of ids.slice(0, 60)) {
		const res = http.get(`${BASE}/api/members/me/regions`, { headers: auth(tokens[id]), tags: { api: 'setup', name: 'setup' } });
		for (const r of res.json('data') || []) {
			if (r.isPrimary) {
				regionIds.add(r.regionId);
			}
		}
	}
	return { tokens: Object.values(tokens), regionIds: [...regionIds] };
}

// VU마다 이어서 보는 상태(직전 목록의 물품·커서)
let lastItemIds = [];
let lastCursor = null;

function get(url, token, api) {
	// name 태그로 URL을 묶는다(쿼리·id마다 시계열이 생기지 않게)
	const params = { tags: { api, name: api } };
	if (token) {
		params.headers = auth(token);
	}
	const res = http.get(url, params);
	const ok = check(res, { [`${api} 200`]: (r) => r.status === 200 });
	failed.add(!ok, { api });
	return ok ? res : null;
}

function collect(res) {
	const items = res.json('data.items') || res.json('data.content') || [];
	if (items.length > 0) {
		lastItemIds = items.map((i) => i.itemId);
	}
}

export default function (data) {
	// 짝수 VU는 회원(대표 동네 기준), 홀수 VU는 비회원(regionId 지정)
	const token = __VU % 2 === 0 ? data.tokens[__VU % data.tokens.length] : null;
	const regionParam = token ? '' : `regionId=${pick(data.regionIds)}&`;
	const r = ONLY === 'feed' ? 0 : ONLY === 'search' ? 0.5 : Math.random();

	if (r < 0.5) {
		let q = regionParam;
		if (Math.random() < 0.2) {
			q += `categoryId=${randInt(1, CATEGORY_COUNT)}&`;
		}
		if (lastCursor && Math.random() < 0.3) {
			q += `cursor=${encodeURIComponent(lastCursor)}&`;
		}
		const res = get(`${BASE}/api/items/feed?${q}`, token, 'feed');
		if (res) {
			collect(res);
			lastCursor = res.json('data.nextCursor');
		}
	} else if (r < 0.8) {
		let q = regionParam;
		if (Math.random() < 0.6) {
			q += `keyword=${encodeURIComponent(pick(KEYWORDS))}&`;
		}
		if (Math.random() < 0.3) {
			q += `categoryId=${randInt(1, CATEGORY_COUNT)}&`;
		}
		if (Math.random() < 0.3) {
			const start = randInt(1, 60);
			q += `startDate=${kstDate(start)}&endDate=${kstDate(start + randInt(0, 3))}&availableOnly=true&`;
		}
		q += `sort=${pick(['ACCURACY', 'LATEST', 'PRICE_ASC'])}&page=${Math.random() < 0.7 ? 0 : randInt(1, 2)}`;
		const res = get(`${BASE}/api/items?${q}`, token, 'search');
		if (res) {
			collect(res);
		}
	} else {
		let id = lastItemIds.length > 0 && Math.random() < 0.8 ? pick(lastItemIds) : 0;
		while (!isListed(id)) {
			id = randInt(1, ITEM_COUNT);
		}
		get(`${BASE}/api/items/${id}`, token, 'detail');
	}

	if (THINK > 0) {
		sleep(THINK * (0.5 + Math.random()));
	}
}

export const handleSummary = makeSummary(`read-mix${ONLY ? `-${ONLY}` : ''}-vus${VUS}`);
