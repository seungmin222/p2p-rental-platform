// k6 공통 도구. 더미 데이터 규칙은 seed.sql 머리말과 같아야 한다.
import http from 'k6/http';
import { check } from 'k6';

export const BASE = __ENV.BASE_URL || 'http://localhost:18080';
export const PASSWORD = 'loadtest123!';
export const MEMBER_COUNT = 10000;
// seed.sql @item_count와 같게(기본 30000)
export const ITEM_COUNT = Number(__ENV.ITEM_COUNT || 30000);
export const CATEGORY_COUNT = 8;
export const KEYWORDS = ['텐트', '캠핑의자', '드릴', '카메라', '삼각대', '유모차', '카시트', '스피커', '빔프로젝터',
	'보드게임', '스키', '제습기', '청소기', '캐리어', '한복', '자전거'];

export const ownerOf = (itemId) => (itemId * 7) % MEMBER_COUNT + 1;
export const isListed = (itemId) => itemId % 50 !== 0 && itemId % 50 !== 1;

export const randInt = (min, max) => min + Math.floor(Math.random() * (max - min + 1));
export const pick = (arr) => arr[Math.floor(Math.random() * arr.length)];

// 앱은 Asia/Seoul 기준으로 "오늘"을 판단한다(k6 컨테이너는 UTC)
export function kstDate(plusDays) {
	const d = new Date(Date.now() + 9 * 3600 * 1000 + plusDays * 86400 * 1000);
	return d.toISOString().slice(0, 10);
}

export function randomListedItem(excludeOwner) {
	for (;;) {
		const id = randInt(1, ITEM_COUNT);
		if (isListed(id) && ownerOf(id) !== excludeOwner) {
			return id;
		}
	}
}

export const auth = (token) => ({ Authorization: `Bearer ${token}` });

/** 회원 memberIds를 25명씩 묶어 로그인한다. BCrypt 비용이 본 측정에 섞이지 않게 setup에서만 부른다. */
export function loginMany(memberIds) {
	const tokens = {};
	for (let i = 0; i < memberIds.length; i += 25) {
		const chunk = memberIds.slice(i, i + 25);
		const res = http.batch(chunk.map((id) => ({
			method: 'POST',
			url: `${BASE}/api/auth/login`,
			body: JSON.stringify({ email: `load${id}@load.test`, password: PASSWORD }),
			params: { headers: { 'Content-Type': 'application/json' }, tags: { api: 'login' } },
		})));
		res.forEach((r, j) => {
			check(r, { 'login 200': (x) => x.status === 200 });
			if (r.status === 200) {
				tokens[chunk[j]] = r.json('data.accessToken');
			}
		});
	}
	const count = Object.keys(tokens).length;
	if (count !== memberIds.length) {
		throw new Error(`로그인 실패: ${memberIds.length - count}명`);
	}
	return tokens;
}

export const errorCode = (res) => {
	try {
		return res.json('error.code');
	} catch (e) {
		return null;
	}
};

const TREND_STATS = ['avg', 'med', 'p(95)', 'p(99)', 'max'];
export const SUMMARY_TREND_STATS = ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'];

function fmtTrend(name, m) {
	if (!m) {
		return null;
	}
	return `${name.padEnd(52)} ${TREND_STATS.map((s) => `${s}=${(m.values[s] ?? 0).toFixed(1)}`).join(' ')}`;
}

/** 결과 JSON을 scripts/load/results/에 남기고, 표에 옮길 지표만 콘솔에 짧게 찍는다. */
export function makeSummary(name) {
	return (data) => {
		const lines = [`== ${name}`];
		for (const [key, m] of Object.entries(data.metrics)) {
			if (m.type === 'trend' && key.startsWith('http_req_duration{api:')) {
				lines.push(fmtTrend(key, m));
			}
		}
		for (const [key, m] of Object.entries(data.metrics)) {
			if (m.type === 'counter') {
				lines.push(`${key.padEnd(52)} count=${m.values.count} rate=${m.values.rate.toFixed(2)}/s`);
			}
			if (m.type === 'rate') {
				lines.push(`${key.padEnd(52)} rate=${(m.values.rate * 100).toFixed(3)}% (${m.values.passes}/${m.values.passes + m.values.fails})`);
			}
		}
		const stamp = new Date().toISOString().replace(/[:.]/g, '-');
		return {
			stdout: lines.filter(Boolean).join('\n') + '\n',
			[`/scripts/results/${name}-${stamp}.json`]: JSON.stringify(data, null, 1),
		};
	};
}
