// 조회 인덱스 실험(chore/query-index-experiment)의 대표 쿼리를 만든다. 앱(ItemListingRepositoryImpl)이 보내는 SQL을
// general_log로 뜬 모양 그대로 두고, 동네 중심·범위만 바꾼다. 출력은 EXPLAIN ANALYZE용 SQL(표준 출력).
// 사용: node scripts/load/query-index/gen-queries.mjs <mode> <denseLat> <denseLng> <sparseLat> <sparseLng> [today]
//   mode: box(현재, F0~F3) | mbr(F4: MBRContains 선필터) | fulltext(S2: MATCH ... AGAINST)
const [mode = 'box', dLat, dLng, sLat, sLng, today = '2026-11-26'] = process.argv.slice(2);
const now = `${today} 12:00:00`;
const R = 6370986.0;
const deg = (r) => (r * 180) / Math.PI;

function box(lat, lng, meters) {
	const a = meters / R;
	const latD = deg(a);
	const lonD = deg(Math.asin(Math.min(1, Math.sin(a) / Math.cos((lat * Math.PI) / 180))));
	return { minLat: lat - latD, maxLat: lat + latD, minLon: lng - lonD, maxLon: lng + lonD };
}

function range(lat, lng, meters) {
	const b = box(lat, lng, meters);
	const dist = `cast(st_distance_sphere(point(case when (i1_0.longitude=-180.0) then 180.0 else i1_0.longitude end,i1_0.latitude),point(${lng},${lat})) as float(53))<=${meters.toFixed(1)}`;
	if (mode === 'mbr') {
		// SRID 4326 축 순서는 (위도 경도)
		const poly = `ST_GeomFromText('POLYGON((${b.minLat} ${b.minLon},${b.maxLat} ${b.minLon},${b.maxLat} ${b.maxLon},${b.minLat} ${b.maxLon},${b.minLat} ${b.minLon}))',4326)`;
		return `(MBRContains(${poly},i1_0.location) and ${dist})`;
	}
	return `(i1_0.latitude between ${b.minLat} and ${b.maxLat} and i1_0.longitude between ${b.minLon} and ${b.maxLon} and ${dist})`;
}

const projection = `i1_0.id,i1_0.region_id,i1_0.name,i1_0.price_per_day,i1_0.deposit,i1_0.status,i1_0.view_count,i1_0.wish_count,i1_0.chat_count,i1_0.bumped_at,exists(select 1 from rentals r1_0 where r1_0.item_id=i1_0.id and r1_0.status in ('RENTING','OVERDUE')),(select count(r2_0.id) from rentals r2_0 where r2_0.item_id=i1_0.id and r2_0.start_date>='${today}' and (r2_0.status='RESERVED' or r2_0.status='PENDING_PAYMENT' and r2_0.payment_expires_at>'${now}'))`;

function keyword(k) {
	if (mode === 'fulltext') {
		return `match(i1_0.name,i1_0.description) against('"${k}"' in boolean mode)`;
	}
	return `(i1_0.name like '%${k}%' escape '!' or i1_0.description like '%${k}%' escape '!')`;
}

const period = (s, e) => `(exists(select 1 from item_available_periods iap1_0 where iap1_0.item_id=i1_0.id and iap1_0.start_date<='${s}' and iap1_0.end_date>='${e}') and not((exists(select 1 from rentals r3_0 where r3_0.item_id=i1_0.id and r3_0.status in ('PENDING_PAYMENT','RESERVED','RENTING','OVERDUE') and r3_0.start_date<='${e}' and r3_0.end_date>='${s}' and (r3_0.status<>'PENDING_PAYMENT' or r3_0.payment_expires_at>'${now}')) or exists(select 1 from rental_extensions re1_0,rentals r4_0 where r4_0.id=re1_0.rental_id and r4_0.item_id=i1_0.id and re1_0.status='PENDING_PAYMENT' and re1_0.expires_at>'${now}' and re1_0.prev_end_date<'${e}' and re1_0.new_end_date>='${s}'))))`;

const D = [Number(dLat), Number(dLng)];
const SP = [Number(sLat), Number(sLng)];
const q = [];
const add = (label, sql) => q.push(`SELECT '${label}' AS q;\nEXPLAIN ANALYZE ${sql};`);
const feed = (c, m, extra = '') => `select ${projection} from items i1_0 where i1_0.status='AVAILABLE' and ${range(c[0], c[1], m)}${extra} order by i1_0.bumped_at desc,i1_0.id desc limit 21`;
add('feed_dense_3km', feed(D, 3000));
add('feed_sparse_3km', feed(SP, 3000));
add('feed_dense_6km', feed(D, 6000));
add('feed_dense_page5', feed(D, 3000, ` and (i1_0.bumped_at<'${today}' - interval 30 day)`));
const where = (c, extra) => `i1_0.status='AVAILABLE' and ${range(c[0], c[1], 3000)} and ${extra}`;
const accuracy = (k) => `case when (i1_0.name like '%${k}%' escape '!') then 0 else 1 end,`;
add('search_kw_list', `select ${projection} from items i1_0 where ${where(D, keyword('텐트'))} order by ${accuracy('텐트')}i1_0.bumped_at desc,i1_0.id desc limit 0,20`);
add('search_kw_count', `select count(i1_0.id) from items i1_0 where ${where(D, keyword('텐트'))}`);
add('search_cat_price_count', `select count(i1_0.id) from items i1_0 where ${where(D, "i1_0.category_id=3 and i1_0.price_per_day>=5000 and i1_0.price_per_day<=15000")}`);
add('search_period_list', `select ${projection} from items i1_0 where ${where(D, period('2026-12-10', '2026-12-12'))} order by i1_0.bumped_at desc,i1_0.id desc limit 0,20`);
console.log(q.join('\n'));
