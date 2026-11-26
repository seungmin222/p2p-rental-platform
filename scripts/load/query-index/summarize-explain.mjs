// EXPLAIN ANALYZE 출력(mysql 배치 모드)을 쿼리별 한 줄로 줄인다: 전체 시간(ms) | items(i1_0) 접근 방법 | 그 접근이 읽은 행 수.
// 사용: node scripts/load/query-index/summarize-explain.mjs < explain.txt
import { readFileSync } from 'node:fs';
const blocks = [];
for (const line of readFileSync(0, 'utf8').replaceAll(String.fromCharCode(92) + 'n', '\n').split(/\r?\n/)) {
	if (/^(feed|search)_/.test(line)) {
		blocks.push({ label: line.trim(), plan: [] });
	} else if (blocks.length > 0 && /->/.test(line)) {
		blocks.at(-1).plan.push(line);
	}
}
for (const { label, plan } of blocks) {
	const total = plan[0]?.match(/actual time=[\d.]+\.\.([\d.]+)/)?.[1];
	const access = plan.filter((p) => /(scan|lookup|search|Full-text)[^(]*on i1_0/i.test(p));
	const desc = access.map((a) => {
		const how = a.trim().replace(/^-> /, '').replace(/\s+\(cost.*$/, '');
		const rows = a.match(/actual time=[^)]*rows=([\d.e+]+)/)?.[1];
		return `${how} rows=${rows}`;
	});
	console.log(`${label} | ${total} ms | ${desc.join(' ; ')}`);
}
