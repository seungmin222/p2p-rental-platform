// S2 채택 조건 확인: 검색 위치 조건(동네 3곳 × 범위 3개) 안에서 키워드마다 LIKE 결과와 FULLTEXT(ngram) MATCH 결과가 같은지.
// FULLTEXT 인덱스 idx_items_name_description이 있어야 한다. 출력은 SQL(표준 출력)이고, 결과 한 행이 한 조합이다.
// only_like·only_match가 모두 0이어야 결과 id 집합과 개수가 같다.
// 사용: node scripts/load/query-index/fulltext-equality.mjs > eq.sql && mysql ... rental_load < eq.sql
const KEYWORDS = ['텐트', '캠핑의자', '드릴', '카메라', '삼각대', '유모차', '카시트', '스피커', '빔프로젝터',
	'보드게임', '스키', '제습기', '청소기', '캐리어', '한복', '자전거'];
// 부하 시나리오 밖의 경계 사례: 공백 포함, 이름 숫자, 설명에만 있는 말, 영문, 한 글자(앱은 LIKE로 되돌린다)
const EDGE = ['미러리스 카메라', '212', '직거래', 'LED', '텐'];
const REGIONS = [8, 210, 359];
const RANGES = [1500, 3000, 6000];
const R = 6370986.0;

const out = ["SET @guard = (SELECT IF(IFNULL(DATABASE(), '') = 'rental_load', 1, (SELECT 1 UNION SELECT 2)));"];
for (const regionId of REGIONS) {
	for (const meters of RANGES) {
		const a = meters / R;
		const latD = `DEGREES(${a})`;
		const lonD = `DEGREES(ASIN(LEAST(1, SIN(${a}) / COS(RADIANS(r.center_lat)))))`;
		for (const k of [...KEYWORDS, ...EDGE]) {
			const like = `(i.name LIKE '%${k}%' OR i.description LIKE '%${k}%')`;
			const match = `(MATCH(i.name, i.description) AGAINST('"${k}"' IN BOOLEAN MODE) > 0)`;
			out.push(`SELECT ${regionId} AS region_id, ${meters} AS meters, '${k}' AS keyword, SUM(${like}) AS like_count, `
				+ `SUM(${match}) AS match_count, SUM(${like} AND NOT ${match}) AS only_like, SUM(${match} AND NOT ${like}) AS only_match `
				+ `FROM items i JOIN regions r ON r.id = ${regionId} WHERE i.status = 'AVAILABLE' `
				+ `AND i.latitude BETWEEN r.center_lat - ${latD} AND r.center_lat + ${latD} `
				+ `AND i.longitude BETWEEN r.center_lng - ${lonD} AND r.center_lng + ${lonD} `
				+ `AND ST_Distance_Sphere(POINT(i.longitude, i.latitude), POINT(r.center_lng, r.center_lat)) <= ${meters};`);
		}
	}
}
console.log(out.join('\n'));
